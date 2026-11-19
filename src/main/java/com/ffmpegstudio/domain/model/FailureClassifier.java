package com.ffmpegstudio.domain.model;

/**
 * Classifies raw FFmpeg, I/O, and validation errors into structured user-friendly categories.
 */
public final class FailureClassifier {

    public enum FailureCategory {
        INVALID_FILE("Arquivo Inválido"),
        FILE_NOT_FOUND("Arquivo Inexistente"),
        PERMISSION_DENIED("Permissão Insuficiente"),
        CODEC_UNAVAILABLE("Codec Indisponível"),
        CONTAINER_INCOMPATIBLE("Contêiner Incompatível"),
        FILTER_UNAVAILABLE("Filtro Indisponível"),
        HARDWARE_FAILURE("Falha de Hardware"),
        DISK_SPACE("Espaço em Disco Insuficiente"),
        INTERRUPTED("Processo Interrompido"),
        INTERNAL_ERROR("Erro Interno");

        private final String label;
        FailureCategory(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record ClassifiedError(
            FailureCategory category,
            String friendlyMessage,
            String technicalDetails
    ) {}

    private FailureClassifier() {}

    public static ClassifiedError classify(String rawMessage) {
        String raw = rawMessage == null ? "" : rawMessage;
        String lower = raw.toLowerCase();

        if (lower.contains("no such file") || lower.contains("does not exist") || lower.contains("não encontrado")) {
            return new ClassifiedError(FailureCategory.FILE_NOT_FOUND,
                    "O arquivo ou diretório especificado não foi encontrado no disco.", raw);
        }
        if (lower.contains("permission denied") || lower.contains("access is denied") || lower.contains("permissão")) {
            return new ClassifiedError(FailureCategory.PERMISSION_DENIED,
                    "Permissão insuficiente para ler a entrada ou gravar no destino.", raw);
        }
        if (lower.contains("no space left") || lower.contains("disk full") || lower.contains("espaço")) {
            return new ClassifiedError(FailureCategory.DISK_SPACE,
                    "Espaço em disco insuficiente para concluir o arquivo de saída.", raw);
        }
        if (lower.contains("vaapi") || lower.contains("nvenc") || lower.contains("amf")
                || lower.contains("qsv") || lower.contains("libcuda") || lower.contains("no usable encoding profile")) {
            return new ClassifiedError(FailureCategory.HARDWARE_FAILURE,
                    "A aceleração por hardware falhou ao inicializar o encoder/driver.", raw);
        }
        if (lower.contains("unknown encoder") || lower.contains("encoder") && lower.contains("not available")
                || lower.contains("indisponível")) {
            return new ClassifiedError(FailureCategory.CODEC_UNAVAILABLE,
                    "O codec selecionado não está disponível na instalação local do FFmpeg.", raw);
        }
        if (lower.contains("no such filter") || lower.contains("filtro") && lower.contains("não disponível")) {
            return new ClassifiedError(FailureCategory.FILTER_UNAVAILABLE,
                    "Um dos filtros solicitados não está disponível no FFmpeg local.", raw);
        }
        if (lower.contains("could not write header") || lower.contains("incompatible") || lower.contains("incompatível")) {
            return new ClassifiedError(FailureCategory.CONTAINER_INCOMPATIBLE,
                    "O contêiner de saída não suporta uma das faixas ou codecs selecionados.", raw);
        }
        if (lower.contains("invalid data found") || lower.contains("moov atom not found") || lower.contains("inválido")) {
            return new ClassifiedError(FailureCategory.INVALID_FILE,
                    "O arquivo de entrada parece estar corrompido ou em formato inválido.", raw);
        }
        if (lower.contains("cancel") || lower.contains("interromp")) {
            return new ClassifiedError(FailureCategory.INTERRUPTED,
                    "A operação foi interrompida antes da conclusão.", raw);
        }
        return new ClassifiedError(FailureCategory.INTERNAL_ERROR,
                "Ocorreu uma falha durante o processamento multimídia.", raw);
    }
}
