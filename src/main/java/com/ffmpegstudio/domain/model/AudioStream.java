package com.ffmpegstudio.domain.model;

/**
 * Representation of an audio stream within a media file.
 */
public record AudioStream(
        int index,
        String codecName,
        String codecLongName,
        String profile,
        int sampleRate,
        int channels,
        String channelLayout,
        long bitRate,
        String language
) implements MediaStream {

    public AudioStream {
        codecName = codecName == null ? "" : codecName;
        codecLongName = codecLongName == null ? "" : codecLongName;
        profile = profile == null ? "" : profile;
        channelLayout = channelLayout == null ? "" : channelLayout;
        language = language == null ? "und" : language;
    }

    public AudioStream(
            int index,
            String codecName,
            String codecLongName,
            int sampleRate,
            int channels,
            String channelLayout,
            long bitRate
    ) {
        this(index, codecName, codecLongName, "", sampleRate, channels, channelLayout, bitRate, "und");
    }

    public String title() {
        return "";
    }
}
