package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.ProcessingProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FFmpegProgressParserTest {

    @Test
    @DisplayName("Should accurately parse progress metrics and calculate percentage and ETA")
    void shouldParseProgressMetrics() {
        // Total duration: 100 seconds (100,000 ms)
        FFmpegProgressParser parser = new FFmpegProgressParser(100_000);

        parser.processLine("frame=1500");
        parser.processLine("fps=60.0");
        parser.processLine("bitrate=2500.5kbits/s");
        parser.processLine("total_size=15728640");
        parser.processLine("out_time_us=50000000"); // 50 seconds processed (50%)
        parser.processLine("speed=2.0x");
        Optional<ProcessingProgress> snapshot = parser.processLine("progress=continue");

        assertThat(snapshot).isPresent();
        ProcessingProgress p = snapshot.get();

        assertThat(p.percentage()).isCloseTo(50.0, within(0.1));
        assertThat(p.currentFps()).isEqualTo(60.0);
        assertThat(p.currentBitrateKbps()).isEqualTo(2500);
        assertThat(p.currentTimeMillis()).isEqualTo(50000);
        assertThat(p.speedMultiplier()).isEqualTo(2.0);
        // Remaining 50 seconds at 2.0x speed = 25 seconds ETA
        assertThat(p.etaSeconds()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should handle alternative out_time format (HH:MM:SS.fraction)")
    void shouldParseFormattedOutTime() {
        FFmpegProgressParser parser = new FFmpegProgressParser(60_000);

        parser.processLine("out_time=00:00:30.000000");
        Optional<ProcessingProgress> snapshot = parser.processLine("progress=continue");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().currentTimeMillis()).isEqualTo(30000);
        assertThat(snapshot.get().percentage()).isCloseTo(50.0, within(0.1));
    }

    @Test
    @DisplayName("Should yield indeterminate progress when total duration is zero or unknown")
    void shouldHandleIndeterminateDuration() {
        FFmpegProgressParser parser = new FFmpegProgressParser(0);

        parser.processLine("fps=30.0");
        parser.processLine("out_time_us=10000000");
        Optional<ProcessingProgress> snapshot = parser.processLine("progress=continue");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().percentage()).isEqualTo(0.0);
        assertThat(snapshot.get().etaSeconds()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Should emit completed progress snapshot on progress=end")
    void shouldHandleProgressEnd() {
        FFmpegProgressParser parser = new FFmpegProgressParser(50_000);

        Optional<ProcessingProgress> end = parser.processLine("progress=end");

        assertThat(end).isPresent();
        assertThat(end.get().percentage()).isEqualTo(100.0);
        assertThat(parser.isFinished()).isTrue();
    }
}
