package com.ffmpegstudio.infrastructure.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmpegstudio.domain.model.LoudnormMetrics;

import java.util.List;
import java.util.Optional;

/**
 * Parses the JSON summary printed by FFmpeg's loudnorm filter (print_format=json).
 */
public final class LoudnormMetricsParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LoudnormMetricsParser() {}

    public static Optional<LoudnormMetrics> parseFromStderr(List<String> stderrLines) {
        if (stderrLines == null || stderrLines.isEmpty()) {
            return Optional.empty();
        }
        String joined = String.join("\n", stderrLines);
        int jsonStart = joined.lastIndexOf('{');
        int jsonEnd = joined.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return Optional.empty();
        }
        String jsonBlock = joined.substring(jsonStart, jsonEnd + 1);
        try {
            JsonNode root = MAPPER.readTree(jsonBlock);
            if (!root.has("input_i") || !root.has("input_tp") || !root.has("input_lra")
                    || !root.has("input_thresh") || !root.has("target_offset")) {
                return Optional.empty();
            }
            double i = parseDoubleSafe(root.get("input_i").asText());
            double tp = parseDoubleSafe(root.get("input_tp").asText());
            double lra = parseDoubleSafe(root.get("input_lra").asText());
            double thresh = parseDoubleSafe(root.get("input_thresh").asText());
            double offset = parseDoubleSafe(root.get("target_offset").asText());

            LoudnormMetrics metrics = new LoudnormMetrics(i, tp, lra, thresh, offset);
            return metrics.isValidForLinearNormalization() ? Optional.of(metrics) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static double parseDoubleSafe(String raw) {
        if (raw == null || raw.isBlank()) return Double.NaN;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
