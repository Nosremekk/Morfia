package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityIntegrationTest {

    private FFmpegCapabilityDetector detector;
    private FileSystemService fileSystemService;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService();
        FFmpegProcessExecutor executor = new FFmpegProcessExecutor();
        detector = new FFmpegCapabilityDetector(executor, fileSystemService);
    }

    @Test
    @DisplayName("Should detect installed FFmpeg on local system and parse actual capabilities")
    void shouldDetectRealFFmpegWhenInstalled() {
        boolean ffmpegFound = fileSystemService.findInPath("ffmpeg").isPresent();
        Assumptions.assumeTrue(ffmpegFound, "FFmpeg binary is not found in PATH; skipping integration test");

        ExecutableInfo info = detector.checkExecutable("ffmpeg", "FFmpeg", "-version");
        assertThat(info.isAvailable()).isTrue();
        assertThat(info.version()).isNotEmpty();
        assertThat(info.version()).doesNotContain("Unknown");

        FFmpegCapabilities capabilities = detector.detectCapabilities("ffmpeg");
        assertThat(capabilities.version()).isEqualTo(info.version());
        assertThat(capabilities.encoders()).isNotEmpty();
        assertThat(capabilities.decoders()).isNotEmpty();
        assertThat(capabilities.filters()).isNotEmpty();
        assertThat(capabilities.formats()).isNotEmpty();

        // Print detected capabilities for diagnostic logging
        System.out.printf("[INTEGRATION] Real FFmpeg detected: %s%n", capabilities.version());
        System.out.printf("[INTEGRATION] Encoders found: %d%n", capabilities.encoders().size());
        System.out.printf("[INTEGRATION] HW Accelerations found: %s%n", capabilities.hardwareAccelerators());
    }

    @Test
    @DisplayName("Should detect installed FFprobe on local system")
    void shouldDetectRealFFprobeWhenInstalled() {
        boolean ffprobeFound = fileSystemService.findInPath("ffprobe").isPresent();
        Assumptions.assumeTrue(ffprobeFound, "FFprobe binary is not found in PATH; skipping integration test");

        ExecutableInfo info = detector.checkExecutable("ffprobe", "FFprobe", "-version");
        assertThat(info.isAvailable()).isTrue();
        assertThat(info.version()).isNotEmpty();
        assertThat(info.version()).doesNotContain("Unknown");
    }
}
