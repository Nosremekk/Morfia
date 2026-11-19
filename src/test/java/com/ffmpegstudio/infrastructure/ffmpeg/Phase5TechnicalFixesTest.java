package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.AudioToolsOperation;
import com.ffmpegstudio.domain.model.AudioToolsOperation.AudioOutputFormat;
import com.ffmpegstudio.domain.model.AudioToolsOperation.LoudnormMode;
import com.ffmpegstudio.domain.model.ExtractFramesOperation.ImageFormat;
import com.ffmpegstudio.domain.model.ImageConversionOperation;
import com.ffmpegstudio.domain.model.ImageConversionOperation.TargetImageFormat;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.LoudnormMetrics;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MediaMetadata;
import com.ffmpegstudio.domain.model.PeriodicFramesOperation;
import com.ffmpegstudio.domain.model.PeriodicFramesOperation.ExtractionMode;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.VideoStream;
import com.ffmpegstudio.domain.model.filter.FilterGraphBuilder;
import com.ffmpegstudio.domain.model.filter.FilterValidationResult;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Reverse;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5.1 Technical Resolutions Tests")
class Phase5TechnicalFixesTest {

    @TempDir
    Path tempDir;

    private FileSystemService fs;
    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private TranscodeMediaService transcodeService;

    @BeforeEach
    void setUp() {
        fs = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        FFmpegCapabilityDetector capDetector = new FFmpegCapabilityDetector(processExecutor, fs);
        ManageSettingsUseCase settings = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fs));
        transcodeService = new TranscodeMediaService(processExecutor, settings, capDetector);
    }

    @Test
    @DisplayName("Should parse valid loudnorm JSON metrics and reject infinite silent metrics")
    void shouldParseLoudnormJsonMetrics() {
        List<String> validStderr = List.of(
                "[Parsed_loudnorm_0] {",
                "  \"input_i\" : \"-22.45\",",
                "  \"input_tp\" : \"-3.10\",",
                "  \"input_lra\" : \"4.20\",",
                "  \"input_thresh\" : \"-32.50\",",
                "  \"target_offset\" : \"6.45\"",
                "}"
        );
        Optional<LoudnormMetrics> parsed = LoudnormMetricsParser.parseFromStderr(validStderr);
        assertTrue(parsed.isPresent());
        assertEquals(-22.45, parsed.get().inputIntegratedLufs(), 0.01);
        assertTrue(parsed.get().toSecondPassFilter(-16.0, -1.5, 11.0).contains("linear=true"));

        List<String> silentStderr = List.of(
                "{ \"input_i\" : \"-inf\", \"input_tp\" : \"-inf\", \"input_lra\" : \"0.00\", \"input_thresh\" : \"-inf\", \"target_offset\" : \"inf\" }"
        );
        assertTrue(LoudnormMetricsParser.parseFromStderr(silentStderr).isEmpty());
    }

    @Test
    @DisplayName("Should execute two-pass loudnorm normalization on synthetic audio")
    void shouldExecuteTwoPassLoudnorm() throws Exception {
        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent() && fs.findInPath("ffprobe").isPresent());

        Path synthAudio = tempDir.resolve("tone.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1.5",
                "-c:a", "aac", synthAudio.toString()
        ), Duration.ofSeconds(10));

        Path outMp3 = tempDir.resolve("normalized_2pass.mp3");
        AudioToolsOperation op = new AudioToolsOperation(
                AudioOutputFormat.MP3, 44100, 2, 192, 1.0, true, 0,
                LoudnormMode.TWO_PASS_LINEAR, null
        );
        ProcessingJob job = ProcessingJob.create(op, synthAudio, outMp3, null);
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(20, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(outMp3));
        assertTrue(done.getLogLines().stream().anyMatch(l -> l.contains("[Loudnorm Pass 1] Medido:")));
    }

    @Test
    @DisplayName("Should extract periodic frames and prevent silent overwriting of existing sequences")
    void shouldExtractPeriodicFramesAndPreventOverwrite() throws Exception {
        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent());

        Path synthVideo = tempDir.resolve("vid2s.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=160x120:rate=10",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", synthVideo.toString()
        ), Duration.ofSeconds(10));

        Path framesDir = tempDir.resolve("frames_out");
        PeriodicFramesOperation op = new PeriodicFramesOperation(
                ExtractionMode.EVERY_N_SECONDS, 1.0, Duration.ZERO, null,
                ImageFormat.PNG, -1, 10, framesDir, "snap_"
        );

        ProcessingJob job = ProcessingJob.create(op, synthVideo, framesDir, null);
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(framesDir.resolve("snap_0001.png")));
        assertTrue(Files.exists(framesDir.resolve("snap_0002.png")));

        // Re-validating against same directory must fail to prevent silent overwrite
        List<String> errs = op.validate(null, null);
        assertFalse(errs.isEmpty());
        assertTrue(errs.get(0).contains("sobrescrita"));
    }

    @Test
    @DisplayName("Should convert image formats (PNG to JPEG) with resizing")
    void shouldConvertImageFormats() throws Exception {
        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent());

        Path srcPng = tempDir.resolve("src.png");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=0.1:size=320x240:rate=1",
                "-frames:v", "1", srcPng.toString()
        ), Duration.ofSeconds(10));

        Path outJpg = tempDir.resolve("converted.jpg");
        ImageConversionOperation op = new ImageConversionOperation(
                TargetImageFormat.JPEG, 160, null, true, 85, false
        );
        ProcessingJob job = ProcessingJob.create(op, srcPng, outJpg, null);
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(outJpg));
        assertTrue(Files.size(outJpg) > 0);
    }

    @Test
    @DisplayName("Should reject Reverse filter when conservative uncompressed RAM exceeds 4 GB safety limit")
    void shouldRejectReverseExceedingSafeMemoryLimit() {
        FilterGraphBuilder builder = new FilterGraphBuilder();
        MediaMetadata meta1080p60s = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(60), 50_000_000, 5000, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 1920, 1080, 30.0, "yuv420p", 5000)));
        MediaFile mf = new MediaFile(Path.of("/tmp/heavy.mp4"), "heavy.mp4", 50_000_000, meta1080p60s);

        FilterValidationResult res = builder.validate(List.of(new Reverse()), mf, null);
        assertFalse(res.valid());
        assertTrue(res.errors().get(0).contains("Limite de segurança"));
    }
}
