package com.ffmpegstudio.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable parameters defining a media transcoding/conversion operation.
 */
public record ConversionSettings(
        ContainerFormat container,
        VideoCodecOption videoCodec,
        AudioCodecOption audioCodec,
        PresetSpeed speedPreset,
        Integer crf,
        Long videoBitrateKbps,
        int audioBitrateKbps,
        ResolutionPreset resolution,
        Integer customWidth,
        Integer customHeight,
        Double targetFps,
        boolean mapAllStreams,
        Integer videoStreamIndex,
        Integer audioStreamIndex,
        Integer subtitleStreamIndex
) {
    public static final int DEFAULT_CRF_H264 = 23;
    public static final int DEFAULT_CRF_H265 = 28;
    public static final int DEFAULT_AUDIO_BITRATE = 192;

    public ConversionSettings {
        container = container == null ? ContainerFormat.MP4 : container;
        videoCodec = videoCodec == null ? VideoCodecOption.H264_LIBX264 : videoCodec;
        audioCodec = audioCodec == null ? AudioCodecOption.AAC : audioCodec;
        speedPreset = speedPreset == null ? PresetSpeed.MEDIUM : speedPreset;
        resolution = resolution == null ? ResolutionPreset.ORIGINAL : resolution;
        if (audioBitrateKbps <= 0) {
            audioBitrateKbps = DEFAULT_AUDIO_BITRATE;
        }
    }

    public static ConversionSettings defaultMp4() {
        return new ConversionSettings(
                ContainerFormat.MP4,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                DEFAULT_CRF_H264,
                null,
                DEFAULT_AUDIO_BITRATE,
                ResolutionPreset.ORIGINAL,
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );
    }

    public static ConversionSettings defaultMkv() {
        return new ConversionSettings(
                ContainerFormat.MKV,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                DEFAULT_CRF_H264,
                null,
                DEFAULT_AUDIO_BITRATE,
                ResolutionPreset.ORIGINAL,
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );
    }

    public Optional<Integer> optCrf() {
        return Optional.ofNullable(crf);
    }

    public Optional<Long> optVideoBitrateKbps() {
        return Optional.ofNullable(videoBitrateKbps);
    }

    public Optional<Double> optTargetFps() {
        return Optional.ofNullable(targetFps);
    }
}
