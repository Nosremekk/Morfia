package com.ffmpegstudio.domain.model;

/**
 * Lifecycle status of an asynchronous processing job.
 */
public enum JobStatus {
    PENDING,
    RUNNING,
    INTERRUPTED,
    COMPLETED,
    FAILED,
    CANCELLED
}
