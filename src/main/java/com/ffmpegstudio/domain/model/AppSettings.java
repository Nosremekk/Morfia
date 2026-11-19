package com.ffmpegstudio.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Global persistent application settings with backward-compatible schema migration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettings(
        int schemaVersion,
        String ffmpegPath,
        String ffprobePath,
        String defaultOutputDir,
        String tempDir,
        String theme,
        String language,
        int maxConcurrentJobs,
        int cacheLimitMb,
        int logRetentionDays,
        boolean confirmationsEnabled,
        boolean autoStartQueue,
        String defaultPresetId,
        boolean preferHardwareAcceleration
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final String DEFAULT_THEME = "DARK";
    public static final String DEFAULT_LANGUAGE = "pt_BR";
    public static final int DEFAULT_MAX_CONCURRENCY = 2;
    public static final int DEFAULT_CACHE_LIMIT_MB = 256;
    public static final int DEFAULT_LOG_RETENTION_DAYS = 14;

    public AppSettings {
        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
            confirmationsEnabled = true;
        }
        ffmpegPath = (ffmpegPath == null || ffmpegPath.isBlank()) ? "ffmpeg" : ffmpegPath.trim();
        ffprobePath = (ffprobePath == null || ffprobePath.isBlank()) ? "ffprobe" : ffprobePath.trim();
        defaultOutputDir = (defaultOutputDir == null || defaultOutputDir.isBlank())
                ? defaultUserVideosDir()
                : defaultOutputDir.trim();
        tempDir = (tempDir == null || tempDir.isBlank())
                ? System.getProperty("java.io.tmpdir", "/tmp")
                : tempDir.trim();
        theme = (theme == null || theme.isBlank()) ? DEFAULT_THEME : theme.trim();
        language = (language == null || language.isBlank()) ? DEFAULT_LANGUAGE : language.trim();
        if (maxConcurrentJobs <= 0) maxConcurrentJobs = DEFAULT_MAX_CONCURRENCY;
        if (cacheLimitMb <= 0) cacheLimitMb = DEFAULT_CACHE_LIMIT_MB;
        if (logRetentionDays <= 0) logRetentionDays = DEFAULT_LOG_RETENTION_DAYS;
        if (defaultPresetId == null || defaultPresetId.isBlank()) defaultPresetId = "builtin-mp4-balanced";
    }

    /**
     * Backward-compatible 5-argument constructor.
     */
    public AppSettings(String ffmpegPath, String ffprobePath, String defaultOutputDir, String theme, int maxConcurrentJobs) {
        this(CURRENT_SCHEMA_VERSION, ffmpegPath, ffprobePath, defaultOutputDir,
                System.getProperty("java.io.tmpdir", "/tmp"), theme, DEFAULT_LANGUAGE,
                maxConcurrentJobs, DEFAULT_CACHE_LIMIT_MB, DEFAULT_LOG_RETENTION_DAYS,
                true, false, "builtin-mp4-balanced", false);
    }

    public static AppSettings createDefault() {
        return new AppSettings("ffmpeg", "ffprobe", defaultUserVideosDir(), DEFAULT_THEME, DEFAULT_MAX_CONCURRENCY);
    }

    private static String defaultUserVideosDir() {
        String userHome = System.getProperty("user.home", ".");
        Path videos = Paths.get(userHome, "Videos");
        return videos.toString();
    }

    public AppSettings withFfmpegPath(String newPath) {
        return new AppSettings(CURRENT_SCHEMA_VERSION, newPath, ffprobePath, defaultOutputDir, tempDir, theme, language,
                maxConcurrentJobs, cacheLimitMb, logRetentionDays, confirmationsEnabled, autoStartQueue, defaultPresetId, preferHardwareAcceleration);
    }

    public AppSettings withFfprobePath(String newPath) {
        return new AppSettings(CURRENT_SCHEMA_VERSION, ffmpegPath, newPath, defaultOutputDir, tempDir, theme, language,
                maxConcurrentJobs, cacheLimitMb, logRetentionDays, confirmationsEnabled, autoStartQueue, defaultPresetId, preferHardwareAcceleration);
    }

    public AppSettings withDefaultOutputDir(String newDir) {
        return new AppSettings(CURRENT_SCHEMA_VERSION, ffmpegPath, ffprobePath, newDir, tempDir, theme, language,
                maxConcurrentJobs, cacheLimitMb, logRetentionDays, confirmationsEnabled, autoStartQueue, defaultPresetId, preferHardwareAcceleration);
    }

    public AppSettings withMaxConcurrentJobs(int maxJobs) {
        return new AppSettings(CURRENT_SCHEMA_VERSION, ffmpegPath, ffprobePath, defaultOutputDir, tempDir, theme, language,
                maxJobs, cacheLimitMb, logRetentionDays, confirmationsEnabled, autoStartQueue, defaultPresetId, preferHardwareAcceleration);
    }
}
