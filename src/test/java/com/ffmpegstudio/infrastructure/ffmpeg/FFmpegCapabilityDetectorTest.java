package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.domain.port.ProcessExecutorPort;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FFmpegCapabilityDetectorTest {

    private FakeProcessExecutor fakeExecutor;
    private FakeFileSystemService fakeFileSystemService;
    private FFmpegCapabilityDetector detector;

    @BeforeEach
    void setUp() {
        fakeExecutor = new FakeProcessExecutor();
        fakeFileSystemService = new FakeFileSystemService();
        detector = new FFmpegCapabilityDetector(fakeExecutor, fakeFileSystemService);
    }

    @Test
    @DisplayName("Should extract clean version from various FFmpeg version output banners")
    void shouldExtractVersionString() {
        assertThat(detector.parseVersionString("ffmpeg version 8.1.3 Copyright (c) 2000-2026 the FFmpeg developers"))
                .isEqualTo("8.1.3");

        assertThat(detector.parseVersionString("ffmpeg version 7.1-rpmfusion Copyright (c) 2000-2025"))
                .isEqualTo("7.1-rpmfusion");

        assertThat(detector.parseVersionString("ffprobe version n6.0-1-g0331006a8e Copyright (c)"))
                .isEqualTo("n6.0-1-g0331006a8e");

        assertThat(detector.parseVersionString("")).isEqualTo("Unknown");
        assertThat(detector.parseVersionString(null)).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("Should mark executable as available when command succeeds")
    void shouldReturnAvailableExecutable() {
        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-version"),
                new ProcessExecutionResult(0, List.of("ffmpeg version 8.1.3 Copyright..."), List.of(), false)
        );

        ExecutableInfo info = detector.checkExecutable("ffmpeg", "FFmpeg", "-version");

        assertThat(info.isAvailable()).isTrue();
        assertThat(info.name()).isEqualTo("FFmpeg");
        assertThat(info.version()).isEqualTo("8.1.3");
    }

    @Test
    @DisplayName("Should mark executable as unavailable when process fails")
    void shouldReturnUnavailableWhenProcessFails() {
        fakeExecutor.registerResponse(
                List.of("/invalid/ffmpeg", "-version"),
                new ProcessExecutionResult(127, List.of(), List.of("No such file or directory"), false)
        );

        ExecutableInfo info = detector.checkExecutable("/invalid/ffmpeg", "FFmpeg", "-version");

        assertThat(info.isAvailable()).isFalse();
        assertThat(info.details()).contains("No such file or directory");
    }

    @Test
    @DisplayName("Should parse hardware accelerations, encoders and filters correctly")
    void shouldParseCapabilities() {
        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-version"),
                new ProcessExecutionResult(0, List.of("ffmpeg version 8.1.3"), List.of(), false)
        );

        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-hwaccels"),
                new ProcessExecutionResult(0, List.of(
                        "Hardware acceleration methods:",
                        "cuda",
                        "vaapi",
                        "vulkan"
                ), List.of(), false)
        );

        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-encoders"),
                new ProcessExecutionResult(0, List.of(
                        "Encoders:",
                        " ------",
                        " V..... libx264              H.264 / AVC / MPEG-4 AVC",
                        " V..... libx265              H.265 / HEVC",
                        " A..... aac                  AAC (Advanced Audio Coding)"
                ), List.of(), false)
        );

        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-decoders"),
                new ProcessExecutionResult(0, List.of(
                        "Decoders:",
                        " ------",
                        " V....D h264                 H.264 / AVC",
                        " A....D mp3                  MP3 (MPEG audio layer 3)"
                ), List.of(), false)
        );

        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-filters"),
                new ProcessExecutionResult(0, List.of(
                        "Filters:",
                        "  ------",
                        " .. scale             V->V       Scale the input video size.",
                        " .. crop              V->V       Crop the input video."
                ), List.of(), false)
        );

        fakeExecutor.registerResponse(
                List.of("ffmpeg", "-formats"),
                new ProcessExecutionResult(0, List.of(
                        "File formats:",
                        " --",
                        "  E mp4             MP4 (MPEG-4 Part 14)",
                        " D  matroska        Matroska / WebM"
                ), List.of(), false)
        );

        FFmpegCapabilities capabilities = detector.detectCapabilities("ffmpeg");

        assertThat(capabilities.version()).isEqualTo("8.1.3");
        assertThat(capabilities.hardwareAccelerators()).containsExactly("cuda", "vaapi", "vulkan");
        assertThat(capabilities.hasHwAccel("vaapi")).isTrue();
        assertThat(capabilities.hasHwAccel("nonexistent")).isFalse();

        assertThat(capabilities.hasEncoder("libx264")).isTrue();
        assertThat(capabilities.hasEncoder("libx265")).isTrue();
        assertThat(capabilities.hasEncoder("aac")).isTrue();
        assertThat(capabilities.videoEncodersCount()).isEqualTo(2);
        assertThat(capabilities.audioEncodersCount()).isEqualTo(1);

        assertThat(capabilities.filters()).contains("scale", "crop");
        assertThat(capabilities.formats()).contains("mp4", "matroska");
    }

    private static class FakeProcessExecutor implements ProcessExecutorPort {
        private final Map<List<String>, ProcessExecutionResult> responses = new HashMap<>();

        void registerResponse(List<String> command, ProcessExecutionResult result) {
            responses.put(command, result);
        }

        @Override
        public ProcessExecutionResult execute(List<String> command, Duration timeout) {
            ProcessExecutionResult res = responses.get(command);
            if (res != null) {
                return res;
            }
            return new ProcessExecutionResult(-1, List.of(), List.of("Unregistered command: " + command), false);
        }
    }

    private static class FakeFileSystemService extends FileSystemService {
        @Override
        public Optional<Path> findInPath(String binaryName) {
            return Optional.empty();
        }
    }
}
