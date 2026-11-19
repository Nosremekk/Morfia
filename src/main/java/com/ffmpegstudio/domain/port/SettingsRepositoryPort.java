package com.ffmpegstudio.domain.port;

import com.ffmpegstudio.domain.model.AppSettings;

/**
 * Port for persisting and retrieving user application settings.
 */
public interface SettingsRepositoryPort {
    AppSettings loadSettings();
    void saveSettings(AppSettings settings);
}
