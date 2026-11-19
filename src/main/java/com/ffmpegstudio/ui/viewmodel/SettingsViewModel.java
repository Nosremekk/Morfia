package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.CodecInfo;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class SettingsViewModel {
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final FileSystemService fileSystemService;

    // Inputs
    private final StringProperty ffmpegPath = new SimpleStringProperty("");
    private final StringProperty ffprobePath = new SimpleStringProperty("");
    private final StringProperty defaultOutputDir = new SimpleStringProperty("");
    private final StringProperty tempDir = new SimpleStringProperty("");
    private final StringProperty theme = new SimpleStringProperty(AppSettings.DEFAULT_THEME);
    private final StringProperty language = new SimpleStringProperty(AppSettings.DEFAULT_LANGUAGE);
    private final IntegerProperty maxConcurrentJobs = new SimpleIntegerProperty(2);
    private final IntegerProperty cacheLimitMb = new SimpleIntegerProperty(256);
    private final IntegerProperty logRetentionDays = new SimpleIntegerProperty(14);
    private final BooleanProperty confirmationsEnabled = new SimpleBooleanProperty(true);

    // Live validation states
    private final BooleanProperty testing = new SimpleBooleanProperty(false);
    private final BooleanProperty ffmpegValid = new SimpleBooleanProperty(false);
    private final BooleanProperty ffprobeValid = new SimpleBooleanProperty(false);
    private final StringProperty ffmpegStatusMessage = new SimpleStringProperty("");
    private final StringProperty ffprobeStatusMessage = new SimpleStringProperty("");
    private final StringProperty saveStatusMessage = new SimpleStringProperty("");

    // Inspected capabilities
    private final ObservableList<String> hwAccels = FXCollections.observableArrayList();
    private final ObservableList<String> videoEncoders = FXCollections.observableArrayList();
    private final ObservableList<String> audioEncoders = FXCollections.observableArrayList();
    private final ObservableList<String> filters = FXCollections.observableArrayList();

    public SettingsViewModel(ManageSettingsUseCase settingsUseCase,
                             DetectCapabilitiesUseCase capabilitiesUseCase,
                             FileSystemService fileSystemService) {
        this.settingsUseCase = settingsUseCase;
        this.capabilitiesUseCase = capabilitiesUseCase;
        this.fileSystemService = fileSystemService;
    }

    public void init() {
        AppSettings current = settingsUseCase.getSettings();
        ffmpegPath.set(current.ffmpegPath());
        ffprobePath.set(current.ffprobePath());
        defaultOutputDir.set(current.defaultOutputDir());
        tempDir.set(current.tempDir());
        theme.set(current.theme());
        language.set(current.language());
        maxConcurrentJobs.set(current.maxConcurrentJobs());
        cacheLimitMb.set(current.cacheLimitMb());
        logRetentionDays.set(current.logRetentionDays());
        confirmationsEnabled.set(current.confirmationsEnabled());

        testExecutables();
    }

    public void autoDetectPaths() {
        fileSystemService.findInPath("ffmpeg").ifPresent(p -> ffmpegPath.set(p.toString()));
        fileSystemService.findInPath("ffprobe").ifPresent(p -> ffprobePath.set(p.toString()));
        testExecutables();
    }

    public void testExecutables() {
        testing.set(true);
        saveStatusMessage.set("");
        String curFfmpeg = ffmpegPath.get();
        String curFfprobe = ffprobePath.get();

        AsyncHelper.supplyAsync(
                () -> {
                    ExecutableInfo ffmpegInfo = capabilitiesUseCase.checkFFmpeg(curFfmpeg);
                    ExecutableInfo ffprobeInfo = capabilitiesUseCase.checkFFprobe(curFfprobe);
                    FFmpegCapabilities caps = ffmpegInfo.isAvailable()
                            ? capabilitiesUseCase.getCapabilities(curFfmpeg)
                            : FFmpegCapabilities.empty();
                    return new TestResult(ffmpegInfo, ffprobeInfo, caps);
                },
                result -> {
                    testing.set(false);
                    ffmpegValid.set(result.ffmpegInfo.isAvailable());
                    ffprobeValid.set(result.ffprobeInfo.isAvailable());

                    if (result.ffmpegInfo.isAvailable()) {
                        ffmpegStatusMessage.set("Ready: FFmpeg " + result.ffmpegInfo.version());
                    } else {
                        ffmpegStatusMessage.set("Not found or invalid (" + result.ffmpegInfo.details() + ")");
                    }

                    if (result.ffprobeInfo.isAvailable()) {
                        ffprobeStatusMessage.set("Ready: FFprobe " + result.ffprobeInfo.version());
                    } else {
                        ffprobeStatusMessage.set("Not found or invalid (" + result.ffprobeInfo.details() + ")");
                    }

                    // Update capabilities
                    hwAccels.setAll(result.capabilities.hardwareAccelerators());
                    videoEncoders.setAll(result.capabilities.encoders().stream()
                            .filter(c -> c.type() == CodecInfo.CodecType.VIDEO)
                            .map(c -> c.name() + " (" + c.description() + ")")
                            .toList());
                    audioEncoders.setAll(result.capabilities.encoders().stream()
                            .filter(c -> c.type() == CodecInfo.CodecType.AUDIO)
                            .map(c -> c.name() + " (" + c.description() + ")")
                            .toList());
                    filters.setAll(result.capabilities.filters());
                },
                err -> {
                    testing.set(false);
                    ffmpegValid.set(false);
                    ffprobeValid.set(false);
                    ffmpegStatusMessage.set("Error: " + err.getMessage());
                }
        );
    }

    public void save() {
        AppSettings current = settingsUseCase.getSettings();
        AppSettings updated = new AppSettings(
                AppSettings.CURRENT_SCHEMA_VERSION,
                ffmpegPath.get(),
                ffprobePath.get(),
                defaultOutputDir.get(),
                tempDir.get(),
                theme.get(),
                language.get(),
                maxConcurrentJobs.get(),
                cacheLimitMb.get(),
                logRetentionDays.get(),
                confirmationsEnabled.get(),
                current.autoStartQueue(),
                current.defaultPresetId(),
                current.preferHardwareAcceleration()
        );
        settingsUseCase.updateSettings(updated);
        com.ffmpegstudio.ui.util.I18n.setLocale(updated.language());
        saveStatusMessage.set("Settings saved successfully.");
    }

    public void reset() {
        AppSettings defaults = settingsUseCase.resetToDefaults();
        ffmpegPath.set(defaults.ffmpegPath());
        ffprobePath.set(defaults.ffprobePath());
        defaultOutputDir.set(defaults.defaultOutputDir());
        tempDir.set(defaults.tempDir());
        theme.set(defaults.theme());
        language.set(defaults.language());
        maxConcurrentJobs.set(defaults.maxConcurrentJobs());
        cacheLimitMb.set(defaults.cacheLimitMb());
        logRetentionDays.set(defaults.logRetentionDays());
        confirmationsEnabled.set(defaults.confirmationsEnabled());
        com.ffmpegstudio.ui.util.I18n.setLocale(defaults.language());
        saveStatusMessage.set("Settings reset to defaults.");
        testExecutables();
    }

    public record TestResult(
            ExecutableInfo ffmpegInfo,
            ExecutableInfo ffprobeInfo,
            FFmpegCapabilities capabilities
    ) {}

    // Getters for properties
    public StringProperty ffmpegPathProperty() { return ffmpegPath; }
    public StringProperty ffprobePathProperty() { return ffprobePath; }
    public StringProperty defaultOutputDirProperty() { return defaultOutputDir; }
    public StringProperty tempDirProperty() { return tempDir; }
    public StringProperty themeProperty() { return theme; }
    public StringProperty languageProperty() { return language; }
    public IntegerProperty maxConcurrentJobsProperty() { return maxConcurrentJobs; }
    public IntegerProperty cacheLimitMbProperty() { return cacheLimitMb; }
    public IntegerProperty logRetentionDaysProperty() { return logRetentionDays; }
    public BooleanProperty confirmationsEnabledProperty() { return confirmationsEnabled; }
    public BooleanProperty testingProperty() { return testing; }
    public BooleanProperty ffmpegValidProperty() { return ffmpegValid; }
    public BooleanProperty ffprobeValidProperty() { return ffprobeValid; }
    public StringProperty ffmpegStatusMessageProperty() { return ffmpegStatusMessage; }
    public StringProperty ffprobeStatusMessageProperty() { return ffprobeStatusMessage; }
    public StringProperty saveStatusMessageProperty() { return saveStatusMessage; }

    public ObservableList<String> getHwAccels() { return hwAccels; }
    public ObservableList<String> getVideoEncoders() { return videoEncoders; }
    public ObservableList<String> getAudioEncoders() { return audioEncoders; }
    public ObservableList<String> getFilters() { return filters; }
}
