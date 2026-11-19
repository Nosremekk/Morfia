package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegCommandBuilder;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class ConverterViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ConverterViewModel.class);

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final com.ffmpegstudio.application.port.in.ManagePresetsUseCase presetUseCase;
    private final com.ffmpegstudio.application.port.in.ManageQueueUseCase queueUseCase;

    // Observable Presets
    private final ObservableList<com.ffmpegstudio.domain.model.ConversionPreset> availablePresets = FXCollections.observableArrayList();

    // Media inspection state
    private final ObjectProperty<MediaFile> currentMediaFile = new SimpleObjectProperty<>();
    private final BooleanProperty isMediaLoaded = new SimpleBooleanProperty(false);
    private final BooleanProperty isInspecting = new SimpleBooleanProperty(false);
    private final StringProperty mediaTitle = new SimpleStringProperty("No media file selected");
    private final StringProperty mediaDetails = new SimpleStringProperty("");
    private final StringProperty videoDetails = new SimpleStringProperty("Video: None");
    private final StringProperty audioDetails = new SimpleStringProperty("Audio: None");

    // Conversion settings options
    private final ObjectProperty<ContainerFormat> selectedContainer = new SimpleObjectProperty<>(ContainerFormat.MP4);
    private final ObjectProperty<VideoCodecOption> selectedVideoCodec = new SimpleObjectProperty<>(VideoCodecOption.H264_LIBX264);
    private final ObjectProperty<AudioCodecOption> selectedAudioCodec = new SimpleObjectProperty<>(AudioCodecOption.AAC);
    private final ObjectProperty<PresetSpeed> selectedPreset = new SimpleObjectProperty<>(PresetSpeed.MEDIUM);
    private final IntegerProperty crfValue = new SimpleIntegerProperty(23);
    private final ObjectProperty<ResolutionPreset> selectedResolution = new SimpleObjectProperty<>(ResolutionPreset.ORIGINAL);
    private final StringProperty outputPath = new SimpleStringProperty("");

    // Execution & Progress state
    private final BooleanProperty isTranscoding = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty progressText = new SimpleStringProperty("0.0%");
    private final StringProperty fpsText = new SimpleStringProperty("-");
    private final StringProperty speedText = new SimpleStringProperty("-");
    private final StringProperty etaText = new SimpleStringProperty("-");
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final StringProperty previewCommand = new SimpleStringProperty("");
    private final ObservableList<String> logLines = FXCollections.observableArrayList();

    private TranscodeMediaUseCase.TranscodeExecutionHandle currentHandle;

    public ConverterViewModel(
            AnalyzeMediaUseCase analyzeUseCase,
            TranscodeMediaUseCase transcodeUseCase,
            ManageSettingsUseCase settingsUseCase
    ) {
        this(analyzeUseCase, transcodeUseCase, settingsUseCase, null, null);
    }

    public ConverterViewModel(
            AnalyzeMediaUseCase analyzeUseCase,
            TranscodeMediaUseCase transcodeUseCase,
            ManageSettingsUseCase settingsUseCase,
            com.ffmpegstudio.application.port.in.ManagePresetsUseCase presetUseCase,
            com.ffmpegstudio.application.port.in.ManageQueueUseCase queueUseCase
    ) {
        this.analyzeUseCase = analyzeUseCase;
        this.transcodeUseCase = transcodeUseCase;
        this.settingsUseCase = settingsUseCase;
        this.presetUseCase = presetUseCase;
        this.queueUseCase = queueUseCase;

        if (presetUseCase != null) {
            availablePresets.setAll(presetUseCase.getAllPresets());
        }

        // Listen for setting modifications to refresh preview command
        selectedContainer.addListener((obs, o, n) -> {
            updateSuggestedOutputPath();
            updatePreviewCommand();
        });
        selectedVideoCodec.addListener((obs, o, n) -> updatePreviewCommand());
        selectedAudioCodec.addListener((obs, o, n) -> updatePreviewCommand());
        selectedPreset.addListener((obs, o, n) -> updatePreviewCommand());
        crfValue.addListener((obs, o, n) -> updatePreviewCommand());
        selectedResolution.addListener((obs, o, n) -> updatePreviewCommand());
        outputPath.addListener((obs, o, n) -> updatePreviewCommand());
    }

    public void loadMediaFile(Path path) {
        if (path == null || !Files.exists(path)) {
            statusMessage.set("Selected file does not exist.");
            return;
        }

        isInspecting.set(true);
        statusMessage.set("Inspecting " + path.getFileName() + " via FFprobe...");

        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                media -> {
                    isInspecting.set(false);
                    currentMediaFile.set(media);
                    isMediaLoaded.set(true);

                    mediaTitle.set(media.fileName());
                    String sizeFormatted = FormatUtils.formatFileSize(media.fileSizeBytes());
                    String durationFormatted = FormatUtils.formatDuration(media.metadata().duration());
                    String bitRateFormatted = FormatUtils.formatBitRate(media.metadata().bitRate());
                    mediaDetails.set(String.format("%s • %s • %s • %s", media.metadata().formatName(), durationFormatted, sizeFormatted, bitRateFormatted));

                    media.metadata().primaryVideoStream().ifPresentOrElse(v -> {
                        String res = FormatUtils.formatResolution(v.width(), v.height());
                        String fps = FormatUtils.formatFps(v.frameRate());
                        videoDetails.set(String.format("Video: %s • %s • %s (%s)", v.codecName().toUpperCase(), res, fps, v.pixelFormat()));
                    }, () -> videoDetails.set("Video: None"));

                    if (!media.metadata().audioStreams().isEmpty()) {
                        var a = media.metadata().audioStreams().get(0);
                        audioDetails.set(String.format("Audio: %s • %d Hz • %s", a.codecName().toUpperCase(), a.sampleRate(), a.channelLayout()));
                    } else {
                        audioDetails.set("Audio: None");
                    }

                    statusMessage.set("Loaded " + media.fileName());
                    updateSuggestedOutputPath();
                    updatePreviewCommand();
                },
                err -> {
                    isInspecting.set(false);
                    isMediaLoaded.set(false);
                    statusMessage.set("Inspection failed: " + err.getMessage());
                    logger.error("Failed inspecting media", err);
                }
        );
    }

    private void updateSuggestedOutputPath() {
        if (currentMediaFile.get() == null) return;
        Path in = currentMediaFile.get().path();
        String originalName = in.getFileName().toString();
        String baseName = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
        String ext = selectedContainer.get().extension();

        String defaultDir = settingsUseCase.getSettings().defaultOutputDir();
        Path out = Paths.get(defaultDir, baseName + "_transcoded." + ext);
        outputPath.set(out.toAbsolutePath().toString());
    }

    public final void updatePreviewCommand() {
        if (currentMediaFile.get() == null || outputPath.get().isBlank()) {
            previewCommand.set("ffmpeg -i input.[ext] [transcoding options] output.[ext]");
            return;
        }

        try {
            ConversionSettings settings = buildCurrentSettings();
            String cmdStr = new FFmpegCommandBuilder()
                    .withFfmpegBinary(settingsUseCase.getSettings().ffmpegPath())
                    .withInput(currentMediaFile.get().path())
                    .withOutput(Paths.get(outputPath.get()))
                    .withSettings(settings)
                    .withOverwrite(false)
                    .toDisplayString();
            previewCommand.set(cmdStr);
        } catch (Exception e) {
            previewCommand.set("Command generation preview unavailable: " + e.getMessage());
        }
    }

    public void startConversion() {
        if (currentMediaFile.get() == null) {
            statusMessage.set("Please select an input media file first.");
            return;
        }
        if (outputPath.get().isBlank()) {
            statusMessage.set("Please specify an output file path.");
            return;
        }

        Path inPath = currentMediaFile.get().path();
        Path outPath = Paths.get(outputPath.get());

        if (inPath.toAbsolutePath().equals(outPath.toAbsolutePath())) {
            statusMessage.set("Output path cannot be identical to input path (would overwrite original).");
            return;
        }

        // Prepare temporary file in the same directory
        Path parent = outPath.getParent() != null ? outPath.getParent() : Paths.get(".");
        Path tempPath = parent.resolve(".~tmp_" + outPath.getFileName().toString());

        ConversionSettings settings = buildCurrentSettings();
        ProcessingJob job = new ProcessingJob(inPath, outPath, tempPath, settings);

        // Pre-populate total duration for progress parsing
        if (currentMediaFile.get().metadata() != null) {
            long totalMs = currentMediaFile.get().metadata().duration().toMillis();
            job.updateProgress(new com.ffmpegstudio.domain.model.ProcessingProgress(0.0, 0, 0, 0, totalMs, 1.0, 0));
        }

        isTranscoding.set(true);
        progressPercentage.set(0.0);
        progressText.set("0.0%");
        statusMessage.set("Transcoding started...");
        logLines.clear();

        currentHandle = transcodeUseCase.executeJob(job, updatedJob -> {
            Platform.runLater(() -> {
                var p = updatedJob.getProgress();
                progressPercentage.set(p.percentage() / 100.0);
                progressText.set(String.format(java.util.Locale.US, "%.1f%%", p.percentage()));
                fpsText.set(p.currentFps() > 0 ? String.format(java.util.Locale.US, "%.1f", p.currentFps()) : "-");
                speedText.set(p.speedMultiplier() > 0 ? String.format(java.util.Locale.US, "%.2fx", p.speedMultiplier()) : "-");
                etaText.set(p.etaSeconds() >= 0 ? FormatUtils.formatDuration(java.time.Duration.ofSeconds(p.etaSeconds())) : "-");

                // Sync latest logs
                List<String> currentLogs = updatedJob.getLogLines();
                if (currentLogs.size() > logLines.size()) {
                    logLines.addAll(currentLogs.subList(logLines.size(), currentLogs.size()));
                }

                switch (updatedJob.getStatus()) {
                    case COMPLETED -> {
                        isTranscoding.set(false);
                        statusMessage.set("Transcoding completed successfully!");
                    }
                    case FAILED -> {
                        isTranscoding.set(false);
                        statusMessage.set("Transcoding failed: " + updatedJob.getErrorMessage());
                    }
                    case CANCELLED -> {
                        isTranscoding.set(false);
                        statusMessage.set("Transcoding cancelled by user.");
                    }
                    case RUNNING -> statusMessage.set("Transcoding in progress...");
                    case PENDING -> statusMessage.set("Job queued...");
                }
            });
        });
    }

    public void applyPreset(com.ffmpegstudio.domain.model.ConversionPreset preset) {
        if (preset == null || preset.settings() == null) return;
        ConversionSettings s = preset.settings();
        if (s.container() != null) selectedContainer.set(s.container());
        if (s.videoCodec() != null) selectedVideoCodec.set(s.videoCodec());
        if (s.audioCodec() != null) selectedAudioCodec.set(s.audioCodec());
        if (s.speedPreset() != null) selectedPreset.set(s.speedPreset());
        crfValue.set(s.crf());
        if (s.resolution() != null) selectedResolution.set(s.resolution());
        statusMessage.set("Preset aplicado: " + preset.name());
    }

    public void addToQueue() {
        if (!isMediaLoaded.get() || currentMediaFile.get() == null) {
            statusMessage.set("Nenhum arquivo de mídia carregado para enfileirar.");
            return;
        }
        if (queueUseCase == null) {
            statusMessage.set("Fila não disponível.");
            return;
        }

        Path inPath = currentMediaFile.get().path();
        Path outPath = Paths.get(outputPath.get().isBlank() ? inPath.toString() + ".out.mp4" : outputPath.get());
        ConversionSettings settings = buildCurrentSettings();
        ProcessingJob job = ProcessingJob.create(
                new com.ffmpegstudio.domain.model.TranscodeOperation(settings),
                inPath,
                outPath,
                currentMediaFile.get().metadata()
        );

        queueUseCase.addJob(job);
        statusMessage.set("Tarefa de conversão adicionada à fila com sucesso!");
    }

    public ObservableList<com.ffmpegstudio.domain.model.ConversionPreset> getAvailablePresets() {
        return availablePresets;
    }

    public void cancelConversion() {
        if (currentHandle != null && currentHandle.isRunning()) {
            statusMessage.set("Cancelling transcoding...");
            currentHandle.cancel();
        }
    }

    private ConversionSettings buildCurrentSettings() {
        return new ConversionSettings(
                selectedContainer.get(),
                selectedVideoCodec.get(),
                selectedAudioCodec.get(),
                selectedPreset.get(),
                crfValue.get(),
                null,
                192,
                selectedResolution.get(),
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );
    }

    // Property getters
    public ObjectProperty<MediaFile> currentMediaFileProperty() { return currentMediaFile; }
    public BooleanProperty isMediaLoadedProperty() { return isMediaLoaded; }
    public BooleanProperty isInspectingProperty() { return isInspecting; }
    public StringProperty mediaTitleProperty() { return mediaTitle; }
    public StringProperty mediaDetailsProperty() { return mediaDetails; }
    public StringProperty videoDetailsProperty() { return videoDetails; }
    public StringProperty audioDetailsProperty() { return audioDetails; }

    public ObjectProperty<ContainerFormat> selectedContainerProperty() { return selectedContainer; }
    public ObjectProperty<VideoCodecOption> selectedVideoCodecProperty() { return selectedVideoCodec; }
    public ObjectProperty<AudioCodecOption> selectedAudioCodecProperty() { return selectedAudioCodec; }
    public ObjectProperty<PresetSpeed> selectedPresetProperty() { return selectedPreset; }
    public IntegerProperty crfValueProperty() { return crfValue; }
    public ObjectProperty<ResolutionPreset> selectedResolutionProperty() { return selectedResolution; }
    public StringProperty outputPathProperty() { return outputPath; }

    public BooleanProperty isTranscodingProperty() { return isTranscoding; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
    public StringProperty progressTextProperty() { return progressText; }
    public StringProperty fpsTextProperty() { return fpsText; }
    public StringProperty speedTextProperty() { return speedText; }
    public StringProperty etaTextProperty() { return etaText; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty previewCommandProperty() { return previewCommand; }
    public ObservableList<String> getLogLines() { return logLines; }
}
