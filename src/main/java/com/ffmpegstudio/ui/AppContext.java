package com.ffmpegstudio.ui;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManagePresetsUseCase;
import com.ffmpegstudio.application.port.in.ManageQueueUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.AnalyzeMediaService;
import com.ffmpegstudio.application.service.DetectCapabilitiesService;
import com.ffmpegstudio.application.service.JobQueueService;
import com.ffmpegstudio.application.service.PresetService;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.infrastructure.config.JsonPresetRepository;
import com.ffmpegstudio.infrastructure.config.JsonSettingsRepository;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegCapabilityDetector;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import com.ffmpegstudio.infrastructure.ffmpeg.FFprobeClient;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;

/**
 * Composition root for FFmpeg Studio services.
 */
public class AppContext {
    private static AppContext instance;

    private final FileSystemService fileSystemService;
    private final FFmpegProcessExecutor processExecutor;
    private final FFmpegCapabilityDetector capabilityDetector;
    private final FFprobeClient ffprobeClient;
    private final JsonSettingsRepository settingsRepository;
    private final JsonPresetRepository presetRepository;
    private final SettingsService settingsService;
    private final DetectCapabilitiesService capabilitiesService;
    private final PresetService presetService;
    private final AnalyzeMediaService analyzeMediaService;
    private final TranscodeMediaService transcodeMediaService;
    private final com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository queueHistoryRepository;
    private final JobQueueService queueService;
    private final com.ffmpegstudio.application.service.FilterPreviewService filterPreviewService;
    private final com.ffmpegstudio.application.service.MediaPreviewService mediaPreviewService;
    private final com.ffmpegstudio.application.service.MediaLibraryService mediaLibraryService;
    private final com.ffmpegstudio.application.service.HardwareAccelerationService hardwareAccelerationService;
    private final com.ffmpegstudio.application.service.DiagnosticsService diagnosticsService;

    public AppContext() {
        this.fileSystemService = new FileSystemService();
        this.processExecutor = new FFmpegProcessExecutor();
        this.capabilityDetector = new FFmpegCapabilityDetector(processExecutor, fileSystemService);
        this.ffprobeClient = new FFprobeClient(processExecutor);
        this.settingsRepository = JsonSettingsRepository.defaultLocation(fileSystemService);
        this.presetRepository = JsonPresetRepository.defaultLocation(fileSystemService);
        this.settingsService = new SettingsService(settingsRepository);
        this.capabilitiesService = new DetectCapabilitiesService(capabilityDetector);
        this.presetService = new PresetService(presetRepository);
        this.analyzeMediaService = new AnalyzeMediaService(ffprobeClient, settingsService);
        this.transcodeMediaService = new TranscodeMediaService(processExecutor, settingsService, capabilityDetector);
        this.queueHistoryRepository = com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository.defaultLocation(fileSystemService);
        this.queueService = new JobQueueService(transcodeMediaService, settingsService, queueHistoryRepository);
        this.filterPreviewService = new com.ffmpegstudio.application.service.FilterPreviewService(processExecutor, settingsService);
        this.mediaPreviewService = new com.ffmpegstudio.application.service.MediaPreviewService(processExecutor, settingsService);
        this.mediaLibraryService = new com.ffmpegstudio.application.service.MediaLibraryService(analyzeMediaService);
        this.hardwareAccelerationService = new com.ffmpegstudio.application.service.HardwareAccelerationService(processExecutor, capabilitiesService, settingsService);
        this.diagnosticsService = new com.ffmpegstudio.application.service.DiagnosticsService(capabilitiesService, settingsService, hardwareAccelerationService);
    }

    public static synchronized AppContext getInstance() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    public static synchronized void setInstance(AppContext customContext) {
        instance = customContext;
    }

    public FileSystemService getFileSystemService() {
        return fileSystemService;
    }

    public FFmpegProcessExecutor getProcessExecutor() {
        return processExecutor;
    }

    public FFmpegCapabilityDetector getCapabilityDetector() {
        return capabilityDetector;
    }

    public FFprobeClient getFfprobeClient() {
        return ffprobeClient;
    }

    public DetectCapabilitiesUseCase getCapabilitiesService() {
        return capabilitiesService;
    }

    public ManageSettingsUseCase getSettingsService() {
        return settingsService;
    }

    public ManagePresetsUseCase getPresetService() {
        return presetService;
    }

    public AnalyzeMediaUseCase getAnalyzeMediaService() {
        return analyzeMediaService;
    }

    public TranscodeMediaUseCase getTranscodeMediaService() {
        return transcodeMediaService;
    }

    public ManageQueueUseCase getQueueService() {
        return queueService;
    }

    public com.ffmpegstudio.application.service.FilterPreviewService getFilterPreviewService() {
        return filterPreviewService;
    }

    public com.ffmpegstudio.application.service.HardwareAccelerationService getHardwareAccelerationService() {
        return hardwareAccelerationService;
    }

    public com.ffmpegstudio.application.service.MediaPreviewService getMediaPreviewService() {
        return mediaPreviewService;
    }

    public com.ffmpegstudio.application.service.MediaLibraryService getMediaLibraryService() {
        return mediaLibraryService;
    }

    public com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository getQueueHistoryRepository() {
        return queueHistoryRepository;
    }

    public com.ffmpegstudio.application.service.DiagnosticsService getDiagnosticsService() {
        return diagnosticsService;
    }

    public void shutdown() {
        try { queueService.cancelAll(); } catch (Exception ignored) {}
        try { filterPreviewService.clearCache(); } catch (Exception ignored) {}
        try { mediaPreviewService.shutdown(); } catch (Exception ignored) {}
    }
}
