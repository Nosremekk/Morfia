package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.ConcatOperation;
import com.ffmpegstudio.domain.model.ConcatOperation.ConcatMode;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
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

@DisplayName("ConcatOperation Integration Tests (Real FFmpeg)")
class ConcatIntegrationTest {

    @TempDir
    Path tempDir;

    private FileSystemService fileSystemService;
    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private TranscodeMediaService transcodeService;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        FFmpegCapabilityDetector capabilityDetector = new FFmpegCapabilityDetector(processExecutor, fileSystemService);
        ManageSettingsUseCase settingsUseCase = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fileSystemService));
        transcodeService = new TranscodeMediaService(processExecutor, settingsUseCase, capabilityDetector);

        Assumptions.assumeTrue(fileSystemService.findInPath("ffmpeg").isPresent()
                && fileSystemService.findInPath("ffprobe").isPresent());
    }

    @Test
    @DisplayName("Should concatenate two compatible clips via FAST_DEMUXER and clean temp list")
    void shouldConcatenateFastCompatibleClips() throws Exception {
        Path c1 = tempDir.resolve("c1.mp4");
        Path c2 = tempDir.resolve("c2.mp4");
        Path out = tempDir.resolve("merged_fast.mp4");

        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", c1.toString()
        ), Duration.ofSeconds(10));

        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=880:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", c2.toString()
        ), Duration.ofSeconds(10));

        MediaFile mf1 = ffprobeClient.inspect("ffprobe", c1);
        MediaFile mf2 = ffprobeClient.inspect("ffprobe", c2);

        ConcatOperation op = new ConcatOperation(List.of(mf1, mf2), ConcatMode.FAST_DEMUXER, null);
        ProcessingJob job = ProcessingJob.create(op, c1, out, mf1.metadata());

        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completed = handle.completionFuture().get(20, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completed.getStatus());
        assertTrue(Files.exists(out));

        MediaFile merged = ffprobeClient.inspect("ffprobe", out);
        assertTrue(merged.metadata().duration().toMillis() >= 1800);

        // Verify no .~tmp_concat_ files remain
        try (var stream = Files.list(tempDir)) {
            assertFalse(stream.anyMatch(p -> p.getFileName().toString().startsWith(".~tmp_concat_")));
        }
    }

    @Test
    @DisplayName("Should concatenate incompatible clips (different resolution & missing audio) via COMPATIBLE_REENCODE")
    void shouldConcatenateIncompatibleClipsWithMissingAudio() throws Exception {
        Path c1 = tempDir.resolve("incomp1.mp4");
        Path c2 = tempDir.resolve("incomp2_noaudio.mp4");
        Path out = tempDir.resolve("merged_compat.mp4");

        // 320x240 with audio
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", c1.toString()
        ), Duration.ofSeconds(10));

        // 160x120 WITHOUT audio
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=160x120:rate=30",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", c2.toString()
        ), Duration.ofSeconds(10));

        MediaFile mf1 = ffprobeClient.inspect("ffprobe", c1);
        MediaFile mf2 = ffprobeClient.inspect("ffprobe", c2);

        ConversionSettings settings = new ConversionSettings(
                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                PresetSpeed.ULTRAFAST, 28, null, 96, ResolutionPreset.ORIGINAL,
                null, null, null, false, null, null, null
        );

        ConcatOperation op = new ConcatOperation(List.of(mf1, mf2), ConcatMode.COMPATIBLE_REENCODE, settings);
        ProcessingJob job = ProcessingJob.create(op, c1, out, mf1.metadata());

        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completed = handle.completionFuture().get(20, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completed.getStatus());
        assertTrue(Files.exists(out));

        MediaFile merged = ffprobeClient.inspect("ffprobe", out);
        assertFalse(merged.metadata().videoStreams().isEmpty());
        assertFalse(merged.metadata().audioStreams().isEmpty());
        assertTrue(merged.metadata().duration().toMillis() >= 1800);
    }
}
