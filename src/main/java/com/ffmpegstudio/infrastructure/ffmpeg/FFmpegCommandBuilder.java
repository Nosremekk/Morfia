package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ConversionSettings;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds structured, validated FFmpeg arguments safe for ProcessBuilder.
 */
public class FFmpegCommandBuilder {

    private String ffmpegBinary = "ffmpeg";
    private Path inputPath;
    private Path outputPath;
    private ConversionSettings settings = ConversionSettings.defaultMp4();
    private boolean overwrite = false;
    private boolean enableProgressPipe = true;

    public FFmpegCommandBuilder withFfmpegBinary(String binary) {
        if (binary != null && !binary.isBlank()) {
            this.ffmpegBinary = binary;
        }
        return this;
    }

    public FFmpegCommandBuilder withInput(Path inputPath) {
        this.inputPath = Objects.requireNonNull(inputPath, "inputPath must not be null");
        return this;
    }

    public FFmpegCommandBuilder withOutput(Path outputPath) {
        this.outputPath = Objects.requireNonNull(outputPath, "outputPath must not be null");
        return this;
    }

    public FFmpegCommandBuilder withSettings(ConversionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        return this;
    }

    public FFmpegCommandBuilder withOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public FFmpegCommandBuilder withProgressPipe(boolean enableProgressPipe) {
        this.enableProgressPipe = enableProgressPipe;
        return this;
    }

    public List<String> build() {
        validate();

        List<String> command = new ArrayList<>();
        command.add(ffmpegBinary);

        // Global input options
        command.add("-nostdin");
        command.add("-hide_banner");

        // Overwrite policy
        if (overwrite) {
            command.add("-y");
        } else {
            command.add("-n");
        }

        if (settings.videoCodec() == VideoCodecOption.H264_VAAPI || settings.videoCodec() == VideoCodecOption.H265_VAAPI) {
            command.add("-vaapi_device");
            command.add("/dev/dri/renderD128");
        }

        // Input file
        command.add("-i");
        command.add(inputPath.toAbsolutePath().toString());

        // Progress reporting
        if (enableProgressPipe) {
            command.add("-progress");
            command.add("pipe:1");
            command.add("-nostats");
        }

        // Stream selection
        appendStreamMapping(command);

        // Video filters
        appendVideoFilters(command);

        // Video codec & encoding options
        appendVideoCodecOptions(command);

        // Audio codec & encoding options
        appendAudioCodecOptions(command);

        // Destination container faststart flag for MP4 web playback
        if (settings.container().extension().equalsIgnoreCase("mp4") && settings.videoCodec() != VideoCodecOption.NONE) {
            command.add("-movflags");
            command.add("+faststart");
        }

        // Output destination
        command.add(outputPath.toAbsolutePath().toString());

        return Collections.unmodifiableList(command);
    }

