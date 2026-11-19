package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Common abstraction for multimedia processing operations executed by the FFmpeg engine.
 */
public interface MediaOperation {

    /**
     * Display name of the operation.
     */
    String name();

    /**
     * Technical description of the operation.
     */
    String description();

    /**
     * Validates operation parameters against file metadata and local FFmpeg capabilities.
     * @return List of validation errors (empty if valid).
     */
    List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities);

    /**
     * Constructs the structured list of command arguments for ProcessBuilder.
     */
    List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe);

    /**
     * Suggests a safe default output path for the processed file.
     */
    Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir);
}
