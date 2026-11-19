package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.ProcessingProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses FFmpeg `-progress pipe:1` key-value output and computes accurate completion metrics.
 */
public class FFmpegProgressParser {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegProgressParser.class);

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");

    private final long totalDurationMillis;

    private double currentFps = 0.0;
    private long currentBitrateKbps = 0;
    private long currentTimeMillis = 0;
    private double speedMultiplier = 1.0;
    private boolean isFinished = false;

    public FFmpegProgressParser(long totalDurationMillis) {
        this.totalDurationMillis = totalDurationMillis;
    }

    public synchronized Optional<ProcessingProgress> processLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        int eqIdx = line.indexOf('=');
        if (eqIdx <= 0) {
            return Optional.empty();
        }

        String key = line.substring(0, eqIdx).trim();
        String value = line.substring(eqIdx + 1).trim();

        switch (key) {
            case "fps" -> currentFps = parseDouble(value, currentFps);
            case "bitrate" -> currentBitrateKbps = parseBitrate(value, currentBitrateKbps);
            case "speed" -> speedMultiplier = parseSpeed(value, speedMultiplier);
            case "out_time_us" -> {
                long us = parseLong(value, -1);
                if (us >= 0) {
                    currentTimeMillis = us / 1000;
                }
            }
            case "out_time_ms" -> {
                long ms = parseLong(value, -1);
                if (ms >= 0) {
                    currentTimeMillis = ms;
                }
            }
            case "out_time" -> {
                long ms = parseOutTime(value);
                if (ms >= 0) {
                    currentTimeMillis = ms;
                }
            }
            case "progress" -> {
                if (value.equalsIgnoreCase("end")) {
                    isFinished = true;
                    return Optional.of(ProcessingProgress.completed());
                } else if (value.equalsIgnoreCase("continue")) {
                    return Optional.of(computeProgressSnapshot());
                }
            }
        }

        return Optional.empty();
    }

    private ProcessingProgress computeProgressSnapshot() {
        if (totalDurationMillis <= 0) {
            // Indeterminate duration
            return new ProcessingProgress(
                    0.0,
                    currentFps,
                    currentBitrateKbps,
                    currentTimeMillis,
                    0,
                    speedMultiplier,
                    -1
            );
        }

        double percentage = Math.clamp((currentTimeMillis * 100.0) / totalDurationMillis, 0.0, 99.9);
        long remainingMillis = Math.max(0, totalDurationMillis - currentTimeMillis);
        long etaSeconds = (speedMultiplier > 0.01) 
                ? (long) ((remainingMillis / 1000.0) / speedMultiplier) 
                : -1;

        return new ProcessingProgress(
                percentage,
                currentFps,
                currentBitrateKbps,
                currentTimeMillis,
                totalDurationMillis,
                speedMultiplier,
                etaSeconds
        );
    }

    long parseOutTime(String val) {
        if (val == null || val.isBlank() || val.equalsIgnoreCase("N/A")) {
            return -1;
        }
        Matcher m = TIME_PATTERN.matcher(val);
        if (m.find()) {
            try {
                long hours = Long.parseLong(m.group(1));
                long minutes = Long.parseLong(m.group(2));
                double seconds = Double.parseDouble(m.group(3));
                return (long) ((hours * 3600 + minutes * 60 + seconds) * 1000.0);
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private double parseDouble(String str, double fallback) {
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseLong(String str, long fallback) {
        try {
            return Long.parseLong(str);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseSpeed(String str, double fallback) {
        if (str == null || str.isBlank() || str.equalsIgnoreCase("N/A")) {
            return fallback;
        }
        try {
            String clean = str.replace("x", "").trim();
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseBitrate(String str, long fallback) {
        if (str == null || str.isBlank() || str.equalsIgnoreCase("N/A")) {
            return fallback;
        }
        try {
            String clean = str.toLowerCase().replace("kbits/s", "").replace("kbps", "").trim();
            return (long) Double.parseDouble(clean);
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean isFinished() {
        return isFinished;
    }

    public long getTotalDurationMillis() {
        return totalDurationMillis;
    }
}
