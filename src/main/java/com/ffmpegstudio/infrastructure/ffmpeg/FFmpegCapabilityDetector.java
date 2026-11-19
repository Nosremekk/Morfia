package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.CodecInfo;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.port.CapabilityDetectorPort;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.domain.port.ProcessExecutorPort;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FFmpegCapabilityDetector implements CapabilityDetectorPort {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegCapabilityDetector.class);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)(?:ffmpeg|ffprobe)\\s+version\\s+([^\\s,]+)");
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private final ProcessExecutorPort processExecutor;
    private final FileSystemService fileSystemService;

    public FFmpegCapabilityDetector(ProcessExecutorPort processExecutor, FileSystemService fileSystemService) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
        this.fileSystemService = Objects.requireNonNull(fileSystemService, "fileSystemService must not be null");
    }

    @Override
    public ExecutableInfo checkExecutable(String executablePath, String name, String versionFlag) {
        if (executablePath == null || executablePath.isBlank()) {
            return ExecutableInfo.unavailable(name, "", "Path not specified");
        }

        Optional<java.nio.file.Path> resolvedPath = fileSystemService.findInPath(executablePath);
        String finalPath = resolvedPath.map(java.nio.file.Path::toString).orElse(executablePath);

        try {
            ProcessExecutionResult result = processExecutor.execute(List.of(finalPath, versionFlag), TIMEOUT);
            if (result.isSuccess()) {
                String firstLine = result.standardOutputLines().stream()
                        .filter(l -> l.toLowerCase().contains("version"))
                        .findFirst()
                        .or(() -> result.standardErrorLines().stream()
                                .filter(l -> l.toLowerCase().contains("version"))
                                .findFirst())
                        .orElse("");

                String version = parseVersionString(firstLine);
                return ExecutableInfo.available(name, finalPath, version, firstLine);
            } else {
                String errorDetails = result.standardErrorLines().isEmpty()
                        ? "Process exited with code " + result.exitCode()
                        : String.join(" ", result.standardErrorLines());
                return ExecutableInfo.unavailable(name, finalPath, errorDetails);
            }
        } catch (Exception e) {
            logger.debug("Failed testing executable {}: {}", executablePath, e.getMessage());
            return ExecutableInfo.unavailable(name, finalPath, e.getMessage());
        }
    }

    @Override
    public FFmpegCapabilities detectCapabilities(String ffmpegPath) {
        ExecutableInfo info = checkExecutable(ffmpegPath, "FFmpeg", "-version");
        if (!info.isAvailable()) {
            logger.warn("FFmpeg is unavailable at '{}'; returning empty capabilities", ffmpegPath);
            return FFmpegCapabilities.empty();
        }

        String exec = info.path();
        List<String> hwaccels = parseHwAccels(exec);
        List<CodecInfo> encoders = parseCodecs(exec, "-encoders", true);
        List<CodecInfo> decoders = parseCodecs(exec, "-decoders", false);
        List<String> filters = parseFilters(exec);
        List<String> formats = parseFormats(exec);

        return new FFmpegCapabilities(
                info.version(),
                hwaccels,
                encoders,
                decoders,
                filters,
                formats
        );
    }

    String parseVersionString(String line) {
        if (line == null || line.isBlank()) {
            return "Unknown";
        }
        Matcher matcher = VERSION_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Fallback: return the trimmed first line if it's not too long
        return line.length() > 30 ? line.substring(0, 30).trim() : line.trim();
    }

    private List<String> parseHwAccels(String ffmpegPath) {
        List<String> hwaccels = new ArrayList<>();
        try {
            ProcessExecutionResult result = processExecutor.execute(List.of(ffmpegPath, "-hwaccels"), TIMEOUT);
            if (result.isSuccess()) {
                boolean headerFound = false;
                for (String line : result.standardOutputLines()) {
                    String trimmed = line.trim();
                    if (trimmed.equalsIgnoreCase("Hardware acceleration methods:")) {
                        headerFound = true;
                        continue;
                    }
                    if (headerFound && !trimmed.isEmpty()) {
                        hwaccels.add(trimmed);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse hwaccels: {}", e.getMessage());
        }
        return hwaccels;
    }

    private List<CodecInfo> parseCodecs(String ffmpegPath, String flag, boolean isEncoder) {
        List<CodecInfo> codecs = new ArrayList<>();
        try {
            ProcessExecutionResult result = processExecutor.execute(List.of(ffmpegPath, flag), TIMEOUT);
            if (result.isSuccess()) {
                boolean separatorPassed = false;
                for (String line : result.standardOutputLines()) {
                    if (line.trim().startsWith("------")) {
                        separatorPassed = true;
                        continue;
                    }
                    if (!separatorPassed || line.length() < 8) {
                        continue;
                    }

                    // Format: " V..... libx264              H.264 / AVC..."
                    String flags = line.substring(0, Math.min(line.length(), 7)).trim();
                    if (flags.isEmpty()) continue;

                    CodecInfo.CodecType type = switch (flags.charAt(0)) {
                        case 'V' -> CodecInfo.CodecType.VIDEO;
                        case 'A' -> CodecInfo.CodecType.AUDIO;
                        case 'S' -> CodecInfo.CodecType.SUBTITLE;
                        default -> CodecInfo.CodecType.OTHER;
                    };

                    String rest = line.substring(Math.min(line.length(), 7)).trim();
                    int spaceIdx = rest.indexOf(' ');
                    String name;
                    String desc = "";
                    if (spaceIdx > 0) {
                        name = rest.substring(0, spaceIdx).trim();
                        desc = rest.substring(spaceIdx + 1).trim();
                    } else {
                        name = rest.trim();
                    }

                    if (!name.isEmpty()) {
                        codecs.add(new CodecInfo(name, desc, type, isEncoder, !isEncoder));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse codecs with flag {}: {}", flag, e.getMessage());
        }
        return codecs;
    }

    private List<String> parseFilters(String ffmpegPath) {
        List<String> filters = new ArrayList<>();
        try {
            ProcessExecutionResult result = processExecutor.execute(List.of(ffmpegPath, "-filters"), TIMEOUT);
            if (result.isSuccess()) {
                boolean separatorPassed = false;
                for (String line : result.standardOutputLines()) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("---") || trimmed.startsWith("------")) {
                        separatorPassed = true;
                        continue;
                    }
                    if (!separatorPassed) {
                        // Allow fallback if no explicit dash line was present but valid filter flag appears
                        if (trimmed.matches("^[T\\.\\|SACV\\s]{3,4}\\s+[a-zA-Z0-9_]+.*")) {
                            separatorPassed = true;
                        } else {
                            continue;
                        }
                    }
                    if (trimmed.length() < 4) {
                        continue;
                    }
                    String[] tokens = trimmed.split("\\s+");
                    if (tokens.length >= 2) {
                        filters.add(tokens[1]);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse filters: {}", e.getMessage());
        }
        return filters;
    }

    private List<String> parseFormats(String ffmpegPath) {
        List<String> formats = new ArrayList<>();
        try {
            ProcessExecutionResult result = processExecutor.execute(List.of(ffmpegPath, "-formats"), TIMEOUT);
            if (result.isSuccess()) {
                boolean separatorPassed = false;
                for (String line : result.standardOutputLines()) {
                    if (line.trim().startsWith("--")) {
                        separatorPassed = true;
                        continue;
                    }
                    if (!separatorPassed || line.length() < 4) {
                        continue;
                    }
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length >= 2) {
                        formats.add(tokens[1]);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse formats: {}", e.getMessage());
        }
        return formats;
    }
}
