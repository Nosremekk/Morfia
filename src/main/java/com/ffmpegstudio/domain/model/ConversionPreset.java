package com.ffmpegstudio.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable preset representing a pre-configured multimedia conversion recipe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversionPreset(
        String id,
        String name,
        String description,
        String category,
        ConversionSettings settings,
        boolean isBuiltIn
) {

    public ConversionPreset {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        Objects.requireNonNull(name, "name must not be null");
        description = description == null ? "" : description;
        category = category == null || category.isBlank() ? "Custom" : category;
        settings = settings == null ? ConversionSettings.defaultMp4() : settings;
    }

    public static List<ConversionPreset> getBuiltInPresets() {
        return List.of(
                new ConversionPreset(
                        "builtin-mp4-balanced",
                        "MP4 H.264 Equilibrado",
                        "Equilíbrio ideal entre compatibilidade universal, tamanho de arquivo e qualidade visual (CRF 23).",
                        "Geral",
                        new ConversionSettings(
                                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                                PresetSpeed.MEDIUM, 23, null, 192, ResolutionPreset.ORIGINAL,
                                null, null, null, true, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-mp4-hq",
                        "MP4 H.264 Alta Qualidade",
                        "Qualidade visual superior para arquivamento e reprodução local (CRF 18, preset slow).",
                        "Geral",
                        new ConversionSettings(
                                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                                PresetSpeed.SLOW, 18, null, 256, ResolutionPreset.ORIGINAL,
                                null, null, null, true, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-h265-compact",
                        "H.265 Compacto (HEVC)",
                        "Máxima compressão moderna com codec H.265 para redução substancial de espaço.",
                        "Compressão",
                        new ConversionSettings(
                                ContainerFormat.MKV, VideoCodecOption.H265_LIBX265, AudioCodecOption.AAC,
                                PresetSpeed.MEDIUM, 28, null, 128, ResolutionPreset.ORIGINAL,
                                null, null, null, true, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-compress-720p",
                        "Compressão 720p HD",
                        "Redimensiona para 1280x720 com taxa de quadros e bitrate otimizados para compartilhamento rápido.",
                        "Compressão",
                        new ConversionSettings(
                                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                                PresetSpeed.FAST, 25, null, 128, ResolutionPreset.P720,
                                null, null, null, true, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-compress-1080p",
                        "Compressão 1080p Full HD",
                        "Adequa vídeos de resolução superior (4K) para Full HD 1080p preservando fluidez.",
                        "Compressão",
                        new ConversionSettings(
                                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                                PresetSpeed.MEDIUM, 23, null, 160, ResolutionPreset.P1080,
                                null, null, null, true, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-extract-mp3",
                        "Extração MP3",
                        "Extrai a faixa sonora e converte para MP3 em 192 kbps estéreo.",
                        "Áudio",
                        new ConversionSettings(
                                ContainerFormat.MP3, VideoCodecOption.NONE, AudioCodecOption.MP3_LIBMP3LAME,
                                PresetSpeed.MEDIUM, null, null, 192, ResolutionPreset.ORIGINAL,
                                null, null, null, false, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-extract-flac",
                        "Extração FLAC (Lossless)",
                        "Extrai o áudio sem perdas preservando a fidelidade acústica integral da gravação.",
                        "Áudio",
                        new ConversionSettings(
                                ContainerFormat.FLAC, VideoCodecOption.NONE, AudioCodecOption.FLAC,
                                PresetSpeed.MEDIUM, null, null, 0, ResolutionPreset.ORIGINAL,
                                null, null, null, false, null, null, null
                        ),
                        true
                ),
                new ConversionPreset(
                        "builtin-gif-optimized",
                        "GIF Otimizado",
                        "Converte pequenos vídeos para GIF animado em 480p.",
                        "Web & Animação",
                        new ConversionSettings(
                                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.NONE,
                                PresetSpeed.FAST, 24, null, 0, ResolutionPreset.P480,
                                null, null, 15.0, false, null, null, null
                        ),
                        true
                )
        );
    }
}
