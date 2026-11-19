package com.ffmpegstudio.infrastructure.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ffmpegstudio.domain.model.ConversionPreset;
import com.ffmpegstudio.domain.port.PresetRepositoryPort;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JsonPresetRepository implements PresetRepositoryPort {
    private static final Logger logger = LoggerFactory.getLogger(JsonPresetRepository.class);

    private final Path presetsFilePath;
    private final ObjectMapper objectMapper;
    private final FileSystemService fileSystemService;

    public JsonPresetRepository(Path presetsFilePath, FileSystemService fileSystemService) {
        this.presetsFilePath = Objects.requireNonNull(presetsFilePath, "presetsFilePath must not be null");
        this.fileSystemService = Objects.requireNonNull(fileSystemService, "fileSystemService must not be null");
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static JsonPresetRepository defaultLocation(FileSystemService fileSystemService) {
        Path dir = com.ffmpegstudio.infrastructure.platform.PlatformDirectories.current().configDir();
        Path file = dir.resolve("presets.json");
        return new JsonPresetRepository(file, fileSystemService);
    }

    @Override
    public synchronized List<ConversionPreset> loadAllPresets() {
        Map<String, ConversionPreset> map = new LinkedHashMap<>();

        // 1. Built-in presets
        for (ConversionPreset builtIn : ConversionPreset.getBuiltInPresets()) {
            map.put(builtIn.id(), builtIn);
        }

        // 2. Custom user presets from JSON
        if (Files.exists(presetsFilePath)) {
            try {
                List<ConversionPreset> customs = objectMapper.readValue(
                        presetsFilePath.toFile(),
                        new TypeReference<List<ConversionPreset>>() {}
                );
                for (ConversionPreset p : customs) {
                    if (p != null && !p.isBuiltIn()) {
                        map.put(p.id(), p);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to load custom presets from {}: {}", presetsFilePath, e.getMessage());
            }
        }

        return new ArrayList<>(map.values());
    }

    @Override
    public synchronized void saveCustomPreset(ConversionPreset preset) {
        Objects.requireNonNull(preset, "preset must not be null");
        if (preset.isBuiltIn()) {
            throw new IllegalArgumentException("Cannot modify or overwrite a built-in preset.");
        }

        List<ConversionPreset> existingCustoms = loadOnlyCustomPresets();
        existingCustoms.removeIf(p -> p.id().equals(preset.id()));
        existingCustoms.add(preset);

        persistCustomPresets(existingCustoms);
        logger.info("Saved custom preset '{}' ({})", preset.name(), preset.id());
    }

    @Override
    public synchronized void deleteCustomPreset(String presetId) {
        Objects.requireNonNull(presetId, "presetId must not be null");
        if (presetId.startsWith("builtin-")) {
            throw new IllegalArgumentException("Cannot delete built-in presets.");
        }

        List<ConversionPreset> existingCustoms = loadOnlyCustomPresets();
        boolean removed = existingCustoms.removeIf(p -> p.id().equals(presetId));
        if (removed) {
            persistCustomPresets(existingCustoms);
            logger.info("Deleted custom preset {}", presetId);
        }
    }

    @Override
    public synchronized Optional<ConversionPreset> findById(String id) {
        if (id == null) return Optional.empty();
        return loadAllPresets().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public void exportPresets(Path targetFile, List<ConversionPreset> presets) {
        Objects.requireNonNull(targetFile, "targetFile must not be null");
        Objects.requireNonNull(presets, "presets must not be null");
        try {
            if (targetFile.getParent() != null) {
                fileSystemService.ensureDirectoryExists(targetFile.getParent());
            }
            objectMapper.writeValue(targetFile.toFile(), presets);
            logger.info("Exported {} presets to {}", presets.size(), targetFile);
        } catch (IOException e) {
            logger.error("Failed to export presets to {}: {}", targetFile, e.getMessage(), e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ConversionPreset> importPresets(Path sourceFile) {
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        try {
            List<ConversionPreset> imported = objectMapper.readValue(
                    sourceFile.toFile(),
                    new TypeReference<List<ConversionPreset>>() {}
            );
            List<ConversionPreset> toSave = new ArrayList<>();
            for (ConversionPreset p : imported) {
                if (p != null) {
                    // Mark imported presets as custom (not built-in) with fresh ID if needed
                    ConversionPreset custom = new ConversionPreset(
                            p.id().startsWith("builtin-") ? null : p.id(),
                            p.name(),
                            p.description(),
                            p.category(),
                            p.settings(),
                            false
                    );
                    saveCustomPreset(custom);
                    toSave.add(custom);
                }
            }
            logger.info("Successfully imported {} presets from {}", toSave.size(), sourceFile);
            return toSave;
        } catch (IOException e) {
            logger.error("Failed to import presets from {}: {}", sourceFile, e.getMessage(), e);
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

    private List<ConversionPreset> loadOnlyCustomPresets() {
        if (!Files.exists(presetsFilePath)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    presetsFilePath.toFile(),
                    new TypeReference<List<ConversionPreset>>() {}
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void persistCustomPresets(List<ConversionPreset> customs) {
        try {
            if (presetsFilePath.getParent() != null) {
                fileSystemService.ensureDirectoryExists(presetsFilePath.getParent());
            }
            objectMapper.writeValue(presetsFilePath.toFile(), customs);
        } catch (IOException e) {
            logger.error("Failed to persist custom presets to {}: {}", presetsFilePath, e.getMessage(), e);
        }
    }
}
