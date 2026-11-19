package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Media compression operation focusing on reducing file size via optimized codecs, CRF, resolution, and audio bitrate.
 */
public record CompressOperation(
        VideoCodecOption videoCodec,
        PresetSpeed speedPreset,
        int crf,
        Long targetVideoBitrateKbps,
        ResolutionPreset resolution,
        Double targetFps,
        AudioCodecOption audioCodec,
        int audioBitrateKbps,
        CompressionProfile profile
) implements MediaOperation {

    public enum CompressionProfile {
        LIGHT(22, 192, "Light Compression (High Quality, CRF 22)"),
        BALANCED(26, 128, "Balanced Compression (Recommended, CRF 26)"),
        AGGRESSIVE(30, 96, "Aggressive Compression (Higher Compression, CRF 30, visual tradeoffs)"),
        CUSTOM(-1, -1, "Custom Parameters");

        private final int suggestedCrf;
        private final int suggestedAudioBitrate;
        private final String label;

        CompressionProfile(int suggestedCrf, int suggestedAudioBitrate, String label) {
            this.suggestedCrf = suggestedCrf;
            this.suggestedAudioBitrate = suggestedAudioBitrate;
            this.label = label;
        }

        public int suggestedCrf() { return suggestedCrf; }
        public int suggestedAudioBitrate() { return suggestedAudioBitrate; }
        public String label() { return label; }
    }

    public CompressOperation {
        videoCodec = videoCodec == null ? VideoCodecOption.H264_LIBX264 : videoCodec;
        speedPreset = speedPreset == null ? PresetSpeed.MEDIUM : speedPreset;
        resolution = resolution == null ? ResolutionPreset.ORIGINAL : resolution;
        audioCodec = audioCodec == null ? AudioCodecOption.AAC : audioCodec;
        profile = profile == null ? CompressionProfile.BALANCED : profile;
        if (crf < 0 || crf > 51) {
            crf = 26;
        }
        if (audioBitrateKbps <= 0) {
            audioBitrateKbps = 128;
        }
    }

    public static CompressOperation createBalanced() {
        return new CompressOperation(
                VideoCodecOption.H264_LIBX264,
                PresetSpeed.MEDIUM,
                26,
                null,
                ResolutionPreset.ORIGINAL,
                null,
                AudioCodecOption.AAC,
                128,
                CompressionProfile.BALANCED
        );
    }

    @Override
    public String name() {
        return "Video Compression (" + profile.name() + ")";
    }

    @Override
    public String description() {
        return "Compress video using " + videoCodec.label() + " (CRF " + crf + ") and " + audioBitrateKbps + "k audio";
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (crf < 0 || crf > 51) {
            errors.add("CRF must be between 0 and 51.");
        }
        if (capabilities != null) {
            if (videoCodec == VideoCodecOption.H264_LIBX264 && !capabilities.hasEncoder("libx264")) {
                errors.add("libx264 encoder is not available on your system.");
            }
            if (videoCodec == VideoCodecOption.H265_LIBX265 && !capabilities.hasEncoder("libx265")) {
                errors.add("libx265 encoder is not available on your system.");
            }
        }
        return errors;
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        // Map all streams by default
        cmd.add("-map");
        cmd.add("0");

        // Video filters (Scale & FPS)
        List<String> filters = new ArrayList<>();
        if (resolution != ResolutionPreset.ORIGINAL) {
            filters.add("scale=" + resolution.width() + ":-2:force_original_aspect_ratio=decrease");
        }
        if (targetFps != null && targetFps > 0) {
            filters.add("fps=" + targetFps);
        }
        if (!filters.isEmpty()) {
            cmd.add("-vf");
            cmd.add(String.join(",", filters));
        }

        // Video Codec & Encoding params
        if (videoCodec == VideoCodecOption.H265_LIBX265) {
            cmd.add("-c:v");
            cmd.add("libx265");
            cmd.add("-tag:v");
            cmd.add("hvc1");
        } else {
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        }

        cmd.add("-preset");
        cmd.add(speedPreset.value());

        if (targetVideoBitrateKbps != null && targetVideoBitrateKbps > 0) {
            cmd.add("-b:v");
            cmd.add(targetVideoBitrateKbps + "k");
        } else {
            cmd.add("-crf");
            cmd.add(String.valueOf(crf));
        }

        // Audio compression
        if (audioCodec == AudioCodecOption.NONE) {
            cmd.add("-an");
        } else if (audioCodec == AudioCodecOption.COPY) {
            cmd.add("-c:a");
            cmd.add("copy");
        } else {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add(audioBitrateKbps + "k");
        }

        // MP4 faststart
        cmd.add("-movflags");
        cmd.add("+faststart");

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("compressed_output.mp4");
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return defaultOutputDir.resolve(baseName + "_compressed.mp4");
    }
}
