package com.ffmpegstudio.domain.model;

public enum ContainerFormat {
    MP4("mp4", "video/mp4", "MPEG-4 Part 14"),
    MKV("mkv", "video/x-matroska", "Matroska Multimedia Container"),
    WEBM("webm", "video/webm", "WebM Video Container"),
    MP3("mp3", "audio/mpeg", "MPEG Audio Layer 3"),
    FLAC("flac", "audio/flac", "Free Lossless Audio Codec");

    private final String extension;
    private final String mimeType;
    private final String description;

    ContainerFormat(String extension, String mimeType, String description) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.description = description;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String description() {
        return description;
    }

    public static ContainerFormat fromExtension(String ext) {
        if (ext == null) return MP4;
        String clean = ext.toLowerCase().replace(".", "").trim();
        return switch (clean) {
            case "mkv" -> MKV;
            case "webm" -> WEBM;
            case "mp3" -> MP3;
            case "flac" -> FLAC;
            default -> MP4;
        };
    }
}
