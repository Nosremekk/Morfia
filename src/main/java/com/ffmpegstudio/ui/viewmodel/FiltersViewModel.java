package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.FilterPreviewService;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.FilterOperation;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.filter.FilterGraphBuilder;
import com.ffmpegstudio.domain.model.filter.FilterValidationResult;
import com.ffmpegstudio.domain.model.filter.MediaFilter;
import com.ffmpegstudio.domain.model.filter.PreviewResult;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.animation.PauseTransition;
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
import javafx.scene.image.Image;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class FiltersViewModel {

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final FilterPreviewService previewService;
    private final FilterGraphBuilder graphBuilder = new FilterGraphBuilder();

    private final ObjectProperty<MediaFile> currentMedia = new SimpleObjectProperty<>();
    private final ObservableList<MediaFilter> filterChain = FXCollections.observableArrayList();
    private final DoubleProperty previewTimestampSec = new SimpleDoubleProperty(0.0);
    private final DoubleProperty maxDurationSec = new SimpleDoubleProperty(10.0);

    private final ObjectProperty<Image> beforeImage = new SimpleObjectProperty<>();
    private final ObjectProperty<Image> afterImage = new SimpleObjectProperty<>();
    private final StringProperty previewNotice = new SimpleStringProperty("");
    private final StringProperty validationSummary = new SimpleStringProperty("Adicione filtros para compor a cadeia.");
    private final StringProperty impactSummary = new SimpleStringProperty("-");
    private final StringProperty outputPath = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Selecione um arquivo de mídia.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);

    private final PauseTransition previewDebounce = new PauseTransition(javafx.util.Duration.millis(250));
    private TranscodeMediaUseCase.TranscodeExecutionHandle activeHandle;

    public FiltersViewModel(AnalyzeMediaUseCase analyzeUseCase,
                            TranscodeMediaUseCase transcodeUseCase,
                            ManageQueueUseCase queueUseCase,
                            ManageSettingsUseCase settingsUseCase,
                            DetectCapabilitiesUseCase capabilitiesUseCase,
                            FilterPreviewService previewService) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);
        this.previewService = Objects.requireNonNull(previewService);

        previewDebounce.setOnFinished(e -> triggerPreview());
        previewTimestampSec.addListener((obs, o, n) -> schedulePreview());
    }

    public void loadMedia(Path path) {
        if (path == null || !Files.exists(path)) return;
        statusMessage.set("Analisando mídia...");
        previewService.clearCache();
        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                mf -> {
                    currentMedia.set(mf);
                    if (mf.metadata() != null && mf.metadata().duration() != null) {
                        double secs = Math.max(1.0, mf.metadata().duration().toMillis() / 1000.0);
                        maxDurationSec.set(secs);
                        previewTimestampSec.set(Math.min(1.0, secs / 2.0));
                    }
                    Path dir = mf.path().getParent() != null ? mf.path().getParent() : Path.of(".");
                    outputPath.set(new FilterOperation(filterChain, ConversionSettings.defaultMp4())
                            .getSuggestedOutputPath(mf, dir).toAbsolutePath().toString());
                    statusMessage.set("Mídia carregada.");
                    revalidateAndPreview();
                },
                err -> statusMessage.set("Erro ao analisar: " + err.getMessage())
        );
    }

    public void addFilter(MediaFilter filter) {
        if (filter == null) return;
        filterChain.add(filter);
        revalidateAndPreview();
    }

    public void moveUp(int index) {
        if (index > 0 && index < filterChain.size()) {
            MediaFilter f = filterChain.remove(index);
            filterChain.add(index - 1, f);
            revalidateAndPreview();
        }
    }

    public void moveDown(int index) {
        if (index >= 0 && index < filterChain.size() - 1) {
            MediaFilter f = filterChain.remove(index);
            filterChain.add(index + 1, f);
            revalidateAndPreview();
        }
    }

    public void removeAt(int index) {
        if (index >= 0 && index < filterChain.size()) {
            filterChain.remove(index);
            revalidateAndPreview();
        }
    }

    public void clearFilters() {
        filterChain.clear();
        revalidateAndPreview();
    }

    public void revalidateAndPreview() {
        MediaFile mf = currentMedia.get();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        FilterValidationResult res = graphBuilder.validate(filterChain, mf, caps);

        if (!res.errors().isEmpty()) {
            validationSummary.set("Erros: " + String.join(" | ", res.errors()));
        } else if (!res.warnings().isEmpty()) {
            validationSummary.set("Avisos: " + String.join(" | ", res.warnings()));
        } else {
            validationSummary.set("Cadeia válida (" + filterChain.size() + " filtro(s)).");
        }

        String dim = (res.estimatedWidth() != null && res.estimatedHeight() != null)
                ? res.estimatedWidth() + "x" + res.estimatedHeight()
                : "-";
        String dur = res.estimatedDuration() != null ? FormatUtils.formatDuration(res.estimatedDuration()) : "-";
        impactSummary.set("Saída estimada: " + dim + " • Duração: " + dur);

        schedulePreview();
    }

    public void schedulePreview() {
        if (currentMedia.get() != null) {
            previewDebounce.playFromStart();
        }
    }

    private void triggerPreview() {
        MediaFile mf = currentMedia.get();
        if (mf == null) return;
        Duration ts = Duration.ofMillis((long) (previewTimestampSec.get() * 1000));

        previewService.generatePreviewAsync(mf, ts, List.copyOf(filterChain))
                .thenAccept(result -> {
                    if (result == null) return; // stale request discarded
                    Platform.runLater(() -> applyPreviewResult(result));
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> previewNotice.set("Erro na prévia: " + ex.getMessage()));
                    return null;
                });
    }

    private void applyPreviewResult(PreviewResult result) {
        if (result.originalFramePath() != null && Files.exists(result.originalFramePath())) {
            beforeImage.set(new Image(result.originalFramePath().toUri().toString()));
        }
        if (result.filteredFramePath() != null && Files.exists(result.filteredFramePath())) {
            afterImage.set(new Image(result.filteredFramePath().toUri().toString()));
        }
        previewNotice.set(result.notices().isEmpty() ? "" : String.join(" ", result.notices()));
    }

    public void execute() {
        MediaFile mf = currentMedia.get();
        if (mf == null || isProcessing.get()) return;

        FilterOperation op = new FilterOperation(filterChain, ConversionSettings.defaultMp4());
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
        statusMessage.set("Aplicando filtros...");

        activeHandle = transcodeUseCase.executeJob(job, updated -> Platform.runLater(() -> {
            ProcessingProgress p = updated.getProgress();
            if (p != null) progressPercentage.set(p.percentage() / 100.0);
        }));

        activeHandle.completionFuture().thenAccept(done -> Platform.runLater(() -> {
            isProcessing.set(false);
            if (done.getStatus() == JobStatus.COMPLETED) {
                progressPercentage.set(1.0);
                statusMessage.set("Filtros aplicados com sucesso: " + out.getFileName());
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
        FilterOperation op = new FilterOperation(filterChain, ConversionSettings.defaultMp4());
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settingsUseCase.getSettings().ffmpegPath());
        List<String> errs = op.validate(mf, caps);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação: " + String.join(" ", errs));
            return;
        }
        Path out = Path.of(outputPath.get());
        queueUseCase.addJob(ProcessingJob.create(op, mf.path(), out, mf.metadata()));
        statusMessage.set("Pipeline de filtros adicionado à fila!");
    }

    public void cancel() {
        if (activeHandle != null && isProcessing.get()) {
            activeHandle.cancel();
        }
    }

    public ObjectProperty<MediaFile> currentMediaProperty() { return currentMedia; }
    public ObservableList<MediaFilter> getFilterChain() { return filterChain; }
    public DoubleProperty previewTimestampSecProperty() { return previewTimestampSec; }
    public DoubleProperty maxDurationSecProperty() { return maxDurationSec; }
    public ObjectProperty<Image> beforeImageProperty() { return beforeImage; }
    public ObjectProperty<Image> afterImageProperty() { return afterImage; }
    public StringProperty previewNoticeProperty() { return previewNotice; }
    public StringProperty validationSummaryProperty() { return validationSummary; }
    public StringProperty impactSummaryProperty() { return impactSummary; }
    public StringProperty outputPathProperty() { return outputPath; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
}
