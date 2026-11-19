package com.ffmpegstudio.domain.model.filter;

import java.time.Duration;
import java.util.List;

/**
 * Validation result and impact estimation for a composed filter chain.
 */
public record FilterValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        boolean requiresVideoReencode,
        boolean requiresAudioReencode,
        Integer estimatedWidth,
        Integer estimatedHeight,
        Duration estimatedDuration
) {
    public FilterValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
