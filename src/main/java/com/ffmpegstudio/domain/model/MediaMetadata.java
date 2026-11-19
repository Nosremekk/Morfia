package com.ffmpegstudio.domain.model;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Format-level metadata of a media container.
 */
public record MediaMetadata(
        String formatName,
        String formatLongName,
        Duration duration,
        long sizeBytes,
        long bitRate,
        Map<String, String> tags,
        List<MediaStream> streams,
        List<ChapterInfo> chapters
) {
    public MediaMetadata {
        formatName = formatName == null ? "" : formatName;
        formatLongName = formatLongName == null ? "" : formatLongName;
        duration = duration == null ? Duration.ZERO : duration;
        tags = tags == null ? Collections.emptyMap() : Collections.unmodifiableMap(tags);
        streams = streams == null ? Collections.emptyList() : Collections.unmodifiableList(streams);
        chapters = chapters == null ? Collections.emptyList() : Collections.unmodifiableList(chapters);
    }

    public MediaMetadata(
            String formatName,
            String formatLongName,
            Duration duration,
            long sizeBytes,
            long bitRate,
            Map<String, String> tags,
            List<MediaStream> streams
    ) {
        this(formatName, formatLongName, duration, sizeBytes, bitRate, tags, streams, List.of());
    }

    public Optional<VideoStream> primaryVideoStream() {
        return streams.stream()
                .filter(VideoStream.class::isInstance)
                .map(VideoStream.class::cast)
                .findFirst();
    }

    public List<VideoStream> videoStreams() {
        return streams.stream()
                .filter(VideoStream.class::isInstance)
                .map(VideoStream.class::cast)
                .toList();
    }

    public List<AudioStream> audioStreams() {
        return streams.stream()
                .filter(AudioStream.class::isInstance)
                .map(AudioStream.class::cast)
                .toList();
    }

    public List<SubtitleStream> subtitleStreams() {
        return streams.stream()
                .filter(SubtitleStream.class::isInstance)
                .map(SubtitleStream.class::cast)
                .toList();
    }

    public List<OtherStream> otherStreams() {
        return streams.stream()
                .filter(OtherStream.class::isInstance)
                .map(OtherStream.class::cast)
                .toList();
    }
}
