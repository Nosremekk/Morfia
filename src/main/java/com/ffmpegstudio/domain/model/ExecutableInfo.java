package com.ffmpegstudio.domain.model;

import java.util.Objects;

/**
 * Information regarding an external executable (FFmpeg or FFprobe).
 */
public record ExecutableInfo(
        String name,
        String path,
        boolean isAvailable,
        String version,
        String details
) {
    public ExecutableInfo {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(path, "path must not be null");
        version = version == null ? "" : version;
        details = details == null ? "" : details;
    }

    public static ExecutableInfo unavailable(String name, String path, String details) {
        return new ExecutableInfo(name, path, false, "", details);
    }

    public static ExecutableInfo available(String name, String path, String version, String details) {
        return new ExecutableInfo(name, path, true, version, details);
    }
}
