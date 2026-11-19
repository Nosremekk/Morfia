package com.ffmpegstudio.domain.port;

import com.ffmpegstudio.domain.model.JobHistoryEntry;

import java.util.List;

public interface QueueHistoryRepositoryPort {
    void savePendingQueue(List<JobHistoryEntry> entries);
    List<JobHistoryEntry> loadPendingQueue();

    void appendHistory(JobHistoryEntry entry);
    List<JobHistoryEntry> loadHistory();
    void deleteHistoryEntry(String jobId);
    void clearHistory();
    void pruneOldHistory(int maxDays);
}
