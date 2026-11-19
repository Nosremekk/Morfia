package com.ffmpegstudio.domain.model;

import java.time.Duration;

/**
 * Pure domain formatting helpers for human-readable technical media representations.
 */
public final class FormatUtils {

    private FormatUtils() {}

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(java.util.Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatBitRate(long bitRate) {
        if (bitRate <= 0) {
            return "N/A";
        }
        if (bitRate < 1_000) {
            return bitRate + " bps";
        } else if (bitRate < 1_000_000) {
            return String.format(java.util.Locale.US, "%.1f kbps", bitRate / 1_000.0);
        } else {
            return String.format(java.util.Locale.US, "%.2f Mbps", bitRate / 1_000_000.0);
        }
    }

    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return "00:00:00";
        }
        long totalSeconds = duration.toSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "N/A";
        }
        return width + " x " + height;
    }

    public static String formatFps(double fps) {
        if (fps <= 0.0) {
            return "N/A";
        }
        return String.format(java.util.Locale.US, "%.2f fps", fps);
    }
}
