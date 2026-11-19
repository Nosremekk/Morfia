package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.HardwareEncoderProbeResult;
import com.ffmpegstudio.domain.model.HardwareProbeReport;

import java.time.Instant;
import java.util.Objects;

/**
 * Generates sanitized local diagnostic reports without telemetry or private user paths by default.
 */
public class DiagnosticsService {
    private static final String APP_VERSION = "0.1.0-SNAPSHOT";

    private final DetectCapabilitiesUseCase capabilitiesUseCase;
    private final ManageSettingsUseCase settingsUseCase;
    private final HardwareAccelerationService hardwareService;

    public DiagnosticsService(
            DetectCapabilitiesUseCase capabilitiesUseCase,
            ManageSettingsUseCase settingsUseCase,
            HardwareAccelerationService hardwareService
    ) {
        this.capabilitiesUseCase = Objects.requireNonNull(capabilitiesUseCase);
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase);
        this.hardwareService = Objects.requireNonNull(hardwareService);
    }

    public String generateReport(boolean includePaths) {
        AppSettings settings = settingsUseCase.getSettings();
        FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(settings.ffmpegPath());
        HardwareProbeReport hw = hardwareService.getLastReport();

        StringBuilder sb = new StringBuilder();
        sb.append(com.ffmpegstudio.domain.model.AppIdentity.diagnosticsHeader()).append("\n");
        sb.append("Gerado em: ").append(Instant.now()).append("\n");
        sb.append("Aplicação: ").append(com.ffmpegstudio.domain.model.AppIdentity.DISPLAY_NAME)
                .append(" ").append(com.ffmpegstudio.domain.model.AppIdentity.VERSION).append("\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n");
        sb.append("JavaFX: ").append(System.getProperty("javafx.version", "24")).append("\n");
        sb.append("SO: ").append(System.getProperty("os.name")).append(" ")
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n\n");

        sb.append("--- Engine FFmpeg ---\n");
        sb.append("Versão FFmpeg: ").append(caps.version()).append("\n");
        sb.append("Encoders de Vídeo: ").append(caps.videoEncodersCount()).append("\n");
        sb.append("Encoders de Áudio: ").append(caps.audioEncodersCount()).append("\n");
        sb.append("Filtros: ").append(caps.filters().size()).append("\n");
        sb.append("HWAccels compilados: ").append(caps.hardwareAccelerators()).append("\n\n");

        sb.append("--- Configurações Técnicas ---\n");
        sb.append("Tema: ").append(settings.theme()).append(" | Idioma: ").append(settings.language()).append("\n");
        sb.append("Concorrência Máxima: ").append(settings.maxConcurrentJobs()).append("\n");
        sb.append("Limite Cache: ").append(settings.cacheLimitMb()).append(" MB | Retenção Logs: ")
                .append(settings.logRetentionDays()).append(" dias\n");
        if (includePaths) {
            sb.append("FFmpeg Path: ").append(settings.ffmpegPath()).append("\n");
            sb.append("FFprobe Path: ").append(settings.ffprobePath()).append("\n");
            sb.append("Output Dir: ").append(settings.defaultOutputDir()).append("\n");
        } else {
            sb.append("(Caminhos locais omitidos por privacidade)\n");
        }
        sb.append("\n");

        sb.append("--- Diagnóstico de Hardware ---\n");
        if (hw == null) {
            sb.append("Teste sintético de hardware ainda não executado nesta sessão.\n");
        } else {
            sb.append("Dispositivos DRI: ").append(hw.driDevices()).append(" (Acessíveis: ")
                    .append(hw.accessibleDriDevices().size()).append(")\n");
            for (HardwareEncoderProbeResult r : hw.encoderResults()) {
                sb.append(String.format("  - [%s] %s: %s (%s)\n",
                        r.backend().name(), r.encoderName(), r.statusSummary(), r.diagnosticMessage()));
            }
        }
        return sb.toString();
    }
}
