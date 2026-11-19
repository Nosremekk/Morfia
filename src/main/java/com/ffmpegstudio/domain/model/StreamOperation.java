package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Stream management operation: remuxing, stream selection, reordering,
 * individual stream extraction, and per-stream title/language/default disposition editing.
 * Always uses explicit global FFprobe stream indices (-map 0:<globalIndex>).
 */
public record StreamOperation(
        List<StreamSelection> selections,
        ContainerFormat targetContainer,
        boolean extractSingleStream
) implements MediaOperation {

    public record StreamSelection(
            int globalStreamIndex,
            String codecName,
            String streamType, // "video", "audio", "subtitle", "data", "attachment"
            boolean included,
            String newTitle,
            String newLanguage,
            Boolean setDefault
    ) {
        public StreamSelection {
            codecName = codecName == null ? "" : codecName;
            streamType = streamType == null ? "video" : streamType;
        }
    }

    public StreamOperation {
        selections = selections == null ? List.of() : List.copyOf(selections);
        targetContainer = targetContainer == null ? ContainerFormat.MKV : targetContainer;
    }

    @Override
    public String name() {
        long count = selections.stream().filter(StreamSelection::included).count();
        return extractSingleStream
                ? "Extração de Stream Individual"
                : "Remuxing & Gerenciamento de Streams (" + count + " faixas)";
    }

    @Override
    public String description() {
        long count = selections.stream().filter(StreamSelection::included).count();
        return String.format("%d faixa(s) selecionada(s) para %s (Stream Copy)", count, targetContainer.name());
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        List<StreamSelection> active = selections.stream().filter(StreamSelection::included).toList();

        if (active.isEmpty()) {
            errors.add("Selecione pelo menos uma stream para o arquivo de saída.");
            return errors;
        }
        if (extractSingleStream && active.size() > 1) {
            errors.add("A extração individual requer exatamente 1 stream selecionada.");
        }

        if (mediaFile != null && mediaFile.metadata() != null) {
            Set<Integer> validIndices = mediaFile.metadata().streams().stream()
                    .map(MediaStream::index)
                    .collect(java.util.stream.Collectors.toSet());
            for (StreamSelection sel : active) {
                if (!validIndices.contains(sel.globalStreamIndex())) {
                    errors.add("Índice global de stream inexistente no arquivo: " + sel.globalStreamIndex());
                }
            }
        }

        if (!extractSingleStream && targetContainer == ContainerFormat.MP4) {
            for (StreamSelection sel : active) {
                String codec = sel.codecName().toLowerCase();
                if ("attachment".equalsIgnoreCase(sel.streamType())) {
                    errors.add("Contêiner MP4 não suporta streams de anexo (attachment #" + sel.globalStreamIndex() + "). Use MKV ou remova a faixa.");
                }
                if (codec.contains("pgs") || codec.contains("dvd_subtitle") || codec.equals("ass") || codec.equals("ssa")) {
                    errors.add("Contêiner MP4 não suporta legenda '" + sel.codecName() + "' (stream #" + sel.globalStreamIndex() + ") via stream copy. Use MKV.");
                }
            }
        }

        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.mkv");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        List<StreamSelection> active = selections.stream().filter(StreamSelection::included).toList();
        for (StreamSelection sel : active) {
            cmd.add("-map");
            cmd.add("0:" + sel.globalStreamIndex());
        }

        cmd.add("-c");
        cmd.add("copy");

        // Apply per-stream metadata & disposition using the 0-based output stream position
        for (int outPos = 0; outPos < active.size(); outPos++) {
            StreamSelection sel = active.get(outPos);
            if (sel.newTitle() != null && !sel.newTitle().isBlank()) {
                cmd.add("-metadata:s:" + outPos);
                cmd.add("title=" + sel.newTitle().trim());
            }
            if (sel.newLanguage() != null && !sel.newLanguage().isBlank()) {
                cmd.add("-metadata:s:" + outPos);
                cmd.add("language=" + sel.newLanguage().trim().toLowerCase());
            }
            if (sel.setDefault() != null) {
                cmd.add("-disposition:" + outPos);
                cmd.add(sel.setDefault() ? "default" : "0");
            }
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        String ext = targetContainer.extension();
        if (mediaFile == null || mediaFile.fileName() == null) {
            return defaultOutputDir.resolve("remuxed_output." + ext);
        }
        String fn = mediaFile.fileName();
        String base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        return defaultOutputDir.resolve(base + (extractSingleStream ? "_stream." : "_remux.") + ext);
    }
}
