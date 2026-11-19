package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.MediaFile;

import java.nio.file.Path;

public interface AnalyzeMediaUseCase {
    MediaFile analyze(Path mediaFilePath);
}
