package com.ffmpegstudio.domain.model;

/**
 * Supported subtitle formats, distinguishing text-based from image-based (bitmap) subtitles.
 */
public enum SubtitleFormat {
    SRT("srt", "srt", "SubRip (.srt)", true),
    ASS("ass", "ass", "Advanced SubStation Alpha (.ass)", true),
    SSA("ssa", "ssa", "SubStation Alpha (.ssa)", true),
    WEBVTT("vtt", "webvtt", "WebVTT (.vtt)", true),
    MOV_TEXT("mov_text", "mov_text", "MPEG-4 Timed Text (mov_text)", true),
    PGS("sup", "hdmv_pgs_subtitle", "Blu-ray PGS (Imagem)", false),
    DVD_SUBTITLE("sub", "dvd_subtitle", "DVD VobSub (Imagem)", false);

    private final String extension;
    private final String ffmpegCodec;
    private final String label;
    private final boolean textBased;

    SubtitleFormat(String extension, String ffmpegCodec, String label, boolean textBased) {
        this.extension = extension;
        this.ffmpegCodec = ffmpegCodec;
        this.label = label;
        this.textBased = textBased;
    }

    public String extension() { return extension; }
    public String ffmpegCodec() { return ffmpegCodec; }
    public String label() { return label; }
    public boolean isTextBased() { return textBased; }
    public boolean isImageBased() { return !textBased; }

    public static SubtitleFormat fromCodecOrExtension(String value) {
        if (value == null || value.isBlank()) return SRT;
        String norm = value.trim().toLowerCase();
        return switch (norm) {
            case "subrip", "srt" -> SRT;
            case "ass" -> ASS;
            case "ssa" -> SSA;
            case "webvtt", "vtt" -> WEBVTT;
            case "mov_text", "tx3g" -> MOV_TEXT;
            case "hdmv_pgs_subtitle", "pgssub", "sup" -> PGS;
            case "dvd_subtitle", "dvdsub", "vobsub", "sub" -> DVD_SUBTITLE;
            default -> SRT;
        };
    }

    public boolean isCompatibleWithContainer(ContainerFormat container) {
        if (container == null) return true;
        return switch (container) {
            case MP4 -> this == MOV_TEXT || this == SRT || this == WEBVTT; // FFmpeg converts SRT/VTT -> mov_text in MP4
            case MKV -> true; // MKV supports SRT, ASS, SSA, WebVTT, PGS, DVD_SUBTITLE
            default -> false;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
