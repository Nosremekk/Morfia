package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides bounded-cache low-resolution FFmpeg proxy generation,
 * streaming PCM audio waveform peak aggregation (no full-file RAM buffering),
 * and lightweight JDK SourceDataLine audio playback.
 */
public class MediaPreviewService {
    private static final Logger logger = LoggerFactory.getLogger(MediaPreviewService.class);
    private static final int MAX_PROXY_CACHE = 4;

    private final FFmpegProcessExecutor processExecutor;
    private final ManageSettingsUseCase settingsUseCase;
    private final Path proxyCacheDir;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ffmpeg-studio-media-preview");
        t.setDaemon(true);
        return t;
    });

    private final Map<Path, Path> proxyCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_PROXY_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Path, Path> eldest) {
                    if (size() > MAX_PROXY_CACHE) {
                        deleteQuietly(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            }
    );

    private volatile Process activeAudioProcess;
    private volatile SourceDataLine activeAudioLine;
    private final AtomicBoolean audioPlaying = new AtomicBoolean(false);

    public MediaPreviewService(FFmpegProcessExecutor processExecutor, ManageSettingsUseCase settingsUseCase) {
        this.processExecutor = Objects.requireNonNull(processExecutor);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        try {
            this.proxyCacheDir = Files.createTempDirectory("ffmpeg-studio-proxies-");
            this.proxyCacheDir.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize proxy cache directory", e);
        }
    }

    /**
     * Generates a cancelable low-resolution H.264/AAC proxy for incompatible formats.
     */
    public CompletableFuture<Path> generateProxyAsync(Path inputFile, AtomicBoolean cancelSignal) {
        Objects.requireNonNull(inputFile, "inputFile must not be null");
        Path norm = inputFile.toAbsolutePath().normalize();
        Path existing = proxyCache.get(norm);
        if (existing != null && Files.exists(existing)) {
            return CompletableFuture.completedFuture(existing);
        }

        return CompletableFuture.supplyAsync(() -> {
            if (cancelSignal != null && cancelSignal.get()) {
                return null;
            }
            String ffmpeg = settingsUseCase.getSettings().ffmpegPath();
            Path outProxy = proxyCacheDir.resolve("proxy_" + Math.abs(norm.hashCode()) + "_" + System.nanoTime() + ".mp4");
            List<String> cmd = List.of(
                    ffmpeg, "-nostdin", "-hide_banner", "-y",
                    "-i", norm.toString(),
                    "-vf", "scale=640:-2",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "96k",
                    "-movflags", "+faststart",
                    outProxy.toString()
            );
            ProcessExecutionResult res = processExecutor.execute(cmd, Duration.ofMinutes(2));
            if ((cancelSignal != null && cancelSignal.get()) || !res.isSuccess() || !Files.exists(outProxy)) {
                deleteQuietly(outProxy);
                return null;
            }
            proxyCache.put(norm, outProxy);
            return outProxy;
        }, executor);
    }

    /**
     * Computes normalized waveform peak bins [0.0..1.0] by streaming 8kHz mono PCM
     * directly from FFmpeg stdout without loading the full audio file into RAM.
     */
    public CompletableFuture<float[]> extractWaveformPeaksAsync(Path inputFile, Duration totalDuration, int numBins) {
        Objects.requireNonNull(inputFile, "inputFile must not be null");
        int bins = Math.clamp(numBins, 16, 512);
        return CompletableFuture.supplyAsync(() -> {
            String ffmpeg = settingsUseCase.getSettings().ffmpegPath();
            long approxTotalSamples = (totalDuration != null && !totalDuration.isZero())
                    ? Math.max(bins, (totalDuration.toMillis() * 8000L) / 1000L)
                    : 8000L * 30L;
            long samplesPerBin = Math.max(1L, approxTotalSamples / bins);

            float[] peaks = new float[bins];
            List<String> cmd = List.of(
                    ffmpeg, "-nostdin", "-hide_banner", "-v", "error",
                    "-i", inputFile.toAbsolutePath().toString(),
                    "-vn", "-ac", "1", "-ar", "8000", "-f", "s16le", "pipe:1"
            );

            Process proc = null;
            try {
                proc = new ProcessBuilder(cmd).start();
                try (InputStream is = proc.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int bytesRead;
                    long sampleIdx = 0;
                    while ((bytesRead = is.read(buf)) != -1) {
                        for (int i = 0; i + 1 < bytesRead; i += 2) {
                            short sample = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
                            float amp = Math.abs(sample / 32768.0f);
                            int bin = (int) Math.min(bins - 1, sampleIdx / samplesPerBin);
                            if (amp > peaks[bin]) {
                                peaks[bin] = amp;
                            }
                            sampleIdx++;
                        }
                    }
                }
                proc.waitFor();
            } catch (Exception e) {
                logger.debug("Waveform extraction error: {}", e.getMessage());
            } finally {
                if (proc != null && proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
            return peaks;
        }, executor);
    }

    /**
     * Streams decoded stereo PCM from FFmpeg to the JDK audio output line.
     */
    public void playAudioAsync(Path inputFile, Duration startAt, float volumePercent) {
        stopAudio();
        audioPlaying.set(true);
        executor.submit(() -> {
            String ffmpeg = settingsUseCase.getSettings().ffmpegPath();
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg);
            cmd.add("-nostdin");
            cmd.add("-hide_banner");
            cmd.add("-v");
            cmd.add("error");
            if (startAt != null && !startAt.isZero() && !startAt.isNegative()) {
                cmd.add("-ss");
                cmd.add(String.format(java.util.Locale.US, "%.3f", startAt.toMillis() / 1000.0));
            }
            cmd.add("-i");
            cmd.add(inputFile.toAbsolutePath().toString());
            cmd.add("-vn");
            cmd.add("-ac");
            cmd.add("2");
            cmd.add("-ar");
            cmd.add("44100");
            cmd.add("-f");
            cmd.add("s16le");
            cmd.add("pipe:1");

            AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float dB = (float) (Math.log10(Math.max(0.01, volumePercent / 100.0)) * 20.0);
                    gain.setValue(Math.clamp(dB, gain.getMinimum(), gain.getMaximum()));
                }
                line.start();
                activeAudioLine = line;

                Process proc = new ProcessBuilder(cmd).start();
                activeAudioProcess = proc;

                byte[] buffer = new byte[8192];
                try (InputStream is = proc.getInputStream()) {
                    int read;
                    while (audioPlaying.get() && (read = is.read(buffer)) != -1) {
                        line.write(buffer, 0, read);
                    }
                }
                line.drain();
            } catch (Exception e) {
                logger.debug("Audio playback stopped or unavailable: {}", e.getMessage());
            } finally {
                stopAudio();
            }
        });
    }

    public void stopAudio() {
        audioPlaying.set(false);
        if (activeAudioProcess != null) {
            try { activeAudioProcess.destroyForcibly(); } catch (Exception ignored) {}
            activeAudioProcess = null;
        }
        if (activeAudioLine != null) {
            try {
                activeAudioLine.stop();
                activeAudioLine.close();
            } catch (Exception ignored) {}
            activeAudioLine = null;
        }
    }

    public void shutdown() {
        stopAudio();
        synchronized (proxyCache) {
            for (Path p : proxyCache.values()) {
                deleteQuietly(p);
            }
            proxyCache.clear();
        }
        executor.shutdownNow();
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        }
    }
}
