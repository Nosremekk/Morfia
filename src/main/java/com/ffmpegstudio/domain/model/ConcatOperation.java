package com.ffmpegstudio.domain.model;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Operation for joining multiple video or audio files sequentially.
 * Supports fast stream-copy concatenation (concat demuxer) and compatible
 * normalized re-encoding concatenation (concat filter).
 */
public record ConcatOperation(
        List<MediaFile> inputs,
        ConcatMode mode,
        ConversionSettings targetSettings,
        Path concatListFile
) implements MediaOperation {

    public enum ConcatMode {
        FAST_DEMUXER("Concatenação Rápida (Stream Copy, sem recodificação)"),
        COMPATIBLE_REENCODE("Concatenação Compatível (Normalização + Recodificação)");

        private final String label;

        ConcatMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public ConcatOperation {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        mode = mode == null ? ConcatMode.COMPATIBLE_REENCODE : mode;
        if (targetSettings == null) {
            targetSettings = ConversionSettings.defaultMp4();
        }
    }

    public ConcatOperation(List<MediaFile> inputs, ConcatMode mode, ConversionSettings targetSettings) {
        this(inputs, mode, targetSettings, null);
    }

    public ConcatOperation withConcatListFile(Path listFile) {
        return new ConcatOperation(inputs, mode, targetSettings, listFile);
    }

    @Override
    public String name() {
        return mode == ConcatMode.FAST_DEMUXER
                ? "Concatenação Rápida (" + inputs.size() + " arquivos)"
                : "Concatenação Compatível (" + inputs.size() + " arquivos)";
    }

    @Override
    public String description() {
        return String.format("%s — %d clipes (Duração total est.: %s)",
                mode.label(), inputs.size(), FormatUtils.formatDuration(totalDuration()));
    }

    public Duration totalDuration() {
        Duration total = Duration.ZERO;
        for (MediaFile mf : inputs) {
            if (mf.metadata() != null && mf.metadata().duration() != null) {
                total = total.plus(mf.metadata().duration());
            }
        }
        return total;
    }

    /**
     * Escapes a file path for FFmpeg's concat demuxer list file syntax.
     * Single quotes are escaped as '\'' inside single-quoted paths.
     */
    public static String escapeConcatDemuxerPath(Path path) {
        String abs = path.toAbsolutePath().toString();
        return "'" + abs.replace("'", "'\\''") + "'";
    }

    public String generateConcatListContent() {
        StringBuilder sb = new StringBuilder();
        for (MediaFile mf : inputs) {
            sb.append("file ").append(escapeConcatDemuxerPath(mf.path())).append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<String> validate(MediaFile ignoredPrimary, FFmpegCapabilities capabilities) {
        List<String> errors = new ArrayList<>();
        if (inputs.size() < 2) {
            errors.add("Selecione pelo menos 2 arquivos para concatenar.");
            return errors;
        }

        for (MediaFile mf : inputs) {
            if (mf.path() == null || !Files.exists(mf.path())) {
                errors.add("Arquivo não encontrado: " + (mf.path() != null ? mf.path() : "nulo"));
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        if (mode == ConcatMode.FAST_DEMUXER) {
            validateFastCompatibility(errors);
        } else {
            boolean anyVideo = inputs.stream().anyMatch(m -> m.metadata() != null && !m.metadata().videoStreams().isEmpty());
            boolean anyAudio = inputs.stream().anyMatch(m -> m.metadata() != null && !m.metadata().audioStreams().isEmpty());
            if (!anyVideo && !anyAudio) {
                errors.add("Os arquivos selecionados não possuem streams de vídeo ou áudio válidos.");
            }
            if (capabilities != null && anyVideo) {
                String vEncoder = targetSettings.videoCodec().encoderName();
                if (!"copy".equals(vEncoder) && !vEncoder.isBlank() && !capabilities.hasEncoder(vEncoder)) {
                    errors.add("Encoder de vídeo indisponível no FFmpeg: " + vEncoder);
                }
            }
        }
        return errors;
    }

    private void validateFastCompatibility(List<String> errors) {
        MediaFile first = inputs.get(0);
        if (first.metadata() == null) {
            errors.add("Metadados ausentes para o primeiro clipe. Analise os arquivos antes da concatenação rápida.");
            return;
        }

        var firstVideoOpt = first.metadata().primaryVideoStream();
        var firstAudioList = first.metadata().audioStreams();
        boolean firstHasVideo = firstVideoOpt.isPresent();
        boolean firstHasAudio = !firstAudioList.isEmpty();

        for (int i = 1; i < inputs.size(); i++) {
            MediaFile current = inputs.get(i);
            if (current.metadata() == null) {
                errors.add("Metadados ausentes para o clipe #" + (i + 1) + " (" + current.fileName() + ").");
                continue;
            }
            var curVideoOpt = current.metadata().primaryVideoStream();
            var curAudioList = current.metadata().audioStreams();

            if (curVideoOpt.isPresent() != firstHasVideo) {
                errors.add("Incompatibilidade de vídeo no clipe '" + current.fileName() + "': presença de faixa de vídeo difere. Use Concatenação Compatível.");
                continue;
            }
            if (curVideoOpt.isPresent()) {
                VideoStream v0 = firstVideoOpt.get();
                VideoStream vi = curVideoOpt.get();
                if (!Objects.equals(v0.codecName(), vi.codecName())) {
                    errors.add("Codec de vídeo incompatível em '" + current.fileName() + "' (" + vi.codecName() + " vs " + v0.codecName() + "). Use Concatenação Compatível.");
                }
                if (v0.width() != vi.width() || v0.height() != vi.height()) {
                    errors.add("Resolução incompatível em '" + current.fileName() + "' (" + vi.width() + "x" + vi.height() + " vs " + v0.width() + "x" + v0.height() + "). Use Concatenação Compatível.");
                }
                if (!Objects.equals(v0.pixelFormat(), vi.pixelFormat())) {
                    errors.add("Pixel format incompatível em '" + current.fileName() + "' (" + vi.pixelFormat() + " vs " + v0.pixelFormat() + "). Use Concatenação Compatível.");
                }
            }

            if ((!curAudioList.isEmpty()) != firstHasAudio) {
                errors.add("Incompatibilidade de áudio no clipe '" + current.fileName() + "': presença de faixa de áudio difere. Use Concatenação Compatível.");
            } else if (firstHasAudio) {
                AudioStream a0 = firstAudioList.get(0);
                AudioStream ai = curAudioList.get(0);
                if (!Objects.equals(a0.codecName(), ai.codecName())) {
                    errors.add("Codec de áudio incompatível em '" + current.fileName() + "' (" + ai.codecName() + " vs " + a0.codecName() + "). Use Concatenação Compatível.");
                }
                if (a0.sampleRate() != ai.sampleRate()) {
                    errors.add("Sample rate incompatível em '" + current.fileName() + "' (" + ai.sampleRate() + " Hz vs " + a0.sampleRate() + " Hz). Use Concatenação Compatível.");
                }
                if (a0.channels() != ai.channels()) {
                    errors.add("Canais de áudio incompatíveis em '" + current.fileName() + "' (" + ai.channels() + " vs " + a0.channels() + "). Use Concatenação Compatível.");
                }
            }
        }
    }

    @Override
    public List<String> buildCommand(String ffmpegBinary, MediaFile ignoredPrimary, Path outputPath, boolean enableProgressPipe) {
        if (mode == ConcatMode.FAST_DEMUXER) {
            return buildFastDemuxerCommand(ffmpegBinary, outputPath, enableProgressPipe);
        }
        return buildCompatibleFilterCommand(ffmpegBinary, outputPath, enableProgressPipe);
    }

    private List<String> buildFastDemuxerCommand(String ffmpegBinary, Path outputPath, boolean enableProgressPipe) {
        Path listPath = concatListFile != null
                ? concatListFile
                : outputPath.getParent().resolve(".~tmp_concat_list.txt");

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");
        cmd.add("-f");
        cmd.add("concat");
        cmd.add("-safe");
        cmd.add("0");
        cmd.add("-i");
        cmd.add(listPath.toAbsolutePath().toString());

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        cmd.add("-c");
        cmd.add("copy");
        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    private List<String> buildCompatibleFilterCommand(String ffmpegBinary, Path outputPath, boolean enableProgressPipe) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBinary);
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-y");

        for (MediaFile mf : inputs) {
            cmd.add("-i");
            cmd.add(mf.path().toAbsolutePath().toString());
        }

        if (enableProgressPipe) {
            cmd.add("-progress");
            cmd.add("pipe:1");
            cmd.add("-nostats");
        }

        boolean anyVideo = inputs.stream().anyMatch(m -> m.metadata() != null && !m.metadata().videoStreams().isEmpty());
        boolean anyAudio = inputs.stream().anyMatch(m -> m.metadata() != null && !m.metadata().audioStreams().isEmpty());
        // Fallback if metadata was not populated in unit test
        if (!anyVideo && !anyAudio) {
            anyVideo = true;
            anyAudio = true;
        }

        int targetW = resolveTargetWidth();
        int targetH = resolveTargetHeight();
        double targetFps = resolveTargetFps();

        StringBuilder filter = new StringBuilder();
        StringBuilder concatInputs = new StringBuilder();

        for (int i = 0; i < inputs.size(); i++) {
            MediaFile mf = inputs.get(i);
            boolean hasVideo = mf.metadata() == null || !mf.metadata().videoStreams().isEmpty();
            boolean hasAudio = mf.metadata() == null || !mf.metadata().audioStreams().isEmpty();

            if (anyVideo) {
                if (hasVideo) {
                    filter.append(String.format(Locale.US,
                            "[%d:v]scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=%.3f,format=yuv420p[v%d];",
                            i, targetW, targetH, targetW, targetH, targetFps, i));
                } else {
                    double dur = mf.metadata() != null && mf.metadata().duration() != null && !mf.metadata().duration().isZero()
                            ? mf.metadata().duration().toMillis() / 1000.0
                            : 1.0;
                    filter.append(String.format(Locale.US,
                            "color=c=black:s=%dx%d:r=%.3f:d=%.3f,format=yuv420p[v%d];",
                            targetW, targetH, targetFps, dur, i));
                }
                concatInputs.append("[v").append(i).append("]");
            }

            if (anyAudio) {
                if (hasAudio) {
                    filter.append(String.format(Locale.US,
                            "[%d:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo[a%d];",
                            i, i));
                } else {
                    double dur = mf.metadata() != null && mf.metadata().duration() != null && !mf.metadata().duration().isZero()
                            ? mf.metadata().duration().toMillis() / 1000.0
                            : 1.0;
                    filter.append(String.format(Locale.US,
                            "anullsrc=channel_layout=stereo:sample_rate=48000:d=%.3f[a%d];",
                            dur, i));
                }
                concatInputs.append("[a").append(i).append("]");
            }
        }

        filter.append(concatInputs);
        filter.append("concat=n=").append(inputs.size())
                .append(":v=").append(anyVideo ? 1 : 0)
                .append(":a=").append(anyAudio ? 1 : 0);
        if (anyVideo) filter.append("[outv]");
        if (anyAudio) filter.append("[outa]");

        cmd.add("-filter_complex");
        cmd.add(filter.toString());

        if (anyVideo) {
            cmd.add("-map");
            cmd.add("[outv]");
            String vCodec = targetSettings.videoCodec() != VideoCodecOption.COPY && targetSettings.videoCodec() != VideoCodecOption.NONE
                    ? targetSettings.videoCodec().encoderName()
                    : "libx264";
            cmd.add("-c:v");
            cmd.add(vCodec);
            cmd.add("-preset");
            cmd.add(targetSettings.speedPreset().value());
            cmd.add("-crf");
            cmd.add(String.valueOf(targetSettings.crf() != null ? targetSettings.crf() : 23));
            cmd.add("-pix_fmt");
            cmd.add("yuv420p");
        } else {
            cmd.add("-vn");
        }

        if (anyAudio) {
            cmd.add("-map");
            cmd.add("[outa]");
            String aCodec = targetSettings.audioCodec() != AudioCodecOption.COPY && targetSettings.audioCodec() != AudioCodecOption.NONE
                    ? targetSettings.audioCodec().encoderName()
                    : "aac";
            cmd.add("-c:a");
            cmd.add(aCodec);
            if (!"flac".equals(aCodec) && !"pcm_s16le".equals(aCodec)) {
                cmd.add("-b:a");
                cmd.add(targetSettings.audioBitrateKbps() + "k");
            }
        } else {
            cmd.add("-an");
        }

        cmd.add(outputPath.toAbsolutePath().toString());
        return Collections.unmodifiableList(cmd);
    }

    private int resolveTargetWidth() {
        if (targetSettings.resolution() != null && targetSettings.resolution() != ResolutionPreset.ORIGINAL && targetSettings.resolution() != ResolutionPreset.CUSTOM) {
            return targetSettings.resolution().width();
        }
        return inputs.stream()
                .filter(m -> m.metadata() != null && m.metadata().primaryVideoStream().isPresent())
                .mapToInt(m -> m.metadata().primaryVideoStream().get().width())
                .filter(w -> w > 0)
                .findFirst()
                .orElse(1280);
    }

    private int resolveTargetHeight() {
        if (targetSettings.resolution() != null && targetSettings.resolution() != ResolutionPreset.ORIGINAL && targetSettings.resolution() != ResolutionPreset.CUSTOM) {
            return targetSettings.resolution().height();
        }
        return inputs.stream()
                .filter(m -> m.metadata() != null && m.metadata().primaryVideoStream().isPresent())
                .mapToInt(m -> m.metadata().primaryVideoStream().get().height())
                .filter(h -> h > 0)
                .findFirst()
                .orElse(720);
    }

    private double resolveTargetFps() {
        if (targetSettings.targetFps() != null && targetSettings.targetFps() > 0) {
            return targetSettings.targetFps();
        }
        return inputs.stream()
                .filter(m -> m.metadata() != null && m.metadata().primaryVideoStream().isPresent())
                .mapToDouble(m -> m.metadata().primaryVideoStream().get().nominalFps())
                .filter(fps -> fps > 0 && fps <= 240)
                .findFirst()
                .orElse(30.0);
    }

    @Override
    public Path getSuggestedOutputPath(MediaFile ignored, Path defaultOutputDir) {
        String ext = targetSettings.container() != null ? targetSettings.container().extension() : "mp4";
        if (!inputs.isEmpty()) {
            String first = inputs.get(0).fileName();
            String base = first.contains(".") ? first.substring(0, first.lastIndexOf('.')) : first;
            return defaultOutputDir.resolve(base + "_merged." + ext);
        }
        return defaultOutputDir.resolve("merged_output." + ext);
    }
}
