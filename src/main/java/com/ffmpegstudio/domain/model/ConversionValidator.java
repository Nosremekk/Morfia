package com.ffmpegstudio.domain.model;

import java.util.ArrayList;
import java.util.List;

public final class ConversionValidator {

    private ConversionValidator() {}

    public static List<String> validate(ConversionSettings settings, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (settings == null) {
            errors.add("Conversion settings must not be null.");
            return errors;
        }

        // Check video and audio are not both disabled
        if (settings.videoCodec() == VideoCodecOption.NONE && settings.audioCodec() == AudioCodecOption.NONE) {
            errors.add("Cannot disable both video and audio streams simultaneously.");
        }

        // Validate CRF
        if (settings.crf() != null) {
            int crf = settings.crf();
            if (crf < 0 || crf > 51) {
                errors.add("CRF value must be between 0 and 51 (received " + crf + ").");
            }
        }

        // Validate Bitrate
        if (settings.videoBitrateKbps() != null && settings.videoBitrateKbps() <= 0) {
            errors.add("Video bitrate must be positive (received " + settings.videoBitrateKbps() + " kbps).");
        }

        if (settings.audioBitrateKbps() <= 0 && settings.audioCodec() != AudioCodecOption.NONE && settings.audioCodec() != AudioCodecOption.COPY) {
            errors.add("Audio bitrate must be positive (received " + settings.audioBitrateKbps() + " kbps).");
        }

        // Validate FPS
        if (settings.targetFps() != null && settings.targetFps() <= 0) {
            errors.add("Target FPS must be positive (received " + settings.targetFps() + ").");
        }

        // Validate Custom Dimensions
        if (settings.resolution() == ResolutionPreset.CUSTOM) {
            if (settings.customWidth() == null || settings.customWidth() <= 0 ||
                settings.customHeight() == null || settings.customHeight() <= 0) {
                errors.add("Custom resolution requires positive width and height.");
            }
        }

        // Validate Capabilities if available
        if (capabilities != null) {
            if (settings.videoCodec() == VideoCodecOption.H264_LIBX264 && !capabilities.hasEncoder("libx264")) {
                errors.add("H.264 encoder 'libx264' is not supported by your local FFmpeg installation.");
            }
            if (settings.videoCodec() == VideoCodecOption.H265_LIBX265 && !capabilities.hasEncoder("libx265")) {
                errors.add("H.265 encoder 'libx265' is not supported by your local FFmpeg installation.");
            }
            if (settings.audioCodec() == AudioCodecOption.AAC && !capabilities.hasEncoder("aac")) {
                errors.add("AAC audio encoder is not supported by your local FFmpeg installation.");
            }
        }

        return errors;
    }
}
