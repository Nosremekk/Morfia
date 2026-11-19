package com.ffmpegstudio.domain.model.filter;

import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.VideoStream;
import com.ffmpegstudio.domain.model.filter.AudioFilters.AudioSpeed;
import com.ffmpegstudio.domain.model.filter.AudioFilters.Volume;
import com.ffmpegstudio.domain.model.filter.MediaFilter.StreamKind;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Crop;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Pad;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Resize;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Reverse;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Rotate;
import com.ffmpegstudio.domain.model.filter.VideoFilters.RotationAngle;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Speed;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates ordered filter chains and composes unified -vf / -af filtergraphs.
 */
public final class FilterGraphBuilder {

    public FilterValidationResult validate(List<MediaFilter> filters, MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (filters == null || filters.isEmpty()) {
            errors.add("Adicione pelo menos um filtro à cadeia de processamento.");
            return new FilterValidationResult(false, errors, warnings, false, false, null, null, null);
        }

        boolean hasVideoStream = mediaFile == null || mediaFile.metadata() == null || !mediaFile.metadata().videoStreams().isEmpty();
        boolean hasAudioStream = mediaFile == null || mediaFile.metadata() == null || !mediaFile.metadata().audioStreams().isEmpty();

        Integer curW = null;
        Integer curH = null;
        Duration curDur = null;

        if (mediaFile != null && mediaFile.metadata() != null) {
            Optional<VideoStream> vs = mediaFile.metadata().primaryVideoStream();
            if (vs.isPresent()) {
                curW = vs.get().width();
                curH = vs.get().height();
            }
            curDur = mediaFile.metadata().duration();
        }

        boolean needsVideoReencode = false;
        boolean needsAudioReencode = false;

        for (MediaFilter f : filters) {
            if (f.streamKind() == StreamKind.VIDEO) {
                if (!hasVideoStream) {
                    errors.add("Filtro de vídeo '" + f.displayName() + "' não pode ser aplicado: mídia não possui stream de vídeo.");
                }
                needsVideoReencode = true;
            } else {
                if (!hasAudioStream) {
                    errors.add("Filtro de áudio '" + f.displayName() + "' não pode ser aplicado: mídia não possui stream de áudio.");
                }
                needsAudioReencode = true;
            }

            if (capabilities != null && !capabilities.filters().isEmpty() && !capabilities.hasFilter(f.requiredFFmpegFilter())) {
                errors.add("Filtro FFmpeg '" + f.requiredFFmpegFilter() + "' não disponível na instalação local.");
            }

            // Parameter & geometry tracking
            switch (f) {
                case Resize r -> {
                    if (r.width() <= 0 && r.height() <= 0) {
                        errors.add("Resize requer largura ou altura positiva.");
                    } else if (curW != null && curH != null && curW > 0 && curH > 0) {
                        if (r.keepAspectRatio()) {
                            if (r.width() > 0) {
                                int newH = (int) Math.round((double) curH * r.width() / curW);
                                curW = r.width();
                                curH = (newH % 2 == 0) ? newH : newH + 1;
                            } else {
                                int newW = (int) Math.round((double) curW * r.height() / curH);
                                curH = r.height();
                                curW = (newW % 2 == 0) ? newW : newW + 1;
                            }
                        } else {
                            curW = r.width();
                            curH = r.height();
                        }
                    }
                }
                case Crop c -> {
                    if (c.width() <= 0 || c.height() <= 0 || c.x() < 0 || c.y() < 0) {
                        errors.add("Crop possui dimensões ou deslocamentos inválidos.");
                    } else if (curW != null && curH != null && (c.x() + c.width() > curW || c.y() + c.height() > curH)) {
                        errors.add("Crop (" + c.width() + "x" + c.height() + " em " + c.x() + "," + c.y()
                                + ") excede a dimensão atual do vídeo (" + curW + "x" + curH + ").");
                    } else {
                        curW = c.width();
                        curH = c.height();
                    }
                }
                case Pad p -> {
                    if (p.width() <= 0 || p.height() <= 0) {
                        errors.add("Pad requer dimensões positivas.");
                    } else if (curW != null && curH != null && (p.width() < curW || p.height() < curH)) {
                        errors.add("Pad (" + p.width() + "x" + p.height() + ") deve ser maior ou igual à dimensão atual (" + curW + "x" + curH + ").");
                    } else {
                        curW = p.width();
                        curH = p.height();
                    }
                }
                case Rotate rot -> {
                    if (rot.angle() != RotationAngle.ROTATE_180 && curW != null && curH != null) {
                        int tmp = curW;
                        curW = curH;
                        curH = tmp;
                    }
                }
                case Speed sp -> {
                    if (sp.multiplier() <= 0.1 || sp.multiplier() > 10.0) {
                        errors.add("Velocidade de vídeo deve estar entre 0.1x e 10.0x.");
                    } else if (curDur != null && !curDur.isZero()) {
                        curDur = Duration.ofMillis((long) (curDur.toMillis() / sp.multiplier()));
                    }
                }
                case Reverse rev -> {
                    int w = (curW != null && curW > 0) ? curW : 1280;
                    int h = (curH != null && curH > 0) ? curH : 720;
                    double fps = 30.0;
                    if (mediaFile != null && mediaFile.metadata() != null && mediaFile.metadata().primaryVideoStream().isPresent()) {
                        double streamFps = mediaFile.metadata().primaryVideoStream().get().frameRate();
                        if (streamFps > 0 && streamFps <= 240) fps = streamFps;
                    }
                    double durSec = (curDur != null && !curDur.isZero()) ? curDur.toMillis() / 1000.0 : 10.0;
                    // Conservative uncompressed frame buffer estimate (~3 bytes/pixel for RGB/internal buffers)
                    double estimatedMb = (w * (double) h * 3.0 * fps * durSec) / (1024.0 * 1024.0);
                    if (estimatedMb > 4096.0 || durSec > 120.0) {
                        errors.add(String.format(java.util.Locale.US,
                                "Limite de segurança excedido para 'reverse': duração %.1fs em %dx%d @ %.1ffps consumiria ~%.0f MB (%.2f GB) de RAM descomprimida. Recorte o clipe para um trecho menor.",
                                durSec, w, h, fps, estimatedMb, estimatedMb / 1024.0));
                    } else {
                        warnings.add(String.format(java.util.Locale.US,
                                "Aviso de memória: O filtro 'reverse' carrega todos os quadros descomprimidos em RAM (estimativa conservadora: ~%.0f MB para %.1fs em %dx%d).",
                                estimatedMb, durSec, w, h));
                    }
                }
                case AudioSpeed asp -> {
                    if (asp.multiplier() < 0.25 || asp.multiplier() > 4.0) {
                        errors.add("Velocidade de áudio deve estar entre 0.25x e 4.0x.");
                    }
                }
                case Volume vol -> {
                    if (vol.multiplier() < 0.0 || vol.multiplier() > 10.0) {
                        errors.add("Multiplicador de volume inválido.");
                    }
                }
                default -> {
                    // Other filters are valid by record construction
                }
            }
        }

        return new FilterValidationResult(
                errors.isEmpty(),
                errors,
                warnings,
                needsVideoReencode,
                needsAudioReencode,
                curW,
                curH,
                curDur
        );
    }

    /**
     * Composes a single unified -vf string from all video filters in order.
     */
    public String buildVideoFilterChain(List<MediaFilter> filters) {
        if (filters == null) return "";
        List<String> exprs = new ArrayList<>();
        for (MediaFilter f : filters) {
            if (f.streamKind() == StreamKind.VIDEO) {
                exprs.add(f.toFilterExpression());
            }
        }
        return String.join(",", exprs);
    }

    /**
     * Composes a single unified -af string from all audio filters in order.
     */
    public String buildAudioFilterChain(List<MediaFilter> filters) {
        if (filters == null) return "";
        List<String> exprs = new ArrayList<>();
        for (MediaFilter f : filters) {
            if (f.streamKind() == StreamKind.AUDIO) {
                exprs.add(f.toFilterExpression());
            }
        }
        return String.join(",", exprs);
    }
}
