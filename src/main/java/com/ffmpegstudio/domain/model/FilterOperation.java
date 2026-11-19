package com.ffmpegstudio.domain.model;

import com.ffmpegstudio.domain.model.filter.FilterGraphBuilder;
import com.ffmpegstudio.domain.model.filter.FilterValidationResult;
import com.ffmpegstudio.domain.model.filter.MediaFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Media operation applying an ordered chain of typed video and audio filters.
 */
public record FilterOperation(
        List<MediaFilter> filters,
        ConversionSettings encodeSettings
) implements MediaOperation {

    private static final FilterGraphBuilder GRAPH_BUILDER = new FilterGraphBuilder();

    public FilterOperation {
        filters = filters == null ? List.of() : List.copyOf(filters);
        if (encodeSettings == null) {
            encodeSettings = ConversionSettings.defaultMp4();
        }
    }

    @Override
    public String name() {
        return "Pipeline de Filtros (" + filters.size() + ")";
    }

    @Override
    public String description() {
        if (filters.isEmpty()) return "Nenhum filtro selecionado";
        List<String> names = filters.stream().map(MediaFilter::displayName).limit(3).toList();
        String joined = String.join(" -> ", names);
        return filters.size() > 3 ? joined + " (+" + (filters.size() - 3) + ")" : joined;
    }

    public FilterValidationResult validateDetailed(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        return GRAPH_BUILDER.validate(filters, mediaFile, capabilities);
    }

    @Override
    public List<String> validate(MediaFile mediaFile, FFmpegCapabilities capabilities) {
        return validateDetailed(mediaFile, capabilities).errors();
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile mediaFile, Path outputPath, boolean enableProgressPipe) {
        Path input = mediaFile != null ? mediaFile.path() : Paths.get("input.mp4");
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

        cmd.add("-map");
        cmd.add("0");

        String vf = GRAPH_BUILDER.buildVideoFilterChain(filters);
        String af = GRAPH_BUILDER.buildAudioFilterChain(filters);

        if (!vf.isBlank()) {
            cmd.add("-vf");
            cmd.add(vf);
            String vEncoder = encodeSettings.videoCodec() != VideoCodecOption.COPY && encodeSettings.videoCodec() != VideoCodecOption.NONE
                    ? encodeSettings.videoCodec().encoderName()
                    : "libx264";
            cmd.add("-c:v");
            cmd.add(vEncoder);
            cmd.add("-preset");
            cmd.add(encodeSettings.speedPreset().value());
            cmd.add("-crf");
            cmd.add(String.valueOf(encodeSettings.crf() != null ? encodeSettings.crf() : 23));
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        } else {
            cmd.add("-c:v");
            cmd.add("copy");
        }

        if (!af.isBlank()) {
            cmd.add("-af");
            cmd.add(af);
            String aEncoder = encodeSettings.audioCodec() != AudioCodecOption.COPY && encodeSettings.audioCodec() != AudioCodecOption.NONE
                    ? encodeSettings.audioCodec().encoderName()
                    : "aac";
            cmd.add("-c:a");
            cmd.add(aEncoder);
            if (!"flac".equals(aEncoder) && !"pcm_s16le".equals(aEncoder)) {
                cmd.add("-b:a");
                cmd.add(encodeSettings.audioBitrateKbps() + "k");
            }
        } else {
            cmd.add("-c:a");
            cmd.add("copy");
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile mediaFile, Path defaultOutputDir) {
        String ext = encodeSettings.container() != null ? encodeSettings.container().extension() : "mp4";
        if (mediaFile == null || mediaFile.fileName() == null) {
            return defaultOutputDir.resolve("filtered_output." + ext);
        }
        String fn = mediaFile.fileName();
        String base = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
        return defaultOutputDir.resolve(base + "_filtered." + ext);
    }
}
