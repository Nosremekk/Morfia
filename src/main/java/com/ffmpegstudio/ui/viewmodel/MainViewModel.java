package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class MainViewModel {
    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final ManageSettingsUseCase settingsUseCase;

    private final StringProperty currentView = new SimpleStringProperty("DASHBOARD");
    private final BooleanProperty systemReady = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("Checking FFmpeg...");
    private final StringProperty ffmpegVersion = new SimpleStringProperty("");

    public MainViewModel(DetectCapabilitiesUseCase capabilitiesUseCase, ManageSettingsUseCase settingsUseCase) {
        this.capabilitiesUseCase = capabilitiesUseCase;
        this.settingsUseCase = settingsUseCase;
    }

    public void refreshStatus() {
        AppSettings settings = settingsUseCase.getSettings();
        statusMessage.set("Checking executables...");

        AsyncHelper.supplyAsync(
                () -> {
                    ExecutableInfo ffmpeg = capabilitiesUseCase.checkFFmpeg(settings.ffmpegPath());
                    ExecutableInfo ffprobe = capabilitiesUseCase.checkFFprobe(settings.ffprobePath());
                    return new ExecutablesCheck(ffmpeg, ffprobe);
                },
                result -> {
                    boolean ready = result.ffmpeg().isAvailable();
                    systemReady.set(ready);
                    if (ready) {
                        ffmpegVersion.set("v" + result.ffmpeg().version());
                        if (result.ffprobe().isAvailable()) {
                            statusMessage.set("FFmpeg " + result.ffmpeg().version() + " & FFprobe ready");
                        } else {
                            statusMessage.set("FFmpeg " + result.ffmpeg().version() + " ready (FFprobe missing)");
                        }
                    } else {
                        ffmpegVersion.set("");
                        statusMessage.set("FFmpeg not detected. Please check Settings.");
                    }
                },
                error -> {
                    systemReady.set(false);
                    statusMessage.set("Error checking executables: " + error.getMessage());
                }
        );
    }

    public record ExecutablesCheck(ExecutableInfo ffmpeg, ExecutableInfo ffprobe) {}

    public StringProperty currentViewProperty() { return currentView; }
    public BooleanProperty systemReadyProperty() { return systemReady; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty ffmpegVersionProperty() { return ffmpegVersion; }
}
