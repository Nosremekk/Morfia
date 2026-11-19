package com.ffmpegstudio.domain.model;

public record SubtitleStream(
        int index,
        String codecName,
        String codecLongName,
        String language,
        String title,
        boolean isDefault,
        boolean isForced
) implements MediaStream {
}
