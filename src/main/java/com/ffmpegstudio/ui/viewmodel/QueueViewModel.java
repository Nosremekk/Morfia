package com.ffmpegstudio.ui.viewmodel;

import com.ffmpegstudio.application.port.in.ManagePresetsUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.domain.model.ConversionPreset;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.TranscodeOperation;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QueueViewModel {
    private static final Logger logger = LoggerFactory.getLogger(QueueViewModel.class);

    private final ManageQueueUseCase queueUseCase;
    private final ManagePresetsUseCase presetUseCase;

    private final ObservableList<ProcessingJob> jobsList = FXCollections.observableArrayList();
    private final ObservableList<ConversionPreset> availablePresets = FXCollections.observableArrayList();

    private final BooleanProperty isRunning = new SimpleBooleanProperty(false);
    private final IntegerProperty totalJobsCount = new SimpleIntegerProperty(0);
    private final IntegerProperty completedJobsCount = new SimpleIntegerProperty(0);
    private final IntegerProperty activeJobsCount = new SimpleIntegerProperty(0);
    private final IntegerProperty failedJobsCount = new SimpleIntegerProperty(0);
    private final DoubleProperty aggregateProgress = new SimpleDoubleProperty(0.0);
    private final StringProperty statusSummary = new SimpleStringProperty("Fila vazia.");

    public QueueViewModel(ManageQueueUseCase queueUseCase, ManagePresetsUseCase presetUseCase) {
        this.queueUseCase = Objects.requireNonNull(queueUseCase);
        this.presetUseCase = Objects.requireNonNull(presetUseCase);

        initPresets();
        queueUseCase.addListener(new ManageQueueUseCase.QueueChangeListener() {
            @Override
            public void onQueueChanged() {
                refreshFromQueueService();
            }

            @Override
            public void onJobUpdated(ProcessingJob job) {
                refreshFromQueueService();
            }
        });
        refreshFromQueueService();
    }

    private void initPresets() {
        availablePresets.setAll(presetUseCase.getAllPresets());
    }

    public void refreshFromQueueService() {
        Platform.runLater(() -> {
            List<ProcessingJob> all = queueUseCase.getAllJobs();
            jobsList.setAll(all);

            int total = all.size();
            int completed = 0;
            int active = 0;
            int failed = 0;

            for (ProcessingJob job : all) {
                if (job.getStatus() == JobStatus.COMPLETED) completed++;
                else if (job.getStatus() == JobStatus.RUNNING) active++;
                else if (job.getStatus() == JobStatus.FAILED) failed++;
            }

            totalJobsCount.set(total);
            completedJobsCount.set(completed);
            activeJobsCount.set(active);
            failedJobsCount.set(failed);
            isRunning.set(queueUseCase.isQueueRunning());
            aggregateProgress.set(queueUseCase.getAggregateProgress());

            if (total == 0) {
                statusSummary.set("Nenhuma tarefa na fila.");
            } else {
                statusSummary.set(String.format("%d de %d concluídas • %d em andamento • %d com falha",
                        completed, total, active, failed));
            }
        });
    }

    public void addBatchFiles(List<Path> files, ConversionPreset preset) {
        if (files == null || files.isEmpty()) return;

        ConversionSettings settings = preset != null ? preset.settings() : ConversionSettings.defaultMp4();
        TranscodeOperation operation = new TranscodeOperation(settings);

        List<ProcessingJob> newJobs = new ArrayList<>();
        for (Path input : files) {
            String dir = input.getParent() != null ? input.getParent().toString() : ".";
            String name = input.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = settings.container().extension();
            Path output = Path.of(dir, base + "_batch." + ext);

            ProcessingJob job = ProcessingJob.create(operation, input, output, null);
            newJobs.add(job);
        }

        queueUseCase.addJobs(newJobs);
    }

    public void startQueue() {
        queueUseCase.startQueue();
    }

    public void pauseQueue() {
        queueUseCase.pauseQueue();
    }

    public void cancelAll() {
        queueUseCase.cancelAll();
    }

    public void clearFinished() {
        queueUseCase.clearFinished();
    }

    public void moveUp(String jobId) {
        queueUseCase.moveJobUp(jobId);
    }

    public void moveDown(String jobId) {
        queueUseCase.moveJobDown(jobId);
    }

    public void removeJob(String jobId) {
        queueUseCase.removeJob(jobId);
    }

    public void cancelJob(String jobId) {
        queueUseCase.cancelJob(jobId);
    }

    // Properties
    public ObservableList<ProcessingJob> getJobsList() { return jobsList; }
    public ObservableList<ConversionPreset> getAvailablePresets() { return availablePresets; }
    public BooleanProperty isRunningProperty() { return isRunning; }
    public IntegerProperty totalJobsCountProperty() { return totalJobsCount; }
    public IntegerProperty completedJobsCountProperty() { return completedJobsCount; }
    public IntegerProperty activeJobsCountProperty() { return activeJobsCount; }
    public IntegerProperty failedJobsCountProperty() { return failedJobsCount; }
    public DoubleProperty aggregateProgressProperty() { return aggregateProgress; }
    public StringProperty statusSummaryProperty() { return statusSummary; }
}
