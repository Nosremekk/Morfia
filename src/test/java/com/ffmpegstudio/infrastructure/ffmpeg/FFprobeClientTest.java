package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.AudioStream;
import com.ffmpegstudio.domain.model.MediaMetadata;
import com.ffmpegstudio.domain.model.SubtitleStream;
import com.ffmpegstudio.domain.model.VideoStream;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.domain.port.ProcessExecutorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FFprobeClientTest {

    private FFprobeClient client;

    @BeforeEach
    void setUp() {
        ProcessExecutorPort dummyExecutor = (command, timeout) -> new ProcessExecutionResult(0, List.of(), List.of(), false);
        client = new FFprobeClient(dummyExecutor);
    }

    @Test
    @DisplayName("Should parse comprehensive FFprobe JSON with multiple streams and chapters")
    void shouldParseCompleteJson() {
        String json = """
        {
          "streams": [
            {
              "index": 0,
              "codec_name": "h264",
              "codec_long_name": "H.264 / AVC / MPEG-4 AVC",
              "profile": "High",
              "codec_type": "video",
              "width": 1920,
              "height": 1080,
              "display_aspect_ratio": "16:9",
              "pix_fmt": "yuv420p",
              "r_frame_rate": "30000/1001",
              "avg_frame_rate": "30000/1001",
              "bit_rate": "2500000",
              "tags": {
                "language": "eng"
              }
            },
            {
              "index": 1,
              "codec_name": "aac",
              "codec_long_name": "AAC (Advanced Audio Coding)",
              "profile": "LC",
              "codec_type": "audio",
              "sample_rate": "48000",
              "channels": 6,
              "channel_layout": "5.1",
              "bit_rate": "384000",
              "tags": {
                "language": "por"
              }
            },
            {
              "index": 2,
              "codec_name": "subrip",
              "codec_long_name": "SubRip subtitle",
              "codec_type": "subtitle",
              "disposition": {
                "default": 1,
                "forced": 0
              },
              "tags": {
                "language": "por",
                "title": "Português Brasileiro"
              }
            }
          ],
          "format": {
            "format_name": "mov,mp4",
            "format_long_name": "QuickTime / MOV",
            "duration": "125.500000",
            "size": "50000000",
            "bit_rate": "3187251",
            "tags": {
              "title": "Test Movie"
            }
          },
          "chapters": [
            {
              "id": 1,
              "start_time": "0.000000",
              "end_time": "60.000000",
              "tags": { "title": "Opening" }
            }
          ]
        }
        """;

        MediaMetadata metadata = client.parseJsonMetadata(json, 0);

        assertThat(metadata.formatName()).isEqualTo("mov,mp4");
        assertThat(metadata.duration()).isEqualTo(Duration.ofMillis(125500));
        assertThat(metadata.sizeBytes()).isEqualTo(50000000L);
        assertThat(metadata.tags()).containsEntry("title", "Test Movie");

        // Video Stream
        assertThat(metadata.primaryVideoStream()).isPresent();
        VideoStream v = metadata.primaryVideoStream().get();
        assertThat(v.codecName()).isEqualTo("h264");
        assertThat(v.width()).isEqualTo(1920);
        assertThat(v.height()).isEqualTo(1080);
        assertThat(v.aspectRatio()).isEqualTo("16:9");
        assertThat(v.frameRate()).isCloseTo(29.97, within(0.01));
        assertThat(v.pixelFormat()).isEqualTo("yuv420p");
        assertThat(v.language()).isEqualTo("eng");

        // Audio Stream
        assertThat(metadata.audioStreams()).hasSize(1);
        AudioStream a = metadata.audioStreams().get(0);
        assertThat(a.codecName()).isEqualTo("aac");
        assertThat(a.sampleRate()).isEqualTo(48000);
        assertThat(a.channels()).isEqualTo(6);
        assertThat(a.channelLayout()).isEqualTo("5.1");
        assertThat(a.language()).isEqualTo("por");

        // Subtitle Stream
        assertThat(metadata.subtitleStreams()).hasSize(1);
        SubtitleStream s = metadata.subtitleStreams().get(0);
        assertThat(s.codecName()).isEqualTo("subrip");
        assertThat(s.language()).isEqualTo("por");
        assertThat(s.title()).isEqualTo("Português Brasileiro");
        assertThat(s.isDefault()).isTrue();
        assertThat(s.isForced()).isFalse();

        // Chapters
        assertThat(metadata.chapters()).hasSize(1);
        assertThat(metadata.chapters().get(0).title()).isEqualTo("Opening");
    }

    @Test
    @DisplayName("Should handle missing format, N/A values, and corrupted JSON gracefully")
    void shouldHandleMissingAndCorruptedJson() {
        String corruptedJson = "not a valid json";
        MediaMetadata metaCorrupted = client.parseJsonMetadata(corruptedJson, 1024);
        assertThat(metaCorrupted.sizeBytes()).isEqualTo(1024);
        assertThat(metaCorrupted.streams()).isEmpty();

        String missingFieldsJson = """
        {
          "streams": [
            {
              "codec_type": "video",
              "width": 1280,
              "height": 720,
              "r_frame_rate": "0/0",
              "bit_rate": "N/A"
            }
          ],
          "format": {
            "duration": "N/A",
            "bit_rate": "N/A"
          }
        }
        """;

        MediaMetadata metaMissing = client.parseJsonMetadata(missingFieldsJson, 2048);
        assertThat(metaMissing.sizeBytes()).isEqualTo(2048);
        assertThat(metaMissing.duration()).isEqualTo(Duration.ZERO);
        assertThat(metaMissing.primaryVideoStream()).isPresent();
        VideoStream v = metaMissing.primaryVideoStream().get();
        assertThat(v.width()).isEqualTo(1280);
        assertThat(v.height()).isEqualTo(720);
        assertThat(v.frameRate()).isEqualTo(0.0);
        assertThat(v.bitRate()).isEqualTo(0);
    }
}
