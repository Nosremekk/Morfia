package com.ffmpegstudio.domain.port;

import com.ffmpegstudio.domain.model.ConversionPreset;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface PresetRepositoryPort {
    List<ConversionPreset> loadAllPresets();
    void saveCustomPreset(ConversionPreset preset);
    void deleteCustomPreset(String presetId);
    Optional<ConversionPreset> findById(String id);
    void exportPresets(Path targetFile, List<ConversionPreset> presets);
    List<ConversionPreset> importPresets(Path sourceFile);
}
