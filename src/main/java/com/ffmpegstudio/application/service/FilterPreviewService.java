package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.filter.FilterGraphBuilder;
import com.ffmpegstudio.domain.model.filter.MediaFilter;
import com.ffmpegstudio.domain.model.filter.PreviewResult;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous, bounded-cache frame preview service using FFmpeg.
 * Discards stale requests automatically and avoids loading full videos into memory.
 */
public class FilterPreviewService {
    private static final Logger logger = LoggerFactory.getLogger(FilterPreviewService.class);
    private static final int MAX_CACHE_ENTRIES = 16;
    private static final Duration PREVIEW_TIMEOUT = Duration.ofSeconds(10);

    private final FFmpegProcessExecutor processExecutor;
    private final ManageSettingsUseCase settingsUseCase;
    private final FilterGraphBuilder graphBuilder = new FilterGraphBuilder();
    private final Path previewCacheDir;
    private final AtomicLong latestRequestId = new AtomicLong(0);

    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ffmpeg-studio-preview");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Path> frameCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
                    if (size() > MAX_CACHE_ENTRIES) {
                        deleteQuietly(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            }
    );

    public FilterPreviewService(FFmpegProcessExecutor processExecutor, ManageSettingsUseCase settingsUseCase) {
        this.processExecutor = Objects.requireNonNull(processExecutor);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        try {
            this.previewCacheDir = Files.createTempDirectory("ffmpeg-studio-previews-");
            this.previewCacheDir.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize preview cache directory", e);
        }
    }

    public CompletableFuture<PreviewResult> generatePreviewAsync(MediaFile mediaFile, Duration timestamp, List<MediaFilter> filters) {
        Objects.requireNonNull(mediaFile, "mediaFile must not be null");
        Duration safeTs = (timestamp == null || timestamp.isNegative()) ? Duration.ZERO : timestamp;
        List<MediaFilter> safeFilters = filters == null ? List.of() : List.copyOf(filters);

        long requestId = latestRequestId.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            // Discard obsolete request before spawning FFmpeg
            if (requestId != latestRequestId.get()) {
                return null;
            }

            String ffmpeg = settingsUseCase.getSettings().ffmpegPath();
            String tsArg = String.format(Locale.US, "%.3f", safeTs.toMillis() / 1000.0);
            String vfExpr = graphBuilder.buildVideoFilterChain(safeFilters);

            List<String> notices = new ArrayList<>();
            boolean temporal = safeFilters.stream().anyMatch(MediaFilter::isTemporal);
            if (temporal) {
                notices.add("Filtros temporais (velocidade, fade, reverse, FPS) não podem ser representados integralmente em um único frame estático.");
            }

            String origKey = mediaFile.path().toAbsolutePath() + "|" + tsArg + "|orig";
            Path origFrame = getOrGenerateFrame(ffmpeg, mediaFile.path(), tsArg, "", origKey);

            if (requestId != latestRequestId.get()) {
                return null;
            }

            Path filteredFrame = origFrame;
            if (!vfExpr.isBlank()) {
                String filtKey = mediaFile.path().toAbsolutePath() + "|" + tsArg + "|" + vfExpr;
                filteredFrame = getOrGenerateFrame(ffmpeg, mediaFile.path(), tsArg, vfExpr, filtKey);
            }

            if (requestId != latestRequestId.get()) {
                return null;
            }

            return new PreviewResult(origFrame, filteredFrame, notices, temporal);
        }, previewExecutor);
    }

    private Path getOrGenerateFrame(String ffmpeg, Path input, String tsArg, String vfExpr, String cacheKey) {
        Path cached = frameCache.get(cacheKey);
        if (cached != null && Files.exists(cached)) {
            return cached;
        }

        Path outFile = previewCacheDir.resolve("preview_" + Math.abs(cacheKey.hashCode()) + "_" + System.nanoTime() + ".png");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");
        cmd.add("-ss");
        cmd.add(tsArg);
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add("-frames:v");
        cmd.add("1");

        if (vfExpr != null && !vfExpr.isBlank()) {
            cmd.add("-vf");
            cmd.add(vfExpr);
        }
        cmd.add(outFile.toAbsolutePath().toString());

        ProcessExecutionResult res = processExecutor.execute(cmd, PREVIEW_TIMEOUT);
        if (!res.isSuccess() || !Files.exists(outFile)) {
            String err = res.standardErrorLines().isEmpty() ? "exit=" + res.exitCode() : res.standardErrorLines().get(res.standardErrorLines().size() - 1);
            throw new IllegalStateException("Falha ao gerar pré-visualização: " + err);
        }

        frameCache.put(cacheKey, outFile);
        return outFile;
    }

    public void clearCache() {
        synchronized (frameCache) {
            for (Path p : frameCache.values()) {
                deleteQuietly(p);
            }
            frameCache.clear();
        }
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
    }
}
