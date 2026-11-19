package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.CompressOperation;
import com.ffmpegstudio.domain.model.CompressOperation.CompressionProfile;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
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
import java.util.List;
import java.util.Objects;

public final class CompressionViewModel {
    private static final Logger logger = LoggerFactory.getLogger(CompressionViewModel.class);

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;

    // Observable properties
    private final ObjectProperty<Path> sourceFilePath = new SimpleObjectProperty<>();
    private final ObjectProperty<MediaFile> currentMediaFile = new SimpleObjectProperty<>();
    private final StringProperty originalSizeText = new SimpleStringProperty("-");
    private final ObjectProperty<Path> outputFilePath = new SimpleObjectProperty<>();

    private final ObjectProperty<CompressionProfile> selectedProfile = new SimpleObjectProperty<>(CompressionProfile.BALANCED);
    private final ObjectProperty<VideoCodecOption> selectedVideoCodec = new SimpleObjectProperty<>(VideoCodecOption.H264_LIBX264);
    private final IntegerProperty crfValue = new SimpleIntegerProperty(26);
    private final ObjectProperty<PresetSpeed> selectedSpeed = new SimpleObjectProperty<>(PresetSpeed.MEDIUM);
    private final ObjectProperty<ResolutionPreset> selectedResolution = new SimpleObjectProperty<>(ResolutionPreset.ORIGINAL);
    private final IntegerProperty audioBitrateKbps = new SimpleIntegerProperty(128);

    private final StringProperty commandPreview = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Selecione um arquivo de vídeo para comprimir.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty currentFps = new SimpleStringProperty("-");
    private final StringProperty currentSpeed = new SimpleStringProperty("-");
    private final StringProperty finalSizeText = new SimpleStringProperty("-");
    private final StringProperty compressionRatioText = new SimpleStringProperty("");
    private final BooleanProperty compressionSuccess = new SimpleBooleanProperty(false);

    private final ObservableList<VideoCodecOption> availableVideoCodecs = FXCollections.observableArrayList();
    private final ObservableList<CompressionProfile> availableProfiles = FXCollections.observableArrayList(CompressionProfile.values());
    private final ObservableList<PresetSpeed> availableSpeeds = FXCollections.observableArrayList(PresetSpeed.values());
    private final ObservableList<ResolutionPreset> availableResolutions = FXCollections.observableArrayList(ResolutionPreset.values());

    private TranscodeMediaUseCase.TranscodeExecutionHandle activeExecutionHandle;

    public CompressionViewModel(AnalyzeMediaUseCase analyzeUseCase,
                                TranscodeMediaUseCase transcodeUseCase,
                                ManageQueueUseCase queueUseCase,
                                ManageSettingsUseCase settingsUseCase,
                                DetectCapabilitiesUseCase capabilitiesUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);

