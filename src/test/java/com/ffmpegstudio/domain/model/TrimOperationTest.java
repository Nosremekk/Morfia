package com.ffmpegstudio.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TrimOperation Unit Tests")
class TrimOperationTest {

    @Test
    @DisplayName("Should build fast stream-copy cut command")
    void shouldBuildFastCutCommand() {
        TrimOperation op = TrimOperation.fast(Duration.ofSeconds(5), Duration.ofSeconds(15));
        Path out = Paths.get("/tmp/trimmed.mp4");

        List<String> cmd = op.buildCommand("ffmpeg", null, out, true);

        assertTrue(cmd.contains("-ss"));
        assertTrue(cmd.contains("5.000"));
        assertTrue(cmd.contains("-to"));
        assertTrue(cmd.contains("15.000"));
        assertTrue(cmd.contains("-c"));
        assertTrue(cmd.contains("copy"));
    }

    @Test
    @DisplayName("Should build frame-accurate re-encoded cut command")
    void shouldBuildAccurateCutCommand() {
        TrimOperation op = TrimOperation.accurate(Duration.ofSeconds(2), Duration.ofSeconds(8), ConversionSettings.defaultMp4());
        Path out = Paths.get("/tmp/trimmed.mp4");

        List<String> cmd = op.buildCommand("ffmpeg", null, out, true);

        assertTrue(cmd.contains("-ss"));
        assertTrue(cmd.contains("2.000"));
        assertTrue(cmd.contains("-to"));
        assertTrue(cmd.contains("8.000"));
        assertTrue(cmd.contains("-c:v"));
        assertTrue(cmd.contains("libx264"));
        assertFalse(cmd.contains("copy"));
    }

    @Test
    @DisplayName("Should fail validation when end time is before start time")
    void shouldFailValidationWhenEndTimeIsBeforeStartTime() {
        TrimOperation op = TrimOperation.fast(Duration.ofSeconds(10), Duration.ofSeconds(5));
        List<String> errors = op.validate(null, null);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("greater than start time"));
    }
}
