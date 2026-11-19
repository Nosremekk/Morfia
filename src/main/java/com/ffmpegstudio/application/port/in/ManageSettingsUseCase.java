package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.AppSettings;

public interface ManageSettingsUseCase {
    AppSettings getSettings();
    void updateSettings(AppSettings settings);
    AppSettings resetToDefaults();
}
