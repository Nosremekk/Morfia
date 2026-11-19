package com.ffmpegstudio.domain.model;

public enum AudioCodecOption {
    AAC("aac", "AAC (Advanced Audio Coding)", true),
    MP3_LIBMP3LAME("libmp3lame", "MP3 (libmp3lame)", true),
    OPUS("libopus", "Opus (libopus)", true),
    FLAC("flac", "FLAC (Lossless)", true),
    COPY("copy", "Stream Copy (No re-encoding)", false),
    NONE("none", "Disable Audio (Mute)", false);

    private final String encoderName;
    private final String label;
    private final boolean requiresEncoding;

    AudioCodecOption(String encoderName, String label, boolean requiresEncoding) {
        this.encoderName = encoderName;
        this.label = label;
        this.requiresEncoding = requiresEncoding;
    }

    public String encoderName() {
        return encoderName;
    }

    public String label() {
        return label;
    }

    public boolean requiresEncoding() {
        return requiresEncoding;
    }
}
