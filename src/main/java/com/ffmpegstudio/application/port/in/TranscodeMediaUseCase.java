package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.ProcessingJob;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface TranscodeMediaUseCase {
    TranscodeExecutionHandle executeJob(ProcessingJob job, Consumer<ProcessingJob> onProgressUpdate);

    interface TranscodeExecutionHandle {
        CompletableFuture<ProcessingJob> completionFuture();
        void cancel();
        boolean isRunning();
    }
}
