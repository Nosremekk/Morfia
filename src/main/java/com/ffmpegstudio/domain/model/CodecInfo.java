package com.ffmpegstudio.domain.model;

import java.util.Objects;

/**
 * Information regarding a supported codec or encoder/decoder in FFmpeg.
 */
public record CodecInfo(
        String name,
        String description,
        CodecType type,
        boolean canEncode,
        boolean canDecode
) {
    public enum CodecType {
        VIDEO,
        AUDIO,
        SUBTITLE,
        OTHER
    }

    public CodecInfo {
        Objects.requireNonNull(name, "Codec name must not be null");
        Objects.requireNonNull(type, "Codec type must not be null");
        description = description == null ? "" : description;
    }
}
