package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Task representing a media processing job with state machine lifecycle validation.
 */
public class ProcessingJob {
    private final String id;
    private final Path inputPath;
    private final Path targetOutputPath;
    private final Path temporaryOutputPath;
    private final MediaOperation operation;
    private final MediaFile sourceMedia;

    private JobStatus status;
    private ProcessingProgress progress;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
    private final List<String> logLines;

    public ProcessingJob(Path inputPath, Path targetOutputPath, Path temporaryOutputPath, ConversionSettings settings) {
        this(inputPath, targetOutputPath, temporaryOutputPath, new TranscodeOperation(settings), null);
    }

    public ProcessingJob(Path inputPath, Path targetOutputPath, Path temporaryOutputPath, MediaOperation operation) {
        this(inputPath, targetOutputPath, temporaryOutputPath, operation, null);
    }

    public ProcessingJob(MediaFile sourceMedia, Path targetOutputPath, Path temporaryOutputPath, MediaOperation operation) {
        this(sourceMedia != null ? sourceMedia.path() : null, targetOutputPath, temporaryOutputPath, operation, sourceMedia);
    }

    public ProcessingJob(Path inputPath, Path targetOutputPath, Path temporaryOutputPath, MediaOperation operation, MediaFile sourceMedia) {
        this.id = UUID.randomUUID().toString();
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath must not be null");
        this.targetOutputPath = Objects.requireNonNull(targetOutputPath, "targetOutputPath must not be null");
        this.temporaryOutputPath = temporaryOutputPath;
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
        this.sourceMedia = sourceMedia;

        this.status = JobStatus.PENDING;
        this.progress = ProcessingProgress.initial();
        this.createdAt = Instant.now();
        this.logLines = Collections.synchronizedList(new ArrayList<>());
    }

    public static ProcessingJob create(MediaOperation operation, Path inputPath, Path outputPath, MediaMetadata metadata) {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        Path tempPath = outputPath.getParent() != null
                ? outputPath.getParent().resolve(".~tmp_" + shortId + "_" + outputPath.getFileName().toString())
                : Path.of(".~tmp_" + shortId + "_" + outputPath.getFileName().toString());
        MediaFile mediaFile = new MediaFile(inputPath, inputPath.getFileName().toString(), 0, metadata);
        return new ProcessingJob(inputPath, outputPath, tempPath, operation, mediaFile);
    }

    public Path getInputFile() { return inputPath; }
    public Path getOutputFile() { return targetOutputPath; }

    public synchronized void start() {
        if (status != JobStatus.PENDING) {
            throw new IllegalStateException("Cannot start job from state " + status + ". Expected PENDING.");
        }
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public synchronized void updateProgress(ProcessingProgress newProgress) {
        if (status == JobStatus.RUNNING) {
            this.progress = Objects.requireNonNull(newProgress, "progress must not be null");
        }
    }

    public synchronized void addLog(String line) {
        if (line != null) {
            if (logLines.size() >= 1000) {
                logLines.remove(0);
            }
            logLines.add(line);
        }
    }

    public synchronized void markInterrupted() {
        if (status == JobStatus.RUNNING || status == JobStatus.PENDING) {
            this.status = JobStatus.INTERRUPTED;
            this.errorMessage = "Execução interrompida pelo encerramento anterior da aplicação.";
            this.finishedAt = Instant.now();
        }
    }

    public synchronized void requeueFromInterrupted() {
        if (status != JobStatus.INTERRUPTED) {
            throw new IllegalStateException("Can only requeue from INTERRUPTED state, current: " + status);
        }
        this.status = JobStatus.PENDING;
        this.progress = ProcessingProgress.initial();
        this.errorMessage = null;
        this.startedAt = null;
        this.finishedAt = null;
    }

    public synchronized void complete() {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Cannot complete job from state " + status + ". Expected RUNNING.");
        }
        this.status = JobStatus.COMPLETED;
        this.progress = ProcessingProgress.completed();
        this.finishedAt = Instant.now();
    }

    public synchronized void fail(String error) {
        if (status == JobStatus.COMPLETED || status == JobStatus.CANCELLED) {
            throw new IllegalStateException("Cannot fail job from terminal state " + status);
        }
        this.status = JobStatus.FAILED;
        this.errorMessage = error == null ? "Unknown processing failure" : error;
        this.finishedAt = Instant.now();
    }

    public synchronized void cancel() {
        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
            throw new IllegalStateException("Cannot cancel job from terminal state " + status);
        }
        this.status = JobStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public Path getInputPath() { return inputPath; }
    public Path getTargetOutputPath() { return targetOutputPath; }
    public Path getTemporaryOutputPath() { return temporaryOutputPath; }
    public MediaOperation getOperation() { return operation; }
    public MediaFile getSourceMedia() { return sourceMedia; }

    public ConversionSettings getSettings() {
        if (operation instanceof TranscodeOperation t) {
            return t.settings();
        }
        return ConversionSettings.defaultMp4();
    }

    public synchronized JobStatus getStatus() { return status; }
    public synchronized ProcessingProgress getProgress() { return progress; }
    public Instant getCreatedAt() { return createdAt; }
    public synchronized Instant getStartedAt() { return startedAt; }
    public synchronized Instant getFinishedAt() { return finishedAt; }
    public synchronized String getErrorMessage() { return errorMessage; }
    public List<String> getLogLines() { return List.copyOf(logLines); }
}
