package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Isolated image format conversion operation supporting PNG, JPEG, WebP, BMP, and TIFF,
 * with resizing, quality control, and alpha-channel transparency preservation when compatible.
 */
public record ImageConversionOperation(
        TargetImageFormat format,
        Integer targetWidth,
        Integer targetHeight,
        boolean keepAspectRatio,
        int quality, // 1-100
        boolean preserveTransparency
) implements MediaOperation {

    public enum TargetImageFormat {
        PNG("png", "png", true, "PNG (Lossless + Transparência)"),
        JPEG("jpg", "mjpeg", false, "JPEG (Alta compatibilidade, sem transparência)"),
        WEBP("webp", "libwebp", true, "WebP (Compacto + Transparência)"),
        BMP("bmp", "bmp", false, "BMP (Bitmap sem compressão)"),
        TIFF("tiff", "tiff", true, "TIFF (Arquivo/Impressão + Transparência)");

        private final String extension;
        private final String encoderName;
        private final boolean supportsAlpha;
        private final String label;

        TargetImageFormat(String extension, String encoderName, boolean supportsAlpha, String label) {
            this.extension = extension;
            this.encoderName = encoderName;
            this.supportsAlpha = supportsAlpha;
            this.label = label;
        }

        public String extension() { return extension; }
        public String encoderName() { return encoderName; }
        public boolean supportsAlpha() { return supportsAlpha; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public ImageConversionOperation {
        format = format == null ? TargetImageFormat.PNG : format;
        quality = Math.clamp(quality, 1, 100);
    }

    @Override
    public String name() {
        return "Conversão de Imagem (" + format.name() + ")";
    }

    @Override
    public String description() {
        return format.label() + (targetWidth != null ? " • " + targetWidth + "px" : "");
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (targetWidth != null && targetWidth <= 0) {
            errors.add("Largura alvo deve ser positiva.");
        }
        if (targetHeight != null && targetHeight <= 0) {
            errors.add("Altura alvo deve ser positiva.");
        }
        if (capabilities != null && !capabilities.encoders().isEmpty() && !capabilities.hasEncoder(format.encoderName())) {
            errors.add("Encoder de imagem '" + format.encoderName() + "' não está disponível no FFmpeg local.");
        }
        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.png");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        List<String> vf = new ArrayList<>();
        if (targetWidth != null && targetWidth > 0) {
            if (keepAspectRatio || targetHeight == null || targetHeight <= 0) {
                vf.add("scale=" + targetWidth + ":-1");
            } else {
                vf.add("scale=" + targetWidth + ":" + targetHeight);
            }
        } else if (targetHeight != null && targetHeight > 0) {
            vf.add("scale=-1:" + targetHeight);
        }

        if (preserveTransparency && format.supportsAlpha()) {
            vf.add("format=rgba");
        } else if (!format.supportsAlpha()) {
            vf.add(format == TargetImageFormat.JPEG ? "format=yuvj420p" : "format=rgb24");
        }

        if (!vf.isEmpty()) {
            cmd.add("-vf");
            cmd.add(String.join(",", vf));
        }

        cmd.add("-c:v");
        cmd.add(format.encoderName());

        if (format == TargetImageFormat.JPEG) {
            // Map 1..100 quality to FFmpeg mjpeg -q:v (31=worst .. 2=best)
            int qv = Math.clamp((int) Math.round(31 - (quality / 100.0) * 29.0), 2, 31);
            cmd.add("-q:v");
            cmd.add(String.valueOf(qv));
        } else if (format == TargetImageFormat.WEBP) {
            cmd.add("-quality");
            cmd.add(String.valueOf(quality));
        }

        cmd.add("-frames:v");
        cmd.add("1");
        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        String base = "converted_image";
        if (mediaFile != null && mediaFile.fileName() != null) {
            String fn = mediaFile.fileName();
            base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        }
        return defaultOutputDir.resolve(base + "." + format.extension());
    }
}
