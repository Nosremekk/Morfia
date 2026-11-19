package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.domain.model.AppIdentity;
import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.infrastructure.platform.ExecutableResolver;
import com.ffmpegstudio.infrastructure.platform.PlatformInfo;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 9-step guided setup wizard for external FFmpeg & FFprobe configuration (KD-004)
 * plus centralized product & MIT license information (KD-001 / KD-002).
 */
public class SetupWizardController {

    private static final int TOTAL_STEPS = 9;

    @FXML private Label lblStepIndicator;
    @FXML private ProgressBar stepProgress;
    @FXML private Label lblStepHeading;
    @FXML private Label lblStepDescription;
    @FXML private VBox stepContentBox;
    @FXML private VBox manualPathsBox;
    @FXML private TextField txtFfmpegPath;
    @FXML private TextField txtFfprobePath;
    @FXML private TextArea txtDetailsOutput;
    @FXML private Button btnActionPrimary;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblAboutIdentity;
    @FXML private Label lblAboutLicense;

    private MainLayoutController mainController;
    private ManageSettingsUseCase settingsUseCase;
    private DetectCapabilitiesUseCase capabilitiesUseCase;
    private int currentStep = 1;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext ctx = AppContext.getInstance();
        this.settingsUseCase = ctx.getSettingsService();
        this.capabilitiesUseCase = ctx.getCapabilitiesService();

        AppSettings settings = settingsUseCase.getSettings();
        txtFfmpegPath.setText(settings.ffmpegPath());
        txtFfprobePath.setText(settings.ffprobePath());

        lblAboutIdentity.setText(AppIdentity.DISPLAY_NAME + " — " + AppIdentity.SUBTITLE
                + " | v" + AppIdentity.VERSION + " (" + AppIdentity.APPLICATION_ID + ")");
        lblAboutLicense.setText("Licença: " + AppIdentity.LICENSE_SPDX
                + " • Copyright (c) " + AppIdentity.COPYRIGHT_YEAR + " " + AppIdentity.COPYRIGHT_HOLDER
                + " • " + AppIdentity.OFFICIAL_URL);

