package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.SubtitleFormat;
import com.ffmpegstudio.domain.model.SubtitleOperation;
import com.ffmpegstudio.domain.model.SubtitleOperation.SubtitleAction;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.infrastructure.config.JsonSettingsRepository;
import com.ffmpegstudio.infrastructure.filesystem.FileSystemService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubtitleOperation Integration Tests (Real FFmpeg)")
class SubtitleIntegrationTest {

    @TempDir
    Path tempDir;

    private FileSystemService fileSystemService;
    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private FFmpegCapabilityDetector capabilityDetector;
    private TranscodeMediaService transcodeService;
    private Path synthVideo;
    private Path srtFile;

    @BeforeEach
    void setUp() throws Exception {
        fileSystemService = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        capabilityDetector = new FFmpegCapabilityDetector(processExecutor, fileSystemService);
        ManageSettingsUseCase settingsUseCase = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fileSystemService));
        transcodeService = new TranscodeMediaService(processExecutor, settingsUseCase, capabilityDetector);

        Assumptions.assumeTrue(fileSystemService.findInPath("ffmpeg").isPresent()
                && fileSystemService.findInPath("ffprobe").isPresent());

        synthVideo = tempDir.resolve("base.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1.5:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1.5",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", synthVideo.toString()
        ), Duration.ofSeconds(10));

        srtFile = tempDir.resolve("test_sub.srt");
        Files.writeString(srtFile, "1\n00:00:00,100 --> 00:00:01,400\nFFmpeg Studio Test\n");
    }

    @Test
    @DisplayName("Should embed soft subtitle into MKV and extract it back to SRT")
    void shouldEmbedAndExtractSubtitle() throws Exception {
        Path mkvOut = tempDir.resolve("with_sub.mkv");
        MediaFile baseMedia = ffprobeClient.inspect("ffprobe", synthVideo);

        SubtitleOperation embedOp = new SubtitleOperation(
                SubtitleAction.EMBED_SOFT, null, srtFile, SubtitleFormat.SRT, ContainerFormat.MKV,
                "por", "Português", true, false, 0.0, null);

        ProcessingJob embedJob = ProcessingJob.create(embedOp, synthVideo, mkvOut, baseMedia.metadata());
        ProcessingJob embedDone = transcodeService.executeJob(embedJob, null).completionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.COMPLETED, embedDone.getStatus());

        MediaFile mkvMedia = ffprobeClient.inspect("ffprobe", mkvOut);
        assertEquals(1, mkvMedia.metadata().subtitleStreams().size());
        assertEquals("por", mkvMedia.metadata().subtitleStreams().get(0).language());

        // Now extract back to SRT
        Path extractedSrt = tempDir.resolve("extracted.srt");
        SubtitleOperation extractOp = new SubtitleOperation(
                SubtitleAction.EXTRACT, 0, null, SubtitleFormat.SRT, ContainerFormat.MKV,
                "por", "", false, false, 0.0, null);

        ProcessingJob extractJob = ProcessingJob.create(extractOp, mkvOut, extractedSrt, mkvMedia.metadata());
        ProcessingJob extractDone = transcodeService.executeJob(extractJob, null).completionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.COMPLETED, extractDone.getStatus());
        assertTrue(Files.exists(extractedSrt));
        assertTrue(Files.readString(extractedSrt).contains("FFmpeg Studio Test"));
    }

    @Test
    @DisplayName("Should burn-in SRT subtitle into video when subtitles filter is available")
    void shouldBurnInSubtitle() throws Exception {
        FFmpegCapabilities caps = capabilityDetector.detectCapabilities("ffmpeg");
        Assumptions.assumeTrue(caps.hasFilter("subtitles"), "FFmpeg 'subtitles' filter (libass) not available; skipping burn-in test");

        Path burnedOut = tempDir.resolve("burned.mp4");
        MediaFile baseMedia = ffprobeClient.inspect("ffprobe", synthVideo);

        ConversionSettings fastSettings = new ConversionSettings(
                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                PresetSpeed.ULTRAFAST, 28, null, 96, ResolutionPreset.ORIGINAL,
                null, null, null, false, null, null, null);

        SubtitleOperation burnOp = new SubtitleOperation(
                SubtitleAction.BURN_IN, null, srtFile, SubtitleFormat.SRT, ContainerFormat.MP4,
                "por", "", false, false, 0.0, fastSettings);

        ProcessingJob job = ProcessingJob.create(burnOp, synthVideo, burnedOut, baseMedia.metadata());
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(burnedOut));
        assertTrue(Files.size(burnedOut) > 0);
    }
}
