package com.ffmpegstudio.infrastructure.platform;

import com.ffmpegstudio.domain.model.AppIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Resolves OS-appropriate configuration, cache, and log directories while preserving
 * existing legacy configuration in {@code ~/.config/ffmpeg-studio}.
 */
public final class PlatformDirectories {

    private static final Logger logger = LoggerFactory.getLogger(PlatformDirectories.class);

    private final PlatformInfo platform;
    private final Path userHome;
    private final Map<String, String> env;

    public PlatformDirectories(PlatformInfo platform, Path userHome, Map<String, String> env) {
        this.platform = platform;
        this.userHome = userHome;
        this.env = env == null ? Map.of() : env;
    }

    public static PlatformDirectories current() {
        return new PlatformDirectories(
                PlatformInfo.detect(),
                Paths.get(System.getProperty("user.home", ".")),
                System.getenv()
        );
    }

    public Path configDir() {
        Path legacy = userHome.resolve(".config").resolve(AppIdentity.LEGACY_CONFIG_DIR_NAME);
        Path target = resolveConfigBase().resolve(AppIdentity.CONFIG_DIR_NAME);
        migrateLegacyDirectoryIfNeeded(legacy, target);
        return target;
    }

    public Path cacheDir() {
        if (platform.isWindows()) {
            String localAppData = env.get("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank())
                    ? Paths.get(localAppData)
                    : userHome.resolve("AppData").resolve("Local");
            return base.resolve(AppIdentity.CONFIG_DIR_NAME).resolve("cache");
        }
        if (platform.isMacOs()) {
            return userHome.resolve("Library").resolve("Caches").resolve(AppIdentity.CONFIG_DIR_NAME);
        }
        String xdgCache = env.get("XDG_CACHE_HOME");
        Path base = (xdgCache != null && !xdgCache.isBlank())
                ? Paths.get(xdgCache)
                : userHome.resolve(".cache");
        return base.resolve(AppIdentity.CONFIG_DIR_NAME);
    }

    public Path logsDir() {
        if (platform.isMacOs()) {
            return userHome.resolve("Library").resolve("Logs").resolve(AppIdentity.CONFIG_DIR_NAME);
        }
        return configDir().resolve("logs");
    }

    public Path defaultVideosDir() {
        return userHome.resolve("Videos");
    }

    private Path resolveConfigBase() {
        if (platform.isWindows()) {
            String appData = env.get("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData);
            }
            return userHome.resolve("AppData").resolve("Roaming");
        }
        if (platform.isMacOs()) {
            return userHome.resolve("Library").resolve("Application Support");
        }
        String xdgConfig = env.get("XDG_CONFIG_HOME");
        if (xdgConfig != null && !xdgConfig.isBlank()) {
            return Paths.get(xdgConfig);
        }
        return userHome.resolve(".config");
    }

    /**
     * Non-destructive, idempotent migration: copies missing files from the legacy
     * directory into the target directory without overwriting newer target files
     * and never deleting the legacy directory.
     */
    static void migrateLegacyDirectoryIfNeeded(Path legacyDir, Path targetDir) {
        if (legacyDir.equals(targetDir) || !Files.isDirectory(legacyDir)) {
            return;
        }
        try {
            Files.createDirectories(targetDir);
            try (Stream<Path> entries = Files.list(legacyDir)) {
                entries.filter(Files::isRegularFile).forEach(src -> {
                    Path dest = targetDir.resolve(src.getFileName());
                    if (Files.exists(dest)) {
                        return;
                    }
                    try {
                        Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        logger.info("Migrated legacy file {} -> {} (original preserved).", src.getFileName(), targetDir);
                    } catch (IOException e) {
                        logger.warn("Could not copy legacy config file {} to {}", src, dest, e);
                    }
                });
            }
        } catch (IOException e) {
            logger.warn("Non-destructive legacy config migration failed from {} to {}", legacyDir, targetDir, e);
        }
    }
}
