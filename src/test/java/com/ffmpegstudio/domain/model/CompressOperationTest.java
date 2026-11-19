package com.ffmpegstudio.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompressOperation Unit Tests")
class CompressOperationTest {

    @Test
    @DisplayName("Should build valid arguments for balanced compression")
    void shouldBuildValidArgumentsForBalancedCompression() {
        CompressOperation op = CompressOperation.createBalanced();
        Path input = Paths.get("/tmp/source.mp4");
        Path output = Paths.get("/tmp/output.mp4");

        List<String> cmd = op.buildCommand("ffmpeg", new MediaFile(input, "source.mp4", 1000, null), output, true);

        assertTrue(cmd.contains("-i"));
        assertTrue(cmd.contains(input.toAbsolutePath().toString()));
        assertTrue(cmd.contains(output.toAbsolutePath().toString()));
        assertTrue(cmd.contains("-c:v"));
        assertTrue(cmd.contains("libx264"));
        assertTrue(cmd.contains("-crf"));
        assertTrue(cmd.contains("26"));
        assertTrue(cmd.contains("-preset"));
        assertTrue(cmd.contains("medium"));
        assertTrue(cmd.contains("-c:a"));
        assertTrue(cmd.contains("aac"));
        assertTrue(cmd.contains("-b:a"));
        assertTrue(cmd.contains("128k"));
        assertTrue(cmd.contains("-progress"));
        assertTrue(cmd.contains("pipe:1"));
    }

    @Test
    @DisplayName("Should include scale filter when resolution preset is specified")
    void shouldIncludeScaleFilterWhenResolutionPresetIsSpecified() {
        CompressOperation op = new CompressOperation(
                VideoCodecOption.H264_LIBX264,
                PresetSpeed.FAST,
                28,
                null,
                ResolutionPreset.P720,
                null,
                AudioCodecOption.AAC,
                96,
                CompressOperation.CompressionProfile.AGGRESSIVE
        );

        List<String> cmd = op.buildCommand("ffmpeg", null, Paths.get("/tmp/out.mp4"), false);

        assertTrue(cmd.contains("-vf"));
        int vfIndex = cmd.indexOf("-vf");
        String filter = cmd.get(vfIndex + 1);
        assertTrue(filter.contains("scale=1280:-2"));
    }

    @Test
    @DisplayName("Should validate CRF range and encoder presence")
    void shouldValidateCrfRangeAndEncoderPresence() {
        CompressOperation op = new CompressOperation(
                VideoCodecOption.H265_LIBX265,
                PresetSpeed.MEDIUM,
                28,
                null,
                ResolutionPreset.ORIGINAL,
                null,
                AudioCodecOption.AAC,
                128,
                CompressOperation.CompressionProfile.BALANCED
        );

        FFmpegCapabilities capsWithoutX265 = new FFmpegCapabilities(
                "8.1.3",
                List.of(),
                List.of(new CodecInfo("libx264", "H.264", CodecInfo.CodecType.VIDEO, true, false)),
                List.of(),
                List.of(),
                List.of()
        );

        List<String> errors = op.validate(null, capsWithoutX265);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("libx265"));
    }
}
