package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.TranscodeOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobQueueService Unit Tests")
class JobQueueServiceTest {

    static class FakeSettingsService implements ManageSettingsUseCase {
        private AppSettings settings = new AppSettings("/usr/bin/ffmpeg", "/usr/bin/ffprobe", "/tmp", "DARK", 2);

        @Override
        public AppSettings getSettings() { return settings; }
        @Override
        public void updateSettings(AppSettings newSettings) { this.settings = newSettings; }
        @Override
        public AppSettings resetToDefaults() {
            this.settings = AppSettings.createDefault();
            return this.settings;
        }
    }

    static class ControlledFakeTranscodeUseCase implements TranscodeMediaUseCase {
        @Override
        public TranscodeExecutionHandle executeJob(ProcessingJob job, Consumer<ProcessingJob> onProgressUpdate) {
            job.start();
            CompletableFuture<ProcessingJob> future = new CompletableFuture<>();
            return new TranscodeExecutionHandle() {
                @Override
                public CompletableFuture<ProcessingJob> completionFuture() {
                    return future;
                }

                @Override
                public void cancel() {
                    job.cancel();
                    future.complete(job);
                }

                @Override
                public boolean isRunning() {
                    return job.getStatus() == JobStatus.RUNNING;
                }
            };
        }
    }

    @Test
    @DisplayName("Should add, reorder and remove jobs in queue")
    void shouldAddReorderAndRemoveJobs() {
        JobQueueService queue = new JobQueueService(new ControlledFakeTranscodeUseCase(), new FakeSettingsService());

        ProcessingJob job1 = ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in1.mp4"), Paths.get("out1.mp4"), null);
        ProcessingJob job2 = ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in2.mp4"), Paths.get("out2.mp4"), null);
        ProcessingJob job3 = ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in3.mp4"), Paths.get("out3.mp4"), null);

        queue.addJobs(List.of(job1, job2, job3));
        assertEquals(3, queue.getAllJobs().size());
        assertEquals(job1.getId(), queue.getAllJobs().get(0).getId());

        // Move job2 up
        assertTrue(queue.moveJobUp(job2.getId()));
        assertEquals(job2.getId(), queue.getAllJobs().get(0).getId());
        assertEquals(job1.getId(), queue.getAllJobs().get(1).getId());

        // Move job2 down
        assertTrue(queue.moveJobDown(job2.getId()));
        assertEquals(job1.getId(), queue.getAllJobs().get(0).getId());
        assertEquals(job2.getId(), queue.getAllJobs().get(1).getId());

        // Remove job3
        assertTrue(queue.removeJob(job3.getId()));
        assertEquals(2, queue.getAllJobs().size());
    }

    @Test
    @DisplayName("Should respect max concurrent jobs limit")
    void shouldRespectMaxConcurrentJobsLimit() {
        JobQueueService queue = new JobQueueService(new ControlledFakeTranscodeUseCase(), new FakeSettingsService());

        for (int i = 0; i < 5; i++) {
            queue.addJob(ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in" + i + ".mp4"), Paths.get("out" + i + ".mp4"), null));
        }

        queue.startQueue();

        assertTrue(queue.isQueueRunning());
        // Max concurrent jobs is 2 in FakeSettingsService
        assertEquals(2, queue.getActiveJobsCount());
    }

    @Test
    @DisplayName("Should cancel all running and pending jobs")
    void shouldCancelAllJobs() {
        JobQueueService queue = new JobQueueService(new ControlledFakeTranscodeUseCase(), new FakeSettingsService());

        for (int i = 0; i < 4; i++) {
            queue.addJob(ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in" + i + ".mp4"), Paths.get("out" + i + ".mp4"), null));
        }

        queue.startQueue();
        queue.cancelAll();

        assertFalse(queue.isQueueRunning());
        assertEquals(0, queue.getActiveJobsCount());
        for (ProcessingJob job : queue.getAllJobs()) {
            assertEquals(JobStatus.CANCELLED, job.getStatus());
        }
    }

    @Test
    @DisplayName("Should reject job with colliding target destination")
    void shouldRejectCollidingDestination() {
        JobQueueService queue = new JobQueueService(new ControlledFakeTranscodeUseCase(), new FakeSettingsService());

        ProcessingJob job1 = ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in1.mp4"), Paths.get("/tmp/same_out.mp4"), null);
        ProcessingJob job2 = ProcessingJob.create(new TranscodeOperation(ConversionSettings.defaultMp4()), Paths.get("in2.mp4"), Paths.get("/tmp/same_out.mp4"), null);

        queue.addJob(job1);
        assertThrows(IllegalArgumentException.class, () -> queue.addJob(job2));
    }
}
