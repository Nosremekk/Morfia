package com.ffmpegstudio.domain.model.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed immutable audio filter definitions.
 */
public final class AudioFilters {
    private AudioFilters() {}

    public sealed interface AudioFilter extends MediaFilter
            permits Volume, AudioFade, AudioSpeed, Equalizer, Loudnorm, Channels {
        @Override
        default StreamKind streamKind() { return StreamKind.AUDIO; }

        @Override
        default FilterCategory category() { return FilterCategory.AUDIO; }
    }

    public record Volume(double multiplier) implements AudioFilter {
        @Override public String id() { return "volume"; }
        @Override public String displayName() { return String.format(Locale.US, "Volume (%d%%)", Math.round(multiplier * 100)); }
        @Override public String requiredFFmpegFilter() { return "volume"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "volume=%.2f", multiplier);
        }
    }

    public record AudioFade(boolean fadeIn, double startSec, double durationSec) implements AudioFilter {
        @Override public String id() { return fadeIn ? "afade_in" : "afade_out"; }
        @Override public String displayName() {
            return String.format(Locale.US, "%s Áudio (%.1fs @ %.1fs)", fadeIn ? "Fade In" : "Fade Out", durationSec, startSec);
        }
        @Override public String requiredFFmpegFilter() { return "afade"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "afade=t=%s:st=%.2f:d=%.2f", fadeIn ? "in" : "out", startSec, durationSec);
        }
        @Override public boolean isTemporal() { return true; }
    }

    public record AudioSpeed(double multiplier) implements AudioFilter {
        @Override public String id() { return "atempo"; }
        @Override public String displayName() { return String.format(Locale.US, "Velocidade Áudio (%.2fx)", multiplier); }
        @Override public String requiredFFmpegFilter() { return "atempo"; }
        @Override public boolean isTemporal() { return true; }

        @Override
        public String toFilterExpression() {
            // FFmpeg atempo supports [0.5, 2.0] per stage; chain stages for values outside that range
            double remaining = Math.clamp(multiplier, 0.25, 4.0);
            List<String> stages = new ArrayList<>();
            while (remaining > 2.0) {
                stages.add("atempo=2.0");
                remaining /= 2.0;
            }
            while (remaining < 0.5) {
                stages.add("atempo=0.5");
                remaining /= 0.5;
            }
            stages.add(String.format(Locale.US, "atempo=%.4f", remaining));
            return String.join(",", stages);
        }
    }

    public record Equalizer(double bassGainDb, double midGainDb, double trebleGainDb) implements AudioFilter {
        @Override public String id() { return "equalizer"; }
        @Override public String displayName() {
            return String.format(Locale.US, "Equalizador (Graves %.1fdB, Médios %.1fdB, Agudos %.1fdB)",
                    bassGainDb, midGainDb, trebleGainDb);
        }
        @Override public String requiredFFmpegFilter() { return "equalizer"; }

        @Override
        public String toFilterExpression() {
            List<String> parts = new ArrayList<>();
            if (Math.abs(bassGainDb) > 0.1) {
                parts.add(String.format(Locale.US, "bass=g=%.1f", bassGainDb));
            }
            if (Math.abs(midGainDb) > 0.1) {
                parts.add(String.format(Locale.US, "equalizer=f=1000:t=q:w=1:g=%.1f", midGainDb));
            }
            if (Math.abs(trebleGainDb) > 0.1) {
                parts.add(String.format(Locale.US, "treble=g=%.1f", trebleGainDb));
            }
            return parts.isEmpty() ? "anull" : String.join(",", parts);
        }
    }

    public record Loudnorm(double integratedTarget, double truePeak, double lra) implements AudioFilter {
        public static Loudnorm standardEbuR128() {
            return new Loudnorm(-16.0, -1.5, 11.0);
        }
        @Override public String id() { return "loudnorm"; }
        @Override public String displayName() { return "Normalização EBU R128 (Loudnorm)"; }
        @Override public String requiredFFmpegFilter() { return "loudnorm"; }
        @Override public String toFilterExpression() {
            return String.format(Locale.US, "loudnorm=I=%.1f:TP=%.1f:LRA=%.1f", integratedTarget, truePeak, lra);
        }
    }

    public record Channels(int channelCount) implements AudioFilter {
        @Override public String id() { return "aformat_channels"; }
        @Override public String displayName() { return channelCount == 1 ? "Converter para Mono (1.0)" : "Converter para Estéreo (2.0)"; }
        @Override public String requiredFFmpegFilter() { return "aformat"; }
        @Override public String toFilterExpression() {
            return channelCount == 1 ? "aformat=channel_layouts=mono" : "aformat=channel_layouts=stereo";
        }
    }
}
