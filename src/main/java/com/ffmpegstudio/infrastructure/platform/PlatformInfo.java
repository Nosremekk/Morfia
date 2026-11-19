package com.ffmpegstudio.infrastructure.platform;

import java.util.Locale;

/**
 * Operating system and CPU architecture detection for cross-platform adaptation.
 */
public final class PlatformInfo {

    public enum OsFamily {
        LINUX,
        WINDOWS,
        MACOS,
        OTHER
    }

    private final OsFamily osFamily;
    private final String osName;
    private final String osVersion;
    private final String architecture;

    public PlatformInfo(OsFamily osFamily, String osName, String osVersion, String architecture) {
        this.osFamily = osFamily;
        this.osName = osName == null ? "Unknown" : osName;
        this.osVersion = osVersion == null ? "" : osVersion;
        this.architecture = normalizeArch(architecture);
    }

    public static PlatformInfo detect() {
        String name = System.getProperty("os.name", "Unknown");
        String version = System.getProperty("os.version", "");
        String arch = System.getProperty("os.arch", "unknown");
        return new PlatformInfo(classifyOs(name), name, version, arch);
    }

    public static OsFamily classifyOs(String osName) {
        if (osName == null) {
            return OsFamily.OTHER;
        }
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("win")) {
            return OsFamily.WINDOWS;
        }
        if (lower.contains("mac") || lower.contains("darwin")) {
            return OsFamily.MACOS;
        }
        if (lower.contains("nux") || lower.contains("nix") || lower.contains("aix")) {
            return OsFamily.LINUX;
        }
        return OsFamily.OTHER;
    }

    private static String normalizeArch(String arch) {
        if (arch == null || arch.isBlank()) {
            return "unknown";
        }
        String lower = arch.toLowerCase(Locale.ROOT);
        if ("amd64".equals(lower) || "x86_64".equals(lower) || "x64".equals(lower)) {
            return "x86_64";
        }
        if ("aarch64".equals(lower) || "arm64".equals(lower)) {
            return "arm64";
        }
        return lower;
    }

    public OsFamily osFamily() {
        return osFamily;
    }

    public String osName() {
        return osName;
    }

    public String osVersion() {
        return osVersion;
    }

    public String architecture() {
        return architecture;
    }

    public boolean isLinux() {
        return osFamily == OsFamily.LINUX;
    }

    public boolean isWindows() {
        return osFamily == OsFamily.WINDOWS;
    }

    public boolean isMacOs() {
        return osFamily == OsFamily.MACOS;
    }

    public String executableName(String baseName) {
        return isWindows() ? baseName + ".exe" : baseName;
    }

    public String summary() {
        return osName + " " + osVersion + " (" + architecture + ")";
    }
}