        initCodecs();
        setupListeners();
    }

    private void initCodecs() {
        availableVideoCodecs.add(VideoCodecOption.H264_LIBX264);
        try {
            String ffmpegPath = settingsUseCase.getSettings().ffmpegPath();
            FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(ffmpegPath);
            if (caps != null && caps.hasEncoder("libx265")) {
                availableVideoCodecs.add(VideoCodecOption.H265_LIBX265);
            }
        } catch (Exception e) {
            logger.warn("Could not check H.265 capabilities", e);
        }
    }

    private void setupListeners() {
        sourceFilePath.addListener((obs, oldV, newV) -> updateOutputFile());
        selectedProfile.addListener((obs, oldV, profile) -> applyProfileDefaults(profile));
        selectedVideoCodec.addListener((obs, oldV, codec) -> updatePreview());
        crfValue.addListener((obs, oldV, crf) -> updatePreview());
        selectedSpeed.addListener((obs, oldV, speed) -> updatePreview());
        selectedResolution.addListener((obs, oldV, res) -> updatePreview());
        audioBitrateKbps.addListener((obs, oldV, bit) -> updatePreview());
    }

    public void loadMedia(Path path) {
        if (path == null || !Files.exists(path)) return;
        sourceFilePath.set(path);
        statusMessage.set("Analisando metadados do arquivo...");
        compressionSuccess.set(false);
        finalSizeText.set("-");
        compressionRatioText.set("");

        try {
            long sizeBytes = Files.size(path);
            originalSizeText.set(FormatUtils.formatFileSize(sizeBytes));
        } catch (Exception e) {
            originalSizeText.set("-");
        }

        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                mediaFile -> {
                    currentMediaFile.set(mediaFile);
                    statusMessage.set("Arquivo pronto para compressão.");
                    updateOutputFile();
                    updatePreview();
                },
                err -> {
                    logger.error("Failed to inspect media file for compression", err);
                    statusMessage.set("Erro ao analisar arquivo: " + err.getMessage());
                }
        );
    }

    public void applyProfileDefaults(CompressionProfile profile) {
        if (profile == null || profile == CompressionProfile.CUSTOM) {
            updatePreview();
            return;
        }
        crfValue.set(profile.suggestedCrf());
        audioBitrateKbps.set(profile.suggestedAudioBitrate());
        if (profile == CompressionProfile.AGGRESSIVE) {
            selectedResolution.set(ResolutionPreset.P720);
        } else if (profile == CompressionProfile.BALANCED) {
            selectedResolution.set(ResolutionPreset.P1080);
        } else {
            selectedResolution.set(ResolutionPreset.ORIGINAL);
        }
        updatePreview();
    }

    private void updateOutputFile() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        String dir = input.getParent() != null ? input.getParent().toString() : ".";
        String name = input.getFileName().toString();
        int dotIdx = name.lastIndexOf('.');
        String baseName = dotIdx > 0 ? name.substring(0, dotIdx) : name;
        String outName = baseName + "_compressed.mp4";
        outputFilePath.set(Path.of(dir, outName));
    }

    public CompressOperation buildOperation() {
        return new CompressOperation(
                selectedVideoCodec.get(),
                selectedSpeed.get(),
                crfValue.get(),
                null,
                selectedResolution.get(),
                null,
                AudioCodecOption.AAC,
                audioBitrateKbps.get(),
                selectedProfile.get()
        );
    }

    private void updatePreview() {
        Path input = sourceFilePath.get();
        Path output = outputFilePath.get();
        if (input == null || output == null) {
            commandPreview.set("");
            return;
        }
        CompressOperation op = buildOperation();
        List<String> cmd = op.buildCommand("ffmpeg", currentMediaFile.get(), output, true);
        commandPreview.set(String.join(" ", cmd));
    }

    public void startCompression() {
        Path input = sourceFilePath.get();
        Path output = outputFilePath.get();
        if (input == null || output == null || isProcessing.get()) return;

        CompressOperation operation = buildOperation();
        MediaFile media = currentMediaFile.get();

        String ffmpegPath = settingsUseCase.getSettings().ffmpegPath();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(ffmpegPath);
        List<String> validationErrors = operation.validate(media, caps);
        if (!validationErrors.isEmpty()) {
            statusMessage.set("Validação falhou: " + String.join(", ", validationErrors));
            return;
        }

        ProcessingJob job = ProcessingJob.create(operation, input, output, media != null ? media.metadata() : null);

        isProcessing.set(true);
        compressionSuccess.set(false);
        progressPercentage.set(0.0);
        statusMessage.set("Comprimindo vídeo...");
        finalSizeText.set("-");
        compressionRatioText.set("");

        activeExecutionHandle = transcodeUseCase.executeJob(job, this::onProgressUpdate);
        activeExecutionHandle.completionFuture().thenAccept(this::onCompleted);
    }

    public void cancelCompression() {
        if (activeExecutionHandle != null && isProcessing.get()) {
            statusMessage.set("Cancelando compressão...");
            activeExecutionHandle.cancel();
        }
    }

    public void addToQueue() {
        Path input = sourceFilePath.get();
        Path output = outputFilePath.get();
        if (input == null || output == null) return;

        CompressOperation operation = buildOperation();
        MediaFile media = currentMediaFile.get();
        ProcessingJob job = ProcessingJob.create(operation, input, output, media != null ? media.metadata() : null);

        queueUseCase.addJob(job);
        statusMessage.set("Tarefa de compressão adicionada à fila com sucesso!");
    }

    private void onProgressUpdate(ProcessingJob updatedJob) {
        ProcessingProgress p = updatedJob.getProgress();
        Platform.runLater(() -> {
            if (p != null) {
                progressPercentage.set(p.percentage() / 100.0);
                if (p.currentFps() > 0) {
                    currentFps.set(String.format("%.1f", p.currentFps()));
                }
                if (p.speedMultiplier() > 0) {
                    currentSpeed.set(String.format("%.2fx", p.speedMultiplier()));
                }
                statusMessage.set(String.format("Comprimindo... %.1f%% (%s fps, %s)",
                        p.percentage(),
                        currentFps.get(),
                        currentSpeed.get()));
            }
        });
    }

    private void onCompleted(ProcessingJob finishedJob) {
        Platform.runLater(() -> {
            isProcessing.set(false);
            if (finishedJob.getStatus() == JobStatus.COMPLETED) {
                compressionSuccess.set(true);
                statusMessage.set("Compressão concluída com sucesso!");
                progressPercentage.set(1.0);

                Path outPath = outputFilePath.get();
                if (outPath != null && Files.exists(outPath)) {
                    try {
                        long finalBytes = Files.size(outPath);
                        finalSizeText.set(FormatUtils.formatFileSize(finalBytes));

                        Path inPath = sourceFilePath.get();
                        if (inPath != null && Files.exists(inPath)) {
                            long origBytes = Files.size(inPath);
                            if (origBytes > 0) {
                                double reduction = (1.0 - ((double) finalBytes / origBytes)) * 100.0;
                                compressionRatioText.set(String.format("Redução de %.1f%%", reduction));
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Could not calculate final compressed size", e);
                    }
                }
            } else if (finishedJob.getStatus() == JobStatus.CANCELLED) {
                statusMessage.set("Compressão cancelada pelo usuário.");
            } else {
                statusMessage.set("Falha na compressão: " + finishedJob.getErrorMessage());
            }
        });
    }

    // Property getters
    public ObjectProperty<Path> sourceFilePathProperty() { return sourceFilePath; }
    public ObjectProperty<MediaFile> currentMediaFileProperty() { return currentMediaFile; }
    public StringProperty originalSizeTextProperty() { return originalSizeText; }
    public ObjectProperty<Path> outputFilePathProperty() { return outputFilePath; }
    public ObjectProperty<CompressionProfile> selectedProfileProperty() { return selectedProfile; }
    public ObjectProperty<VideoCodecOption> selectedVideoCodecProperty() { return selectedVideoCodec; }
    public IntegerProperty crfValueProperty() { return crfValue; }
    public ObjectProperty<PresetSpeed> selectedSpeedProperty() { return selectedSpeed; }
    public ObjectProperty<ResolutionPreset> selectedResolutionProperty() { return selectedResolution; }
    public IntegerProperty audioBitrateKbpsProperty() { return audioBitrateKbps; }
    public StringProperty commandPreviewProperty() { return commandPreview; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
    public StringProperty currentFpsProperty() { return currentFps; }
    public StringProperty currentSpeedProperty() { return currentSpeed; }
    public StringProperty finalSizeTextProperty() { return finalSizeText; }
    public StringProperty compressionRatioTextProperty() { return compressionRatioText; }
    public BooleanProperty compressionSuccessProperty() { return compressionSuccess; }

    public ObservableList<VideoCodecOption> getAvailableVideoCodecs() { return availableVideoCodecs; }
    public ObservableList<CompressionProfile> getAvailableProfiles() { return availableProfiles; }
    public ObservableList<PresetSpeed> getAvailableSpeeds() { return availableSpeeds; }
    public ObservableList<ResolutionPreset> getAvailableResolutions() { return availableResolutions; }
}
