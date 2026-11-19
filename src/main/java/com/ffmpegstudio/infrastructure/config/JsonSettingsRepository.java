package com.ffmpegstudio.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.port.SettingsRepositoryPort;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class JsonSettingsRepository implements SettingsRepositoryPort {
    private static final Logger logger = LoggerFactory.getLogger(JsonSettingsRepository.class);

    private final Path configFilePath;
    private final ObjectMapper objectMapper;
    private final FileSystemService fileSystemService;

    public JsonSettingsRepository(Path configFilePath, FileSystemService fileSystemService) {
        this.configFilePath = Objects.requireNonNull(configFilePath, "configFilePath must not be null");
        this.fileSystemService = Objects.requireNonNull(fileSystemService, "fileSystemService must not be null");
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static JsonSettingsRepository defaultLocation(FileSystemService fileSystemService) {
        Path dir = com.ffmpegstudio.infrastructure.platform.PlatformDirectories.current().configDir();
        Path file = dir.resolve("settings.json");
        return new JsonSettingsRepository(file, fileSystemService);
    }

    @Override
    public synchronized AppSettings loadSettings() {
        if (!Files.exists(configFilePath)) {
            logger.info("Settings file not found at {}. Using defaults.", configFilePath);
            AppSettings defaults = AppSettings.createDefault();
            saveSettings(defaults);
            return defaults;
        }

        try {
            AppSettings settings = objectMapper.readValue(configFilePath.toFile(), AppSettings.class);
            logger.debug("Successfully loaded settings from {}", configFilePath);
            return settings;
        } catch (IOException e) {
            logger.error("Failed to read settings from {}. Falling back to default settings.", configFilePath, e);
            return AppSettings.createDefault();
        }
    }

    @Override
    public synchronized void saveSettings(AppSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        try {
            Path parent = configFilePath.getParent();
            if (parent != null) {
                fileSystemService.ensureDirectoryExists(parent);
            }
            objectMapper.writeValue(configFilePath.toFile(), settings);
            logger.info("Settings saved successfully to {}", configFilePath);
        } catch (IOException e) {
            logger.error("Failed to write settings to {}", configFilePath, e);
        }
    }

    public Path getConfigFilePath() {
        return configFilePath;
    }
}
