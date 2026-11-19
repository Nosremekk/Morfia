package com.ffmpegstudio.domain.model;

public enum VideoCodecOption {
    H264_LIBX264("libx264", "H.264 / AVC (libx264)", true, false),
    H265_LIBX265("libx265", "H.265 / HEVC (libx265)", true, false),
    H264_VAAPI("h264_vaapi", "H.264 VA-API (Hardware GPU)", true, true),
    H265_VAAPI("hevc_vaapi", "H.265 VA-API (Hardware GPU)", true, true),
    VP9_LIBVPX("libvpx-vp9", "VP9 (libvpx-vp9)", true, false),
    COPY("copy", "Stream Copy (No re-encoding)", false, false),
    NONE("none", "Disable Video (Audio only)", false, false);

    private final String encoderName;
    private final String label;
    private final boolean requiresEncoding;
    private final boolean hardwareAccelerated;

    VideoCodecOption(String encoderName, String label, boolean requiresEncoding, boolean hardwareAccelerated) {
        this.encoderName = encoderName;
        this.label = label;
        this.requiresEncoding = requiresEncoding;
        this.hardwareAccelerated = hardwareAccelerated;
    }

    public String encoderName() {
        return encoderName;
    }

    public String label() {
        return label;
    }

    public boolean requiresEncoding() {
        return requiresEncoding;
    }

    public boolean isHardwareAccelerated() {
        return hardwareAccelerated;
    }
}
