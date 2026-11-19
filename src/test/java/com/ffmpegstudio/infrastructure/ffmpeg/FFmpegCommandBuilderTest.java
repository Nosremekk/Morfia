package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FFmpegCommandBuilderTest {

    private final Path input = Paths.get("/tmp/input.mov");
    private final Path output = Paths.get("/tmp/output.mp4");

    @Test
    @DisplayName("Should build standard H.264/AAC command with CRF and progress pipe")
    void shouldBuildStandardH264Command() {
        ConversionSettings settings = ConversionSettings.defaultMp4();

        List<String> command = new FFmpegCommandBuilder()
                .withInput(input)
                .withOutput(output)
                .withSettings(settings)
                .withProgressPipe(true)
                .build();

        assertThat(command).containsSubsequence("-i", input.toAbsolutePath().toString());
        assertThat(command).containsSubsequence("-c:v", "libx264");
        assertThat(command).containsSubsequence("-preset", "medium");
        assertThat(command).containsSubsequence("-crf", "23");
        assertThat(command).containsSubsequence("-pix_fmt", "yuv420p");
        assertThat(command).containsSubsequence("-c:a", "aac");
        assertThat(command).containsSubsequence("-b:a", "192k");
        assertThat(command).containsSubsequence("-progress", "pipe:1");
        assertThat(command).containsSubsequence("-movflags", "+faststart");
        assertThat(command).endsWith(output.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("Should append resolution scaling filter when requested")
    void shouldAppendScalingFilter() {
        ConversionSettings settings = new ConversionSettings(
                ContainerFormat.MKV,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.FAST,
                20,
                null,
                192,
                ResolutionPreset.P720,
                null, null, null, true, null, null, null
        );

        List<String> command = new FFmpegCommandBuilder()
                .withInput(input)
                .withOutput(Paths.get("/tmp/output.mkv"))
                .withSettings(settings)
                .build();

        assertThat(command).contains("-vf");
        assertThat(command).contains("scale=1280:720:force_original_aspect_ratio=decrease");
    }

    @Test
    @DisplayName("Should prevent building command if input equals output")
    void shouldPreventOverwritingInputFileDirectly() {
        assertThatThrownBy(() -> new FFmpegCommandBuilder()
                .withInput(input)
                .withOutput(input)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Original file would be destroyed");
    }

    @Test
    @DisplayName("Should support stream copy and disabling streams")
    void shouldSupportStreamCopyAndDisabling() {
        ConversionSettings settings = new ConversionSettings(
                ContainerFormat.MKV,
                VideoCodecOption.COPY,
                AudioCodecOption.NONE,
                PresetSpeed.MEDIUM,
                null, null, 0,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );

        List<String> command = new FFmpegCommandBuilder()
                .withInput(input)
                .withOutput(Paths.get("/tmp/output.mkv"))
                .withSettings(settings)
                .build();

        assertThat(command).containsSubsequence("-c:v", "copy");
        assertThat(command).contains("-an");
    }
}
