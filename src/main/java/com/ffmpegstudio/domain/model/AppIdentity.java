package com.ffmpegstudio.domain.model;

/**
 * Centralized product identity and distribution metadata for Morfia.
 * <p>
 * Keeps product naming, versioning, license, copyright holder, and config directory
 * identifiers in a single authoritative location.
 */
public final class AppIdentity {

    public static final String DISPLAY_NAME = "Morfia";
    public static final String PUBLIC_NAME = "Morfia";
    public static final String SUBTITLE = "Multimedia Processing Studio";
    public static final String TECHNICAL_NAME = "morfia";

    public static final String GROUP_ID = "io.github.nosremekk";
    public static final String ARTIFACT_ID = "morfia";
    public static final String APPLICATION_ID = "io.github.nosremekk.morfia";

    public static final String VERSION = "1.0.0-rc.1";
    /**
     * Numeric version mapped for native packagers (jpackage, RPM, DEB, MSI, EXE, DMG)
     * that reject SemVer pre-release suffixes.
     */
    public static final String NATIVE_VERSION = "1.0.0";
    public static final String LINUX_PKG_RELEASE = "0.1.rc1";
    public static final String WINDOWS_MSI_VERSION = "0.99.1";
    public static final String MACOS_PKG_VERSION = "0.99.1";

    public static String toWindowsMsiVersion(String semVer) {
        if (semVer == null || semVer.isBlank()) return "0.0.0";
        int dash = semVer.indexOf('-');
        if (dash < 0) return semVer;
        String base = semVer.substring(0, dash);
        String pre = semVer.substring(dash + 1);
        if ("1.0.0".equals(base) && pre.startsWith("rc.")) {
            String num = pre.substring(3).replaceAll("[^0-9]", "");
            return "0.99." + (num.isEmpty() ? "1" : num);
        }
        return base;
    }

    public static String toRpmRelease(String semVer) {
        if (semVer == null || !semVer.contains("-")) return "1";
        String pre = semVer.substring(semVer.indexOf('-') + 1).replace("-", ".");
        String num = pre.replaceAll("[^0-9]", "");
        return "0." + (num.isEmpty() ? "1" : num) + "." + pre;
    }

    public static final String DESCRIPTION_PT =
            "Uma estação de trabalho multimídia de código aberto para conversão, edição, inspeção e processamento de arquivos locais, utilizando FFmpeg.";
    public static final String DESCRIPTION_EN =
            "An open-source multimedia workstation for converting, editing, inspecting and processing local media files using FFmpeg.";

    public static final String LICENSE_SPDX = "MIT";
    public static final String COPYRIGHT_YEAR = "2026";
    public static final String COPYRIGHT_HOLDER = "Emerson Vinicius Santos de Santana";
    public static final String OFFICIAL_URL = "https://github.com/Nosremekk/Morfia";

    public static final String LEGACY_CONFIG_DIR_NAME = "ffmpeg-studio";
    public static final String CONFIG_DIR_NAME = "morfia";

    private AppIdentity() {
    }

    public static String windowTitle() {
        return DISPLAY_NAME + " — " + SUBTITLE;
    }

    public static String diagnosticsHeader() {
        return "=== " + DISPLAY_NAME + " (" + VERSION + ") — Local Diagnostics Report ===";
    }

    public static String diagnosticsFilename() {
        return TECHNICAL_NAME + "-diagnostics.txt";
    }
}
