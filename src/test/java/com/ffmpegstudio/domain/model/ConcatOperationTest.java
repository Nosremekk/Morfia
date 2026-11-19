package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.ConcatOperation.ConcatMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConcatOperation Unit Tests")
class ConcatOperationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should escape single quotes and spaces for concat demuxer list file")
    void shouldEscapeSingleQuotesAndSpaces() {
        Path p = tempDir.resolve("my 'special' video.mp4");
        String escaped = ConcatOperation.escapeConcatDemuxerPath(p);
        String expected = "'" + p.toAbsolutePath().normalize().toString().replace("\\", "/").replace("'", "'\\''") + "'";
        assertEquals(expected, escaped);
    }

    @Test
    @DisplayName("Should reject incompatible resolutions in FAST_DEMUXER mode")
    void shouldRejectIncompatibleResolutionsInFastMode() throws Exception {
        Path f1 = tempDir.resolve("clip1.mp4");
        Path f2 = tempDir.resolve("clip2.mp4");
        Files.writeString(f1, "dummy");
        Files.writeString(f2, "dummy");

        MediaMetadata m1 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(5), 1000, 500, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 1920, 1080, 30.0, "yuv420p", 500)));
        MediaMetadata m2 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(5), 1000, 500, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 1280, 720, 30.0, "yuv420p", 500)));

        MediaFile mf1 = new MediaFile(f1, "clip1.mp4", 1000, m1);
        MediaFile mf2 = new MediaFile(f2, "clip2.mp4", 1000, m2);

        ConcatOperation op = new ConcatOperation(List.of(mf1, mf2), ConcatMode.FAST_DEMUXER, null);
        List<String> errors = op.validate(null, null);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("Resolução incompatível"));
    }

    @Test
    @DisplayName("Should reject audio presence mismatch in FAST_DEMUXER mode")
    void shouldRejectAudioPresenceMismatchInFastMode() throws Exception {
        Path f1 = tempDir.resolve("clip1.mp4");
        Path f2 = tempDir.resolve("clip2.mp4");
        Files.writeString(f1, "dummy");
        Files.writeString(f2, "dummy");

        MediaMetadata m1 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(5), 1000, 500, Map.of(),
                List.of(
                        new VideoStream(0, "h264", "H.264", 1280, 720, 30.0, "yuv420p", 500),
                        new AudioStream(1, "aac", "AAC", 48000, 2, "stereo", 128)
                ));
        MediaMetadata m2 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(5), 1000, 500, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 1280, 720, 30.0, "yuv420p", 500)));

        MediaFile mf1 = new MediaFile(f1, "clip1.mp4", 1000, m1);
        MediaFile mf2 = new MediaFile(f2, "clip2.mp4", 1000, m2);

        ConcatOperation op = new ConcatOperation(List.of(mf1, mf2), ConcatMode.FAST_DEMUXER, null);
        List<String> errors = op.validate(null, null);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("presença de faixa de áudio difere")));
    }

    @Test
    @DisplayName("Should build filter_complex with anullsrc when one clip lacks audio in COMPATIBLE_REENCODE mode")
    void shouldBuildCompatibleFilterWithMissingAudio() throws Exception {
        Path f1 = tempDir.resolve("clip1.mp4");
        Path f2 = tempDir.resolve("clip2.mp4");
        Files.writeString(f1, "dummy");
        Files.writeString(f2, "dummy");

        MediaMetadata m1 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(3), 1000, 500, Map.of(),
                List.of(
                        new VideoStream(0, "h264", "H.264", 1280, 720, 30.0, "yuv420p", 500),
                        new AudioStream(1, "aac", "AAC", 48000, 2, "stereo", 128)
                ));
        MediaMetadata m2 = new MediaMetadata("mp4", "MP4", Duration.ofSeconds(2), 1000, 500, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", 640, 480, 25.0, "yuv420p", 500)));

        MediaFile mf1 = new MediaFile(f1, "clip1.mp4", 1000, m1);
        MediaFile mf2 = new MediaFile(f2, "clip2.mp4", 1000, m2);

        ConcatOperation op = new ConcatOperation(List.of(mf1, mf2), ConcatMode.COMPATIBLE_REENCODE, ConversionSettings.defaultMp4());
        assertTrue(op.validate(null, null).isEmpty());

        List<String> cmd = op.buildCommand("ffmpeg", null, tempDir.resolve("out.mp4"), true);
        assertTrue(cmd.contains("-filter_complex"));
        String filterComplex = cmd.get(cmd.indexOf("-filter_complex") + 1);
        assertTrue(filterComplex.contains("anullsrc="));
        assertTrue(filterComplex.contains("concat=n=2:v=1:a=1"));
    }
}
