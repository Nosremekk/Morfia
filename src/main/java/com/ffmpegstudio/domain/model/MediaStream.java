package com.ffmpegstudio.domain.model;

/**
 * Base interface representing an audio, video, or subtitle stream in a container.
 */
public sealed interface MediaStream permits VideoStream, AudioStream, SubtitleStream, OtherStream {
    int index();
    String codecName();
    String codecLongName();
}
