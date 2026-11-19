package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.ExtractFramesOperation.ImageFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Periodic frame extraction operation supporting interval-based (every N seconds)
 * or FPS-based extraction within an optional temporal range into a dedicated directory.
 */
public record PeriodicFramesOperation(
        ExtractionMode mode,
        double value, // N seconds (for EVERY_N_SECONDS) or FPS (for TARGET_FPS)
        Duration startTime,
        Duration endTime,
        ImageFormat format,
        int width,
        int maxFramesLimit,
        Path outputDirectory,
        String filenamePrefix
) implements MediaOperation {

    public enum ExtractionMode {
        EVERY_N_SECONDS("A cada N segundos"),
        TARGET_FPS("Por FPS");

        private final String label;
        ExtractionMode(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public PeriodicFramesOperation {
        mode = mode == null ? ExtractionMode.EVERY_N_SECONDS : mode;
        if (value <= 0) value = 1.0;
        startTime = startTime == null ? Duration.ZERO : startTime;
        format = format == null ? ImageFormat.PNG : format;
        if (width <= 0) width = -1;
        if (maxFramesLimit <= 0) maxFramesLimit = 500;
        filenamePrefix = (filenamePrefix == null || filenamePrefix.isBlank())
                ? "frame_"
                : filenamePrefix.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public long estimateFrameCount(MediaFile mediaFile) {
        Duration effectiveDuration = null;
        if (endTime != null && endTime.compareTo(startTime) > 0) {
            effectiveDuration = endTime.minus(startTime);
        } else if (mediaFile != null && mediaFile.metadata() != null && mediaFile.metadata().duration() != null) {
            Duration total = mediaFile.metadata().duration();
            if (total.compareTo(startTime) > 0) {
                effectiveDuration = total.minus(startTime);
            }
        }
        if (effectiveDuration == null || effectiveDuration.isZero()) return 0;

        double seconds = effectiveDuration.toMillis() / 1000.0;
        double fps = (mode == ExtractionMode.EVERY_N_SECONDS) ? (1.0 / value) : value;
        long estimated = Math.max(1, (long) Math.floor(seconds * fps));
        return Math.min(estimated, maxFramesLimit);
    }

    @Override
    public String name() {
        return mode == ExtractionMode.EVERY_N_SECONDS
                ? String.format(Locale.US, "Extração Periódica (1 frame / %.1fs)", value)
                : String.format(Locale.US, "Extração Periódica (%.1f FPS)", value);
    }

    @Override
    public String description() {
        return String.format("%s em %s (limite: %d frames)", name(), format.name(), maxFramesLimit);
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (value <= 0) {
            errors.add("O intervalo ou FPS deve ser maior que zero.");
        }
        if (startTime.isNegative()) {
            errors.add("Tempo inicial não pode ser negativo.");
        }
        if (endTime != null && endTime.compareTo(startTime) <= 0) {
            errors.add("Tempo final deve ser maior que o tempo inicial.");
        }
        if (outputDirectory == null) {
            errors.add("Diretório de saída dedicado é obrigatório.");
        } else if (Files.exists(outputDirectory) && Files.isDirectory(outputDirectory)) {
            String ext = "." + format.extension();
            try (Stream<Path> s = Files.list(outputDirectory)) {
                boolean collision = s.anyMatch(p -> {
                    String fn = p.getFileName().toString();
                    return fn.startsWith(filenamePrefix) && fn.endsWith(ext);
                });
                if (collision) {
                    errors.add("O diretório de saída já contém frames com o prefixo '" + filenamePrefix
                            + "'. Escolha outro diretório ou prefixo para evitar sobrescrita silenciosa.");
                }
            } catch (IOException e) {
                errors.add("Não foi possível inspecionar o diretório de saída: " + e.getMessage());
            }
        }
        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path ignoredOutputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.mp4");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-n"); // Never silently overwrite existing sequence frames

        if (!startTime.isZero()) {
            cmd.add("-ss");
            cmd.add(String.format(Locale.US, "%.3f", startTime.toMillis() / 1000.0));
        }
        if (endTime != null && endTime.compareTo(startTime) > 0) {
            cmd.add("-to");
            cmd.add(String.format(Locale.US, "%.3f", endTime.toMillis() / 1000.0));
        }

        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        List<String> vf = new ArrayList<>();
        double fps = (mode == ExtractionMode.EVERY_N_SECONDS) ? (1.0 / value) : value;
        vf.add(String.format(Locale.US, "fps=%.4f", fps));
        if (width > 0) {
            vf.add("scale=" + width + ":-2");
        }
        cmd.add("-vf");
        cmd.add(String.join(",", vf));

        cmd.add("-frames:v");
        cmd.add(String.valueOf(maxFramesLimit));

        if (format == ImageFormat.JPEG) {
            cmd.add("-q:v");
            cmd.add("2");
        }

        Path dir = outputDirectory != null ? outputDirectory : Paths.get(".");
        Path pattern = dir.resolve(filenamePrefix + "%04d." + format.extension());
        cmd.add(pattern.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        return outputDirectory != null ? outputDirectory : defaultOutputDir.resolve("frames_export");
    }
}
