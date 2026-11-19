package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.AudioToolsOperation.AudioOutputFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AudioToolsOperation Unit Tests")
class AudioToolsOperationTest {

    @Test
    @DisplayName("Should build MP3 extraction command with volume and loudnorm")
    void shouldBuildMp3ExtractionWithFilters() {
        AudioToolsOperation op = new AudioToolsOperation(
                AudioOutputFormat.MP3,
                44100,
                2,
                192,
                1.5,
                true,
                0
        );

        Path in = Paths.get("/tmp/video.mp4");
        Path out = Paths.get("/tmp/audio.mp3");

        List<String> cmd = op.buildCommand("ffmpeg", new MediaFile(in, "video.mp4", 500, null), out, true);

        assertTrue(cmd.contains("-vn"));
        assertTrue(cmd.contains("-c:a"));
        assertTrue(cmd.contains("libmp3lame"));
        assertTrue(cmd.contains("-b:a"));
        assertTrue(cmd.contains("192k"));
        assertTrue(cmd.contains("-ar"));
        assertTrue(cmd.contains("44100"));
        assertTrue(cmd.contains("-ac"));
        assertTrue(cmd.contains("2"));
        assertTrue(cmd.contains("-af"));

        int afIdx = cmd.indexOf("-af");
        String filter = cmd.get(afIdx + 1);
        assertTrue(filter.contains("volume=1.50"));
        assertTrue(filter.contains("loudnorm"));
    }

    @Test
    @DisplayName("Should detect missing audio streams on validation")
    void shouldDetectMissingAudioStreamsOnValidation() {
        AudioToolsOperation op = AudioToolsOperation.extractMp3(192);

        MediaMetadata metadataWithoutAudio = new MediaMetadata(
                "mp4", "MP4", Duration.ofSeconds(10), 5000, 1000,
                java.util.Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 1920, 1080, 25.0, "yuv420p", 1000))
        );

        MediaFile media = new MediaFile(Paths.get("/tmp/no-audio.mp4"), "no-audio.mp4", 5000, metadataWithoutAudio);

        List<String> errors = op.validate(media, null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("no audio streams"));
    }
}
