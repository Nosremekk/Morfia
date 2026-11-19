package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Operation for extracting high-resolution stills or thumbnails from video streams.
 */
public record ExtractFramesOperation(
        Duration timestamp,
        ImageFormat format,
        int width
) implements MediaOperation {

    public enum ImageFormat {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg");

        private final String extension;
        private final String mimeType;

        ImageFormat(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String extension() { return extension; }
        public String mimeType() { return mimeType; }
    }

    public ExtractFramesOperation {
        timestamp = timestamp == null ? Duration.ZERO : timestamp;
        format = format == null ? ImageFormat.PNG : format;
        if (width <= 0) {
            width = -1; // keep original width
        }
    }

    public static ExtractFramesOperation thumbnailAt(Duration time) {
        return new ExtractFramesOperation(time, ImageFormat.PNG, -1);
    }

    @Override
    public String name() {
        return "Extract Frame (" + format.name() + " @ " + FormatUtils.formatDuration(timestamp) + ")";
    }

    @Override
    public String description() {
        return "Capture high-fidelity still frame at " + FormatUtils.formatDuration(timestamp);
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (timestamp.isNegative()) {
            errors.add("Timestamp cannot be negative.");
        }
        if (mediaFile != null && mediaFile.metadata() != null) {
            Duration total = mediaFile.metadata().duration();
            if (total != null && !total.isZero() && timestamp.compareTo(total) > 0) {
                errors.add("Timestamp exceeds video duration (" + FormatUtils.formatDuration(total) + ").");
            }
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

        // Fast seek to timestamp
        double sec = timestamp.toMillis() / 1000.0;
        cmd.add("-ss");
        cmd.add(String.format(java.util.Locale.US, "%.3f", sec));

        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        cmd.add("-frames:v");
        cmd.add("1");

        if (width > 0) {
            cmd.add("-vf");
            cmd.add("scale=" + width + ":-2");
        }

        if (format == ImageFormat.JPEG) {
            cmd.add("-q:v");
            cmd.add("2"); // High quality JPEG
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("frame." + format.extension());
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        long sec = timestamp.toSeconds();
        return defaultOutputDir.resolve(String.format("%s_frame_%ds.%s", baseName, sec, format.extension()));
    }
}
