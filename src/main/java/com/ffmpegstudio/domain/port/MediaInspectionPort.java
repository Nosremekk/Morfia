package com.ffmpegstudio.domain.port;

import com.ffmpegstudio.domain.model.MediaFile;

import java.nio.file.Path;

/**
 * Port for inspecting media files using external tools (such as FFprobe).
 */
public interface MediaInspectionPort {
    MediaFile inspect(String ffprobePath, Path mediaFilePath);
}
