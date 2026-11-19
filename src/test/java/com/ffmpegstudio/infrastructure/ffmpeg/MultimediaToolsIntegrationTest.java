package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.JobQueueService;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.AudioToolsOperation;
import com.ffmpegstudio.domain.model.AudioToolsOperation.AudioOutputFormat;
import com.ffmpegstudio.domain.model.CompressOperation;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.GenerateGifOperation;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.TranscodeOperation;
import com.ffmpegstudio.domain.model.TrimOperation;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Multimedia Tools Integration Tests (Real FFmpeg Engine)")
class MultimediaToolsIntegrationTest {

    @TempDir
    Path tempDir;

    private FileSystemService fileSystemService;
    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private FFmpegCapabilityDetector capabilityDetector;
    private ManageSettingsUseCase settingsUseCase;
    private TranscodeMediaService transcodeService;
    private Path synthInput;

    @BeforeEach
    void setUp() throws Exception {
        fileSystemService = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        capabilityDetector = new FFmpegCapabilityDetector(processExecutor, fileSystemService);

        JsonSettingsRepository repo = new JsonSettingsRepository(tempDir.resolve("settings.json"), fileSystemService);
        settingsUseCase = new SettingsService(repo);
        transcodeService = new TranscodeMediaService(processExecutor, settingsUseCase, capabilityDetector);

        boolean ffmpegFound = fileSystemService.findInPath("ffmpeg").isPresent();
        boolean ffprobeFound = fileSystemService.findInPath("ffprobe").isPresent();
        Assumptions.assumeTrue(ffmpegFound && ffprobeFound, "FFmpeg/FFprobe binaries not present; skipping real integration test");

        // Generate synthetic 2-second test video with video and audio
        synthInput = tempDir.resolve("synthetic_test.mp4");
        if (!Files.exists(synthInput)) {
            List<String> synthCommand = List.of(
                    "ffmpeg", "-nostdin", "-hide_banner", "-y",
                    "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=25",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    synthInput.toAbsolutePath().toString()
            );
            processExecutor.execute(synthCommand, Duration.ofSeconds(10));
        }
        assertTrue(Files.exists(synthInput), "Synthetic test file was not created");
    }

    @Test
    @DisplayName("Should compress synthetic video using CompressOperation")
    void shouldCompressVideo() throws Exception {
        Path output = tempDir.resolve("compressed.mp4");
        MediaFile media = ffprobeClient.inspect("ffprobe", synthInput);

        CompressOperation op = new CompressOperation(
                VideoCodecOption.H264_LIBX264,
                PresetSpeed.ULTRAFAST,
                28,
                null,
                ResolutionPreset.ORIGINAL,
                null,
                AudioCodecOption.AAC,
                96,
                CompressOperation.CompressionProfile.BALANCED
        );

        ProcessingJob job = ProcessingJob.create(op, synthInput, output, media.metadata());
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completedJob = handle.completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);
    }

    @Test
    @DisplayName("Should extract MP3 audio with volume and loudnorm using AudioToolsOperation")
    void shouldExtractMp3Audio() throws Exception {
        Path output = tempDir.resolve("extracted_audio.mp3");
        MediaFile media = ffprobeClient.inspect("ffprobe", synthInput);

        AudioToolsOperation op = new AudioToolsOperation(
                AudioOutputFormat.MP3,
                44100,
                2,
                128,
                1.2,
                true,
                0
        );

        ProcessingJob job = ProcessingJob.create(op, synthInput, output, media.metadata());
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completedJob = handle.completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertTrue(Files.exists(output));

        MediaFile inspectedAudio = ffprobeClient.inspect("ffprobe", output);
        assertFalse(inspectedAudio.metadata().audioStreams().isEmpty());
        assertEquals("mp3", inspectedAudio.metadata().audioStreams().get(0).codecName());
    }

    @Test
    @DisplayName("Should trim video fast with stream copy using TrimOperation")
    void shouldTrimVideo() throws Exception {
        Path output = tempDir.resolve("trimmed.mp4");
        TrimOperation op = TrimOperation.fast(Duration.ZERO, Duration.ofSeconds(1));

        ProcessingJob job = ProcessingJob.create(op, synthInput, output, null);
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completedJob = handle.completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertTrue(Files.exists(output));
    }

    @Test
    @DisplayName("Should generate high-quality GIF using 2-pass palettegen/paletteuse")
    void shouldGenerateGif() throws Exception {
        Path output = tempDir.resolve("animation.gif");
        GenerateGifOperation op = new GenerateGifOperation(Duration.ZERO, Duration.ofSeconds(1), 10, 240, true);

        ProcessingJob job = ProcessingJob.create(op, synthInput, output, null);
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);
        ProcessingJob completedJob = handle.completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertTrue(Files.exists(output));
        assertTrue(Files.size(output) > 0);
    }

    @Test
    @DisplayName("Should execute batch jobs in JobQueueService to completion")
    void shouldExecuteBatchQueueToCompletion() throws Exception {
        JobQueueService queue = new JobQueueService(transcodeService, settingsUseCase);

        Path out1 = tempDir.resolve("queue_out1.mp4");
        Path out2 = tempDir.resolve("queue_out2.mp4");

        ConversionSettings fastSettings = new ConversionSettings(
                com.ffmpegstudio.domain.model.ContainerFormat.MP4,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.ULTRAFAST,
                28,
                null,
                96,
                ResolutionPreset.ORIGINAL,
                null,
                null,
                null,
                true,
                null,
                null,
                null
        );

        ProcessingJob job1 = ProcessingJob.create(new TranscodeOperation(fastSettings), synthInput, out1, null);
        ProcessingJob job2 = ProcessingJob.create(new TranscodeOperation(fastSettings), synthInput, out2, null);

        CountDownLatch latch = new CountDownLatch(2);
        java.util.Set<String> completedJobIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        queue.addListener(new com.ffmpegstudio.application.port.in.ManageQueueUseCase.QueueChangeListener() {
            @Override
            public void onQueueChanged() {}

            @Override
            public void onJobUpdated(ProcessingJob job) {
                if (job.getStatus() == JobStatus.COMPLETED && completedJobIds.add(job.getId())) {
                    latch.countDown();
                }
            }
        });

        queue.addJobs(List.of(job1, job2));
        queue.startQueue();

        boolean allCompleted = latch.await(25, TimeUnit.SECONDS);
        assertTrue(allCompleted, "Batch queue jobs did not complete in time");
        assertEquals(JobStatus.COMPLETED, job1.getStatus());
        assertEquals(JobStatus.COMPLETED, job2.getStatus());
        assertTrue(Files.exists(out1));
        assertTrue(Files.exists(out2));
    }
}
