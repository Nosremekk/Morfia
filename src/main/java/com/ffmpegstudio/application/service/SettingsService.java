package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.port.SettingsRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SettingsService implements ManageSettingsUseCase {
    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    private final SettingsRepositoryPort settingsRepository;
    private AppSettings cachedSettings;

    public SettingsService(SettingsRepositoryPort settingsRepository) {
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository must not be null");
        this.cachedSettings = settingsRepository.loadSettings();
    }

    @Override
    public synchronized AppSettings getSettings() {
        if (cachedSettings == null) {
            cachedSettings = settingsRepository.loadSettings();
        }
        return cachedSettings;
    }

    @Override
    public synchronized void updateSettings(AppSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        logger.info("Updating application settings: {}", settings);
        settingsRepository.saveSettings(settings);
        this.cachedSettings = settings;
    }

    @Override
    public synchronized AppSettings resetToDefaults() {
        logger.info("Resetting application settings to defaults");
        AppSettings defaults = AppSettings.createDefault();
        settingsRepository.saveSettings(defaults);
        this.cachedSettings = defaults;
        return defaults;
    }
}
