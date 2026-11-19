package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.service.FilterPreviewService;
import com.ffmpegstudio.application.service.SettingsService;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.filter.PreviewResult;
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

@DisplayName("FilterPreviewService Integration Tests")
class FilterPreviewIntegrationTest {

    @TempDir
    Path tempDir;

    private FFmpegProcessExecutor processExecutor;
    private FFprobeClient ffprobeClient;
    private FilterPreviewService previewService;
    private Path synthVideo;

    @BeforeEach
    void setUp() throws Exception {
        FileSystemService fs = new FileSystemService();
        processExecutor = new FFmpegProcessExecutor();
        ffprobeClient = new FFprobeClient(processExecutor);
        ManageSettingsUseCase settingsUseCase = new SettingsService(new JsonSettingsRepository(tempDir.resolve("settings.json"), fs));
        previewService = new FilterPreviewService(processExecutor, settingsUseCase);

        Assumptions.assumeTrue(fs.findInPath("ffmpeg").isPresent() && fs.findInPath("ffprobe").isPresent());

        synthVideo = tempDir.resolve("preview_src.mp4");
        processExecutor.execute(List.of(
                "ffmpeg", "-nostdin", "-hide_banner", "-y",
                "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=25",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", synthVideo.toString()
        ), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should generate before and after PNG preview frames and flag temporal filters")
    void shouldGenerateBeforeAfterFrames() throws Exception {
        MediaFile media = ffprobeClient.inspect("ffprobe", synthVideo);

        PreviewResult res = previewService.generatePreviewAsync(
                media,
                Duration.ofMillis(500),
                List.of(new Resize(160, -1, true), new Grayscale(), new Speed(2.0))
        ).get(10, TimeUnit.SECONDS);

        assertNotNull(res);
        assertTrue(Files.exists(res.originalFramePath()));
        assertTrue(Files.exists(res.filteredFramePath()));
        assertNotEquals(res.originalFramePath(), res.filteredFramePath());
        assertTrue(res.hasTemporalLimitation());
        assertFalse(res.notices().isEmpty());

        previewService.clearCache();
        assertFalse(Files.exists(res.originalFramePath()));
        assertFalse(Files.exists(res.filteredFramePath()));
    }
}
