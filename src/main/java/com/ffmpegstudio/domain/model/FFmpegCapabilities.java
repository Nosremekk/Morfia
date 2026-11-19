package com.ffmpegstudio.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the detected capabilities of the local FFmpeg installation.
 */
public record FFmpegCapabilities(
        String version,
        List<String> hardwareAccelerators,
        List<CodecInfo> encoders,
        List<CodecInfo> decoders,
        List<String> filters,
        List<String> formats
) {
    public FFmpegCapabilities {
        version = version == null ? "Unknown" : version;
        hardwareAccelerators = hardwareAccelerators == null ? List.of() : List.copyOf(hardwareAccelerators);
        encoders = encoders == null ? List.of() : List.copyOf(encoders);
        decoders = decoders == null ? List.of() : List.copyOf(decoders);
        filters = filters == null ? List.of() : List.copyOf(filters);
        formats = formats == null ? List.of() : List.copyOf(formats);
    }

    public static FFmpegCapabilities empty() {
        return new FFmpegCapabilities("Unavailable", List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean hasEncoder(String name) {
        return encoders.stream().anyMatch(codec -> codec.name().equalsIgnoreCase(name));
    }

    public boolean hasHwAccel(String accelName) {
        return hardwareAccelerators.stream().anyMatch(accel -> accel.equalsIgnoreCase(accelName));
    }

    public boolean hasFilter(String filterName) {
        return filters.stream().anyMatch(f -> f.equalsIgnoreCase(filterName));
    }

    public long videoEncodersCount() {
        return encoders.stream().filter(c -> c.type() == CodecInfo.CodecType.VIDEO).count();
    }

    public long audioEncodersCount() {
        return encoders.stream().filter(c -> c.type() == CodecInfo.CodecType.AUDIO).count();
    }
}
