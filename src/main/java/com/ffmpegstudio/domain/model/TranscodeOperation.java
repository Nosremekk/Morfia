package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegCommandBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

/**
 * Standard transcoding operation converting video/audio between containers and codecs.
 */
public record TranscodeOperation(ConversionSettings settings) implements MediaOperation {

    public TranscodeOperation {
        Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public String name() {
        return "Transcode / Convert (" + settings.container().extension().toUpperCase() + ")";
    }

    @Override
    public String description() {
        return "Convert media to " + settings.container().description() + " using " + settings.videoCodec().label();
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        return ConversionValidator.validate(settings, capabilities);
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input");
        return new FFmpegCommandBuilder()
                .withFfmpegBinary(ffmpegBinary)
                .withInput(input)
                .withOutput(outputPath)
                .withSettings(settings)
                .withOverwrite(true)
                .withProgressPipe(enableProgressPipe)
                .build();
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("output." + settings.container().extension());
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return defaultOutputDir.resolve(baseName + "_converted." + settings.container().extension());
    }
}
