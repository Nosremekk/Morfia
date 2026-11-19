package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.AudioStream;
import com.ffmpegstudio.domain.model.ChapterInfo;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MediaStream;
import com.ffmpegstudio.domain.model.MetadataOperation;
import com.ffmpegstudio.domain.model.OtherStream;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.model.StreamOperation;
import com.ffmpegstudio.domain.model.StreamOperation.StreamSelection;
import com.ffmpegstudio.domain.model.SubtitleStream;
import com.ffmpegstudio.domain.model.VideoStream;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StreamsMetadataViewModel {

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageQueueUseCase queueUseCase;

    private final ObjectProperty<MediaFile> currentMedia = new SimpleObjectProperty<>();
    private final ObservableList<StreamItem> streamItems = FXCollections.observableArrayList();
    private final ObservableList<ChapterInfo> chapters = FXCollections.observableArrayList();
    private final ObjectProperty<ContainerFormat> targetContainer = new SimpleObjectProperty<>(ContainerFormat.MKV);

    // Common Global Metadata Fields
    private final StringProperty tagTitle = new SimpleStringProperty("");
    private final StringProperty tagArtist = new SimpleStringProperty("");
    private final StringProperty tagAlbum = new SimpleStringProperty("");
    private final StringProperty tagDate = new SimpleStringProperty("");
    private final StringProperty tagGenre = new SimpleStringProperty("");
    private final StringProperty tagComment = new SimpleStringProperty("");
    private final StringProperty tagDescription = new SimpleStringProperty("");
    private final StringProperty tagCopyright = new SimpleStringProperty("");
    private final StringProperty tagLanguage = new SimpleStringProperty("");
    private final BooleanProperty clearAllMetadata = new SimpleBooleanProperty(false);
    private final BooleanProperty preserveChapters = new SimpleBooleanProperty(true);

    private final StringProperty outputPath = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Selecione um arquivo de mídia.");
    private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);

    private TranscodeMediaUseCase.TranscodeExecutionHandle activeHandle;

    public static final class StreamItem {
        private final int globalIndex;
        private final String streamType;
        private final String codecName;
        private final String technicalDetails;
        private final BooleanProperty included = new SimpleBooleanProperty(true);
        private final StringProperty title = new SimpleStringProperty("");
        private final StringProperty language = new SimpleStringProperty("und");
        private final BooleanProperty defaultFlag = new SimpleBooleanProperty(false);

        public StreamItem(int globalIndex, String streamType, String codecName, String technicalDetails,
                          String title, String language, boolean defaultFlag) {
            this.globalIndex = globalIndex;
            this.streamType = streamType;
            this.codecName = codecName;
            this.technicalDetails = technicalDetails;
            this.title.set(title != null ? title : "");
            this.language.set(language != null ? language : "und");
            this.defaultFlag.set(defaultFlag);
        }

        public int getGlobalIndex() { return globalIndex; }
        public String getStreamType() { return streamType; }
        public String getCodecName() { return codecName; }
        public String getTechnicalDetails() { return technicalDetails; }
        public BooleanProperty includedProperty() { return included; }
        public StringProperty titleProperty() { return title; }
        public StringProperty languageProperty() { return language; }
        public BooleanProperty defaultFlagProperty() { return defaultFlag; }

        public StreamSelection toSelection() {
            return new StreamSelection(globalIndex, codecName, streamType, included.get(),
                    title.get(), language.get(), defaultFlag.get());
        }
    }

    public StreamsMetadataViewModel(AnalyzeMediaUseCase analyzeUseCase,
                                    TranscodeMediaUseCase transcodeUseCase,
                                    ManageQueueUseCase queueUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase);
        this.queueUseCase = Objects.requireNonNull(queueUseCase);

        targetContainer.addListener((obs, o, n) -> updateSuggestedOutput());
    }

    public void loadMedia(Path path) {
        if (path == null || !Files.exists(path)) return;
        statusMessage.set("Inspecionando streams e metadados...");
        AsyncHelper.supplyAsync(
                () -> analyzeUseCase.analyze(path),
                mf -> {
                    currentMedia.set(mf);
                    populateStreamsAndMetadata(mf);
                    updateSuggestedOutput();
                    statusMessage.set("Inspeção concluída: " + streamItems.size() + " streams encontradas.");
                },
                err -> statusMessage.set("Erro ao analisar: " + err.getMessage())
        );
    }

    private void populateStreamsAndMetadata(MediaFile mf) {
        streamItems.clear();
        chapters.clear();
        if (mf.metadata() == null) return;

        for (MediaStream ms : mf.metadata().streams()) {
            switch (ms) {
                case VideoStream v -> streamItems.add(new StreamItem(
                        v.index(), "video", v.codecName(),
                        v.width() + "x" + v.height() + " • " + String.format("%.2f", v.frameRate()) + " fps • " + v.pixelFormat(),
                        "", v.language(), v.index() == 0));
                case AudioStream a -> streamItems.add(new StreamItem(
                        a.index(), "audio", a.codecName(),
                        a.sampleRate() + " Hz • " + a.channels() + " ch",
                        "", a.language(), false));
                case SubtitleStream s -> streamItems.add(new StreamItem(
                        s.index(), "subtitle", s.codecName(),
                        (s.isDefault() ? "Default " : "") + (s.isForced() ? "Forced" : ""),
                        s.title(), s.language(), s.isDefault()));
                case OtherStream o -> streamItems.add(new StreamItem(
                        o.index(), o.streamType(), o.codecName(),
                        o.filename().isBlank() ? o.mimeType() : o.filename() + " (" + o.mimeType() + ")",
                        o.title(), o.language(), false));
            }
        }

        chapters.setAll(mf.metadata().chapters());

        Map<String, String> tags = mf.metadata().tags();
        tagTitle.set(tags.getOrDefault("title", ""));
        tagArtist.set(tags.getOrDefault("artist", ""));
        tagAlbum.set(tags.getOrDefault("album", ""));
        tagDate.set(tags.getOrDefault("date", ""));
        tagGenre.set(tags.getOrDefault("genre", ""));
        tagComment.set(tags.getOrDefault("comment", ""));
        tagDescription.set(tags.getOrDefault("description", ""));
        tagCopyright.set(tags.getOrDefault("copyright", ""));
        tagLanguage.set(tags.getOrDefault("language", ""));
    }

    private void updateSuggestedOutput() {
        MediaFile mf = currentMedia.get();
        if (mf == null) return;
        Path dir = mf.path().getParent() != null ? mf.path().getParent() : Path.of(".");
        String fn = mf.fileName();
        String base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        String ext = targetContainer.get() != null ? targetContainer.get().extension() : "mkv";
        outputPath.set(dir.resolve(base + "_remux." + ext).toAbsolutePath().toString());
    }

    public void moveStreamUp(int idx) {
        if (idx > 0 && idx < streamItems.size()) {
            StreamItem item = streamItems.remove(idx);
            streamItems.add(idx - 1, item);
        }
    }

    public void moveStreamDown(int idx) {
        if (idx >= 0 && idx < streamItems.size() - 1) {
            StreamItem item = streamItems.remove(idx);
            streamItems.add(idx + 1, item);
        }
    }

    public StreamOperation buildStreamOperation(boolean extractSingle) {
        List<StreamSelection> sels = streamItems.stream().map(StreamItem::toSelection).toList();
        return new StreamOperation(sels, targetContainer.get(), extractSingle);
    }

    public MetadataOperation buildMetadataOperation() {
        Map<String, String> updates = new LinkedHashMap<>();
        List<String> removals = new ArrayList<>();

        putOrRemove(updates, removals, "title", tagTitle.get());
        putOrRemove(updates, removals, "artist", tagArtist.get());
        putOrRemove(updates, removals, "album", tagAlbum.get());
        putOrRemove(updates, removals, "date", tagDate.get());
        putOrRemove(updates, removals, "genre", tagGenre.get());
        putOrRemove(updates, removals, "comment", tagComment.get());
        putOrRemove(updates, removals, "description", tagDescription.get());
        putOrRemove(updates, removals, "copyright", tagCopyright.get());
        putOrRemove(updates, removals, "language", tagLanguage.get());

        return new MetadataOperation(
                clearAllMetadata.get() ? Map.of() : updates,
                removals,
                clearAllMetadata.get(),
                preserveChapters.get(),
                Map.of(),
                targetContainer.get()
        );
    }

    private void putOrRemove(Map<String, String> updates, List<String> removals, String key, String val) {
        if (val != null && !val.isBlank()) {
            updates.put(key, val.trim());
        } else if (currentMedia.get() != null && currentMedia.get().metadata() != null
                && currentMedia.get().metadata().tags().containsKey(key)) {
            removals.add(key);
        }
    }

    public void executeRemux() {
        runOp(buildStreamOperation(false));
    }

    public void executeMetadataSave() {
        runOp(buildMetadataOperation());
    }

    public void queueRemux() {
        queueOp(buildStreamOperation(false));
    }

    public void queueMetadataSave() {
        queueOp(buildMetadataOperation());
    }

    private void runOp(com.ffmpegstudio.domain.model.MediaOperation op) {
        MediaFile mf = currentMedia.get();
        if (mf == null || isProcessing.get()) return;
        List<String> errs = op.validate(mf, null);
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
                statusMessage.set("Concluído com sucesso: " + out.getFileName());
            } else if (done.getStatus() == JobStatus.CANCELLED) {
                statusMessage.set("Cancelado.");
            } else {
                statusMessage.set("Erro: " + done.getErrorMessage());
            }
        }));
    }

    private void queueOp(com.ffmpegstudio.domain.model.MediaOperation op) {
        MediaFile mf = currentMedia.get();
        if (mf == null) return;
        List<String> errs = op.validate(mf, null);
        if (!errs.isEmpty()) {
            statusMessage.set("Validação: " + String.join(" ", errs));
            return;
        }
        Path out = Path.of(outputPath.get());
        queueUseCase.addJob(ProcessingJob.create(op, mf.path(), out, mf.metadata()));
        statusMessage.set("Adicionado à fila: " + op.name());
    }

    public void cancel() {
        if (activeHandle != null && isProcessing.get()) {
            activeHandle.cancel();
        }
    }

    public ObjectProperty<MediaFile> currentMediaProperty() { return currentMedia; }
    public ObservableList<StreamItem> getStreamItems() { return streamItems; }
    public ObservableList<ChapterInfo> getChapters() { return chapters; }
    public ObjectProperty<ContainerFormat> targetContainerProperty() { return targetContainer; }
    public StringProperty tagTitleProperty() { return tagTitle; }
    public StringProperty tagArtistProperty() { return tagArtist; }
    public StringProperty tagAlbumProperty() { return tagAlbum; }
    public StringProperty tagDateProperty() { return tagDate; }
    public StringProperty tagGenreProperty() { return tagGenre; }
    public StringProperty tagCommentProperty() { return tagComment; }
    public StringProperty tagDescriptionProperty() { return tagDescription; }
    public StringProperty tagCopyrightProperty() { return tagCopyright; }
    public StringProperty tagLanguageProperty() { return tagLanguage; }
    public BooleanProperty clearAllMetadataProperty() { return clearAllMetadata; }
    public BooleanProperty preserveChaptersProperty() { return preserveChapters; }
    public StringProperty outputPathProperty() { return outputPath; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public BooleanProperty isProcessingProperty() { return isProcessing; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
}
