package com.ffmpegstudio.domain.model;

/**
 * Representation of a data or attachment stream within a media container.
 */
public record OtherStream(
        int index,
        String streamType, // "data" or "attachment"
        String codecName,
        String codecLongName,
        String language,
        String title,
        String filename,
        String mimeType
) implements MediaStream {

    public OtherStream {
        streamType = streamType == null ? "data" : streamType;
        codecName = codecName == null ? "" : codecName;
        codecLongName = codecLongName == null ? "" : codecLongName;
        language = language == null ? "und" : language;
        title = title == null ? "" : title;
        filename = filename == null ? "" : filename;
        mimeType = mimeType == null ? "" : mimeType;
    }
}
