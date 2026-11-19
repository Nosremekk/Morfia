package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManagePresetsUseCase;
import com.ffmpegstudio.domain.model.ConversionPreset;
import com.ffmpegstudio.domain.port.PresetRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PresetService implements ManagePresetsUseCase {
    private static final Logger logger = LoggerFactory.getLogger(PresetService.class);

    private final PresetRepositoryPort presetRepository;

    public PresetService(PresetRepositoryPort presetRepository) {
        this.presetRepository = Objects.requireNonNull(presetRepository, "presetRepository must not be null");
    }

    @Override
    public List<ConversionPreset> getAllPresets() {
        return presetRepository.loadAllPresets();
    }

    @Override
    public List<ConversionPreset> getPresetsByCategory(String category) {
        return getAllPresets().stream()
                .filter(p -> p.category().equalsIgnoreCase(category))
                .toList();
    }

    @Override
    public void savePreset(ConversionPreset preset) {
        presetRepository.saveCustomPreset(preset);
    }

    @Override
    public void deletePreset(String presetId) {
        presetRepository.deleteCustomPreset(presetId);
    }

    @Override
    public Optional<ConversionPreset> getPresetById(String id) {
        return presetRepository.findById(id);
    }

    @Override
    public void exportPresets(Path targetFile, List<ConversionPreset> presets) {
        presetRepository.exportPresets(targetFile, presets);
    }

    @Override
    public List<ConversionPreset> importPresets(Path sourceFile) {
        return presetRepository.importPresets(sourceFile);
    }
}
