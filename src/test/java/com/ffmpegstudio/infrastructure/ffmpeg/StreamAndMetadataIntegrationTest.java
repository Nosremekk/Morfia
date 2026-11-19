package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.application.service.TranscodeMediaService;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MetadataOperation;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.StreamOperation;
import com.ffmpegstudio.domain.model.StreamOperation.StreamSelection;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamOperation & MetadataOperation Integration Tests")
class StreamAndMetadataIntegrationTest {

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

        synthInput = tempDir.resolve("streams_src.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=1:size=320x240:rate=25",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                "-metadata", "title=OriginalTitle",
                synthInput.toString()
        ), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should remux selecting only video stream (dropping audio) with global FFprobe index")
    void shouldRemuxVideoOnlyStream() throws Exception {
        MediaFile src = ffprobeClient.inspect("ffprobe", synthInput);
        assertEquals(1, src.metadata().videoStreams().size());
        assertEquals(1, src.metadata().audioStreams().size());

        int videoGlobalIdx = src.metadata().videoStreams().get(0).index();
        int audioGlobalIdx = src.metadata().audioStreams().get(0).index();

        Path out = tempDir.resolve("video_only.mkv");
        StreamOperation op = new StreamOperation(
                List.of(
                        new StreamSelection(videoGlobalIdx, "h264", "video", true, "Main Video", "por", true),
                        new StreamSelection(audioGlobalIdx, "aac", "audio", false, null, null, null)
                ),
                ContainerFormat.MKV,
                false
        );

        ProcessingJob job = ProcessingJob.create(op, synthInput, out, src.metadata());
        ProcessingJob done = transcodeService.executeJob(job, null).completionFuture().get(15, TimeUnit.SECONDS);

        assertEquals(JobStatus.COMPLETED, done.getStatus());
        assertTrue(Files.exists(out));

        MediaFile remuxed = ffprobeClient.inspect("ffprobe", out);
        assertEquals(1, remuxed.metadata().videoStreams().size());
        assertEquals(0, remuxed.metadata().audioStreams().size());
    }

    @Test
    @DisplayName("Should edit and then clear global metadata via stream copy")
    void shouldEditAndClearMetadata() throws Exception {
        MediaFile src = ffprobeClient.inspect("ffprobe", synthInput);
        Path editedOut = tempDir.resolve("meta_edited.mp4");

        MetadataOperation editOp = new MetadataOperation(
                Map.of("title", "Edited Title", "artist", "FFmpeg Studio", "comment", "Phase 4 Test"),
                List.of(),
                false,
                true,
                Map.of(),
                ContainerFormat.MP4
        );

        ProcessingJob editJob = ProcessingJob.create(editOp, synthInput, editedOut, src.metadata());
        ProcessingJob editDone = transcodeService.executeJob(editJob, null).completionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.COMPLETED, editDone.getStatus());

        MediaFile editedMedia = ffprobeClient.inspect("ffprobe", editedOut);
        assertEquals("Edited Title", editedMedia.metadata().tags().get("title"));
        assertEquals("FFmpeg Studio", editedMedia.metadata().tags().get("artist"));

        // Now clear all global metadata
        Path clearedOut = tempDir.resolve("meta_cleared.mp4");
        MetadataOperation clearOp = new MetadataOperation(
                Map.of(), List.of(), true, true, Map.of(), ContainerFormat.MP4
        );
        ProcessingJob clearJob = ProcessingJob.create(clearOp, editedOut, clearedOut, editedMedia.metadata());
        ProcessingJob clearDone = transcodeService.executeJob(clearJob, null).completionFuture().get(15, TimeUnit.SECONDS);
        assertEquals(JobStatus.COMPLETED, clearDone.getStatus());

        MediaFile clearedMedia = ffprobeClient.inspect("ffprobe", clearedOut);
        assertNull(clearedMedia.metadata().tags().get("title"));
        assertNull(clearedMedia.metadata().tags().get("artist"));
    }
}
