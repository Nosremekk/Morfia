package com.ffmpegstudio.application.port.in;

import com.ffmpegstudio.domain.model.ProcessingJob;

import java.util.List;
import java.util.function.Consumer;

public interface ManageQueueUseCase {

    void addJob(ProcessingJob job);
    void addJobs(List<ProcessingJob> jobs);
    boolean removeJob(String jobId);
    boolean moveJobUp(String jobId);
    boolean moveJobDown(String jobId);

    void startQueue();
    void pauseQueue();
    void cancelJob(String jobId);
    void cancelAll();
    void clearFinished();

    List<ProcessingJob> getAllJobs();
    boolean isQueueRunning();
    int getActiveJobsCount();
    double getAggregateProgress();

    void addListener(QueueChangeListener listener);
    void removeListener(QueueChangeListener listener);

    interface QueueChangeListener {
        void onQueueChanged();
        void onJobUpdated(ProcessingJob job);
    }
}
