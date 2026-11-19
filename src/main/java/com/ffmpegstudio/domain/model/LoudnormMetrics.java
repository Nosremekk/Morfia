package com.ffmpegstudio.domain.model;

/**
 * Measured EBU R128 loudness metrics from FFmpeg loudnorm first pass.
 */
public record LoudnormMetrics(
        double inputIntegratedLufs,
        double inputTruePeakDbtp,
        double inputLraLu,
        double inputThresh,
        double targetOffset
) {
    public boolean isValidForLinearNormalization() {
        return Double.isFinite(inputIntegratedLufs)
                && Double.isFinite(inputTruePeakDbtp)
                && Double.isFinite(inputLraLu)
                && Double.isFinite(inputThresh)
                && Double.isFinite(targetOffset)
                && inputIntegratedLufs > -70.0
                && inputIntegratedLufs < 0.0;
    }

    public String toSecondPassFilter(double targetI, double targetTp, double targetLra) {
        return String.format(java.util.Locale.US,
                "loudnorm=I=%.1f:TP=%.1f:LRA=%.1f:measured_I=%.2f:measured_TP=%.2f:measured_LRA=%.2f:measured_thresh=%.2f:offset=%.2f:linear=true",
                targetI, targetTp, targetLra,
                inputIntegratedLufs, inputTruePeakDbtp, inputLraLu, inputThresh, targetOffset);
    }
}
