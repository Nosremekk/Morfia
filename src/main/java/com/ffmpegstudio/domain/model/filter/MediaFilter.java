package com.ffmpegstudio.domain.model.filter;

/**
 * Typed representation of an FFmpeg video or audio filter.
 * Prevents arbitrary raw shell/filter injection from the UI.
 */
public interface MediaFilter {

    enum FilterCategory {
        GEOMETRIC("Geométrico"),
        IMAGE("Imagem e Cor"),
        TEMPORAL("Temporal"),
        AUDIO("Áudio");

        private final String label;
        FilterCategory(String label) { this.label = label; }
        public String label() { return label; }
    }

    enum StreamKind {
        VIDEO,
        AUDIO
    }

    String id();
    String displayName();
    FilterCategory category();
    StreamKind streamKind();

    /**
     * Primary FFmpeg filter name(s) required in `ffmpeg -filters`.
     */
    String requiredFFmpegFilter();

    /**
     * Generates the validated FFmpeg filter expression (e.g. "scale=1280:-2").
     */
    String toFilterExpression();

    /**
     * Whether this filter depends on temporal state across multiple frames (e.g. fade, speed, reverse),
     * meaning a single-frame preview cannot fully represent its effect.
     */
    default boolean isTemporal() {
        return category() == FilterCategory.TEMPORAL;
    }

    default boolean requiresReencode() {
        return true;
    }
}
