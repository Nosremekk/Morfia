package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation for inspecting, editing, and selectively or globally removing
 * container-level and stream-level metadata using stream copy (no re-encoding).
 */
public record MetadataOperation(
        Map<String, String> updatedGlobalTags,
        List<String> globalKeysToRemove,
        boolean clearAllGlobalMetadata,
        boolean preserveChapters,
        Map<Integer, Map<String, String>> streamTagUpdates,
        ContainerFormat targetContainer
) implements MediaOperation {

    public static final List<String> COMMON_TAG_KEYS = List.of(
            "title", "artist", "album", "date", "genre", "comment", "description", "copyright", "language"
    );

    public MetadataOperation {
        updatedGlobalTags = updatedGlobalTags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(updatedGlobalTags));
        globalKeysToRemove = globalKeysToRemove == null ? List.of() : List.copyOf(globalKeysToRemove);
        streamTagUpdates = streamTagUpdates == null ? Map.of() : Collections.unmodifiableMap(streamTagUpdates);
        targetContainer = targetContainer == null ? ContainerFormat.MP4 : targetContainer;
    }

    @Override
    public String name() {
        return clearAllGlobalMetadata
                ? "Limpeza de Metadados Globais"
                : "Edição de Metadados (" + updatedGlobalTags.size() + " tags)";
    }

    @Override
    public String description() {
        return "Atualizar/remover tags de contêiner via Stream Copy (sem recodificação)";
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (!clearAllGlobalMetadata && updatedGlobalTags.isEmpty() && globalKeysToRemove.isEmpty() && streamTagUpdates.isEmpty()) {
            errors.add("Nenhuma alteração de metadados foi especificada.");
        }
        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.mp4");
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

        cmd.add("-map");
        cmd.add("0");
        cmd.add("-c");
        cmd.add("copy");

        if (clearAllGlobalMetadata) {
            cmd.add("-map_metadata");
            cmd.add("-1");
        }

        if (!preserveChapters) {
            cmd.add("-map_chapters");
            cmd.add("-1");
        }

        // Remove selected global keys (if not already clearing all)
        if (!clearAllGlobalMetadata) {
            for (String key : globalKeysToRemove) {
                if (key != null && !key.isBlank()) {
                    cmd.add("-metadata");
                    cmd.add(key.trim() + "=");
                }
            }
        }

        // Write updated/preserved global tags
        for (Map.Entry<String, String> entry : updatedGlobalTags.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                cmd.add("-metadata");
                cmd.add(entry.getKey().trim() + "=" + entry.getValue());
            }
        }

        // Stream-level metadata updates
        for (Map.Entry<Integer, Map<String, String>> streamEntry : streamTagUpdates.entrySet()) {
            int streamIdx = streamEntry.getKey();
            for (Map.Entry<String, String> tag : streamEntry.getValue().entrySet()) {
                if (tag.getKey() != null && !tag.getKey().isBlank() && tag.getValue() != null) {
                    cmd.add("-metadata:s:" + streamIdx);
                    cmd.add(tag.getKey().trim() + "=" + tag.getValue());
                }
            }
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        String ext = targetContainer.extension();
        if (mediaFile == null || mediaFile.fileName() == null) {
            return defaultOutputDir.resolve("metadata_updated." + ext);
        }
        String fn = mediaFile.fileName();
        String base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        return defaultOutputDir.resolve(base + "_meta." + ext);
    }
}