        renderStep();
    }

    @FXML
    public void handlePrevStep() {
        if (currentStep > 1) {
            currentStep--;
            renderStep();
        }
    }

    @FXML
    public void handleNextStep() {
        if (currentStep < TOTAL_STEPS) {
            currentStep++;
            renderStep();
        } else {
            persistAndFinish();
        }
    }

    @FXML
    public void handleSkipForNow() {
        if (mainController != null) {
            mainController.showDashboard();
        }
    }

    @FXML
    public void handleBrowseFfmpeg() {
        chooseExecutable("Selecionar Executável FFmpeg", txtFfmpegPath);
    }

    @FXML
    public void handleBrowseFfprobe() {
        chooseExecutable("Selecionar Executável FFprobe", txtFfprobePath);
    }

    @FXML
    public void handleStepAction() {
        switch (currentStep) {
            case 2 -> showOsInfo();
            case 3 -> autoDiscoverExecutables();
            case 4 -> verifyVersions();
            case 5 -> verifyCapabilities();
            case 6 -> persistAndTestPaths();
            case 7 -> showInstallInstructions();
            case 8 -> runFinalCheck();
            case 9 -> persistAndFinish();
            default -> autoDiscoverExecutables();
        }
    }

    private void renderStep() {
        stepProgress.setProgress((double) currentStep / TOTAL_STEPS);
        btnPrev.setDisable(currentStep == 1);
        btnNext.setText(currentStep == TOTAL_STEPS ? "Concluir e Salvar" : "Próximo");

        boolean showManual = (currentStep == 3 || currentStep == 6 || currentStep == 8);
        manualPathsBox.setVisible(showManual);
        manualPathsBox.setManaged(showManual);

        switch (currentStep) {
            case 1 -> {
                lblStepIndicator.setText("Etapa 1 de 9 — Boas-vindas");
                lblStepHeading.setText("1. Boas-vindas ao " + AppIdentity.DISPLAY_NAME);
                lblStepDescription.setText("Este aplicativo é 100% local e offline. Para processar mídia, ele utiliza executáveis externos FFmpeg e FFprobe. Você pode configurar agora ou fechar este assistente e configurar mais tarde.");
                btnActionPrimary.setText("Detectar Ambiente");
                txtDetailsOutput.setText("Pronto para iniciar a verificação do sistema.");
            }
            case 2 -> {
                lblStepIndicator.setText("Etapa 2 de 9 — Sistema Operacional");
                lblStepHeading.setText("2. Identificação do Sistema Operacional");
                lblStepDescription.setText("Detectando sistema operacional, arquitetura de CPU e diretórios padrão.");
                btnActionPrimary.setText("Atualizar Detecção");
                showOsInfo();
            }
            case 3 -> {
                lblStepIndicator.setText("Etapa 3 de 9 — Busca Automática");
                lblStepHeading.setText("3. Busca Automática de FFmpeg e FFprobe");
                lblStepDescription.setText("Procura em: 1) Caminho configurado, 2) PATH do sistema, 3) Diretórios convencionais, 4) Gerenciadores de pacotes (ignorando diretórios temporários inseguros).");
                btnActionPrimary.setText("Buscar Agora");
                autoDiscoverExecutables();
            }
            case 4 -> {
                lblStepIndicator.setText("Etapa 4 de 9 — Versões");
                lblStepHeading.setText("4. Verificação de Versões");
                lblStepDescription.setText("Executa 'ffmpeg -version' e 'ffprobe -version' em background para confirmar que ambos respondem.");
                btnActionPrimary.setText("Verificar Versões");
                verifyVersions();
            }
            case 5 -> {
                lblStepIndicator.setText("Etapa 5 de 9 — Capacidades");
                lblStepHeading.setText("5. Verificação de Codecs e Aceleração");
                lblStepDescription.setText("Inspeciona encoders de software (libx264, libx265, aac) e acelerações de hardware disponíveis. A ausência de aceleração por hardware não impede o uso.");
                btnActionPrimary.setText("Inspecionar Capacidades");
                verifyCapabilities();
            }
            case 6 -> {
                lblStepIndicator.setText("Etapa 6 de 9 — Seleção Manual");
                lblStepHeading.setText("6. Seleção Manual de Executáveis (Opcional)");
                lblStepDescription.setText("Se os binários não foram encontrados automaticamente, aponte os arquivos 'ffmpeg' e 'ffprobe' (ou '.exe' no Windows) abaixo.");
                btnActionPrimary.setText("Validar Caminhos");
            }
            case 7 -> {
                lblStepIndicator.setText("Etapa 7 de 9 — Instruções de Instalação");
                lblStepHeading.setText("7. Como Instalar na Sua Plataforma");
                lblStepDescription.setText("Siga as instruções abaixo caso ainda não possua FFmpeg e FFprobe instalados. O aplicativo não executa instalações nem downloads automáticos.");
                btnActionPrimary.setText("Exibir Instruções");
                showInstallInstructions();
            }
            case 8 -> {
                lblStepIndicator.setText("Etapa 8 de 9 — Teste Final");
                lblStepHeading.setText("8. Teste Final de Funcionamento");
                lblStepDescription.setText("Confirma que FFmpeg e FFprobe estão válidos antes de salvar a configuração.");
                btnActionPrimary.setText("Executar Teste Final");
                runFinalCheck();
            }
            case 9 -> {
                lblStepIndicator.setText("Etapa 9 de 9 — Conclusão");
                lblStepHeading.setText("9. Conclusão");
                lblStepDescription.setText("Clique em 'Concluir e Salvar' para persistir as configurações e retornar ao Dashboard.");
                btnActionPrimary.setText("Salvar Configuração");
            }
            default -> {
            }
        }
    }

    private void showOsInfo() {
        PlatformInfo info = PlatformInfo.detect();
        txtDetailsOutput.setText(
                "Sistema Operacional: " + info.osName() + " " + info.osVersion() + "\n"
                        + "Família: " + info.osFamily() + "\n"
                        + "Arquitetura: " + info.architecture() + "\n"
                        + "Java Runtime: " + System.getProperty("java.version") + "\n"
        );
    }

    private void autoDiscoverExecutables() {
        ExecutableResolver resolver = ExecutableResolver.current();
        Optional<ExecutableResolver.ResolvedExecutable> ff = resolver.resolve("ffmpeg", txtFfmpegPath.getText());
        Optional<ExecutableResolver.ResolvedExecutable> fp = resolver.resolve("ffprobe", txtFfprobePath.getText());

        StringBuilder sb = new StringBuilder();
        if (ff.isPresent()) {
            txtFfmpegPath.setText(ff.get().path().toString());
            sb.append("FFmpeg encontrado: ").append(ff.get().path()).append(" (").append(ff.get().source()).append(")\n");
        } else {
            sb.append("FFmpeg NÃO encontrado automaticamente.\n");
        }
        if (fp.isPresent()) {
            txtFfprobePath.setText(fp.get().path().toString());
            sb.append("FFprobe encontrado: ").append(fp.get().path()).append(" (").append(fp.get().source()).append(")\n");
        } else {
            sb.append("FFprobe NÃO encontrado automaticamente.\n");
        }
        txtDetailsOutput.setText(sb.toString());
    }

    private void verifyVersions() {
        String ffPath = txtFfmpegPath.getText().trim();
        String fpPath = txtFfprobePath.getText().trim();
        txtDetailsOutput.setText("Verificando versões...");
        AsyncHelper.supplyAsync(() -> {
            var ffInfo = capabilitiesUseCase.checkFFmpeg(ffPath);
            var fpInfo = capabilitiesUseCase.checkFFprobe(fpPath);
            return "FFmpeg funcional: " + (ffInfo.isAvailable() ? "SIM (" + ffInfo.version() + ")" : "NÃO") + "\n"
                    + "FFprobe funcional: " + (fpInfo.isAvailable() ? "SIM (" + fpInfo.version() + ")" : "NÃO");
        }, txtDetailsOutput::setText, err -> txtDetailsOutput.setText("Falha ao verificar: " + err.getMessage()));
    }

    private void verifyCapabilities() {
        String ffPath = txtFfmpegPath.getText().trim();
        txtDetailsOutput.setText("Inspecionando capacidades...");
        AsyncHelper.supplyAsync(() -> {
            if (!capabilitiesUseCase.checkFFmpeg(ffPath).isAvailable()) {
                return "FFmpeg indisponível em: " + ffPath;
            }
            FFmpegCapabilities caps = capabilitiesUseCase.getCapabilities(ffPath);
            return "Versão: " + caps.version() + "\n"
                    + "Encoders: " + caps.encoders().size() + " (H.264=" + caps.hasEncoder("libx264")
                    + ", H.265=" + caps.hasEncoder("libx265") + ", AAC=" + caps.hasEncoder("aac") + ")\n"
                    + "HW Accels reportados: " + caps.hardwareAccelerators();
        }, txtDetailsOutput::setText, err -> txtDetailsOutput.setText("Erro: " + err.getMessage()));
    }

    private void showInstallInstructions() {
        PlatformInfo info = PlatformInfo.detect();
        String instructions = switch (info.osFamily()) {
            case LINUX -> """
                    Linux (Fedora):
                      sudo dnf install ffmpeg-free   # ou pacote ffmpeg completo via RPM Fusion (escolha do usuário)
                    Linux (Debian / Ubuntu):
                      sudo apt update && sudo apt install ffmpeg
                    """;
            case WINDOWS -> """
                    Windows (Winget):
                      winget install Gyan.FFmpeg
                    Ou baixe um build confiável (Gyan.dev / BtbN), extraia e aponte ffmpeg.exe e ffprobe.exe na Etapa 6.
                    """;
            case MACOS -> """
                    macOS (Homebrew — Apple Silicon /opt/homebrew/bin ou Intel /usr/local/bin):
                      brew install ffmpeg
                    Caso inicie pelo Finder, o aplicativo também busca em /opt/homebrew/bin e /usr/local/bin.
                    """;
            default -> "Instale o FFmpeg e FFprobe oficiais para o seu sistema e informe os caminhos na Etapa 6.";
        };
        txtDetailsOutput.setText(instructions);
    }

    private void persistAndTestPaths() {
        runFinalCheck();
    }

    private void runFinalCheck() {
        String ffPath = txtFfmpegPath.getText().trim();
        String fpPath = txtFfprobePath.getText().trim();
        AsyncHelper.supplyAsync(() -> {
            boolean ffOk = capabilitiesUseCase.checkFFmpeg(ffPath).isAvailable();
            boolean fpOk = capabilitiesUseCase.checkFFprobe(fpPath).isAvailable();
            return "Resultado do Teste Final:\n"
                    + "- FFmpeg (" + ffPath + "): " + (ffOk ? "OK" : "INDISPONÍVEL") + "\n"
                    + "- FFprobe (" + fpPath + "): " + (fpOk ? "OK" : "INDISPONÍVEL") + "\n"
                    + ((ffOk && fpOk) ? "Ambiente pronto para processar mídias." : "Você ainda pode concluir e usar o aplicativo; operações de mídia ficarão desabilitadas até configurar.");
        }, txtDetailsOutput::setText, err -> txtDetailsOutput.setText("Erro no teste final: " + err.getMessage()));
    }

    private void persistAndFinish() {
        AppSettings updated = settingsUseCase.getSettings()
                .withFfmpegPath(txtFfmpegPath.getText().trim())
                .withFfprobePath(txtFfprobePath.getText().trim());
        settingsUseCase.updateSettings(updated);
        if (mainController != null) {
            mainController.showDashboard();
        }
    }

    private void chooseExecutable(String title, TextField targetField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        File file = chooser.showOpenDialog(targetField.getScene().getWindow());
        if (file != null) {
            Path selected = file.toPath().toAbsolutePath().normalize();
            targetField.setText(selected.toString());
        }
    }
}
