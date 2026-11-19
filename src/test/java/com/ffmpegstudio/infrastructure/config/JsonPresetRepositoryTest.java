package com.ffmpegstudio.infrastructure.config;

import com.ffmpegstudio.domain.model.ConversionPreset;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonPresetRepository Unit Tests")
class JsonPresetRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should return built-in presets when no presets file exists")
    void shouldReturnBuiltInPresetsByDefault() {
        Path presetFile = tempDir.resolve("presets.json");
        JsonPresetRepository repo = new JsonPresetRepository(presetFile, new FileSystemService());

        List<ConversionPreset> presets = repo.loadAllPresets();
        assertFalse(presets.isEmpty());
        assertTrue(presets.stream().anyMatch(ConversionPreset::isBuiltIn));
    }

    @Test
    @DisplayName("Should save custom preset and reject modifying built-in presets")
    void shouldSaveCustomPresetAndProtectBuiltIn() {
        Path presetFile = tempDir.resolve("presets.json");
        JsonPresetRepository repo = new JsonPresetRepository(presetFile, new FileSystemService());

        // Protect built-in
        ConversionPreset builtIn = repo.loadAllPresets().stream().filter(ConversionPreset::isBuiltIn).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> repo.deleteCustomPreset(builtIn.id()));

        // Save custom
        ConversionPreset custom = new ConversionPreset(
                "custom-1",
                "Meu Preset 4K",
                "Configuração 4K personalizada",
                "Custom",
                ConversionSettings.defaultMp4(),
                false
        );
        repo.saveCustomPreset(custom);

        // Reload repo from disk
        JsonPresetRepository reloaded = new JsonPresetRepository(presetFile, new FileSystemService());
        assertTrue(reloaded.findById("custom-1").isPresent());
        assertEquals("Meu Preset 4K", reloaded.findById("custom-1").get().name());

        // Delete custom
        reloaded.deleteCustomPreset("custom-1");
        assertFalse(reloaded.findById("custom-1").isPresent());
    }

    @Test
    @DisplayName("Should export and import presets JSON")
    void shouldExportAndImportPresets() {
        Path presetFile = tempDir.resolve("presets.json");
        Path exportFile = tempDir.resolve("exported_presets.json");
        JsonPresetRepository repo = new JsonPresetRepository(presetFile, new FileSystemService());

        ConversionPreset custom = new ConversionPreset(
                "export-test",
                "Export Preset",
                "Desc",
                "Custom",
                ConversionSettings.defaultMp4(),
                false
        );
        repo.saveCustomPreset(custom);

        repo.exportPresets(exportFile, repo.loadAllPresets());
        assertTrue(java.nio.file.Files.exists(exportFile));

        Path importTarget = tempDir.resolve("import_repo.json");
        JsonPresetRepository targetRepo = new JsonPresetRepository(importTarget, new FileSystemService());
        List<ConversionPreset> imported = targetRepo.importPresets(exportFile);

        assertFalse(imported.isEmpty());
        assertTrue(targetRepo.findById("export-test").isPresent());
    }
}
