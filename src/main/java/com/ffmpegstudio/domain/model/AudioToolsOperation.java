package com.ffmpegstudio.domain.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Audio manipulation operation: extraction, audio format conversion, volume adjustment,
 * channel mixdown (mono/stereo), sample rate conversion and EBU R128 loudnorm normalization.
 */
public record AudioToolsOperation(
        AudioOutputFormat format,
        Integer sampleRate,
        Integer channels,
        int bitrateKbps,
        Double volumeMultiplier,
        boolean enableLoudnorm,
        Integer audioTrackIndex,
        LoudnormMode loudnormMode,
        LoudnormMetrics measuredMetrics
) implements MediaOperation {

    public enum LoudnormMode {
        SINGLE_PASS_DYNAMIC("Passagem Única (Dinâmica)"),
        TWO_PASS_LINEAR("Duas Passagens (Linear EBU R128)");

        private final String label;
        LoudnormMode(String label) { this.label = label; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public AudioToolsOperation(
            AudioOutputFormat format,
            Integer sampleRate,
            Integer channels,
            int bitrateKbps,
            Double volumeMultiplier,
            boolean enableLoudnorm,
            Integer audioTrackIndex
    ) {
        this(format, sampleRate, channels, bitrateKbps, volumeMultiplier, enableLoudnorm, audioTrackIndex,
                LoudnormMode.SINGLE_PASS_DYNAMIC, null);
    }

    public enum AudioOutputFormat {
        MP3("mp3", "libmp3lame", "MP3 Audio (libmp3lame)"),
        AAC("m4a", "aac", "AAC Audio (M4A container)"),
        FLAC("flac", "flac", "FLAC Lossless Audio"),
        WAV("wav", "pcm_s16le", "WAV Uncompressed PCM");

        private final String extension;
        private final String encoderName;
        private final String description;

        AudioOutputFormat(String extension, String encoderName, String description) {
            this.extension = extension;
            this.encoderName = encoderName;
            this.description = description;
        }

        public String extension() { return extension; }
        public String encoderName() { return encoderName; }
        public String description() { return description; }
    }

    public AudioToolsOperation {
        format = format == null ? AudioOutputFormat.MP3 : format;
        loudnormMode = loudnormMode == null ? LoudnormMode.SINGLE_PASS_DYNAMIC : loudnormMode;
        if (bitrateKbps <= 0) {
            bitrateKbps = 192;
        }
    }

    public AudioToolsOperation withMeasuredMetrics(LoudnormMetrics metrics) {
        return new AudioToolsOperation(format, sampleRate, channels, bitrateKbps, volumeMultiplier,
                enableLoudnorm, audioTrackIndex, loudnormMode, metrics);
    }

    public List<String> buildFirstPassAnalysisCommand(String ffmpegBinary, MediaFile mediaFile) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input");
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add("-vn");
        if (audioTrackIndex != null) {
            cmd.add("-map");
            cmd.add("0:a:" + audioTrackIndex);
        } else {
            cmd.add("-map");
            cmd.add("0:a:0?");
        }
        cmd.add("-af");
        cmd.add("loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json");
        cmd.add("-f");
        cmd.add("null");
        cmd.add("-");
        return Collections.unmodifiableList(cmd);
    }

    public static AudioToolsOperation extractMp3(int bitrateKbps) {
        return new AudioToolsOperation(AudioOutputFormat.MP3, null, null, bitrateKbps, 1.0, false, null);
    }

    public static AudioToolsOperation extractFlac() {
        return new AudioToolsOperation(AudioOutputFormat.FLAC, null, null, 0, 1.0, false, null);
    }

    @Override
    public String name() {
        return "Audio Tool (" + format.name() + (enableLoudnorm ? " + Loudnorm" : "") + ")";
    }

    @Override
    public String description() {
        return "Process audio to " + format.description() + " (" + bitrateKbps + " kbps)";
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (mediaFile != null && mediaFile.metadata() != null) {
            if (mediaFile.metadata().audioStreams().isEmpty()) {
                errors.add("The input file contains no audio streams.");
            }
        }
        if (capabilities != null) {
            if (!capabilities.hasEncoder(format.encoderName())) {
                errors.add("Required audio encoder '" + format.encoderName() + "' is not installed in local FFmpeg.");
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

        // Disable video completely
        cmd.add("-vn");

        // Select audio stream
        if (audioTrackIndex != null) {
            cmd.add("-map");
            cmd.add("0:a:" + audioTrackIndex);
        } else {
            cmd.add("-map");
            cmd.add("0:a:0?");
        }

        // Audio Filters (volume adjustment & loudnorm)
        List<String> audioFilters = new ArrayList<>();
        if (volumeMultiplier != null && Math.abs(volumeMultiplier - 1.0) > 0.01) {
            audioFilters.add("volume=" + String.format(java.util.Locale.US, "%.2f", volumeMultiplier));
        }
        if (enableLoudnorm) {
            if (measuredMetrics != null && measuredMetrics.isValidForLinearNormalization()) {
                audioFilters.add(measuredMetrics.toSecondPassFilter(-16.0, -1.5, 11.0));
            } else {
                // EBU R128 dynamic loudness normalization (or fallback)
                audioFilters.add("loudnorm=I=-16:TP=-1.5:LRA=11");
            }
        }
        if (!audioFilters.isEmpty()) {
            cmd.add("-af");
            cmd.add(String.join(",", audioFilters));
        }

        // Codec
        cmd.add("-c:a");
        cmd.add(format.encoderName());

        // Bitrate (applicable to lossy formats like MP3/AAC)
        if (format == AudioOutputFormat.MP3 || format == AudioOutputFormat.AAC) {
            cmd.add("-b:a");
            cmd.add(bitrateKbps + "k");
        }

        // Sample rate
        if (sampleRate != null && sampleRate > 0) {
            cmd.add("-ar");
            cmd.add(String.valueOf(sampleRate));
        }

        // Channels (1 = mono, 2 = stereo)
        if (channels != null && channels > 0) {
            cmd.add("-ac");
            cmd.add(String.valueOf(channels));
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        if (mediaFile == null || mediaFile.path() == null) {
            return defaultOutputDir.resolve("audio_extracted." + format.extension());
        }
        String fileName = mediaFile.fileName();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return defaultOutputDir.resolve(baseName + "_audio." + format.extension());
    }
}
