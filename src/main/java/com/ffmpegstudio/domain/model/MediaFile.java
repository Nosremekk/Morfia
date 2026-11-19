package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates an inspected media file on disk.
 */
public record MediaFile(
        Path path,
        String fileName,
        long fileSizeBytes,
        MediaMetadata metadata
) {
    public MediaFile {
        Objects.requireNonNull(path, "path must not be null");
        fileName = fileName == null ? path.getFileName().toString() : fileName;
    }

    public Optional<MediaMetadata> optMetadata() {
        return Optional.ofNullable(metadata);
    }
}