    private void validate() {
        if (inputPath == null) {
            throw new IllegalStateException("Input path must be provided before building command.");
        }
        if (outputPath == null) {
            throw new IllegalStateException("Output path must be provided before building command.");
        }
        if (inputPath.toAbsolutePath().normalize().equals(outputPath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Output path must not match input path. Original file would be destroyed.");
        }
    }

    private void appendStreamMapping(List<String> command) {
        if (settings.videoStreamIndex() != null) {
            command.add("-map");
            command.add("0:" + settings.videoStreamIndex());
        }
        if (settings.audioStreamIndex() != null) {
            command.add("-map");
            command.add("0:" + settings.audioStreamIndex());
        }
        if (settings.subtitleStreamIndex() != null) {
            command.add("-map");
            command.add("0:" + settings.subtitleStreamIndex());
        }

        // If no explicit streams were picked and mapAllStreams is set
        if (settings.videoStreamIndex() == null &&
            settings.audioStreamIndex() == null &&
            settings.subtitleStreamIndex() == null &&
            settings.mapAllStreams()) {
            command.add("-map");
            command.add("0");
        }
    }

    private void appendVideoFilters(List<String> command) {
        if (settings.videoCodec() == VideoCodecOption.COPY || settings.videoCodec() == VideoCodecOption.NONE) {
            return;
        }

        List<String> filters = new ArrayList<>();

        // Scaling filter
        if (settings.resolution() != ResolutionPreset.ORIGINAL) {
            if (settings.resolution() == ResolutionPreset.CUSTOM) {
                if (settings.customWidth() != null && settings.customHeight() != null) {
                    filters.add("scale=" + settings.customWidth() + ":" + settings.customHeight() + ":force_original_aspect_ratio=decrease");
                }
            } else {
                filters.add("scale=" + settings.resolution().width() + ":" + settings.resolution().height() + ":force_original_aspect_ratio=decrease");
            }
        }

        // FPS filter
        if (settings.targetFps() != null && settings.targetFps() > 0) {
            filters.add("fps=" + settings.targetFps());
        }

        if (settings.videoCodec() == VideoCodecOption.H264_VAAPI || settings.videoCodec() == VideoCodecOption.H265_VAAPI) {
            filters.add("format=nv12");
            filters.add("hwupload");
        }

        if (!filters.isEmpty()) {
            command.add("-vf");
            command.add(String.join(",", filters));
        }
    }

    private void appendVideoCodecOptions(List<String> command) {
        switch (settings.videoCodec()) {
            case NONE -> command.add("-vn");
            case COPY -> {
                command.add("-c:v");
                command.add("copy");
            }
            case H264_LIBX264 -> {
                command.add("-c:v");
                command.add("libx264");
                command.add("-preset");
                command.add(settings.speedPreset().value());

                if (settings.videoBitrateKbps() != null && settings.videoBitrateKbps() > 0) {
                    command.add("-b:v");
                    command.add(settings.videoBitrateKbps() + "k");
                } else if (settings.crf() != null) {
                    command.add("-crf");
                    command.add(String.valueOf(settings.crf()));
                }

                command.add("-pix_fmt");
                command.add("yuv420p");
            }
            case H265_LIBX265 -> {
                command.add("-c:v");
                command.add("libx265");
                command.add("-preset");
                command.add(settings.speedPreset().value());

                if (settings.videoBitrateKbps() != null && settings.videoBitrateKbps() > 0) {
                    command.add("-b:v");
                    command.add(settings.videoBitrateKbps() + "k");
                } else if (settings.crf() != null) {
                    command.add("-crf");
                    command.add(String.valueOf(settings.crf()));
                }

                command.add("-tag:v");
                command.add("hvc1");
            }
            case H264_VAAPI, H265_VAAPI -> {
                command.add("-c:v");
                command.add(settings.videoCodec().encoderName());
                if (settings.videoBitrateKbps() != null && settings.videoBitrateKbps() > 0) {
                    command.add("-b:v");
                    command.add(settings.videoBitrateKbps() + "k");
                } else if (settings.crf() != null) {
                    command.add("-qp");
                    command.add(String.valueOf(settings.crf()));
                }
            }
            case VP9_LIBVPX -> {
                command.add("-c:v");
                command.add("libvpx-vp9");
                if (settings.crf() != null) {
                    command.add("-crf");
                    command.add(String.valueOf(settings.crf()));
                    command.add("-b:v");
                    command.add("0");
                }
            }
        }
    }

    private void appendAudioCodecOptions(List<String> command) {
        switch (settings.audioCodec()) {
            case NONE -> command.add("-an");
            case COPY -> {
                command.add("-c:a");
                command.add("copy");
            }
            case AAC -> {
                command.add("-c:a");
                command.add("aac");
                command.add("-b:a");
                command.add(settings.audioBitrateKbps() + "k");
            }
        }
    }

    /**
     * Produces a human-readable shell-quoted representation of the command.
     */
    public String toDisplayString() {
        List<String> cmd = build();
        StringBuilder sb = new StringBuilder();
        for (String arg : cmd) {
            if (arg.contains(" ") || arg.contains("\t") || arg.contains("\"") || arg.contains(":")) {
                sb.append("\"").append(arg.replace("\"", "\\\"")).append("\" ");
            } else {
                sb.append(arg).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
