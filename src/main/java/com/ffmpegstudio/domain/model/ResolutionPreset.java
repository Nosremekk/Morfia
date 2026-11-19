package com.ffmpegstudio.domain.model;

public enum ResolutionPreset {
    ORIGINAL(-1, -1, "Original Resolution"),
    P1080(1920, 1080, "1080p Full HD (1920x1080)"),
    P720(1280, 720, "720p HD (1280x720)"),
    P480(854, 480, "480p SD (854x480)"),
    CUSTOM(-2, -2, "Custom Dimensions");

    private final int width;
    private final int height;
    private final String label;

    ResolutionPreset(int width, int height, String label) {
        this.width = width;
        this.height = height;
        this.label = label;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String label() {
        return label;
    }

    public boolean isScaleRequired() {
        return this != ORIGINAL;
    }
}
