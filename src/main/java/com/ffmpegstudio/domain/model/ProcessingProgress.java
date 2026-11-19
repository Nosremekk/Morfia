package com.ffmpegstudio.domain.model;

/**
 * Snapshot of progress during media processing.
 */
public record ProcessingProgress(
        double percentage,
        double currentFps,
        long currentBitrateKbps,
        long currentTimeMillis,
        long totalDurationMillis,
        double speedMultiplier,
        long etaSeconds
) {
    public ProcessingProgress {
        percentage = Math.clamp(percentage, 0.0, 100.0);
    }

    public static ProcessingProgress indeterminate() {
        return new ProcessingProgress(0.0, 0.0, 0, 0, 0, 0.0, -1);
    }

    public static ProcessingProgress initial() {
        return new ProcessingProgress(0.0, 0.0, 0, 0, 0, 0.0, 0);
    }

    public static ProcessingProgress completed() {
        return new ProcessingProgress(100.0, 0.0, 0, 0, 0, 1.0, 0);
    }
}
