package com.ffmpegstudio.domain.model.filter;

import java.nio.file.Path;
import java.util.List;

/**
 * Result of an asynchronous single-frame filter preview generation.
 */
public record PreviewResult(
        Path originalFramePath,
        Path filteredFramePath,
        List<String> notices,
        boolean hasTemporalLimitation
) {
    public PreviewResult {
        notices = notices == null ? List.of() : List.copyOf(notices);
    }
}
