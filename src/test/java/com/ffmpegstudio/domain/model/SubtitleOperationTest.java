package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.SubtitleOperation.SubtitleAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SubtitleOperation Unit Tests")
class SubtitleOperationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should escape special filtergraph characters in subtitle path")
    void shouldEscapeSpecialFiltergraphCharacters() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path sub = isWindows
                ? tempDir.resolve("dir_with'special[chars]").resolve("sub.srt")
                : Path.of("/tmp/dir:with'special[chars]/sub.srt");
        String escaped = SubtitleOperation.escapeFilterPath(sub);
        if (isWindows) {
            assertTrue(escaped.contains("\\'special\\[chars\\]"));
        } else {
            assertEquals("'/tmp/dir\\:with\\'special\\[chars\\]/sub.srt'", escaped);
        }
    }

    @Test
    @DisplayName("Should reject converting image-based PGS subtitle to text SRT without OCR")
    void shouldRejectPgsToSrtExtraction() {
        MediaMetadata meta = new MediaMetadata("matroska", "MKV", Duration.ofSeconds(10), 1000, 500, Map.of(),
                List.of(new SubtitleStream(2, "hdmv_pgs_subtitle", "PGS", "eng", "English", false, false)));
        MediaFile media = new MediaFile(Path.of("/tmp/movie.mkv"), "movie.mkv", 1000, meta);

        SubtitleOperation op = new SubtitleOperation(
                SubtitleAction.EXTRACT, 0, null, SubtitleFormat.SRT, ContainerFormat.MKV,
                "eng", "", false, false, 0.0, null);

        List<String> errors = op.validate(media, null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("OCR"));
    }

    @Test
    @DisplayName("Should reject PGS soft embedding in MP4 container")
    void shouldRejectPgsInMp4() throws Exception {
        Path sup = tempDir.resolve("sub.sup");
        Files.writeString(sup, "dummy");

        SubtitleOperation op = new SubtitleOperation(
                SubtitleAction.EMBED_SOFT, null, sup, SubtitleFormat.PGS, ContainerFormat.MP4,
                "por", "Português", true, false, 0.0, null);

        List<String> errors = op.validate(null, null);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("MP4"));
    }

    @Test
    @DisplayName("Should build BURN_IN command with subtitles filter and video re-encoding")
    void shouldBuildBurnInCommand() throws Exception {
        Path srt = tempDir.resolve("subs.srt");
        Files.writeString(srt, "1\n00:00:00,000 --> 00:00:01,000\nHello\n");

        SubtitleOperation op = new SubtitleOperation(
                SubtitleAction.BURN_IN, null, srt, SubtitleFormat.SRT, ContainerFormat.MP4,
                "eng", "", false, false, 0.0, ConversionSettings.defaultMp4());

        List<String> cmd = op.buildCommand("ffmpeg", new MediaFile(tempDir.resolve("in.mp4"), "in.mp4", 100, null), tempDir.resolve("out.mp4"), true);

        assertTrue(cmd.contains("-vf"));
        String vf = cmd.get(cmd.indexOf("-vf") + 1);
        assertTrue(vf.startsWith("subtitles="));
        assertTrue(cmd.contains("libx264"));
        assertTrue(cmd.contains("copy")); // audio copy
    }
}
