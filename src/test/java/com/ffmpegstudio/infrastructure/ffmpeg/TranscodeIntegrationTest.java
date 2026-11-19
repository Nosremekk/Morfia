package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TranscodeIntegrationTest {

    @TempDir
    Path tempDir;

    private FileSystemService fileSystemService;
    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private FFmpegCapabilityDetector capabilityDetector;
    private ManageSettingsUseCase settingsUseCase;
    private TranscodeMediaService transcodeService;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        capabilityDetector = new FFmpegCapabilityDetector(processExecutor, fileSystemService);

        JsonSettingsRepository repo = new JsonSettingsRepository(tempDir.resolve("settings.json"), fileSystemService);
        settingsUseCase = new SettingsService(repo);

        transcodeService = new TranscodeMediaService(processExecutor, settingsUseCase, capabilityDetector);
    }

    @Test
    @DisplayName("Should synthesize media, inspect with FFprobe, transcode to MKV, and verify result")
    void shouldPerformRealEndToEndTranscode() throws Exception {
        boolean ffmpegFound = fileSystemService.findInPath("ffmpeg").isPresent();
        boolean ffprobeFound = fileSystemService.findInPath("ffprobe").isPresent();
        Assumptions.assumeTrue(ffmpegFound && ffprobeFound, "FFmpeg/FFprobe binaries not present; skipping real transcode integration test");

        Path synthInput = tempDir.resolve("synthetic_sample.mp4");
        Path finalOutput = tempDir.resolve("converted_output.mkv");
        Path tempOutput = tempDir.resolve(".~tmp_converted_output.mkv");

        // 1. Generate a small synthetic 1.5-second test video (video testsrc + audio sine)
        List<String> synthCommand = List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1.5:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=1000:duration=1.5",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                synthInput.toAbsolutePath().toString()
        );
        ProcessExecutionResult synthRes = processExecutor.execute(synthCommand, Duration.ofSeconds(10));
        assertThat(synthRes.isSuccess()).isTrue();
        assertThat(Files.exists(synthInput)).isTrue();
        long originalSize = Files.size(synthInput);
        assertThat(originalSize).isGreaterThan(0);

        // 2. Inspect synthetic video with FFprobeClient
        MediaFile inspectedInput = ffprobeClient.inspect("ffprobe", synthInput);
        assertThat(inspectedInput.metadata().duration().toMillis()).isCloseTo(1500, within(200L));
        assertThat(inspectedInput.metadata().primaryVideoStream()).isPresent();
        assertThat(inspectedInput.metadata().primaryVideoStream().get().width()).isEqualTo(320);
        assertThat(inspectedInput.metadata().primaryVideoStream().get().height()).isEqualTo(240);
        assertThat(inspectedInput.metadata().audioStreams()).hasSize(1);

        // 3. Setup Transcoding Job: MP4 -> MKV with H.264 CRF 23, Fast preset, AAC audio
        ConversionSettings settings = new ConversionSettings(
                ContainerFormat.MKV,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.FAST,
                23,
                null,
                128,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );

        ProcessingJob job = new ProcessingJob(synthInput, finalOutput, tempOutput, settings);
        job.updateProgress(new com.ffmpegstudio.domain.model.ProcessingProgress(
                0.0, 0, 0, 0, inspectedInput.metadata().duration().toMillis(), 1.0, 0
        ));

        AtomicBoolean progressReported = new AtomicBoolean(false);

        // 4. Execute transcoding
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, updatedJob -> {
            if (updatedJob.getProgress().percentage() > 0) {
                progressReported.set(true);
            }
        });

        ProcessingJob completedJob = handle.completionFuture().get(15, TimeUnit.SECONDS);

        // 5. Verify successful transcode lifecycle
        assertThat(completedJob.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completedJob.getProgress().percentage()).isEqualTo(100.0);
        assertThat(Files.exists(finalOutput)).isTrue();
        assertThat(Files.size(finalOutput)).isGreaterThan(0);

        // Verify temporary output file was cleaned up / renamed
        assertThat(Files.exists(tempOutput)).isFalse();

        // Verify original input file is 100% intact and preserved
        assertThat(Files.exists(synthInput)).isTrue();
        assertThat(Files.size(synthInput)).isEqualTo(originalSize);

        // 6. Inspect the transcoded MKV file with FFprobe
        MediaFile inspectedOutput = ffprobeClient.inspect("ffprobe", finalOutput);
        assertThat(inspectedOutput.metadata().formatName()).contains("matroska");
        assertThat(inspectedOutput.metadata().primaryVideoStream()).isPresent();
        assertThat(inspectedOutput.metadata().primaryVideoStream().get().codecName()).isEqualTo("h264");
        assertThat(inspectedOutput.metadata().audioStreams()).hasSize(1);
        assertThat(inspectedOutput.metadata().audioStreams().get(0).codecName()).isEqualTo("aac");
    }

    @Test
    @DisplayName("Should support live cancellation and automatically clean up temporary file")
    void shouldCancelAndCleanUpTemporaryFile() throws Exception {
        boolean ffmpegFound = fileSystemService.findInPath("ffmpeg").isPresent();
        Assumptions.assumeTrue(ffmpegFound, "FFmpeg binary not present; skipping cancellation test");

        Path synthInput = tempDir.resolve("synth_long.mp4");
        Path finalOutput = tempDir.resolve("cancelled_output.mp4");
        Path tempOutput = tempDir.resolve(".~tmp_cancelled_output.mp4");

        // Synthesize 5-second video
        List<String> synthCommand = List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=5:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=5",
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                synthInput.toAbsolutePath().toString()
        );
        processExecutor.execute(synthCommand, Duration.ofSeconds(10));

        ConversionSettings slowSettings = new ConversionSettings(
                ContainerFormat.MP4,
                VideoCodecOption.H264_LIBX264,
                AudioCodecOption.AAC,
                PresetSpeed.VERYSLOW, // Slow so we have time to cancel
                18,
                null,
                192,
                ResolutionPreset.ORIGINAL,
                null, null, null, true, null, null, null
        );

        ProcessingJob job = new ProcessingJob(synthInput, finalOutput, tempOutput, slowSettings);
        TranscodeMediaUseCase.TranscodeExecutionHandle handle = transcodeService.executeJob(job, null);

        // Cancel after 200ms
        Thread.sleep(200);
        handle.cancel();

        ProcessingJob cancelledJob = handle.completionFuture().get(5, TimeUnit.SECONDS);

        assertThat(cancelledJob.getStatus()).isEqualTo(JobStatus.CANCELLED);
        // Ensure temporary file was cleanly deleted
        assertThat(Files.exists(tempOutput)).isFalse();
        // Ensure final output was not created
        assertThat(Files.exists(finalOutput)).isFalse();
        // Ensure original input was preserved
        assertThat(Files.exists(synthInput)).isTrue();
    }
}
