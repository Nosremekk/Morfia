package com.ffmpegstudio.domain.model;

public enum PresetSpeed {
    ULTRAFAST("ultrafast", "Ultrafast (Lowest CPU, largest file)"),
    SUPERFAST("superfast", "Superfast"),
    VERYFAST("veryfast", "Veryfast"),
    FASTER("faster", "Faster"),
    FAST("fast", "Fast"),
    MEDIUM("medium", "Medium (Default balance)"),
    SLOW("slow", "Slow (High quality)"),
    SLOWER("slower", "Slower"),
    VERYSLOW("veryslow", "Veryslow (Best compression, slowest)");

    private final String value;
    private final String label;

    PresetSpeed(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }
}
