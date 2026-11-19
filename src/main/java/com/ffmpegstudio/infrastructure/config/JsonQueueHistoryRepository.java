package com.ffmpegstudio.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ffmpegstudio.domain.model.AudioToolsOperation;
import com.ffmpegstudio.domain.model.CompressOperation;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.ExtractFramesOperation;
import com.ffmpegstudio.domain.model.GenerateGifOperation;
import com.ffmpegstudio.domain.model.ImageConversionOperation;
import com.ffmpegstudio.domain.model.JobHistoryEntry;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaOperation;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.TranscodeOperation;
import com.ffmpegstudio.domain.model.TrimOperation;
import com.ffmpegstudio.domain.port.QueueHistoryRepositoryPort;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Safe, versioned JSON persistence for active queue state and execution history.
 * Uses atomic temp-file writes and strict schema/operation-type validation.
 */
public class JsonQueueHistoryRepository implements QueueHistoryRepositoryPort {
    private static final Logger logger = LoggerFactory.getLogger(JsonQueueHistoryRepository.class);
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_HISTORY_ENTRIES = 500;

    private final Path queueFile;
    private final Path historyFile;
    private final FileSystemService fileSystemService;
    private final ObjectMapper mapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersistedEnvelope(
            int schemaVersion,
            List<JobHistoryEntry> entries
    ) {
        public PersistedEnvelope {
            if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA_VERSION;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public JsonQueueHistoryRepository(Path queueFile, Path historyFile, FileSystemService fileSystemService) {
        this.queueFile = Objects.requireNonNull(queueFile);
        this.historyFile = Objects.requireNonNull(historyFile);
        this.fileSystemService = Objects.requireNonNull(fileSystemService);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static JsonQueueHistoryRepository defaultLocation(FileSystemService fs) {
        Path configDir = com.ffmpegstudio.infrastructure.platform.PlatformDirectories.current().configDir();
        return new JsonQueueHistoryRepository(
                configDir.resolve("queue-state.json"),
                configDir.resolve("history.json"),
                fs
        );
    }

    @Override
    public synchronized void savePendingQueue(List<JobHistoryEntry> entries) {
        writeAtomic(queueFile, new PersistedEnvelope(CURRENT_SCHEMA_VERSION, entries));
    }

    @Override
    public synchronized List<JobHistoryEntry> loadPendingQueue() {
        return readValidated(queueFile);
    }

    @Override
    public synchronized void appendHistory(JobHistoryEntry entry) {
        if (entry == null || !entry.isValidSchema()) return;
        List<JobHistoryEntry> current = new ArrayList<>(readValidated(historyFile));
        current.removeIf(e -> e.id().equals(entry.id()));
        current.add(0, entry);
        if (current.size() > MAX_HISTORY_ENTRIES) {
            current = current.subList(0, MAX_HISTORY_ENTRIES);
        }
        writeAtomic(historyFile, new PersistedEnvelope(CURRENT_SCHEMA_VERSION, current));
    }

    @Override
    public synchronized List<JobHistoryEntry> loadHistory() {
        return readValidated(historyFile);
    }

    @Override
    public synchronized void deleteHistoryEntry(String jobId) {
        if (jobId == null) return;
        List<JobHistoryEntry> current = new ArrayList<>(readValidated(historyFile));
        if (current.removeIf(e -> e.id().equals(jobId))) {
            writeAtomic(historyFile, new PersistedEnvelope(CURRENT_SCHEMA_VERSION, current));
        }
    }

    @Override
    public synchronized void clearHistory() {
        writeAtomic(historyFile, new PersistedEnvelope(CURRENT_SCHEMA_VERSION, List.of()));
    }

    @Override
    public synchronized void pruneOldHistory(int maxDays) {
        if (maxDays <= 0) return;
        Instant cutoff = Instant.now().minus(maxDays, ChronoUnit.DAYS);
        List<JobHistoryEntry> current = new ArrayList<>(readValidated(historyFile));
        if (current.removeIf(e -> e.createdAt() != null && e.createdAt().isBefore(cutoff))) {
            writeAtomic(historyFile, new PersistedEnvelope(CURRENT_SCHEMA_VERSION, current));
        }
    }

    public JobHistoryEntry toEntry(ProcessingJob job, String logFilePath) {
        MediaOperation op = job.getOperation();
        String opType = resolveOperationType(op);
        String snapshot = "";
        try {
            if (op instanceof TranscodeOperation t) {
                snapshot = mapper.writeValueAsString(t.settings());
            } else if (op instanceof CompressOperation || op instanceof AudioToolsOperation
                    || op instanceof TrimOperation || op instanceof GenerateGifOperation
                    || op instanceof ExtractFramesOperation || op instanceof ImageConversionOperation) {
                snapshot = mapper.writeValueAsString(op);
            }
        } catch (Exception e) {
            logger.warn("Could not serialize settings snapshot for job {}", job.getId());
        }

        List<String> inputs = List.of(job.getInputPath().toAbsolutePath().toString());
        return new JobHistoryEntry(
                job.getId(),
                opType,
                op.name(),
                op.description(),
                inputs,
                job.getTargetOutputPath().toAbsolutePath().toString(),
                job.getTemporaryOutputPath() != null ? job.getTemporaryOutputPath().toAbsolutePath().toString() : null,
                job.getStatus(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorMessage(),
                logFilePath,
                snapshot
        );
    }

    public Optional<ProcessingJob> reconstructJob(JobHistoryEntry entry) {
        if (entry == null || !entry.isValidSchema() || entry.inputPaths().isEmpty()) {
            return Optional.empty();
        }
        try {
            MediaOperation op = deserializeTypedOperation(entry.operationType(), entry.settingsSnapshotJson());
            if (op == null) return Optional.empty();

            Path input = Paths.get(entry.inputPaths().get(0));
            Path output = Paths.get(entry.outputPath());
            ProcessingJob job = ProcessingJob.create(op, input, output, null);
            if (entry.status() == JobStatus.RUNNING || entry.status() == JobStatus.INTERRUPTED) {
                job.markInterrupted();
            }
            return Optional.of(job);
        } catch (Exception e) {
            logger.warn("Skipping invalid persisted job {}: {}", entry.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private MediaOperation deserializeTypedOperation(String opType, String json) throws IOException {
        if (json == null || json.isBlank()) return null;
        return switch (opType) {
            case "TRANSCODE" -> new TranscodeOperation(mapper.readValue(json, ConversionSettings.class));
            case "COMPRESS" -> mapper.readValue(json, CompressOperation.class);
            case "AUDIO_TOOLS" -> mapper.readValue(json, AudioToolsOperation.class);
            case "TRIM" -> mapper.readValue(json, TrimOperation.class);
            case "GIF" -> mapper.readValue(json, GenerateGifOperation.class);
            case "EXTRACT_FRAMES" -> mapper.readValue(json, ExtractFramesOperation.class);
            case "IMAGE_CONVERT" -> mapper.readValue(json, ImageConversionOperation.class);
            default -> null;
        };
    }

    private String resolveOperationType(MediaOperation op) {
        return switch (op) {
            case TranscodeOperation ignored -> "TRANSCODE";
            case CompressOperation ignored -> "COMPRESS";
            case AudioToolsOperation ignored -> "AUDIO_TOOLS";
            case TrimOperation ignored -> "TRIM";
            case GenerateGifOperation ignored -> "GIF";
            case ExtractFramesOperation ignored -> "EXTRACT_FRAMES";
            case ImageConversionOperation ignored -> "IMAGE_CONVERT";
            default -> switch (op.getClass().getSimpleName()) {
                case "PeriodicFramesOperation" -> "PERIODIC_FRAMES";
                case "ConcatOperation" -> "CONCAT";
                case "SubtitleOperation" -> "SUBTITLE";
                case "FilterOperation" -> "FILTER";
                case "StreamOperation" -> "STREAM";
                case "MetadataOperation" -> "METADATA";
                default -> "TRANSCODE";
            };
        };
    }

    private List<JobHistoryEntry> readValidated(Path path) {
        if (!Files.exists(path)) return List.of();
        try {
            PersistedEnvelope env = mapper.readValue(path.toFile(), PersistedEnvelope.class);
            if (env == null || env.entries() == null) return List.of();
            return env.entries().stream().filter(JobHistoryEntry::isValidSchema).toList();
        } catch (Exception e) {
            logger.error("Failed to read persisted file {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    private void writeAtomic(Path target, PersistedEnvelope envelope) {
        try {
            fileSystemService.ensureDirectoryExists(target.getParent());
            Path tmp = target.getParent().resolve(".~tmp_" + target.getFileName().toString());
            mapper.writeValue(tmp.toFile(), envelope);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to write persisted file {}: {}", target, e.getMessage());
        }
    }
}
