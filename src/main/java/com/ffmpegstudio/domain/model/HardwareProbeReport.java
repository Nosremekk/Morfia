package com.ffmpegstudio.domain.model;

import java.util.List;

/**
 * Aggregated report of local DRM render devices and hardware encoder probe results.
 */
public record HardwareProbeReport(
        List<String> driDevices,
        List<String> accessibleDriDevices,
        List<HardwareEncoderProbeResult> encoderResults
) {
    public HardwareProbeReport {
        driDevices = driDevices == null ? List.of() : List.copyOf(driDevices);
        accessibleDriDevices = accessibleDriDevices == null ? List.of() : List.copyOf(accessibleDriDevices);
        encoderResults = encoderResults == null ? List.of() : List.copyOf(encoderResults);
    }

    public boolean hasAnyFunctionalEncoder() {
        return encoderResults.stream().anyMatch(HardwareEncoderProbeResult::hardwareFunctional);
    }

    public List<HardwareEncoderProbeResult> functionalEncoders() {
        return encoderResults.stream().filter(HardwareEncoderProbeResult::hardwareFunctional).toList();
    }
}
