package com.ffmpegstudio.infrastructure.ffmpeg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffmpegstudio.domain.model.AudioStream;
import com.ffmpegstudio.domain.model.ChapterInfo;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MediaMetadata;
import com.ffmpegstudio.domain.model.MediaStream;
import com.ffmpegstudio.domain.model.SubtitleStream;
import com.ffmpegstudio.domain.model.VideoStream;
import com.ffmpegstudio.domain.port.MediaInspectionPort;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.domain.port.ProcessExecutorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FFprobeClient implements MediaInspectionPort {
    private static final Logger logger = LoggerFactory.getLogger(FFprobeClient.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

    private final ProcessExecutorPort processExecutor;
    private final ObjectMapper objectMapper;

    public FFprobeClient(ProcessExecutorPort processExecutor) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public MediaFile inspect(String ffprobePath, Path mediaFilePath) {
        Objects.requireNonNull(mediaFilePath, "mediaFilePath must not be null");
        String exec = (ffprobePath == null || ffprobePath.isBlank()) ? "ffprobe" : ffprobePath;

        long fileSize = 0;
        try {
            if (Files.exists(mediaFilePath)) {
                fileSize = Files.size(mediaFilePath);
            }
        } catch (Exception e) {
            logger.debug("Could not read disk file size for {}: {}", mediaFilePath, e.getMessage());
        }

        List<String> command = List.of(
                exec,
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                "-show_chapters",
                mediaFilePath.toAbsolutePath().toString()
        );

        ProcessExecutionResult result = processExecutor.execute(command, PROBE_TIMEOUT);
        if (!result.isSuccess()) {
            String errorMsg = result.standardErrorText().isBlank() 
                    ? "FFprobe failed with exit code " + result.exitCode()
                    : result.standardErrorText();
            logger.warn("FFprobe inspection failed for '{}': {}", mediaFilePath, errorMsg);
            throw new IllegalArgumentException("Unable to inspect media file: " + errorMsg);
        }

        String jsonOutput = result.standardOutputText();
        MediaMetadata metadata = parseJsonMetadata(jsonOutput, fileSize);

        return new MediaFile(
                mediaFilePath,
                mediaFilePath.getFileName() != null ? mediaFilePath.getFileName().toString() : "unknown",
                fileSize > 0 ? fileSize : metadata.sizeBytes(),
                metadata
        );
    }

    public MediaMetadata parseJsonMetadata(String json, long fallbackFileSize) {
        if (json == null || json.isBlank()) {
            return new MediaMetadata("", "", Duration.ZERO, fallbackFileSize, 0, Map.of(), List.of(), List.of());
        }

        try {
            JsonNode root = objectMapper.readTree(json);

            // Format Node
            JsonNode formatNode = root.path("format");
            String formatName = formatNode.path("format_name").asText("");
            String formatLongName = formatNode.path("format_long_name").asText("");
            long sizeBytes = parseLongSafe(formatNode.path("size").asText(), fallbackFileSize);
            long bitRate = parseLongSafe(formatNode.path("bit_rate").asText(), 0);
            double durationSec = parseDoubleSafe(formatNode.path("duration").asText(), 0.0);
            Duration duration = Duration.ofMillis((long) (durationSec * 1000.0));

            Map<String, String> tags = parseTags(formatNode.path("tags"));

            // Streams Nodes
            List<MediaStream> streams = new ArrayList<>();
            JsonNode streamsNode = root.path("streams");
            if (streamsNode.isArray()) {
                for (JsonNode s : streamsNode) {
                    MediaStream parsedStream = parseStream(s);
                    if (parsedStream != null) {
                        streams.add(parsedStream);
                    }
                }
            }

            // Chapters Nodes
            List<ChapterInfo> chapters = new ArrayList<>();
            JsonNode chaptersNode = root.path("chapters");
            if (chaptersNode.isArray()) {
                for (JsonNode c : chaptersNode) {
                    long id = c.path("id").asLong(0);
                    double startSec = parseDoubleSafe(c.path("start_time").asText(), 0.0);
                    double endSec = parseDoubleSafe(c.path("end_time").asText(), 0.0);
                    String title = c.path("tags").path("title").asText("Chapter " + id);
                    chapters.add(new ChapterInfo(id, Duration.ofMillis((long) (startSec * 1000)), Duration.ofMillis((long) (endSec * 1000)), title));
                }
            }

            return new MediaMetadata(
                    formatName,
                    formatLongName,
                    duration,
                    sizeBytes,
                    bitRate,
                    tags,
                    streams,
                    chapters
            );

        } catch (Exception e) {
            logger.error("Failed to parse FFprobe JSON: {}", e.getMessage(), e);
            return new MediaMetadata("", "", Duration.ZERO, fallbackFileSize, 0, Map.of(), List.of(), List.of());
        }
    }

    private MediaStream parseStream(JsonNode s) {
        String codecType = s.path("codec_type").asText("").toLowerCase();
        int index = s.path("index").asInt(0);
        String codecName = s.path("codec_name").asText("");
        String codecLongName = s.path("codec_long_name").asText("");
        String profile = s.path("profile").asText("");
        String language = s.path("tags").path("language").asText("und");

        return switch (codecType) {
            case "video" -> {
                int width = s.path("width").asInt(0);
                int height = s.path("height").asInt(0);
                String dar = s.path("display_aspect_ratio").asText("");
                if (dar.isBlank() && width > 0 && height > 0) {
                    dar = calculateAspectRatio(width, height);
                }
                String pixFmt = s.path("pix_fmt").asText("");
                double rFps = parseFraction(s.path("r_frame_rate").asText(""));
                double avgFps = parseFraction(s.path("avg_frame_rate").asText(""));
                double fps = rFps > 0 ? rFps : avgFps;
                long streamBitrate = parseLongSafe(s.path("bit_rate").asText(""), 0);

                yield new VideoStream(
                        index,
                        codecName,
                        codecLongName,
                        profile,
                        width,
                        height,
                        dar,
                        fps,
                        avgFps,
                        pixFmt,
                        streamBitrate,
                        language
                );
            }
            case "audio" -> {
                int sampleRate = s.path("sample_rate").asInt(0);
                int channels = s.path("channels").asInt(0);
                String channelLayout = s.path("channel_layout").asText("");
                long streamBitrate = parseLongSafe(s.path("bit_rate").asText(""), 0);

                yield new AudioStream(
                        index,
                        codecName,
                        codecLongName,
                        profile,
                        sampleRate,
                        channels,
                        channelLayout,
                        streamBitrate,
                        language
                );
            }
            case "subtitle" -> {
                String title = s.path("tags").path("title").asText("");
                boolean isDefault = s.path("disposition").path("default").asInt(0) == 1;
                boolean isForced = s.path("disposition").path("forced").asInt(0) == 1;

                yield new SubtitleStream(
                        index,
                        codecName,
                        codecLongName,
                        language,
                        title,
                        isDefault,
                        isForced
                );
            }
            case "data", "attachment" -> {
                String title = s.path("tags").path("title").asText("");
                String filename = s.path("tags").path("filename").asText("");
                String mimeType = s.path("tags").path("mimetype").asText("");
                yield new com.ffmpegstudio.domain.model.OtherStream(
                        index,
                        codecType,
                        codecName,
                        codecLongName,
                        language,
                        title,
                        filename,
                        mimeType
                );
            }
            default -> null;
        };
    }

    private Map<String, String> parseTags(JsonNode tagsNode) {
        if (tagsNode == null || !tagsNode.isObject()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = tagsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), entry.getValue().asText(""));
        }
        return Map.copyOf(map);
    }

    double parseFraction(String fraction) {
        if (fraction == null || fraction.isBlank() || fraction.equalsIgnoreCase("N/A") || fraction.equals("0/0")) {
            return 0.0;
        }
        int slash = fraction.indexOf('/');
        if (slash > 0) {
            try {
                double num = Double.parseDouble(fraction.substring(0, slash).trim());
                double den = Double.parseDouble(fraction.substring(slash + 1).trim());
                if (den != 0) {
                    return num / den;
                }
            } catch (NumberFormatException ignored) {}
        } else {
            try {
                return Double.parseDouble(fraction.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    private long parseLongSafe(String str, long fallback) {
        if (str == null || str.isBlank() || str.equalsIgnoreCase("N/A")) {
            return fallback;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double parseDoubleSafe(String str, double fallback) {
        if (str == null || str.isBlank() || str.equalsIgnoreCase("N/A")) {
            return fallback;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String calculateAspectRatio(int width, int height) {
        int gcd = gcd(width, height);
        if (gcd > 0) {
            return (width / gcd) + ":" + (height / gcd);
        }
        return "";
    }

    private int gcd(int a, int b) {
        return (b == 0) ? a : gcd(b, a % b);
    }
}
