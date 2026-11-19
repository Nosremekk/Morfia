package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Video trim operation supporting fast stream-copy cut or frame-accurate re-encoded cut.
 */
public record TrimOperation(
        Duration startTime,
        Duration endTime,
        boolean accurateReencode,
        ConversionSettings encodeSettings
) implements MediaOperation {

    public TrimOperation {
        startTime = startTime == null ? Duration.ZERO : startTime;
        Objects.requireNonNull(endTime, "endTime must not be null");
        if (accurateReencode && encodeSettings == null) {
            encodeSettings = ConversionSettings.defaultMp4();
        }
    }

    public static TrimOperation fast(Duration start, Duration end) {
        return new TrimOperation(start, end, false, null);
    }

    public static TrimOperation accurate(Duration start, Duration end, ConversionSettings settings) {
        return new TrimOperation(start, end, true, settings);
    }

    public Duration resultingDuration() {
        return endTime.minus(startTime);
    }

    @Override
    public String name() {
        return accurateReencode ? "Precise Video Cut (Re-encode)" : "Fast Video Cut (Stream Copy)";
    }

    @Override
    public String description() {
        return String.format("Trim from %s to %s (Duration: %s)",
                FormatUtils.formatDuration(startTime),
                FormatUtils.formatDuration(endTime),
                FormatUtils.formatDuration(resultingDuration()));
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (startTime.isNegative()) {
            errors.add("Start time cannot be negative.");
        }
        if (endTime.compareTo(startTime) <= 0) {
            errors.add("End time must be greater than start time.");
        }
        if (mediaFile != null && mediaFile.metadata() != null) {
            Duration total = mediaFile.metadata().duration();
            if (total != null && !total.isZero() && endTime.compareTo(total) > 0) {
                errors.add("End time (" + FormatUtils.formatDuration(endTime) + 
                           ") exceeds total video duration (" + FormatUtils.formatDuration(total) + ").");
            }
        }
        if (accurateReencode && encodeSettings != null) {
            errors.addAll(ConversionValidator.validate(encodeSettings, capabilities));
        }
        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");

        // Start time before input for fast keyframe seek
        double startSeconds = startTime.toMillis() / 1000.0;
        double endSeconds = endTime.toMillis() / 1000.0;

        cmd.add("-ss");
        cmd.add(String.format(java.util.Locale.US, "%.3f", startSeconds));

        cmd.add("-to");
        cmd.add(String.format(java.util.Locale.US, "%.3f", endSeconds));

        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        cmd.add("-map");
        cmd.add("0");

        if (!accurateReencode) {
            // Fast cut (Stream Copy)
            cmd.add("-c");
            cmd.add("copy");
        } else {
            // Accurate cut (Re-encode)
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add(encodeSettings != null ? encodeSettings.speedPreset().value() : "medium");
            cmd.add("-crf");
            cmd.add(String.valueOf(encodeSettings != null && encodeSettings.crf() != null ? encodeSettings.crf() : 22));
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("192k");
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("trimmed_output.mp4");
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "mp4";
        return defaultOutputDir.resolve(baseName + "_trimmed." + ext);
    }
}
