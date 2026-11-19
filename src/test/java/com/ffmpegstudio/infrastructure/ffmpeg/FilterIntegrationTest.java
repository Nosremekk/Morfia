package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FilterOperation;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.domain.model.filter.AudioFilters.AudioSpeed;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Crop;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Grayscale;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Resize;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Speed;
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

@DisplayName("FilterOperation Integration Tests (Real FFmpeg)")
class FilterIntegrationTest {

    @TempDir
    Path tempDir;

    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private TranscodeMediaService transcodeService;
    private Path synthInput;

    @BeforeEach
    void setUp() throws Exception {
        FileSystemService fs = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        FFmpegCapabilityDetector capDetector = new FFmpegCapabilityDetector(processExecutor, fs);
        ManageSettingsUseCase settingsUseCase = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fs));
        transcodeService = new TranscodeMediaService(processExecutor, settingsUseCase, capDetector);

        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent() && fs.findInPath("ffprobe").isPresent());

        synthInput = tempDir.resolve("filter_src.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", synthInput.toString()
        ), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should apply Crop, Resize, Grayscale and 2x Speed on synthetic media")
    void shouldApplyComposedFiltersEndToEnd() throws Exception {
        MediaFile srcMedia = ffprobeClient.inspect("ffprobe", synthInput);
        Path out = tempDir.resolve("filtered_out.mp4");

        ConversionSettings fastSettings = new ConversionSettings(
                ContainerFormat.MP4, VideoCodecOption.H264_LIBX264, AudioCodecOption.AAC,
                PresetSpeed.ULTRAFAST, 28, null, 96, ResolutionPreset.ORIGINAL,
                null, null, null, false, null, null, null);

        FilterOperation op = new FilterOperation(
                List.of(
                        new Crop(200, 200, 10, 10),
                        new Resize(160, 160, false),
                        new Grayscale(),
                        new Speed(2.0),
                        new AudioSpeed(2.0)
                ),
                fastSettings
        );

        ProcessingJob job = ProcessingJob.create(op, synthInput, out, srcMedia.metadata());
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(20, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(out));

        MediaFile result = ffprobeClient.inspect("ffprobe", out);
        assertFalse(result.metadata().videoStreams().isEmpty());
        assertEquals(160, result.metadata().videoStreams().get(0).width());
        assertEquals(160, result.metadata().videoStreams().get(0).height());
        // 2s at 2x speed -> ~1.0s
        assertTrue(result.metadata().duration().toMillis() <= 1300);
    }
}
