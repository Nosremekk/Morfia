package com.ffmpegstudio.infrastructure.platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves external FFmpeg and FFprobe binaries across Linux, Windows, and macOS
 * using an explicit precedence order and rejecting untrusted temporary/current directories
 * during automatic discovery.
 */
public final class ExecutableResolver {

    public enum ResolutionSource {
        EXPLICIT_CONFIG,
        SYSTEM_PATH,
        CONVENTIONAL_DIR,
        PACKAGE_MANAGER_DIR
    }

    public record ResolvedExecutable(Path path, ResolutionSource source) {
    }

    private final PlatformInfo platform;
    private final String pathEnv;
    private final Path userHome;
    private final String localAppData;

    public ExecutableResolver(PlatformInfo platform, String pathEnv, Path userHome, String localAppData) {
        this.platform = platform;
        this.pathEnv = pathEnv;
        this.userHome = userHome;
        this.localAppData = localAppData;
    }

    public static ExecutableResolver current() {
        return new ExecutableResolver(
                PlatformInfo.detect(),
                System.getenv("PATH"),
                Paths.get(System.getProperty("user.home", ".")),
                System.getenv("LOCALAPPDATA")
        );
    }

    /**
     * Precedence:
     * 1. Explicit configured path (if non-blank and executable)
     * 2. System PATH (excluding untrusted temp/relative entries)
     * 3. Conventional OS directories
     * 4. Known package manager locations (Homebrew, Winget, Scoop, Chocolatey)
     */
    public Optional<ResolvedExecutable> resolve(String baseName, String configuredPath) {
        String exeName = platform.executableName(baseName);

        if (configuredPath != null && !configuredPath.isBlank() && !configuredPath.equals(baseName) && !configuredPath.equals(exeName)) {
            Path explicit = Paths.get(configuredPath);
            if (isValidExecutableFile(explicit)) {
                return Optional.of(new ResolvedExecutable(explicit.toAbsolutePath().normalize(), ResolutionSource.EXPLICIT_CONFIG));
            }
        }

        if (pathEnv != null && !pathEnv.isBlank()) {
            for (String entry : pathEnv.split(File.pathSeparator)) {
                if (entry == null || entry.isBlank()) continue;
                try {
                    Path dir = Paths.get(entry).toAbsolutePath().normalize();
                    if (isUntrustedAutoDiscoveryDir(dir)) continue;
                    Path candidate = dir.resolve(exeName);
                    if (isValidExecutableFile(candidate)) {
                        return Optional.of(new ResolvedExecutable(candidate.toAbsolutePath().normalize(), ResolutionSource.SYSTEM_PATH));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        for (Path dir : conventionalDirectories()) {
            Path candidate = dir.resolve(exeName);
            if (isValidExecutableFile(candidate)) {
                return Optional.of(new ResolvedExecutable(candidate.toAbsolutePath().normalize(), ResolutionSource.CONVENTIONAL_DIR));
            }
        }

        for (Path dir : packageManagerDirectories()) {
            Path candidate = dir.resolve(exeName);
            if (isValidExecutableFile(candidate)) {
                return Optional.of(new ResolvedExecutable(candidate.toAbsolutePath().normalize(), ResolutionSource.PACKAGE_MANAGER_DIR));
            }
        }

        return Optional.empty();
    }

    public List<Path> conventionalDirectories() {
        List<Path> dirs = new ArrayList<>();
        if (platform.isLinux()) {
            dirs.add(Paths.get("/usr/bin"));
            dirs.add(Paths.get("/usr/local/bin"));
            dirs.add(userHome.resolve(".local/bin"));
        } else if (platform.isMacOs()) {
            dirs.add(Paths.get("/opt/homebrew/bin"));
            dirs.add(Paths.get("/usr/local/bin"));
            dirs.add(Paths.get("/usr/bin"));
        } else if (platform.isWindows()) {
            dirs.add(Paths.get("C:\\ffmpeg\\bin"));
            dirs.add(Paths.get("C:\\Program Files\\ffmpeg\\bin"));
        }
        return dirs;
    }

    public List<Path> packageManagerDirectories() {
        List<Path> dirs = new ArrayList<>();
        if (platform.isLinux()) {
            dirs.add(Paths.get("/snap/bin"));
            dirs.add(Paths.get("/var/lib/flatpak/exports/bin"));
        } else if (platform.isMacOs()) {
            dirs.add(Paths.get("/opt/local/bin")); // MacPorts
        } else if (platform.isWindows()) {
            if (localAppData != null && !localAppData.isBlank()) {
                dirs.add(Paths.get(localAppData, "Microsoft", "WinGet", "Links"));
            }
            dirs.add(userHome.resolve("scoop").resolve("shims"));
            dirs.add(Paths.get("C:\\ProgramData\\chocolatey\\bin"));
        }
        return dirs;
    }

    static boolean isUntrustedAutoDiscoveryDir(Path dir) {
        String normalized = dir.toAbsolutePath().normalize().toString();
        String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");
        Path normalizedTmp = Paths.get(tmpDir).toAbsolutePath().normalize();
        if (dir.equals(normalizedTmp) || dir.startsWith(normalizedTmp)) {
            return true;
        }
        return normalized.equals("/tmp") || normalized.startsWith("/tmp/")
                || normalized.equals("/var/tmp") || normalized.startsWith("/var/tmp/");
    }

    private boolean isValidExecutableFile(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }
}
