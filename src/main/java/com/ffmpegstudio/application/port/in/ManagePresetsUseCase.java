package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.ConversionPreset;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ManagePresetsUseCase {
    List<ConversionPreset> getAllPresets();
    List<ConversionPreset> getPresetsByCategory(String category);
    void savePreset(ConversionPreset preset);
    void deletePreset(String presetId);
    Optional<ConversionPreset> getPresetById(String id);
    void exportPresets(Path targetFile, List<ConversionPreset> presets);
    List<ConversionPreset> importPresets(Path sourceFile);
}
