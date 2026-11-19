package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.AudioStream;
import com.ffmpegstudio.domain.model.AudioToolsOperation;
import com.ffmpegstudio.domain.model.AudioToolsOperation.AudioOutputFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.ExtractFramesOperation;
import com.ffmpegstudio.domain.model.ExtractFramesOperation.ImageFormat;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.GenerateGifOperation;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MediaOperation;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.TrimOperation;
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
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ToolsViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ToolsViewModel.class);

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;

    // Shared State
    private final ObjectProperty<Path> sourceFilePath = new SimpleObjectProperty<>();
    private final ObjectProperty<MediaFile> currentMediaFile = new SimpleObjectProperty<>();
    private final StringProperty statusMessage = new SimpleStringProperty("Selecione um arquivo de mídia.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty commandPreview = new SimpleStringProperty("");
    private final BooleanProperty operationSuccess = new SimpleBooleanProperty(false);
    private final ObjectProperty<Path> lastGeneratedOutput = new SimpleObjectProperty<>();

    // Audio Tools Properties
    private final ObjectProperty<AudioOutputFormat> audioFormat = new SimpleObjectProperty<>(AudioOutputFormat.MP3);
    private final IntegerProperty audioBitrate = new SimpleIntegerProperty(192);
    private final DoubleProperty audioVolume = new SimpleDoubleProperty(1.0); // 1.0 = 100%
    private final BooleanProperty audioLoudnorm = new SimpleBooleanProperty(false);
    private final ObjectProperty<Integer> audioChannels = new SimpleObjectProperty<>(null); // null = keep
    private final ObjectProperty<Integer> audioSampleRate = new SimpleObjectProperty<>(null); // null = keep
    private final ObservableList<String> audioTracksList = FXCollections.observableArrayList();
    private final ObjectProperty<Integer> selectedAudioTrackIndex = new SimpleObjectProperty<>(null);

    // Video Trim Properties
    private final StringProperty trimStartTime = new SimpleStringProperty("00:00:00");
    private final StringProperty trimEndTime = new SimpleStringProperty("00:00:10");
    private final StringProperty trimDurationText = new SimpleStringProperty("00:00:10");
    private final BooleanProperty trimAccurateReencode = new SimpleBooleanProperty(false);

    // GIF & Frames Properties
    private final StringProperty gifStartTime = new SimpleStringProperty("00:00:00");
    private final IntegerProperty gifDurationSec = new SimpleIntegerProperty(5);
    private final IntegerProperty gifFps = new SimpleIntegerProperty(15);
    private final IntegerProperty gifWidth = new SimpleIntegerProperty(480);
    private final BooleanProperty gifPaletteOptimization = new SimpleBooleanProperty(true);

    private final StringProperty frameTimestamp = new SimpleStringProperty("00:00:01");
    private final ObjectProperty<ImageFormat> frameFormat = new SimpleObjectProperty<>(ImageFormat.PNG);

    private TranscodeMediaUseCase.TranscodeExecutionHandle activeExecutionHandle;

    public ToolsViewModel(AnalyzeMediaUseCase analyzeUseCase,
                          TranscodeMediaUseCase transcodeUseCase,
                          ManageQueueUseCase queueUseCase,
                          ManageSettingsUseCase settingsUseCase,
                          DetectCapabilitiesUseCase capabilitiesUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);
    }

    public void loadMedia(Path path) {
        if (path == null || !Files.exists(path)) return;
        sourceFilePath.set(path);
        statusMessage.set("Analisando metadados da mídia...");
        operationSuccess.set(false);
        lastGeneratedOutput.set(null);

        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                media -> {
                    currentMediaFile.set(media);
                    statusMessage.set("Mídia carregada com sucesso.");
                    populateAudioTracks(media);
                    initTrimTimes(media);
                },
                err -> {
                    logger.error("Failed to analyze media for tools", err);
                    statusMessage.set("Erro ao analisar arquivo: " + err.getMessage());
                }
        );
    }

    private void populateAudioTracks(MediaFile media) {
        audioTracksList.clear();
        if (media != null && media.metadata() != null) {
            List<AudioStream> streams = media.metadata().audioStreams();
            if (streams.isEmpty()) {
                audioTracksList.add("Nenhuma faixa de áudio encontrada");
                selectedAudioTrackIndex.set(null);
            } else {
                for (int i = 0; i < streams.size(); i++) {
                    AudioStream s = streams.get(i);
                    String lang = s.language() != null ? " [" + s.language() + "]" : "";
                    audioTracksList.add(String.format("Faixa #%d: %s (%d canais)%s", i + 1, s.codecName(), s.channels(), lang));
                }
                selectedAudioTrackIndex.set(0);
            }
        }
    }

    private void initTrimTimes(MediaFile media) {
        if (media != null && media.metadata() != null && media.metadata().duration() != null) {
            Duration dur = media.metadata().duration();
            trimStartTime.set("00:00:00");
            Duration end = dur.toSeconds() > 10 ? Duration.ofSeconds(10) : dur;
            trimEndTime.set(FormatUtils.formatDuration(end));
            updateTrimDuration();
        }
    }

    public void updateTrimDuration() {
        Duration start = parseTimestamp(trimStartTime.get());
        Duration end = parseTimestamp(trimEndTime.get());
        if (end.compareTo(start) > 0) {
            trimDurationText.set(FormatUtils.formatDuration(end.minus(start)));
        } else {
            trimDurationText.set("Inválido (Início >= Fim)");
        }
    }

    private Duration parseTimestamp(String text) {
        if (text == null || text.isBlank()) return Duration.ZERO;
        try {
            String[] parts = text.trim().split(":");
            if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return Duration.ofMillis((long) ((h * 3600 + m * 60 + s) * 1000));
            } else if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                double s = Double.parseDouble(parts[1]);
                return Duration.ofMillis((long) ((m * 60 + s) * 1000));
            } else if (parts.length == 1) {
                double s = Double.parseDouble(parts[0]);
                return Duration.ofMillis((long) (s * 1000));
            }
        } catch (Exception ignored) {}
        return Duration.ZERO;
    }

    // Audio Execution
    public AudioToolsOperation buildAudioOperation() {
        return new AudioToolsOperation(
                audioFormat.get(),
                audioSampleRate.get(),
                audioChannels.get(),
                audioBitrate.get(),
                audioVolume.get(),
                audioLoudnorm.get(),
                selectedAudioTrackIndex.get()
        );
    }

    public void executeAudioOperation() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        AudioToolsOperation op = buildAudioOperation();
        Path output = generateOutputPath(input, "_audio." + op.format().extension());
        executeOperation(op, input, output);
    }

    public void addAudioToQueue() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        AudioToolsOperation op = buildAudioOperation();
        Path output = generateOutputPath(input, "_audio." + op.format().extension());
        addOperationToQueue(op, input, output);
    }

    // Trim Execution
    public TrimOperation buildTrimOperation() {
        Duration start = parseTimestamp(trimStartTime.get());
        Duration end = parseTimestamp(trimEndTime.get());
        return new TrimOperation(
                start,
                end,
                trimAccurateReencode.get(),
                trimAccurateReencode.get() ? ConversionSettings.defaultMp4() : null
        );
    }

    public void executeTrimOperation() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        TrimOperation op = buildTrimOperation();
        Path output = generateOutputPath(input, "_trim.mp4");
        executeOperation(op, input, output);
    }

    public void addTrimToQueue() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        TrimOperation op = buildTrimOperation();
        Path output = generateOutputPath(input, "_trim.mp4");
        addOperationToQueue(op, input, output);
    }

    // GIF Execution
    public GenerateGifOperation buildGifOperation() {
        Duration start = parseTimestamp(gifStartTime.get());
        Duration length = Duration.ofSeconds(gifDurationSec.get());
        return new GenerateGifOperation(
                start,
                length,
                gifFps.get(),
                gifWidth.get(),
                gifPaletteOptimization.get()
        );
    }

    public void executeGifOperation() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        GenerateGifOperation op = buildGifOperation();
        Path output = generateOutputPath(input, ".gif");
        executeOperation(op, input, output);
    }

    public void addGifToQueue() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        GenerateGifOperation op = buildGifOperation();
        Path output = generateOutputPath(input, ".gif");
        addOperationToQueue(op, input, output);
    }

    // Frame Extraction Execution
    public ExtractFramesOperation buildFrameOperation() {
        Duration at = parseTimestamp(frameTimestamp.get());
        return new ExtractFramesOperation(at, frameFormat.get(), -1);
    }

    public void executeFrameOperation() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        ExtractFramesOperation op = buildFrameOperation();
        Path output = generateOutputPath(input, "_frame." + op.format().extension());
        executeOperation(op, input, output);
    }

    public void addFrameToQueue() {
        Path input = sourceFilePath.get();
        if (input == null) return;
        ExtractFramesOperation op = buildFrameOperation();
        Path output = generateOutputPath(input, "_frame." + op.format().extension());
        addOperationToQueue(op, input, output);
    }

    private Path generateOutputPath(Path input, String suffix) {
        String dir = input.getParent() != null ? input.getParent().toString() : ".";
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return Path.of(dir, base + suffix);
    }

    private void executeOperation(MediaOperation op, Path input, Path output) {
        if (isProcessing.get()) return;

        String ffmpegPath = settingsUseCase.getSettings().ffmpegPath();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(ffmpegPath);
        List<String> errs = op.validate(currentMediaFile.get(), caps);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação: " + String.join(", ", errs));
            return;
        }

        ProcessingJob job = ProcessingJob.create(op, input, output, currentMediaFile.get() != null ? currentMediaFile.get().metadata() : null);

        isProcessing.set(true);
        operationSuccess.set(false);
        progressPercentage.set(0.0);
        statusMessage.set("Executando: " + op.name() + "...");
        commandPreview.set(String.join(" ", op.buildCommand("ffmpeg", currentMediaFile.get(), output, true)));

        activeExecutionHandle = transcodeUseCase.executeJob(
                job,
                updatedJob -> Platform.runLater(() -> {
                    ProcessingProgress p = updatedJob.getProgress();
                    if (p != null) {
                        progressPercentage.set(p.percentage() / 100.0);
                    }
                })
        );

        activeExecutionHandle.completionFuture().thenAccept(resJob -> Platform.runLater(() -> {
            isProcessing.set(false);
            if (resJob.getStatus() == JobStatus.COMPLETED) {
                operationSuccess.set(true);
                lastGeneratedOutput.set(output);
                statusMessage.set("Sucesso! Arquivo gerado: " + output.getFileName());
                progressPercentage.set(1.0);
            } else if (resJob.getStatus() == JobStatus.CANCELLED) {
                statusMessage.set("Operação cancelada pelo usuário.");
            } else {
                statusMessage.set("Erro: " + resJob.getErrorMessage());
            }
        }));
    }

    private void addOperationToQueue(MediaOperation op, Path input, Path output) {
        ProcessingJob job = ProcessingJob.create(op, input, output, currentMediaFile.get() != null ? currentMediaFile.get().metadata() : null);
        queueUseCase.addJob(job);
        statusMessage.set("Operação '" + op.name() + "' adicionada à fila de processamento!");
    }

    public void cancelOperation() {
        if (activeExecutionHandle != null && isProcessing.get()) {
            statusMessage.set("Cancelando...");
            activeExecutionHandle.cancel();
        }
    }

    // Properties
    public ObjectProperty<Path> sourceFilePathProperty() { return sourceFilePath; }
    public ObjectProperty<MediaFile> currentMediaFileProperty() { return currentMediaFile; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
    public StringProperty commandPreviewProperty() { return commandPreview; }
    public BooleanProperty operationSuccessProperty() { return operationSuccess; }
    public ObjectProperty<Path> lastGeneratedOutputProperty() { return lastGeneratedOutput; }

    public ObjectProperty<AudioOutputFormat> audioFormatProperty() { return audioFormat; }
    public IntegerProperty audioBitrateProperty() { return audioBitrate; }
    public DoubleProperty audioVolumeProperty() { return audioVolume; }
    public BooleanProperty audioLoudnormProperty() { return audioLoudnorm; }
    public ObjectProperty<Integer> audioChannelsProperty() { return audioChannels; }
    public ObjectProperty<Integer> audioSampleRateProperty() { return audioSampleRate; }
    public ObservableList<String> getAudioTracksList() { return audioTracksList; }
    public ObjectProperty<Integer> selectedAudioTrackIndexProperty() { return selectedAudioTrackIndex; }

    public StringProperty trimStartTimeProperty() { return trimStartTime; }
    public StringProperty trimEndTimeProperty() { return trimEndTime; }
    public StringProperty trimDurationTextProperty() { return trimDurationText; }
    public BooleanProperty trimAccurateReencodeProperty() { return trimAccurateReencode; }

    public StringProperty gifStartTimeProperty() { return gifStartTime; }
    public IntegerProperty gifDurationSecProperty() { return gifDurationSec; }
    public IntegerProperty gifFpsProperty() { return gifFps; }
    public IntegerProperty gifWidthProperty() { return gifWidth; }
    public BooleanProperty gifPaletteOptimizationProperty() { return gifPaletteOptimization; }

    public StringProperty frameTimestampProperty() { return frameTimestamp; }
    public ObjectProperty<ImageFormat> frameFormatProperty() { return frameFormat; }
}
