package com.ffmpegstudio.domain.model.filter;

import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.FilterOperation;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.MediaMetadata;
import com.ffmpegstudio.domain.model.VideoStream;
import com.ffmpegstudio.domain.model.filter.AudioFilters.AudioSpeed;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Crop;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Grayscale;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Resize;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Reverse;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Rotate;
import com.ffmpegstudio.domain.model.filter.VideoFilters.RotationAngle;
import com.ffmpegstudio.domain.model.filter.VideoFilters.Speed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilterGraphBuilder & FilterOperation Unit Tests")
class FilterGraphBuilderTest {

    private MediaFile sampleMedia(int w, int h, Duration dur) {
        MediaMetadata meta = new MediaMetadata("mp4", "MP4", dur, 1000, 500, Map.of(),
                List.of(new VideoStream(0, "h264", "H.264", w, h, 30.0, "yuv420p", 500)));
        return new MediaFile(Path.of("/tmp/sample.mp4"), "sample.mp4", 1000, meta);
    }

    @Test
    @DisplayName("Should compose single -vf flag preserving filter order and tracking output dimensions")
    void shouldComposeSingleVfPreservingOrder() {
        FilterGraphBuilder builder = new FilterGraphBuilder();
        List<MediaFilter> chain = List.of(
                new Resize(1280, -1, true),
                new Rotate(RotationAngle.CW_90),
                new Grayscale()
        );

        FilterValidationResult res = builder.validate(chain, sampleMedia(1920, 1080, Duration.ofSeconds(10)), null);
        assertTrue(res.valid());
        assertEquals(720, res.estimatedWidth());
        assertEquals(1280, res.estimatedHeight());

        FilterOperation op = new FilterOperation(chain, ConversionSettings.defaultMp4());
        List<String> cmd = op.buildCommand("ffmpeg", sampleMedia(1920, 1080, Duration.ofSeconds(10)), Path.of("/tmp/out.mp4"), false);

        long vfCount = cmd.stream().filter("-vf"::equals).count();
        assertEquals(1, vfCount, "Must never generate multiple -vf flags");
        String vf = cmd.get(cmd.indexOf("-vf") + 1);
        assertEquals("scale=1280:-2,transpose=1,hue=s=0", vf);
    }

    @Test
    @DisplayName("Should reject crop exceeding resized video dimensions")
    void shouldRejectCropExceedingResizedDimensions() {
        FilterGraphBuilder builder = new FilterGraphBuilder();
        List<MediaFilter> chain = List.of(
                new Resize(640, -1, true), // becomes 640x360
                new Crop(800, 300, 0, 0)   // exceeds 640!
        );

        FilterValidationResult res = builder.validate(chain, sampleMedia(1920, 1080, Duration.ofSeconds(10)), null);
        assertFalse(res.valid());
        assertTrue(res.errors().get(0).contains("excede"));
    }

    @Test
    @DisplayName("Should chain atempo stages for extreme audio speed multipliers")
    void shouldChainAtempoStages() {
        AudioSpeed slow = new AudioSpeed(0.25);
        assertEquals("atempo=0.5,atempo=0.5000", slow.toFilterExpression());

        AudioSpeed fast = new AudioSpeed(4.0);
        assertEquals("atempo=2.0,atempo=2.0000", fast.toFilterExpression());
    }

    @Test
    @DisplayName("Should emit memory warning for Reverse filter and estimate speed duration")
    void shouldWarnOnReverseAndEstimateSpeedDuration() {
        FilterGraphBuilder builder = new FilterGraphBuilder();
        List<MediaFilter> chain = List.of(
                new Speed(2.0),
                new Reverse()
        );

        FilterValidationResult res = builder.validate(chain, sampleMedia(1280, 720, Duration.ofSeconds(40)), null);
        assertTrue(res.valid());
        assertEquals(Duration.ofSeconds(20), res.estimatedDuration());
        assertFalse(res.warnings().isEmpty());
        assertTrue(res.warnings().stream().anyMatch(w -> w.contains("memória")));
    }
}
