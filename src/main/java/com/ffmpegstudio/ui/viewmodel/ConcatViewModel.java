package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.ConcatOperation;
import com.ffmpegstudio.domain.model.ConcatOperation.ConcatMode;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.PresetSpeed;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ConcatViewModel {
    private static final Logger logger = LoggerFactory.getLogger(ConcatViewModel.class);

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;

    private final ObservableList<MediaFile> clips = FXCollections.observableArrayList();
    private final ObjectProperty<ConcatMode> selectedMode = new SimpleObjectProperty<>(ConcatMode.FAST_DEMUXER);
    private final ObjectProperty<ContainerFormat> targetContainer = new SimpleObjectProperty<>(ContainerFormat.MP4);
    private final ObjectProperty<ResolutionPreset> targetResolution = new SimpleObjectProperty<>(ResolutionPreset.ORIGINAL);
    private final StringProperty outputPath = new SimpleStringProperty("");
    private final StringProperty totalDurationText = new SimpleStringProperty("00:00:00");
    private final StringProperty compatibilityMessage = new SimpleStringProperty("Adicione 2 ou mais clipes para verificar compatibilidade.");
    private final BooleanProperty isCompatible = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("Pronto.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);

    private TranscodeMediaUseCase.TranscodeExecutionHandle activeHandle;

    public ConcatViewModel(AnalyzeMediaUseCase analyzeUseCase,
                           TranscodeMediaUseCase transcodeUseCase,
                           ManageQueueUseCase queueUseCase,
                           ManageSettingsUseCase settingsUseCase,
                           DetectCapabilitiesUseCase capabilitiesUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);

        selectedMode.addListener((obs, o, n) -> revalidate());
        targetContainer.addListener((obs, o, n) -> updateSuggestedOutput());
    }

    public void addFiles(List<Path> files) {
        if (files == null || files.isEmpty()) return;
        statusMessage.set("Analisando clipes selecionados...");
        for (Path p : files) {
            if (p != null && Files.exists(p)) {
                AsyncHelper.supplyAsync(
                        () -> analyzeUseCase.analyze(p),
                        mf -> {
                            clips.add(mf);
                            updateSuggestedOutput();
                            revalidate();
                            statusMessage.set("Clipe adicionado: " + mf.fileName());
                        },
                        err -> statusMessage.set("Erro ao analisar " + p.getFileName() + ": " + err.getMessage())
                );
            }
        }
    }

    public void moveUp(int index) {
        if (index > 0 && index < clips.size()) {
            MediaFile item = clips.remove(index);
            clips.add(index - 1, item);
            revalidate();
        }
    }

    public void moveDown(int index) {
        if (index >= 0 && index < clips.size() - 1) {
            MediaFile item = clips.remove(index);
            clips.add(index + 1, item);
            revalidate();
        }
    }

    public void removeAt(int index) {
        if (index >= 0 && index < clips.size()) {
            clips.remove(index);
            revalidate();
        }
    }

    public void clearAll() {
        clips.clear();
        revalidate();
    }

    private void updateSuggestedOutput() {
        if (clips.isEmpty()) return;
        if (outputPath.get().isBlank()) {
            Path first = clips.get(0).path();
            Path dir = first.getParent() != null ? first.getParent() : Path.of(".");
            String name = first.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = targetContainer.get() != null ? targetContainer.get().extension() : "mp4";
            outputPath.set(dir.resolve(base + "_merged." + ext).toAbsolutePath().toString());
        }
    }

    public ConcatOperation buildOperation() {
        ConversionSettings settings = new ConversionSettings(
                targetContainer.get(),
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.MEDIUM,
                23,
                null,
                192,
                targetResolution.get(),
                null,
                null,
                null,
                false,
                null,
                null,
                null
        );
        return new ConcatOperation(clips, selectedMode.get(), settings);
    }

    public void revalidate() {
        Duration total = Duration.ZERO;
        for (MediaFile mf : clips) {
            if (mf.metadata() != null && mf.metadata().duration() != null) {
                total = total.plus(mf.metadata().duration());
            }
        }
        totalDurationText.set(FormatUtils.formatDuration(total));

        if (clips.size() < 2) {
            isCompatible.set(false);
            compatibilityMessage.set("Adicione pelo menos 2 arquivos para concatenar.");
            return;
        }

        ConcatOperation op = buildOperation();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        List<String> errs = op.validate(null, caps);
        if (errs.isEmpty()) {
            isCompatible.set(true);
            compatibilityMessage.set(selectedMode.get() == ConcatMode.FAST_DEMUXER
                    ? "Clipes compatíveis para Concatenação Rápida (Stream Copy)."
                    : "Pronto para Concatenação Compatível (normalização automática).");
        } else {
            isCompatible.set(false);
            compatibilityMessage.set(String.join(" ", errs));
        }
    }

    public void executeConcat() {
        if (isProcessing.get() || clips.size() < 2) return;
        ConcatOperation op = buildOperation();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        List<String> errs = op.validate(null, caps);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação falhou: " + errs.get(0));
            return;
        }

        Path out = Path.of(outputPath.get());
        ProcessingJob job = ProcessingJob.create(op, clips.get(0).path(), out, clips.get(0).metadata());

        isProcessing.set(true);
        progressPercentage.set(0.0);
        statusMessage.set("Concatenando " + clips.size() + " clipes...");

        activeHandle = transcodeUseCase.executeJob(job, updated -> Platform.runLater(() -> {
            ProcessingProgress p = updated.getProgress();
            if (p != null) {
                progressPercentage.set(p.percentage() / 100.0);
            }
        }));

        activeHandle.completionFuture().thenAccept(done -> Platform.runLater(() -> {
            isProcessing.set(false);
            if (done.getStatus() == JobStatus.COMPLETED) {
                progressPercentage.set(1.0);
                statusMessage.set("Concatenação concluída: " + out.getFileName());
            } else if (done.getStatus() == JobStatus.CANCELLED) {
                statusMessage.set("Concatenação cancelada.");
            } else {
                statusMessage.set("Falha: " + done.getErrorMessage());
            }
        }));
    }

    public void addToQueue() {
        if (clips.size() < 2) return;
        ConcatOperation op = buildOperation();
        List<String> errs = op.validate(null, capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath()));
        if (!errs.isEmpty()) {
            statusMessage.set("Validação falhou: " + errs.get(0));
            return;
        }
        Path out = Path.of(outputPath.get());
        ProcessingJob job = ProcessingJob.create(op, clips.get(0).path(), out, clips.get(0).metadata());
        queueUseCase.addJob(job);
        statusMessage.set("Tarefa de concatenação adicionada à fila!");
    }

    public void cancel() {
        if (activeHandle != null && isProcessing.get()) {
            activeHandle.cancel();
        }
    }

    public ObservableList<MediaFile> getClips() { return clips; }
    public ObjectProperty<ConcatMode> selectedModeProperty() { return selectedMode; }
    public ObjectProperty<ContainerFormat> targetContainerProperty() { return targetContainer; }
    public ObjectProperty<ResolutionPreset> targetResolutionProperty() { return targetResolution; }
    public StringProperty outputPathProperty() { return outputPath; }
    public StringProperty totalDurationTextProperty() { return totalDurationText; }
    public StringProperty compatibilityMessageProperty() { return compatibilityMessage; }
    public BooleanProperty isCompatibleProperty() { return isCompatible; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
}
