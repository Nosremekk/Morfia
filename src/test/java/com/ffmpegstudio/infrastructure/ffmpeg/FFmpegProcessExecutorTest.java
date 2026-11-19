package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FFmpegProcessExecutorTest {

    private FFmpegProcessExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FFmpegProcessExecutor();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when command list is empty")
    void shouldRejectEmptyCommand() {
        assertThatThrownBy(() -> executor.execute(List.of(), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should capture stdout and successful exit code for valid commands")
    void shouldExecuteValidCommandSuccessfully() {
        ProcessExecutionResult result = executor.execute(List.of("echo", "ffmpeg-studio-test"), Duration.ofSeconds(3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.standardOutputText()).contains("ffmpeg-studio-test");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("Should return failed result when executing non-existent binary")
    void shouldHandleNonExistentBinary() {
        ProcessExecutionResult result = executor.execute(
                List.of("/nonexistent/binary/path/that/does/not/exist"),
                Duration.ofSeconds(1)
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Should detect timeout and forcibly terminate long running processes")
    void shouldTimeoutAndTerminate() {
        // Sleep for 3 seconds with a 500ms timeout
        ProcessExecutionResult result = executor.execute(
                List.of("sleep", "3"),
                Duration.ofMillis(500)
        );

        assertThat(result.timedOut()).isTrue();
        assertThat(result.isSuccess()).isFalse();
    }
}
