package com.ffmpegstudio.domain.model;

import java.time.Duration;

/**
 * Representation of a chapter mark in a media file.
 */
public record ChapterInfo(
        long id,
        Duration startTime,
        Duration endTime,
        String title
) {
    public ChapterInfo {
        title = title == null ? "Chapter " + id : title;
    }
}
