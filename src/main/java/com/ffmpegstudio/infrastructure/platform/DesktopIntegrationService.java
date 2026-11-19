package com.ffmpegstudio.infrastructure.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Safe cross-platform desktop integration (opening folders/files) without shell invocation.
 */
public final class DesktopIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(DesktopIntegrationService.class);

    private DesktopIntegrationService() {
    }

    public static boolean openDirectory(Path directory) {
        if (directory == null) {
            return false;
        }
        Path target = Files.isDirectory(directory) ? directory : directory.getParent();
        if (target == null || !Files.exists(target)) {
            return false;
        }

        PlatformInfo platform = PlatformInfo.detect();
        try {
            if (platform.isLinux()) {
                ProcessBuilder pb = new ProcessBuilder(List.of("xdg-open", target.toAbsolutePath().toString()));
                pb.start();
                return true;
            }
            if (platform.isMacOs()) {
                ProcessBuilder pb = new ProcessBuilder(List.of("open", target.toAbsolutePath().toString()));
                pb.start();
                return true;
            }
            if (platform.isWindows()) {
                ProcessBuilder pb = new ProcessBuilder(List.of("explorer.exe", target.toAbsolutePath().toString()));
                pb.start();
                return true;
            }
        } catch (Exception e) {
            logger.debug("OS native open command failed for {}, trying AWT Desktop fallback", target, e);
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(target.toFile());
                return true;
            }
        } catch (Exception e) {
            logger.warn("Unable to open directory {}", target, e);
        }
        return false;
    }
}
