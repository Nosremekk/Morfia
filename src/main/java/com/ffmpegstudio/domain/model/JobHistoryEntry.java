package com.ffmpegstudio.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Typed persistent record for queue state and job history entries.
 * Uses explicit operationType discrimination without unrestricted polymorphic deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobHistoryEntry(
        String id,
        String operationType,
        String operationName,
        String operationDescription,
        List<String> inputPaths,
        String outputPath,
        String temporaryPath,
        JobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        String logFilePath,
        String settingsSnapshotJson
) {
    public static final Set<String> ALLOWED_OPERATION_TYPES = Set.of(
            "TRANSCODE", "COMPRESS", "AUDIO_TOOLS", "TRIM", "GIF",
            "EXTRACT_FRAMES", "PERIODIC_FRAMES", "IMAGE_CONVERT",
            "CONCAT", "SUBTITLE", "FILTER", "STREAM", "METADATA"
    );

    public JobHistoryEntry {
        inputPaths = inputPaths == null ? List.of() : List.copyOf(inputPaths);
        status = status == null ? JobStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public boolean isValidSchema() {
        return id != null && !id.isBlank()
                && operationType != null && ALLOWED_OPERATION_TYPES.contains(operationType)
                && outputPath != null && !outputPath.isBlank();
    }
}
