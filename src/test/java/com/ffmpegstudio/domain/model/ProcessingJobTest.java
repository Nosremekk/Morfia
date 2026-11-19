package com.ffmpegstudio.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessingJobTest {

    private final Path in = Paths.get("/tmp/in.mp4");
    private final Path out = Paths.get("/tmp/out.mkv");
    private final Path temp = Paths.get("/tmp/.~tmp_out.mkv");

    @Test
    @DisplayName("Should follow normal lifecycle: PENDING -> RUNNING -> COMPLETED")
    void shouldFollowNormalLifecycle() {
        ProcessingJob job = new ProcessingJob(in, out, temp, ConversionSettings.defaultMp4());
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);

        job.start();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();

        job.complete();
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getProgress().percentage()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should transition from RUNNING to FAILED with error message")
    void shouldHandleFailureTransition() {
        ProcessingJob job = new ProcessingJob(in, out, temp, ConversionSettings.defaultMp4());
        job.start();
        job.fail("Disk full error");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("Disk full error");
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should transition from PENDING or RUNNING to CANCELLED")
    void shouldHandleCancellation() {
        ProcessingJob job = new ProcessingJob(in, out, temp, ConversionSettings.defaultMp4());
        job.start();
        job.cancel();

        assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should reject invalid state transitions")
    void shouldRejectInvalidTransitions() {
        ProcessingJob job = new ProcessingJob(in, out, temp, ConversionSettings.defaultMp4());

        // Cannot complete from PENDING
        assertThatThrownBy(job::complete).isInstanceOf(IllegalStateException.class);

        job.start();
        job.complete();

        // Terminal state cannot be restarted, failed or cancelled
        assertThatThrownBy(job::start).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.fail("err")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(job::cancel).isInstanceOf(IllegalStateException.class);
    }
}
