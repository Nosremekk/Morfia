package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.AnalyzeMediaService;
import com.ffmpegstudio.application.service.DetectCapabilitiesService;
import com.ffmpegstudio.application.service.DiagnosticsService;
import com.ffmpegstudio.application.service.HardwareAccelerationService;
import com.ffmpegstudio.application.service.JobQueueService;
import com.ffmpegstudio.application.service.MediaLibraryService;
import com.ffmpegstudio.application.service.MediaLibraryService.SortOrder;
import com.ffmpegstudio.application.service.MediaPreviewService;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FailureClassifier;
import com.ffmpegstudio.domain.model.FailureClassifier.FailureCategory;
import com.ffmpegstudio.domain.model.JobHistoryEntry;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.TranscodeOperation;
import com.ffmpegstudio.domain.model.WorkspaceMedia;
import com.ffmpegstudio.domain.model.WorkspaceMedia.MediaKind;
import com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository;
import com.ffmpegstudio.infrastructure.config.JsonSettingsRepository;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import com.ffmpegstudio.ui.util.I18n;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5.2 - 5.10 Consolidation Tests")
class Phase5ConsolidationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should manage shared MediaLibraryService references, cache invalidation, and never delete disk files")
    void shouldManageMediaLibrarySafely() throws Exception {
        Path mediaFile = tempDir.resolve("sample clip.mp4");
        Files.writeString(mediaFile, "v1");

        AnalyzeMediaUseCase fakeAnalyze = p -> new MediaFile(p, p.getFileName().toString(), p.toFile().length(), null);
        MediaLibraryService lib = new MediaLibraryService(fakeAnalyze);

        WorkspaceMedia imported = lib.importFile(mediaFile);
        assertEquals(MediaKind.VIDEO, imported.kind());
        assertFalse(imported.missingOnDisk());

        // Search & filter
        assertEquals(1, lib.query("sample", MediaKind.VIDEO, SortOrder.NAME_ASC).size());
        assertEquals(0, lib.query("sample", MediaKind.AUDIO, SortOrder.NAME_ASC).size());

        // Modify file on disk -> refresh must detect modification
        Thread.sleep(20);
        Files.writeString(mediaFile, "v2_modified_content");
        lib.refreshAndValidateFiles();
        assertTrue(lib.getAll().get(0).modifiedSinceInspection());

        // Remove reference -> file on disk must remain intact!
        assertTrue(lib.removeReference(mediaFile));
        assertTrue(Files.exists(mediaFile));
        assertTrue(lib.getAll().isEmpty());
    }

    @Test
    @DisplayName("Should persist queue, mark RUNNING jobs as INTERRUPTED on restart, and archive history")
    void shouldPersistQueueAndRecoverInterruptedJobs() {
        FileSystemService fs = new FileSystemService();
        JsonQueueHistoryRepository repo = new JsonQueueHistoryRepository(
                tempDir.resolve("queue-state.json"),
                tempDir.resolve("history.json"),
                fs
        );

        // Simulate previous session where a job was RUNNING when app closed
        JobHistoryEntry interruptedEntry = new JobHistoryEntry(
                "job-prev-1", "TRANSCODE", "Transcode", "MP4",
                List.of(tempDir.resolve("in.mp4").toString()),
                tempDir.resolve("out.mp4").toString(),
                null, JobStatus.RUNNING, Instant.now(), Instant.now(), null, null, null,
                "{\"container\":\"MP4\",\"videoCodec\":\"H264_LIBX264\",\"audioCodec\":\"AAC\",\"speedPreset\":\"MEDIUM\",\"crf\":23,\"audioBitrateKbps\":192,\"resolution\":\"ORIGINAL\",\"mapAllStreams\":true}"
        );
        repo.savePendingQueue(List.of(interruptedEntry));

        // Reject invalid/unknown operation types safely
        JobHistoryEntry badEntry = new JobHistoryEntry(
                "bad-1", "UNKNOWN_EXPLOIT_TYPE", "Bad", "", List.of("a"), "b", null,
                JobStatus.PENDING, Instant.now(), null, null, null, null, "{}"
        );
        assertFalse(badEntry.isValidSchema());

        ManageSettingsUseCase fakeSettings = new ManageSettingsUseCase() {
            @Override public AppSettings getSettings() { return AppSettings.createDefault(); }
            @Override public void updateSettings(AppSettings s) {}
            @Override public AppSettings resetToDefaults() { return AppSettings.createDefault(); }
        };
        TranscodeMediaUseCase fakeTranscode = (job, cb) -> new TranscodeMediaUseCase.TranscodeExecutionHandle() {
            @Override public CompletableFuture<ProcessingJob> completionFuture() { return new CompletableFuture<>(); }
            @Override public void cancel() {}
            @Override public boolean isRunning() { return false; }
        };

        // Restart queue service -> must restore as INTERRUPTED and NOT auto-start
        JobQueueService restoredQueue = new JobQueueService(fakeTranscode, fakeSettings, repo);
        assertFalse(restoredQueue.isQueueRunning());
        assertEquals(1, restoredQueue.getInterruptedJobs().size());
        assertEquals(JobStatus.INTERRUPTED, restoredQueue.getInterruptedJobs().get(0).getStatus());

        // User confirms requeue -> transitions to PENDING
        restoredQueue.requeueInterruptedJobs();
        assertEquals(0, restoredQueue.getInterruptedJobs().size());
        assertEquals(JobStatus.PENDING, restoredQueue.getAllJobs().get(0).getStatus());

        // Duplicate job -> creates non-colliding copy
        ProcessingJob dup = restoredQueue.duplicateJob(restoredQueue.getAllJobs().get(0));
        assertNotEquals(restoredQueue.getAllJobs().get(0).getTargetOutputPath(), dup.getTargetOutputPath());
        assertEquals(2, restoredQueue.getAllJobs().size());
    }

    @Test
    @DisplayName("Should migrate legacy 5-field settings.json safely")
    void shouldMigrateLegacySettingsJson() throws Exception {
        Path legacyFile = tempDir.resolve("legacy-settings.json");
        Files.writeString(legacyFile, """
                {
                  "ffmpegPath": "/usr/bin/ffmpeg",
                  "ffprobePath": "/usr/bin/ffprobe",
                  "defaultOutputDir": "/tmp/videos",
                  "theme": "DARK",
                  "maxConcurrentJobs": 4
                }
                """);

        JsonSettingsRepository repo = new JsonSettingsRepository(legacyFile, new FileSystemService());
        AppSettings loaded = repo.loadSettings();

        assertEquals("/usr/bin/ffmpeg", loaded.ffmpegPath());
        assertEquals(4, loaded.maxConcurrentJobs());
        assertEquals(AppSettings.DEFAULT_LANGUAGE, loaded.language());
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_MB, loaded.cacheLimitMb());
        assertEquals(AppSettings.DEFAULT_LOG_RETENTION_DAYS, loaded.logRetentionDays());
        assertTrue(loaded.confirmationsEnabled());
    }

    @Test
    @DisplayName("Should classify errors, support pt_BR/en i18n, and generate sanitized diagnostics")
    void shouldClassifyErrorsI18nAndDiagnostics() {
        assertEquals(FailureCategory.HARDWARE_FAILURE,
                FailureClassifier.classify("[h264_vaapi] No usable encoding profile found").category());
        assertEquals(FailureCategory.PERMISSION_DENIED,
                FailureClassifier.classify("Permission denied opening /dev/dri/renderD128").category());
        assertEquals(FailureCategory.CODEC_UNAVAILABLE,
                FailureClassifier.classify("Unknown encoder 'libx265'").category());

        I18n.setLocale("en");
        assertEquals("Task Queue", I18n.get("nav.queue"));
        assertTrue(I18n.format("shutdown.header", 3).contains("3"));

        I18n.setLocale("pt_BR");
        assertEquals("Fila de Tarefas", I18n.get("nav.queue"));

        FileSystemService fs = new FileSystemService();
        FFmpegProcessExecutor exec = new FFmpegProcessExecutor();
        DetectCapabilitiesService cap = new DetectCapabilitiesService(new FFmpegCapabilityDetector(exec, fs));
        ManageSettingsUseCase settings = new SettingsService(new JsonSettingsRepository(tempDir.resolve("s.json"), fs));
        HardwareAccelerationService hw = new HardwareAccelerationService(exec, cap, settings);
        DiagnosticsService diag = new DiagnosticsService(cap, settings, hw);

        String sanitized = diag.generateReport(false);
        assertTrue(sanitized.contains("Caminhos locais omitidos por privacidade"));
    }

    @Test
    @DisplayName("Should extract waveform peaks, generate/clean proxy, and handle Unicode paths")
    void shouldExtractWaveformProxyAndUnicodePaths() throws Exception {
        FileSystemService fs = new FileSystemService();
        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent() && fs.findInPath("ffprobe").isPresent());

        FFmpegProcessExecutor exec = new FFmpegProcessExecutor();
        ManageSettingsUseCase settings = new SettingsService(new JsonSettingsRepository(tempDir.resolve("s2.json"), fs));
        FFprobeClient ffprobe = new FFprobeClient(exec);
        AnalyzeMediaService analyze = new AnalyzeMediaService(ffprobe, settings);
        MediaPreviewService preview = new MediaPreviewService(exec, settings);

        Path unicodeDir = Files.createDirectories(tempDir.resolve("pasta com espaços e áçênto"));
        Path synthUnicode = unicodeDir.resolve("vídeo teste áçênto.mp4");

        exec.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                synthUnicode.toString()
        ), Duration.ofSeconds(10));

        MediaFile mf = analyze.analyze(synthUnicode);
        assertNotNull(mf.metadata());
        assertFalse(mf.metadata().videoStreams().isEmpty());

        float[] peaks = preview.extractWaveformPeaksAsync(synthUnicode, Duration.ofSeconds(1), 32)
                .get(10, TimeUnit.SECONDS);
        assertEquals(32, peaks.length);
        boolean hasSignal = false;
        for (float p : peaks) {
            if (p > 0.05f) hasSignal = true;
        }
        assertTrue(hasSignal, "Expected non-zero waveform peaks from 440Hz sine tone");

        Path proxy = preview.generateProxyAsync(synthUnicode, new AtomicBoolean(false))
                .get(15, TimeUnit.SECONDS);
        assertNotNull(proxy);
        assertTrue(Files.exists(proxy));

        preview.shutdown();
        assertFalse(Files.exists(proxy), "Proxy temporary file should be cleaned on shutdown");
    }
}
