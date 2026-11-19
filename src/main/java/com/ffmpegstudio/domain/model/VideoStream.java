package com.ffmpegstudio.domain.model;

/**
 * Representation of a video stream within a media file.
 */
public record VideoStream(
        int index,
        String codecName,
        String codecLongName,
        String profile,
        int width,
        int height,
        String aspectRatio,
        double frameRate,
        double nominalFps,
        String pixelFormat,
        long bitRate,
        String language
) implements MediaStream {

    public VideoStream {
        codecName = codecName == null ? "" : codecName;
        codecLongName = codecLongName == null ? "" : codecLongName;
        profile = profile == null ? "" : profile;
        aspectRatio = aspectRatio == null ? "" : aspectRatio;
        pixelFormat = pixelFormat == null ? "" : pixelFormat;
        language = language == null ? "und" : language;
    }

    public VideoStream(
            int index,
            String codecName,
            String codecLongName,
            int width,
            int height,
            double frameRate,
            String pixelFormat,
            long bitRate
    ) {
        this(index, codecName, codecLongName, "", width, height, "", frameRate, frameRate, pixelFormat, bitRate, "und");
    }
}
