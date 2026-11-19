package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.ExtractFramesOperation.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GIF and Frame Extraction Operations Unit Tests")
class GenerateGifAndFramesOperationTest {

    @Test
    @DisplayName("Should generate 2-pass palette filter complex for high quality GIF")
    void shouldGeneratePaletteFilterComplex() {
        GenerateGifOperation op = new GenerateGifOperation(Duration.ofSeconds(1), Duration.ofSeconds(4), 15, 480, true);
        Path out = Paths.get("/tmp/animation.gif");

        List<String> cmd = op.buildCommand("ffmpeg", null, out, true);

        assertTrue(cmd.contains("-filter_complex"));
        int idx = cmd.indexOf("-filter_complex");
        String filter = cmd.get(idx + 1);

        assertTrue(filter.contains("palettegen"));
        assertTrue(filter.contains("paletteuse"));
        assertTrue(filter.contains("fps=15"));
        assertTrue(filter.contains("scale=480:-1"));
    }

    @Test
    @DisplayName("Should build single still frame extraction command")
    void shouldBuildSingleFrameExtractionCommand() {
        ExtractFramesOperation op = new ExtractFramesOperation(Duration.ofSeconds(3), ImageFormat.PNG, -1);
        Path out = Paths.get("/tmp/thumb.png");

        List<String> cmd = op.buildCommand("ffmpeg", null, out, false);

        assertTrue(cmd.contains("-ss"));
        assertTrue(cmd.contains("3.000"));
        assertTrue(cmd.contains("-frames:v"));
        assertTrue(cmd.contains("1"));
    }
}
