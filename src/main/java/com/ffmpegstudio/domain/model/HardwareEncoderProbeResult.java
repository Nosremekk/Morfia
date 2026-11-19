package com.ffmpegstudio.domain.model;

/**
 * Three-level hardware acceleration verification result for a specific encoder:
 * 1) backendCompiled: present in `ffmpeg -hwaccels`
 * 2) encoderCompiled: present in `ffmpeg -encoders`
 * 3) hardwareFunctional: verified by a real synthetic encoding test on local hardware
 */
public record HardwareEncoderProbeResult(
        HardwareBackend backend,
        String encoderName,
        boolean backendCompiled,
        boolean encoderCompiled,
        boolean hardwareFunctional,
        String devicePath,
        String diagnosticMessage,
        long probeDurationMs
) {
    public String statusSummary() {
        if (hardwareFunctional) {
            return "Funcional (" + probeDurationMs + " ms)";
        }
        if (!backendCompiled) {
            return "Backend ausente na compilação";
        }
        if (!encoderCompiled) {
            return "Encoder ausente na compilação";
        }
        return "Indisponível no hardware/driver";
    }
}
