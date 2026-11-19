package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Reference to a local media file imported into the shared session workspace.
 * Never duplicates or moves original files on disk.
 */
public record WorkspaceMedia(
        Path path,
        MediaFile mediaFile,
        MediaKind kind,
        long lastModifiedMillis,
        long fileSizeBytes,
        boolean missingOnDisk,
        boolean modifiedSinceInspection,
        Instant importedAt
) {
    public enum MediaKind {
        VIDEO("Vídeo"),
        AUDIO("Áudio"),
        IMAGE("Imagem"),
        OTHER("Outro");

        private final String label;
        MediaKind(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public WorkspaceMedia {
        Objects.requireNonNull(path, "path must not be null");
        kind = kind == null ? MediaKind.OTHER : kind;
        importedAt = importedAt == null ? Instant.now() : importedAt;
    }

    public String fileName() {
        return path.getFileName() != null ? path.getFileName().toString() : path.toString();
    }

    public static MediaKind inferKind(MediaFile mf, Path path) {
        if (mf != null && mf.metadata() != null) {
            if (!mf.metadata().videoStreams().isEmpty()) {
                String fmt = mf.metadata().formatName().toLowerCase();
                if (fmt.contains("image2") || fmt.equals("png_pipe") || fmt.equals("webp_pipe")) {
                    return MediaKind.IMAGE;
                }
                return MediaKind.VIDEO;
            }
            if (!mf.metadata().audioStreams().isEmpty()) {
                return MediaKind.AUDIO;
            }
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".tiff")) {
            return MediaKind.IMAGE;
        }
        if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav")
                || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".m4a")) {
            return MediaKind.AUDIO;
        }
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov")
                || name.endsWith(".avi") || name.endsWith(".webm")) {
            return MediaKind.VIDEO;
        }
        return MediaKind.OTHER;
    }
}
