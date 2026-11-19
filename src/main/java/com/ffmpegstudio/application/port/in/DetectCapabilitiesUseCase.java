package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;

public interface DetectCapabilitiesUseCase {
    ExecutableInfo checkFFmpeg(String path);
    ExecutableInfo checkFFprobe(String path);
    FFmpegCapabilities getCapabilities(String ffmpegPath);
}
