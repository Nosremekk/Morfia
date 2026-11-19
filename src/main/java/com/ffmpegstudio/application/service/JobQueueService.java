package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.ProcessingJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class JobQueueService implements ManageQueueUseCase {
    private static final Logger logger = LoggerFactory.getLogger(JobQueueService.class);

    private final TranscodeMediaUseCase transcodeUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository historyRepository;

    private final List<ProcessingJob> jobs = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, TranscodeMediaUseCase.TranscodeExecutionHandle> activeHandles = new ConcurrentHashMap<>();
    private final List<QueueChangeListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean isRunning = false;

    public JobQueueService(TranscodeMediaUseCase transcodeUseCase, ManageSettingsUseCase settingsUseCase) {
        this(transcodeUseCase, settingsUseCase, null);
    }

    public JobQueueService(TranscodeMediaUseCase transcodeUseCase,
                           ManageSettingsUseCase settingsUseCase,
                           com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository historyRepository) {
        this.transcodeUseCase = Objects.requireNonNull(transcodeUseCase, "transcodeUseCase must not be null");
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase, "settingsUseCase must not be null");
        this.historyRepository = historyRepository;
        if (this.historyRepository != null) {
            this.historyRepository.pruneOldHistory(settingsUseCase.getSettings().logRetentionDays());
            restorePersistedQueue();
        }
    }

    public synchronized void restorePersistedQueue() {
        if (historyRepository == null) return;
        var entries = historyRepository.loadPendingQueue();
        for (var entry : entries) {
            historyRepository.reconstructJob(entry).ifPresent(jobs::add);
        }
        if (!jobs.isEmpty()) {
            logger.info("Restored {} persisted job(s) from previous session.", jobs.size());
        }
    }

    public synchronized List<ProcessingJob> getInterruptedJobs() {
        return jobs.stream().filter(j -> j.getStatus() == JobStatus.INTERRUPTED).toList();
    }

    public synchronized void requeueInterruptedJobs() {
        boolean changed = false;
        for (ProcessingJob j : jobs) {
            if (j.getStatus() == JobStatus.INTERRUPTED) {
                j.requeueFromInterrupted();
                changed = true;
            }
        }
        if (changed) {
            persistQueueState();
            notifyQueueChanged();
        }
    }

    public synchronized void discardInterruptedJobs() {
        if (jobs.removeIf(j -> j.getStatus() == JobStatus.INTERRUPTED)) {
            persistQueueState();
            notifyQueueChanged();
        }
    }

    public synchronized ProcessingJob duplicateJob(ProcessingJob original) {
        Objects.requireNonNull(original);
        java.nio.file.Path out = original.getTargetOutputPath();
        String name = out.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        java.nio.file.Path copyOut = out.getParent() != null
                ? out.getParent().resolve(base + "_copy_" + System.currentTimeMillis() % 10000 + ext)
                : java.nio.file.Path.of(base + "_copy_" + System.currentTimeMillis() % 10000 + ext);
        ProcessingJob dup = ProcessingJob.create(original.getOperation(), original.getInputPath(), copyOut,
                original.getSourceMedia() != null ? original.getSourceMedia().metadata() : null);
        addJob(dup);
        return dup;
    }

    public com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository getHistoryRepository() {
        return historyRepository;
    }

    private void persistQueueState() {
        if (historyRepository == null) return;
        var pendingOrActive = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING || j.getStatus() == JobStatus.INTERRUPTED)
                .map(j -> historyRepository.toEntry(j, null))
                .toList();
        historyRepository.savePendingQueue(pendingOrActive);
    }

    private void archiveFinishedJob(ProcessingJob job) {
        if (historyRepository == null || job == null) return;
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED || job.getStatus() == JobStatus.CANCELLED) {
            historyRepository.appendHistory(historyRepository.toEntry(job, null));
        }
        persistQueueState();
    }

    @Override
    public synchronized void addJob(ProcessingJob job) {
        Objects.requireNonNull(job, "job must not be null");
        validateNoDestinationCollision(job);
        jobs.add(job);
        persistQueueState();
        logger.info("Added job {} to queue (Total: {})", job.getId(), jobs.size());
        notifyQueueChanged();
        if (isRunning) {
            dispatchNext();
        }
    }

    @Override
    public synchronized void addJobs(List<ProcessingJob> newJobs) {
        if (newJobs == null || newJobs.isEmpty()) return;
        for (ProcessingJob job : newJobs) {
            Objects.requireNonNull(job, "job must not be null");
            validateNoDestinationCollision(job);
            jobs.add(job);
        }
        logger.info("Batch added {} jobs to queue (Total: {})", newJobs.size(), jobs.size());
        notifyQueueChanged();
        if (isRunning) {
            dispatchNext();
        }
    }

    private void validateNoDestinationCollision(ProcessingJob newJob) {
        java.nio.file.Path normalizedTarget = newJob.getTargetOutputPath().toAbsolutePath().normalize();
        for (ProcessingJob existing : jobs) {
            if (existing.getStatus() == JobStatus.PENDING || existing.getStatus() == JobStatus.RUNNING) {
                java.nio.file.Path existingTarget = existing.getTargetOutputPath().toAbsolutePath().normalize();
                if (existingTarget.equals(normalizedTarget)) {
                    throw new IllegalArgumentException("Colisão de destino: outra tarefa ativa ou pendente já aponta para " + normalizedTarget);
                }
            }
        }
    }

    @Override
    public synchronized boolean removeJob(String jobId) {
        if (jobId == null) return false;
        ProcessingJob found = findJob(jobId);
        if (found != null && found.getStatus() == JobStatus.PENDING) {
            jobs.remove(found);
            notifyQueueChanged();
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean moveJobUp(String jobId) {
        int idx = indexOf(jobId);
        if (idx > 0 && jobs.get(idx).getStatus() == JobStatus.PENDING && jobs.get(idx - 1).getStatus() == JobStatus.PENDING) {
            Collections.swap(jobs, idx, idx - 1);
            notifyQueueChanged();
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean moveJobDown(String jobId) {
        int idx = indexOf(jobId);
        if (idx >= 0 && idx < jobs.size() - 1 && jobs.get(idx).getStatus() == JobStatus.PENDING && jobs.get(idx + 1).getStatus() == JobStatus.PENDING) {
            Collections.swap(jobs, idx, idx + 1);
            notifyQueueChanged();
            return true;
        }
        return false;
    }

    @Override
    public synchronized void startQueue() {
        if (!isRunning) {
            isRunning = true;
            logger.info("Queue execution started.");
            dispatchNext();
            notifyQueueChanged();
        }
    }

    @Override
    public synchronized void pauseQueue() {
        isRunning = false;
        logger.info("Queue execution paused.");
        notifyQueueChanged();
    }

    @Override
    public synchronized void cancelJob(String jobId) {
        if (jobId == null) return;
        ProcessingJob job = findJob(jobId);
        if (job == null) return;

        if (job.getStatus() == JobStatus.RUNNING) {
            var handle = activeHandles.get(jobId);
            if (handle != null) {
                handle.cancel();
            }
        } else if (job.getStatus() == JobStatus.PENDING) {
            job.cancel();
            notifyJobUpdated(job);
        }
    }

    @Override
    public synchronized void cancelAll() {
        logger.info("Cancelling all jobs in queue...");
        pauseQueue();

        // 1. Cancel active running handles
        List<TranscodeMediaUseCase.TranscodeExecutionHandle> handles = new ArrayList<>(activeHandles.values());
        activeHandles.clear();
        for (var handle : handles) {
            try {
                handle.cancel();
            } catch (Exception e) {
                logger.warn("Error cancelling execution handle", e);
            }
        }

        // 2. Cancel all pending jobs
        for (ProcessingJob job : jobs) {
            if (job.getStatus() == JobStatus.PENDING) {
                job.cancel();
                notifyJobUpdated(job);
            }
        }
    }

    @Override
    public synchronized void clearFinished() {
        jobs.removeIf(j -> j.getStatus() == JobStatus.COMPLETED || j.getStatus() == JobStatus.FAILED || j.getStatus() == JobStatus.CANCELLED);
        notifyQueueChanged();
    }

    @Override
    public List<ProcessingJob> getAllJobs() {
        return List.copyOf(jobs);
    }

    @Override
    public boolean isQueueRunning() {
        return isRunning;
    }

    @Override
    public int getActiveJobsCount() {
        return activeHandles.size();
    }

    @Override
    public double getAggregateProgress() {
        if (jobs.isEmpty()) return 0.0;
        double sum = 0.0;
        for (ProcessingJob j : jobs) {
            if (j.getStatus() == JobStatus.COMPLETED) {
                sum += 100.0;
            } else if (j.getStatus() == JobStatus.RUNNING) {
                sum += j.getProgress().percentage();
            }
        }
        return sum / jobs.size();
    }

    private synchronized void dispatchNext() {
        if (!isRunning) return;

        int maxConcurrency = Math.max(1, settingsUseCase.getSettings().maxConcurrentJobs());

        while (activeHandles.size() < maxConcurrency) {
            ProcessingJob nextPending = null;
            for (ProcessingJob j : jobs) {
                if (j.getStatus() == JobStatus.PENDING) {
                    nextPending = j;
                    break;
                }
            }

            if (nextPending == null) {
                if (activeHandles.isEmpty()) {
                    logger.info("All jobs in queue completed.");
                    isRunning = false;
                    notifyQueueChanged();
                }
                break;
            }

            ProcessingJob jobToRun = nextPending;
            String jobId = jobToRun.getId();

            TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeUseCase.executeJob(jobToRun, updatedJob -> {
                if (updatedJob.getStatus() == JobStatus.RUNNING) {
                    notifyJobUpdated(updatedJob);
                }
            });

            activeHandles.put(jobId, handle);
            persistQueueState();
            notifyJobUpdated(jobToRun);

            handle.completionFuture().whenComplete((completedJob, throwable) -> {
                activeHandles.remove(jobId);
                ProcessingJob fin = completedJob != null ? completedJob : jobToRun;
                archiveFinishedJob(fin);
                notifyJobUpdated(fin);
                dispatchNext();
            });
        }
    }

    private ProcessingJob findJob(String jobId) {
        for (ProcessingJob j : jobs) {
            if (j.getId().equals(jobId)) return j;
        }
        return null;
    }

    private int indexOf(String jobId) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).getId().equals(jobId)) return i;
        }
        return -1;
    }

    @Override
    public void addListener(QueueChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeListener(QueueChangeListener listener) {
        if (listener != null) listeners.remove(listener);
    }

    private void notifyQueueChanged() {
        for (QueueChangeListener l : listeners) {
            try {
                l.onQueueChanged();
            } catch (Exception e) {
                logger.debug("Error notifying queue change listener: {}", e.getMessage());
            }
        }
    }

    private void notifyJobUpdated(ProcessingJob job) {
        for (QueueChangeListener l : listeners) {
            try {
                l.onJobUpdated(job);
            } catch (Exception e) {
                logger.debug("Error notifying job update listener: {}", e.getMessage());
            }
        }
    }
}
