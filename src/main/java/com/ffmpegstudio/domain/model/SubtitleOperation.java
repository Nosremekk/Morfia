package com.ffmpegstudio.domain.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Subtitle management operation supporting extraction, soft embedding,
 * burn-in (hardcoding), removal, and metadata/disposition updates.
 */
public record SubtitleOperation(
        SubtitleAction action,
        Integer subtitleStreamIndex, // 0-based relative subtitle stream index
        Path externalSubtitlePath,
        SubtitleFormat targetFormat,
        ContainerFormat targetContainer,
        String language,
        String title,
        boolean isDefault,
        boolean isForced,
        double timeOffsetSeconds,
        ConversionSettings burnInSettings
) implements MediaOperation {

    public enum SubtitleAction {
        EXTRACT("Extrair Faixa de Legenda"),
        EMBED_SOFT("Incorporar Legenda Externa (Selecionável)"),
        BURN_IN("Queimar Legenda no Vídeo (Hardcode)"),
        REMOVE("Remover Faixa de Legenda"),
        UPDATE_METADATA("Alterar Metadados e Flags da Legenda");

        private final String label;
        SubtitleAction(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public SubtitleOperation {
        action = action == null ? SubtitleAction.EXTRACT : action;
        targetFormat = targetFormat == null ? SubtitleFormat.SRT : targetFormat;
        targetContainer = targetContainer == null ? ContainerFormat.MKV : targetContainer;
        language = (language == null || language.isBlank()) ? "und" : language.trim().toLowerCase();
        title = title == null ? "" : title.trim();
        if (action == SubtitleAction.BURN_IN && burnInSettings == null) {
            burnInSettings = ConversionSettings.defaultMp4();
        }
    }

    /**
     * Escapes a file path for FFmpeg's filtergraph syntax (e.g. subtitles='...').
     * Backslashes, colons, and single quotes are escaped for libavfilter parsing.
     */
    public static String escapeFilterPath(Path path) {
        String abs = path.toAbsolutePath().toString();
        String escaped = abs
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("[", "\\[")
                .replace("]", "\\]");
        return "'" + escaped + "'";
    }

    @Override
    public String name() {
        return action.label();
    }

    @Override
    public String description() {
        return switch (action) {
            case EXTRACT -> "Extrair faixa #" + (subtitleStreamIndex != null ? subtitleStreamIndex : 0) + " para " + targetFormat.extension().toUpperCase();
            case EMBED_SOFT -> "Incorporar " + (externalSubtitlePath != null ? externalSubtitlePath.getFileName() : "legenda") + " (" + language + ")";
            case BURN_IN -> "Queimar legenda no vídeo (" + (externalSubtitlePath != null ? externalSubtitlePath.getFileName() : "faixa interna") + ")";
            case REMOVE -> subtitleStreamIndex != null ? "Remover faixa de legenda #" + subtitleStreamIndex : "Remover todas as legendas";
            case UPDATE_METADATA -> "Atualizar idioma/título/flags da legenda #" + (subtitleStreamIndex != null ? subtitleStreamIndex : 0);
        };
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        List<SubtitleStream> existingSubs = (mediaFile != null && mediaFile.metadata() != null)
                ? mediaFile.metadata().subtitleStreams()
                : List.of();

        switch (action) {
            case EXTRACT -> {
                if (existingSubs.isEmpty()) {
                    errors.add("O arquivo de mídia não possui faixas de legenda.");
                    break;
                }
                int idx = subtitleStreamIndex != null ? subtitleStreamIndex : 0;
                if (idx < 0 || idx >= existingSubs.size()) {
                    errors.add("Índice de legenda inválido: " + idx);
                    break;
                }
                SubtitleFormat sourceFmt = SubtitleFormat.fromCodecOrExtension(existingSubs.get(idx).codecName());
                if (sourceFmt.isImageBased() && targetFormat.isTextBased()) {
                    errors.add("Conversão direta de legenda gráfica (" + sourceFmt.label() + ") para formato textual (" + targetFormat.label() + ") não é suportada sem OCR.");
                }
                if (sourceFmt.isTextBased() && targetFormat.isImageBased()) {
                    errors.add("Conversão de legenda textual para formato gráfico não é suportada.");
                }
            }
            case EMBED_SOFT -> {
                if (externalSubtitlePath == null || !Files.exists(externalSubtitlePath)) {
                    errors.add("Arquivo de legenda externo não encontrado.");
                } else {
                    SubtitleFormat extFmt = inferFormatFromPath(externalSubtitlePath);
                    if (targetContainer == ContainerFormat.MP4 && extFmt.isImageBased()) {
                        errors.add("Contêiner MP4 não suporta legendas gráficas (" + extFmt.label() + "). Use MKV.");
                    }
                    if (targetContainer == ContainerFormat.MP4 && (extFmt == SubtitleFormat.ASS || extFmt == SubtitleFormat.SSA)) {
                        errors.add("Legendas ASS/SSA perdem formatação avançada em MP4 (mov_text). Prefira MKV ou queima (Burn-in).");
                    }
                }
            }
            case BURN_IN -> {
                boolean usingExternal = externalSubtitlePath != null;
                if (usingExternal) {
                    if (!Files.exists(externalSubtitlePath)) {
                        errors.add("Arquivo de legenda para queima não encontrado.");
                    } else {
                        SubtitleFormat extFmt = inferFormatFromPath(externalSubtitlePath);
                        if (extFmt.isImageBased()) {
                            errors.add("Queima via filtro 'subtitles'/'ass' suporta apenas legendas textuais (SRT, ASS, VTT).");
                        }
                    }
                } else {
                    if (existingSubs.isEmpty()) {
                        errors.add("Nenhuma faixa de legenda interna disponível para queima.");
                    } else {
                        int idx = subtitleStreamIndex != null ? subtitleStreamIndex : 0;
                        if (idx < 0 || idx >= existingSubs.size()) {
                            errors.add("Índice de legenda inválido: " + idx);
                        } else {
                            SubtitleFormat srcFmt = SubtitleFormat.fromCodecOrExtension(existingSubs.get(idx).codecName());
                            if (srcFmt.isImageBased()) {
                                errors.add("Queima textual não suporta faixa gráfica (" + srcFmt.label() + ").");
                            }
                        }
                    }
                }
                if (capabilities != null) {
                    boolean isAss = usingExternal && (externalSubtitlePath.toString().toLowerCase().endsWith(".ass")
                            || externalSubtitlePath.toString().toLowerCase().endsWith(".ssa"));
                    String requiredFilter = isAss ? "ass" : "subtitles";
                    if (!capabilities.hasFilter(requiredFilter) && !capabilities.hasFilter("subtitles")) {
                        errors.add("Filtro '" + requiredFilter + "' (libass) não disponível no FFmpeg local.");
                    }
                }
            }
            case REMOVE, UPDATE_METADATA -> {
                if (existingSubs.isEmpty()) {
                    errors.add("O arquivo não possui faixas de legenda para modificar.");
                } else if (subtitleStreamIndex != null && (subtitleStreamIndex < 0 || subtitleStreamIndex >= existingSubs.size())) {
                    errors.add("Índice de legenda inválido: " + subtitleStreamIndex);
                }
            }
        }
        return errors;
    }

    private static SubtitleFormat inferFormatFromPath(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".ass")) return SubtitleFormat.ASS;
        if (name.endsWith(".ssa")) return SubtitleFormat.SSA;
        if (name.endsWith(".vtt")) return SubtitleFormat.WEBVTT;
        if (name.endsWith(".sup")) return SubtitleFormat.PGS;
        if (name.endsWith(".sub") || name.endsWith(".idx")) return SubtitleFormat.DVD_SUBTITLE;
        return SubtitleFormat.SRT;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.mkv");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");

        switch (action) {
            case EXTRACT -> buildExtractCommand(cmd, input, outputPath, enableProgressPipe);
            case EMBED_SOFT -> buildEmbedSoftCommand(cmd, input, outputPath, enableProgressPipe);
            case BURN_IN -> buildBurnInCommand(cmd, input, outputPath, enableProgressPipe);
            case REMOVE -> buildRemoveCommand(cmd, input, outputPath, enableProgressPipe);
            case UPDATE_METADATA -> buildUpdateMetadataCommand(cmd, input, outputPath, enableProgressPipe);
        }

        return Collections.unmodifiableList(cmd);
    }

    private void buildExtractCommand(List<String> cmd, Path input, Path outputPath, boolean enableProgressPipe) {
        if (Math.abs(timeOffsetSeconds) > 0.001) {
            cmd.add("-itsoffset");
            cmd.add(String.format(Locale.US, "%.3f", timeOffsetSeconds));
        }
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }
        int idx = subtitleStreamIndex != null ? subtitleStreamIndex : 0;
        cmd.add("-map");
        cmd.add("0:s:" + idx);
        cmd.add("-c:s");
        cmd.add(targetFormat.ffmpegCodec());
        cmd.add(outputPath.toAbsolutePath().toString());
    }

    private void buildEmbedSoftCommand(List<String> cmd, Path input, Path outputPath, boolean enableProgressPipe) {
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        if (Math.abs(timeOffsetSeconds) > 0.001) {
            cmd.add("-itsoffset");
            cmd.add(String.format(Locale.US, "%.3f", timeOffsetSeconds));
        }
        cmd.add("-i");
        cmd.add(externalSubtitlePath.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        cmd.add("-map");
        cmd.add("0");
        cmd.add("-map");
        cmd.add("1:0");
        cmd.add("-c:v");
        cmd.add("copy");
        cmd.add("-c:a");
        cmd.add("copy");

        // Copy existing subtitle streams and encode/copy the new one
        cmd.add("-c:s");
        cmd.add(targetContainer == ContainerFormat.MP4 ? "mov_text" : "copy");

        // Metadata on newly added subtitle stream (index = existingCount, or use -metadata:s:s:-1? FFmpeg supports -metadata:s:s:...)
        cmd.add("-metadata:s:s:0");
        cmd.add("language=" + language);
        if (!title.isBlank()) {
            cmd.add("-metadata:s:s:0");
            cmd.add("title=" + title);
        }
        String disp = buildDispositionString();
        if (!disp.isBlank()) {
            cmd.add("-disposition:s:0");
            cmd.add(disp);
        }

        cmd.add(outputPath.toAbsolutePath().toString());
    }

    private void buildBurnInCommand(List<String> cmd, Path input, Path outputPath, boolean enableProgressPipe) {
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        Path subSource = externalSubtitlePath != null ? externalSubtitlePath : input;
        String filterExpr;
        if (externalSubtitlePath != null) {
            boolean isAss = subSource.toString().toLowerCase().endsWith(".ass")
                    || subSource.toString().toLowerCase().endsWith(".ssa");
            filterExpr = (isAss ? "ass=" : "subtitles=") + escapeFilterPath(subSource);
        } else {
            int idx = subtitleStreamIndex != null ? subtitleStreamIndex : 0;
            filterExpr = "subtitles=" + escapeFilterPath(input) + ":si=" + idx;
        }

        cmd.add("-vf");
        cmd.add(filterExpr);
        cmd.add("-c:v");
        cmd.add(burnInSettings.videoCodec().encoderName());
        cmd.add("-preset");
        cmd.add(burnInSettings.speedPreset().value());
        cmd.add("-crf");
        cmd.add(String.valueOf(burnInSettings.crf() != null ? burnInSettings.crf() : 23));
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-c:a");
        cmd.add("copy");
        cmd.add(outputPath.toAbsolutePath().toString());
    }

    private void buildRemoveCommand(List<String> cmd, Path input, Path outputPath, boolean enableProgressPipe) {
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }
        cmd.add("-map");
        cmd.add("0");
        if (subtitleStreamIndex != null) {
            cmd.add("-map");
            cmd.add("-0:s:" + subtitleStreamIndex);
        } else {
            cmd.add("-sn");
        }
        cmd.add("-c");
        cmd.add("copy");
        cmd.add(outputPath.toAbsolutePath().toString());
    }

    private void buildUpdateMetadataCommand(List<String> cmd, Path input, Path outputPath, boolean enableProgressPipe) {
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }
        int idx = subtitleStreamIndex != null ? subtitleStreamIndex : 0;
        cmd.add("-map");
        cmd.add("0");
        cmd.add("-c");
        cmd.add("copy");
        cmd.add("-metadata:s:s:" + idx);
        cmd.add("language=" + language);
        if (!title.isBlank()) {
            cmd.add("-metadata:s:s:" + idx);
            cmd.add("title=" + title);
        }
        String disp = buildDispositionString();
        cmd.add("-disposition:s:" + idx);
        cmd.add(disp.isBlank() ? "0" : disp);
        cmd.add(outputPath.toAbsolutePath().toString());
    }

    private String buildDispositionString() {
        List<String> flags = new ArrayList<>();
        if (isDefault) flags.add("default");
        if (isForced) flags.add("forced");
        return String.join("+", flags);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        String base = "output";
        if (mediaFile != null && mediaFile.fileName() != null) {
            String fn = mediaFile.fileName();
            base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        }
        if (action == SubtitleAction.EXTRACT) {
            return defaultOutputDir.resolve(base + "_sub." + targetFormat.extension());
        }
        String ext = targetContainer.extension();
        return defaultOutputDir.resolve(base + "_subtitled." + ext);
    }
}
