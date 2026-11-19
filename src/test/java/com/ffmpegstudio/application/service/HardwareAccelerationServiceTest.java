package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.CodecInfo;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.HardwareBackend;
import com.ffmpegstudio.domain.model.HardwareEncoderProbeResult;
import com.ffmpegstudio.domain.model.HardwareProbeReport;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.infrastructure.config.JsonSettingsRepository;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegCapabilityDetector;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HardwareAccelerationService Tests")
class HardwareAccelerationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should differentiate compiled vs non-functional hardware encoders when driver fails")
    void shouldDifferentiateCompiledVsNonFunctionalEncoders() {
        FFmpegProcessExecutor fakeExecutor = new FFmpegProcessExecutor() {
            @Override
            public ProcessExecutionResult execute(List<String> command, Duration timeout) {
                return new ProcessExecutionResult(1, List.of(),
                        List.of("[AMF] DLL libamfrt64.so.1 failed to open"), false);
            }
        };

        FFmpegCapabilities fakeCaps = new FFmpegCapabilities(
                "8.1.3",
                List.of("amf"),
                List.of(new CodecInfo("h264_amf", "AMD AMF H.264", CodecInfo.CodecType.VIDEO, true, false)),
                List.of(),
                List.of(),
                List.of()
        );

        DetectCapabilitiesUseCase fakeCapUseCase = new DetectCapabilitiesUseCase() {
            @Override public ExecutableInfo checkFFmpeg(String path) { return null; }
            @Override public ExecutableInfo checkFFprobe(String path) { return null; }
            @Override public FFmpegCapabilities getCapabilities(String ffmpegPath) { return fakeCaps; }
        };

        ManageSettingsUseCase fakeSettings = new ManageSettingsUseCase() {
            @Override public AppSettings getSettings() { return AppSettings.createDefault(); }
            @Override public void updateSettings(AppSettings s) {}
            @Override public AppSettings resetToDefaults() { return AppSettings.createDefault(); }
        };

        HardwareAccelerationService service = new HardwareAccelerationService(fakeExecutor, fakeCapUseCase, fakeSettings);
        HardwareProbeReport report = service.probeAll(new AtomicBoolean(false));

        HardwareEncoderProbeResult amfResult = report.encoderResults().stream()
                .filter(r -> r.encoderName().equals("h264_amf"))
                .findFirst()
                .orElseThrow();

        assertTrue(amfResult.backendCompiled());
        assertTrue(amfResult.encoderCompiled());
        assertFalse(amfResult.hardwareFunctional());
        assertTrue(amfResult.diagnosticMessage().contains("libamfrt64.so.1"));

        HardwareEncoderProbeResult nvencResult = report.encoderResults().stream()
                .filter(r -> r.encoderName().equals("h264_nvenc"))
                .findFirst()
                .orElseThrow();
        assertFalse(nvencResult.backendCompiled());
        assertFalse(nvencResult.hardwareFunctional());
    }

    @Test
    @DisplayName("Should run real conditional hardware probe on local system without requiring GPU")
    void shouldRunRealConditionalHardwareProbe() {
        FileSystemService fs = new FileSystemService();
        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent(), "FFmpeg not installed; skipping real hardware probe");

        FFmpegProcessExecutor executor = new FFmpegProcessExecutor();
        FFmpegCapabilityDetector capDetector = new FFmpegCapabilityDetector(executor, fs);
        ManageSettingsUseCase settings = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fs));
        DetectCapabilitiesUseCase capUseCase = new DetectCapabilitiesService(capDetector);

        HardwareAccelerationService service = new HardwareAccelerationService(executor, capUseCase, settings);
        HardwareProbeReport report = service.probeAll(new AtomicBoolean(false));

        assertNotNull(report);
        assertFalse(report.encoderResults().isEmpty());
        for (HardwareEncoderProbeResult r : report.encoderResults()) {
            System.out.printf("[HW PROBE] %s | backend=%s | enc=%s | functional=%s | msg=%s%n",
                    r.encoderName(), r.backendCompiled(), r.encoderCompiled(), r.hardwareFunctional(), r.diagnosticMessage());
        }
    }
}
