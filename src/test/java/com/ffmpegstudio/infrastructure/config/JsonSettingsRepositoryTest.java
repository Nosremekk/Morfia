package com.ffmpegstudio.infrastructure.config;

import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSettingsRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should create default settings file when file does not exist")
    void shouldCreateDefaultSettingsFile() {
        Path configFile = tempDir.resolve("sub").resolve("settings.json");
        FileSystemService fs = new FileSystemService();
        JsonSettingsRepository repository = new JsonSettingsRepository(configFile, fs);

        AppSettings settings = repository.loadSettings();

        assertThat(settings).isNotNull();
        assertThat(settings.ffmpegPath()).isEqualTo("ffmpeg");
        assertThat(settings.ffprobePath()).isEqualTo("ffprobe");
        assertThat(settings.maxConcurrentJobs()).isEqualTo(2);
        assertThat(Files.exists(configFile)).isTrue();
    }

    @Test
    @DisplayName("Should persist and reload modified settings")
    void shouldPersistAndReloadSettings() {
        Path configFile = tempDir.resolve("custom-settings.json");
        FileSystemService fs = new FileSystemService();
        JsonSettingsRepository repository = new JsonSettingsRepository(configFile, fs);

        AppSettings custom = new AppSettings(
                "/opt/custom/ffmpeg",
                "/opt/custom/ffprobe",
                "/tmp/output",
                "DARK",
                4
        );

        repository.saveSettings(custom);
        AppSettings loaded = repository.loadSettings();

        assertThat(loaded.ffmpegPath()).isEqualTo("/opt/custom/ffmpeg");
        assertThat(loaded.ffprobePath()).isEqualTo("/opt/custom/ffprobe");
        assertThat(loaded.defaultOutputDir()).isEqualTo("/tmp/output");
        assertThat(loaded.maxConcurrentJobs()).isEqualTo(4);
    }
}
