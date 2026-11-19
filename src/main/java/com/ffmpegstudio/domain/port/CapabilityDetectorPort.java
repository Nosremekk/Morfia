package com.ffmpegstudio.domain.port;

import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;

/**
 * Port for querying system executables and FFmpeg capabilities.
 */
public interface CapabilityDetectorPort {
    ExecutableInfo checkExecutable(String executablePath, String name, String versionFlag);
    FFmpegCapabilities detectCapabilities(String ffmpegPath);
}
