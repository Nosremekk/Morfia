package com.ffmpegstudio.domain.model;

import java.util.List;

/**
 * Supported hardware acceleration backends in FFmpeg.
 */
public enum HardwareBackend {
    VAAPI("vaapi", "VA-API (Linux AMD/Intel)", List.of("h264_vaapi", "hevc_vaapi")),
    NVENC("cuda", "NVIDIA NVENC", List.of("h264_nvenc", "hevc_nvenc")),
    QSV("qsv", "Intel Quick Sync Video (QSV)", List.of("h264_qsv", "hevc_qsv")),
    AMF("amf", "AMD AMF", List.of("h264_amf", "hevc_amf")),
    VIDEOTOOLBOX("videotoolbox", "Apple VideoToolbox", List.of("h264_videotoolbox", "hevc_videotoolbox"));

    private final String hwaccelName;
    private final String label;
    private final List<String> candidateEncoders;

    HardwareBackend(String hwaccelName, String label, List<String> candidateEncoders) {
        this.hwaccelName = hwaccelName;
        this.label = label;
        this.candidateEncoders = candidateEncoders;
    }

    public String hwaccelName() { return hwaccelName; }
    public String label() { return label; }
    public List<String> candidateEncoders() { return candidateEncoders; }
}
