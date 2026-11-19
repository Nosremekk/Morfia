package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.HardwareBackend;
import com.ffmpegstudio.domain.model.HardwareEncoderProbeResult;
import com.ffmpegstudio.domain.model.HardwareProbeReport;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Service for probing real hardware acceleration availability by executing
 * short, isolated synthetic encoding tests with strict timeouts and cleanup.
 */
public class HardwareAccelerationService {
    private static final Logger logger = LoggerFactory.getLogger(HardwareAccelerationService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final FFmpegProcessExecutor processExecutor;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final ManageSettingsUseCase settingsUseCase;

    private volatile HardwareProbeReport lastReport;

    public HardwareAccelerationService(
            FFmpegProcessExecutor processExecutor,
            DetectCapabilitiesUseCase capabilitiesUseCase,
            ManageSettingsUseCase settingsUseCase
    ) {
        this.processExecutor = Objects.requireNonNull(processExecutor);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
    }

    public CompletableFuture<HardwareProbeReport> probeAllAsync(AtomicBoolean cancelSignal) {
        return CompletableFuture.supplyAsync(() -> probeAll(cancelSignal));
    }

    public HardwareProbeReport probeAll(AtomicBoolean cancelSignal) {
        String ffmpegPath = settingsUseCase.getSettings().ffmpegPath();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(ffmpegPath);

        List<String> driDevices = new ArrayList<>();
        List<String> accessibleDriDevices = new ArrayList<>();
        inspectDrmDevices(driDevices, accessibleDriDevices);

        String primaryRenderDevice = accessibleDriDevices.stream()
                .filter(d -> d.contains("renderD"))
                .findFirst()
                .orElse(accessibleDriDevices.isEmpty() ? null : accessibleDriDevices.get(0));

        List<HardwareEncoderProbeResult> results = new ArrayList<>();
        for (HardwareBackend backend : HardwareBackend.values()) {
            if (cancelSignal != null && cancelSignal.get()) {
                break;
            }
            boolean backendCompiled = caps.hasHwAccel(backend.hwaccelName());
            for (String encoder : backend.candidateEncoders()) {
                if (cancelSignal != null && cancelSignal.get()) {
                    break;
                }
                boolean encoderCompiled = caps.hasEncoder(encoder);
                if (!backendCompiled || !encoderCompiled) {
                    results.add(new HardwareEncoderProbeResult(
                            backend,
                            encoder,
                            backendCompiled,
                            encoderCompiled,
                            false,
                            backend == HardwareBackend.VAAPI ? primaryRenderDevice : null,
                            !backendCompiled
                                    ? "Backend '" + backend.hwaccelName() + "' não compilado no FFmpeg local."
                                    : "Encoder '" + encoder + "' não compilado no FFmpeg local.",
                            0
                    ));
                    continue;
                }

                results.add(probeSingleEncoder(ffmpegPath, backend, encoder, primaryRenderDevice));
            }
        }

        HardwareProbeReport report = new HardwareProbeReport(driDevices, accessibleDriDevices, results);
        this.lastReport = report;
        return report;
    }

    public HardwareProbeReport getLastReport() {
        return lastReport;
    }

    public HardwareEncoderProbeResult probeSingleEncoder(String ffmpegPath, HardwareBackend backend, String encoder, String renderDevice) {
        long startNs = System.nanoTime();
        Path tempOut = null;
        try {
            tempOut = Files.createTempFile("ffmpeg-hwprobe-" + encoder + "-", ".mp4");
            List<String> cmd = buildProbeCommand(ffmpegPath, backend, encoder, renderDevice, tempOut);
            ProcessExecutionResult res = processExecutor.execute(cmd, PROBE_TIMEOUT);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            if (res.timedOut()) {
                return new HardwareEncoderProbeResult(backend, encoder, true, true, false, renderDevice,
                        "Timeout após " + PROBE_TIMEOUT.toSeconds() + "s ao testar encoder.", elapsedMs);
            }
            if (res.isSuccess() && Files.exists(tempOut) && Files.size(tempOut) > 0) {
                return new HardwareEncoderProbeResult(backend, encoder, true, true, true, renderDevice,
                        "Teste sintético 256x256 concluído com sucesso.", elapsedMs);
            }

            String diag = extractDiagnostic(res.standardErrorLines(), res.exitCode());
            logger.debug("Probe {} failed (exit={}): {}", encoder, res.exitCode(), res.standardErrorLines());
            return new HardwareEncoderProbeResult(backend, encoder, true, true, false, renderDevice, diag, elapsedMs);
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            return new HardwareEncoderProbeResult(backend, encoder, true, true, false, renderDevice,
                    "Erro ao executar teste de hardware: " + e.getMessage(), elapsedMs);
        } finally {
            if (tempOut != null) {
                try {
                    Files.deleteIfExists(tempOut);
                } catch (IOException ignored) {}
            }
        }
    }

    private List<String> buildProbeCommand(String ffmpegPath, HardwareBackend backend, String encoder, String renderDevice, Path tempOut) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");

        if (backend == HardwareBackend.VAAPI) {
            String dev = (renderDevice != null && !renderDevice.isBlank()) ? renderDevice : "/dev/dri/renderD128";
            cmd.add("-vaapi_device");
            cmd.add(dev);
        }

        // 256x256 satisfies hardware minimum dimension constraints (e.g. hevc_vaapi >= 130px)
        cmd.add("-f");
        cmd.add("lavfi");
        cmd.add("-i");
        cmd.add("testsrc=duration=0.3:size=256x256:rate=25");

        if (backend == HardwareBackend.VAAPI) {
            cmd.add("-vf");
            cmd.add("format=nv12,hwupload");
        } else {
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        }

        cmd.add("-c:v");
        cmd.add(encoder);
        cmd.add(tempOut.toAbsolutePath().toString());
        return cmd;
    }

    private void inspectDrmDevices(List<String> allDevices, List<String> accessibleDevices) {
        Path driDir = Path.of("/dev/dri");
        if (!Files.isDirectory(driDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(driDir)) {
            stream.sorted().forEach(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith("renderD") || name.startsWith("card")) {
                    String abs = p.toAbsolutePath().toString();
                    allDevices.add(abs);
                    if (Files.isReadable(p) && Files.isWritable(p)) {
                        accessibleDevices.add(abs);
                    }
                }
            });
        } catch (IOException e) {
            logger.debug("Could not inspect /dev/dri: {}", e.getMessage());
        }
    }

    private String extractDiagnostic(List<String> stderr, int exitCode) {
        if (stderr == null || stderr.isEmpty()) {
            return "Falhou com código de saída " + exitCode;
        }
        for (String line : stderr) {
            String lower = line.toLowerCase();
            if (lower.contains("no usable encoding profile") || lower.contains("hardware does not support")
                    || lower.contains("cannot load") || lower.contains("permission denied")
                    || lower.contains("no va display") || lower.contains("failed to initialise")
                    || lower.contains("failed to open") || lower.contains("error creating a mfx")) {
                return line.trim();
            }
        }
        for (String line : stderr) {
            String lower = line.toLowerCase();
            if (lower.contains("failed") || lower.contains("error")) {
                return line.trim();
            }
        }
        return stderr.get(stderr.size() - 1).trim();
    }
}
