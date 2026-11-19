package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Animated GIF generation operation utilizing two-pass palettegen/paletteuse filter graphs for high visual fidelity.
 */
public record GenerateGifOperation(
        Duration startTime,
        Duration duration,
        int fps,
        int width,
        boolean optimizePalette
) implements MediaOperation {

    public GenerateGifOperation {
        startTime = startTime == null ? Duration.ZERO : startTime;
        duration = duration == null ? Duration.ofSeconds(5) : duration;
        if (fps <= 0 || fps > 60) {
            fps = 15;
        }
        if (width <= 0) {
            width = 480;
        }
    }

    public static GenerateGifOperation standard(Duration start, Duration length, int width, int fps) {
        return new GenerateGifOperation(start, length, fps, width, true);
    }

    @Override
    public String name() {
        return "Animated GIF Generator (" + width + "p @ " + fps + "fps)";
    }

    @Override
    public String description() {
        return String.format("Create GIF from %s for %s (%s palette)",
                FormatUtils.formatDuration(startTime),
                FormatUtils.formatDuration(duration),
                optimizePalette ? "Optimized" : "Basic");
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (startTime.isNegative()) {
            errors.add("Start time cannot be negative.");
        }
        if (duration.isZero() || duration.isNegative()) {
            errors.add("GIF duration must be positive.");
        }
        if (duration.toSeconds() > 120) {
            errors.add("GIF duration is longer than recommended (maximum 120s to avoid enormous file size).");
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

        // Seek before input
        double startSec = startTime.toMillis() / 1000.0;
        double lengthSec = duration.toMillis() / 1000.0;

        cmd.add("-ss");
        cmd.add(String.format(java.util.Locale.US, "%.3f", startSec));

        cmd.add("-t");
        cmd.add(String.format(java.util.Locale.US, "%.3f", lengthSec));

        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        if (optimizePalette) {
            // High-quality palette generation filter complex
            String filterGraph = String.format(java.util.Locale.US,
                    "[0:v] fps=%d,scale=%d:-1:flags=lanczos,split [a][b];[a] palettegen=stats_mode=full [p];[b][p] paletteuse=dither=sierra2_4a",
                    fps, width);
            cmd.add("-filter_complex");
            cmd.add(filterGraph);
        } else {
            cmd.add("-vf");
            cmd.add(String.format(java.util.Locale.US, "fps=%d,scale=%d:-1:flags=lanczos", fps, width));
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("animation.gif");
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return defaultOutputDir.resolve(baseName + ".gif");
    }
}
