package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.port.MediaInspectionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

public class AnalyzeMediaService implements AnalyzeMediaUseCase {
    private static final Logger logger = LoggerFactory.getLogger(AnalyzeMediaService.class);

    private final MediaInspectionPort mediaInspector;
    private final ManageSettingsUseCase settingsUseCase;

    public AnalyzeMediaService(MediaInspectionPort mediaInspector, ManageSettingsUseCase settingsUseCase) {
        this.mediaInspector = Objects.requireNonNull(mediaInspector, "mediaInspector must not be null");
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase, "settingsUseCase must not be null");
    }

    @Override
    public MediaFile analyze(Path mediaFilePath) {
        Objects.requireNonNull(mediaFilePath, "mediaFilePath must not be null");
        String ffprobePath = settingsUseCase.getSettings().ffprobePath();
        logger.info("Analyzing media file '{}' using ffprobe at '{}'", mediaFilePath, ffprobePath);
        return mediaInspector.inspect(ffprobePath, mediaFilePath);
    }
}
