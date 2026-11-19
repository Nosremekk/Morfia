package com.ffmpegstudio.domain.model.filter;

import java.util.Locale;

/**
 * Typed immutable video filter definitions.
 */
public final class VideoFilters {
    private VideoFilters() {}

    public sealed interface VideoFilter extends MediaFilter
            permits Resize, Crop, Pad, Rotate, Flip, AspectRatio,
                    ColorAdjust, Grayscale, Sharpen, Blur, Denoise, Deinterlace,
                    ChangeFps, Speed, VideoFade, Reverse {
        @Override
        default StreamKind streamKind() { return StreamKind.VIDEO; }
    }

    // --- GEOMETRIC ---

    public record Resize(int width, int height, boolean keepAspectRatio) implements VideoFilter {
        @Override public String id() { return "resize"; }
        @Override public String displayName() {
            return keepAspectRatio ? "Redimensionar (" + width + "px largura)" : "Redimensionar (" + width + "x" + height + ")";
        }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return "scale"; }
        @Override public String toFilterExpression() {
            if (keepAspectRatio) {
                return width > 0 ? "scale=" + width + ":-2" : "scale=-2:" + height;
            }
            int w = width % 2 == 0 ? width : width - 1;
            int h = height % 2 == 0 ? height : height - 1;
            return "scale=" + Math.max(2, w) + ":" + Math.max(2, h);
        }
    }

    public record Crop(int width, int height, int x, int y) implements VideoFilter {
        @Override public String id() { return "crop"; }
        @Override public String displayName() { return "Recortar (Crop " + width + "x" + height + " @ " + x + "," + y + ")"; }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return "crop"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "crop=%d:%d:%d:%d", width, height, x, y);
        }
    }

    public record Pad(int width, int height, String color) implements VideoFilter {
        public Pad {
            color = (color == null || color.isBlank()) ? "black" : color.replaceAll("[^a-zA-Z0-9#]", "");
        }
        @Override public String id() { return "pad"; }
        @Override public String displayName() { return "Preenchimento (Pad " + width + "x" + height + ")"; }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return "pad"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "pad=%d:%d:(ow-iw)/2:(oh-ih)/2:%s", width, height, color);
        }
    }

    public enum RotationAngle {
        CW_90("90° Horário", "transpose=1"),
        CCW_90("90° Anti-horário", "transpose=2"),
        ROTATE_180("180°", "transpose=2,transpose=2");

        private final String label;
        private final String expr;
        RotationAngle(String label, String expr) { this.label = label; this.expr = expr; }
        public String label() { return label; }
        public String expr() { return expr; }
        @Override public String toString() { return label; }
    }

    public record Rotate(RotationAngle angle) implements VideoFilter {
        public Rotate { angle = angle == null ? RotationAngle.CW_90 : angle; }
        @Override public String id() { return "rotate"; }
        @Override public String displayName() { return "Rotação (" + angle.label() + ")"; }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return "transpose"; }
        @Override public String toFilterExpression() { return angle.expr(); }
    }

    public record Flip(boolean horizontal, boolean vertical) implements VideoFilter {
        @Override public String id() { return "flip"; }
        @Override public String displayName() {
            if (horizontal && vertical) return "Espelhar Horizontal + Vertical";
            return horizontal ? "Espelhar Horizontal" : "Espelhar Vertical";
        }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return horizontal ? "hflip" : "vflip"; }
        @Override public String toFilterExpression() {
            if (horizontal && vertical) return "hflip,vflip";
            if (horizontal) return "hflip";
            if (vertical) return "vflip";
            return "null";
        }
    }

    public record AspectRatio(String dar) implements VideoFilter {
        public AspectRatio {
            dar = (dar == null || dar.isBlank()) ? "16/9" : dar.replace(":", "/").replaceAll("[^0-9./]", "");
        }
        @Override public String id() { return "setdar"; }
        @Override public String displayName() { return "Proporção (DAR " + dar + ")"; }
        @Override public FilterCategory category() { return FilterCategory.GEOMETRIC; }
        @Override public String requiredFFmpegFilter() { return "setdar"; }
        @Override public String toFilterExpression() { return "setdar=" + dar; }
    }

    // --- IMAGE / COLOR ---

    public record ColorAdjust(double brightness, double contrast, double saturation, double gamma) implements VideoFilter {
        @Override public String id() { return "eq"; }
        @Override public String displayName() {
            return String.format(Locale.US, "Cor (B:%.2f C:%.2f S:%.2f G:%.2f)", brightness, contrast, saturation, gamma);
        }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "eq"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "eq=brightness=%.2f:contrast=%.2f:saturation=%.2f:gamma=%.2f",
                    brightness, contrast, saturation, gamma);
        }
    }

    public record Grayscale() implements VideoFilter {
        @Override public String id() { return "grayscale"; }
        @Override public String displayName() { return "Escala de Cinza (Preto & Branco)"; }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "hue"; }
        @Override public String toFilterExpression() { return "hue=s=0"; }
    }

    public record Sharpen(double amount) implements VideoFilter {
        @Override public String id() { return "sharpen"; }
        @Override public String displayName() { return String.format(Locale.US, "Nitidez (%.2f)", amount); }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "unsharp"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "unsharp=5:5:%.2f:5:5:0.0", amount);
        }
    }

    public record Blur(double sigma) implements VideoFilter {
        @Override public String id() { return "blur"; }
        @Override public String displayName() { return String.format(Locale.US, "Desfoque (Sigma %.1f)", sigma); }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "gblur"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "gblur=sigma=%.2f", sigma);
        }
    }

    public record Denoise(double lumaSpatial) implements VideoFilter {
        @Override public String id() { return "denoise"; }
        @Override public String displayName() { return String.format(Locale.US, "Redução de Ruído (%.1f)", lumaSpatial); }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "hqdn3d"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "hqdn3d=%.1f", lumaSpatial);
        }
    }

    public record Deinterlace() implements VideoFilter {
        @Override public String id() { return "deinterlace"; }
        @Override public String displayName() { return "Desentrelaçamento (YADIF)"; }
        @Override public FilterCategory category() { return FilterCategory.IMAGE; }
        @Override public String requiredFFmpegFilter() { return "yadif"; }
        @Override public String toFilterExpression() { return "yadif"; }
    }

    // --- TEMPORAL ---

    public record ChangeFps(double fps) implements VideoFilter {
        @Override public String id() { return "fps"; }
        @Override public String displayName() { return String.format(Locale.US, "Alterar FPS (%.2f)", fps); }
        @Override public FilterCategory category() { return FilterCategory.TEMPORAL; }
        @Override public String requiredFFmpegFilter() { return "fps"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "fps=%.3f", fps);
        }
    }

    public record Speed(double multiplier) implements VideoFilter {
        @Override public String id() { return "speed_video"; }
        @Override public String displayName() { return String.format(Locale.US, "Velocidade Vídeo (%.2fx)", multiplier); }
        @Override public FilterCategory category() { return FilterCategory.TEMPORAL; }
        @Override public String requiredFFmpegFilter() { return "setpts"; }
        @Override public String toFilterExpression() {
            double ptsFactor = 1.0 / Math.max(0.1, multiplier);
            return String.format(Locale.US, "setpts=%.4f*PTS", ptsFactor);
        }
    }

    public record VideoFade(boolean fadeIn, double startSec, double durationSec) implements VideoFilter {
        @Override public String id() { return fadeIn ? "fade_in" : "fade_out"; }
        @Override public String displayName() {
            return String.format(Locale.US, "%s Vídeo (%.1fs @ %.1fs)", fadeIn ? "Fade In" : "Fade Out", durationSec, startSec);
        }
        @Override public FilterCategory category() { return FilterCategory.TEMPORAL; }
        @Override public String requiredFFmpegFilter() { return "fade"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "fade=t=%s:st=%.2f:d=%.2f", fadeIn ? "in" : "out", startSec, durationSec);
        }
    }

    public record Reverse() implements VideoFilter {
        @Override public String id() { return "reverse"; }
        @Override public String displayName() { return "Reverter Vídeo (Apenas clipes curtos!)"; }
        @Override public FilterCategory category() { return FilterCategory.TEMPORAL; }
        @Override public String requiredFFmpegFilter() { return "reverse"; }
        @Override public String toFilterExpression() { return "reverse"; }
    }
}
