package com.ffmpegstudio.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionValidatorTest {

    @Test
    @DisplayName("Should detect invalid CRF values")
    void shouldValidateCrfRange() {
        ConversionSettings valid = new ConversionSettings(
                ContainerFormat.MP4,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                23,
                null,
                192,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );
        assertThat(ConversionValidator.validate(valid, null)).isEmpty();

        ConversionSettings invalidCrfHigh = new ConversionSettings(
                ContainerFormat.MP4,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                55,
                null,
                192,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );
        List<String> errors = ConversionValidator.validate(invalidCrfHigh, null);
        assertThat(errors).anyMatch(e -> e.contains("CRF value must be between 0 and 51"));
    }

    @Test
    @DisplayName("Should reject disabling both video and audio streams simultaneously")
    void shouldRejectBothStreamsDisabled() {
        ConversionSettings invalid = new ConversionSettings(
                ContainerFormat.MP4,
                VideoCodecOption.NONE,
                AudioCodecOption.NONE,
                PresetSpeed.MEDIUM,
                23,
                null,
                192,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );
        List<String> errors = ConversionValidator.validate(invalid, null);
        assertThat(errors).anyMatch(e -> e.contains("Cannot disable both video and audio"));
    }

    @Test
    @DisplayName("Should detect missing encoder against system capabilities")
    void shouldDetectMissingEncoderInCapabilities() {
        FFmpegCapabilities capsWithoutHevc = new FFmpegCapabilities(
                "8.1.3",
                List.of(),
                List.of(new CodecInfo("libx264", "H.264", CodecInfo.CodecType.VIDEO, true, false)),
                List.of(),
                List.of(),
                List.of()
        );

        ConversionSettings requestHevc = new ConversionSettings(
                ContainerFormat.MKV,
                VideoCodecOption.H265_LIBX265,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                28,
                null,
                192,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );

        List<String> errors = ConversionValidator.validate(requestHevc, capsWithoutHevc);
        assertThat(errors).anyMatch(e -> e.contains("libx265"));
    }
}
