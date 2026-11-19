package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DashboardViewModel {
    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final ManageSettingsUseCase settingsUseCase;

    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final StringProperty ffmpegVersionText = new SimpleStringProperty("Detecting...");
    private final StringProperty ffprobeVersionText = new SimpleStringProperty("Detecting...");
    private final IntegerProperty videoEncodersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty audioEncodersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty hwAccelsCount = new SimpleIntegerProperty(0);
    private final IntegerProperty filtersCount = new SimpleIntegerProperty(0);
    private final StringProperty hwAccelsSummary = new SimpleStringProperty("None");

    public DashboardViewModel(DetectCapabilitiesUseCase capabilitiesUseCase, ManageSettingsUseCase settingsUseCase) {
        this.capabilitiesUseCase = capabilitiesUseCase;
        this.settingsUseCase = settingsUseCase;
    }

    public void loadStats() {
        loading.set(true);
        AppSettings settings = settingsUseCase.getSettings();

        AsyncHelper.supplyAsync(
                () -> {
                    ExecutableInfo ffmpeg = capabilitiesUseCase.checkFFmpeg(settings.ffmpegPath());
                    ExecutableInfo ffprobe = capabilitiesUseCase.checkFFprobe(settings.ffprobePath());
                    FFmpegCapabilities caps = ffmpeg.isAvailable()
                            ? capabilitiesUseCase.getCapabilities(settings.ffmpegPath())
                            : FFmpegCapabilities.empty();
                    return new DashboardData(ffmpeg, ffprobe, caps);
                },
                data -> {
                    loading.set(false);
                    ready.set(data.ffmpeg.isAvailable());
                    if (data.ffmpeg.isAvailable()) {
                        ffmpegVersionText.set("FFmpeg " + data.ffmpeg.version());
                    } else {
                        ffmpegVersionText.set("FFmpeg Not Found");
                    }

                    if (data.ffprobe.isAvailable()) {
                        ffprobeVersionText.set("FFprobe " + data.ffprobe.version());
                    } else {
                        ffprobeVersionText.set("FFprobe Not Found");
                    }

                    videoEncodersCount.set((int) data.capabilities.videoEncodersCount());
                    audioEncodersCount.set((int) data.capabilities.audioEncodersCount());
                    hwAccelsCount.set(data.capabilities.hardwareAccelerators().size());
                    filtersCount.set(data.capabilities.filters().size());

                    if (data.capabilities.hardwareAccelerators().isEmpty()) {
                        hwAccelsSummary.set("None detected");
                    } else {
                        hwAccelsSummary.set(String.join(", ", data.capabilities.hardwareAccelerators()));
                    }
                },
                err -> {
                    loading.set(false);
                    ready.set(false);
                    ffmpegVersionText.set("Error during detection");
                }
        );
    }

    public record DashboardData(
            ExecutableInfo ffmpeg,
            ExecutableInfo ffprobe,
            FFmpegCapabilities capabilities
    ) {}

    public BooleanProperty loadingProperty() { return loading; }
    public BooleanProperty readyProperty() { return ready; }
    public StringProperty ffmpegVersionTextProperty() { return ffmpegVersionText; }
    public StringProperty ffprobeVersionTextProperty() { return ffprobeVersionText; }
    public IntegerProperty videoEncodersCountProperty() { return videoEncodersCount; }
    public IntegerProperty audioEncodersCountProperty() { return audioEncodersCount; }
    public IntegerProperty hwAccelsCountProperty() { return hwAccelsCount; }
    public IntegerProperty filtersCountProperty() { return filtersCount; }
    public StringProperty hwAccelsSummaryProperty() { return hwAccelsSummary; }
}
