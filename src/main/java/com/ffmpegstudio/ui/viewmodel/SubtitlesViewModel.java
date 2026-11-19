package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.SubtitleFormat;
import com.ffmpegstudio.domain.model.SubtitleOperation;
import com.ffmpegstudio.domain.model.SubtitleOperation.SubtitleAction;
import com.ffmpegstudio.domain.model.SubtitleStream;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class SubtitlesViewModel {

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;

    private final ObjectProperty<MediaFile> currentMedia = new SimpleObjectProperty<>();
    private final ObservableList<SubtitleStream> subtitleStreams = FXCollections.observableArrayList();
    private final ObjectProperty<SubtitleAction> selectedAction = new SimpleObjectProperty<>(SubtitleAction.EMBED_SOFT);
    private final ObjectProperty<Integer> selectedStreamIndex = new SimpleObjectProperty<>(0);
    private final StringProperty externalSubPath = new SimpleStringProperty("");
    private final ObjectProperty<SubtitleFormat> targetFormat = new SimpleObjectProperty<>(SubtitleFormat.SRT);
    private final ObjectProperty<ContainerFormat> targetContainer = new SimpleObjectProperty<>(ContainerFormat.MKV);
    private final StringProperty language = new SimpleStringProperty("por");
    private final StringProperty title = new SimpleStringProperty("");
    private final BooleanProperty defaultFlag = new SimpleBooleanProperty(true);
    private final BooleanProperty forcedFlag = new SimpleBooleanProperty(false);
    private final StringProperty timeOffsetText = new SimpleStringProperty("0.0");
    private final StringProperty outputPath = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Selecione um arquivo de mídia.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);

    private TranscodeMediaUseCase.TranscodeExecutionHandle activeHandle;

    public SubtitlesViewModel(AnalyzeMediaUseCase analyzeUseCase,
                              TranscodeMediaUseCase transcodeUseCase,
                              ManageQueueUseCase queueUseCase,
                              ManageSettingsUseCase settingsUseCase,
                              DetectCapabilitiesUseCase capabilitiesUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);

        selectedAction.addListener((obs, o, n) -> updateSuggestedOutput());
        targetFormat.addListener((obs, o, n) -> updateSuggestedOutput());
        targetContainer.addListener((obs, o, n) -> updateSuggestedOutput());
    }

    public void loadMedia(Path path) {
        if (path == null || !Files.exists(path)) return;
        statusMessage.set("Inspecionando faixas de legenda...");
        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                mf -> {
                    currentMedia.set(mf);
                    subtitleStreams.setAll(mf.metadata() != null ? mf.metadata().subtitleStreams() : List.of());
                    updateSuggestedOutput();
                    statusMessage.set("Carregado: " + subtitleStreams.size() + " faixa(s) de legenda encontrada(s).");
                },
                err -> statusMessage.set("Erro ao analisar: " + err.getMessage())
        );
    }

    private void updateSuggestedOutput() {
        MediaFile mf = currentMedia.get();
        if (mf == null) return;
        Path dir = mf.path().getParent() != null ? mf.path().getParent() : Path.of(".");
        SubtitleOperation op = buildOperation();
        outputPath.set(op.getSuggestedOutputPath(mf, dir).toAbsolutePath().toString());
    }

    public SubtitleOperation buildOperation() {
        double offset = 0.0;
        try {
            offset = Double.parseDouble(timeOffsetText.get().trim().replace(",", "."));
        } catch (Exception ignored) {}

        Path extSub = externalSubPath.get() != null && !externalSubPath.get().isBlank()
                ? Path.of(externalSubPath.get().trim())
                : null;

        return new SubtitleOperation(
                selectedAction.get(),
                selectedStreamIndex.get(),
                extSub,
                targetFormat.get(),
                targetContainer.get(),
                language.get(),
                title.get(),
                defaultFlag.get(),
                forcedFlag.get(),
                offset,
                ConversionSettings.defaultMp4()
        );
    }

    public void execute() {
        MediaFile mf = currentMedia.get();
        if (mf == null || isProcessing.get()) return;

        SubtitleOperation op = buildOperation();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        List<String> errs = op.validate(mf, caps);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação: " + String.join(" ", errs));
            return;
        }

        Path out = Path.of(outputPath.get());
        ProcessingJob job = ProcessingJob.create(op, mf.path(), out, mf.metadata());

        isProcessing.set(true);
        progressPercentage.set(0.0);
        statusMessage.set("Executando: " + op.name() + "...");

        activeHandle = transcodeUseCase.executeJob(job, updated -> Platform.runLater(() -> {
            ProcessingProgress p = updated.getProgress();
            if (p != null) progressPercentage.set(p.percentage() / 100.0);
        }));

        activeHandle.completionFuture().thenAccept(done -> Platform.runLater(() -> {
            isProcessing.set(false);
            if (done.getStatus() == JobStatus.COMPLETED) {
                progressPercentage.set(1.0);
                statusMessage.set("Concluído: " + out.getFileName());
            } else if (done.getStatus() == JobStatus.CANCELLED) {
                statusMessage.set("Operação cancelada.");
            } else {
                statusMessage.set("Erro: " + done.getErrorMessage());
            }
        }));
    }

    public void addToQueue() {
        MediaFile mf = currentMedia.get();
        if (mf == null) return;
        SubtitleOperation op = buildOperation();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        List<String> errs = op.validate(mf, caps);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação: " + String.join(" ", errs));
            return;
        }
        Path out = Path.of(outputPath.get());
        queueUseCase.addJob(ProcessingJob.create(op, mf.path(), out, mf.metadata()));
        statusMessage.set("Operação de legenda adicionada à fila!");
    }

    public void cancel() {
        if (activeHandle != null && isProcessing.get()) {
            activeHandle.cancel();
        }
    }

    public ObjectProperty<MediaFile> currentMediaProperty() { return currentMedia; }
    public ObservableList<SubtitleStream> getSubtitleStreams() { return subtitleStreams; }
    public ObjectProperty<SubtitleAction> selectedActionProperty() { return selectedAction; }
    public ObjectProperty<Integer> selectedStreamIndexProperty() { return selectedStreamIndex; }
    public StringProperty externalSubPathProperty() { return externalSubPath; }
    public ObjectProperty<SubtitleFormat> targetFormatProperty() { return targetFormat; }
    public ObjectProperty<ContainerFormat> targetContainerProperty() { return targetContainer; }
    public StringProperty languageProperty() { return language; }
    public StringProperty titleProperty() { return title; }
    public BooleanProperty defaultFlagProperty() { return defaultFlag; }
    public BooleanProperty forcedFlagProperty() { return forcedFlag; }
    public StringProperty timeOffsetTextProperty() { return timeOffsetText; }
    public StringProperty outputPathProperty() { return outputPath; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
}
