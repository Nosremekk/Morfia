package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.IconHelper;
import com.ffmpegstudio.ui.viewmodel.SettingsViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;

public class SettingsController {

    @FXML private TextField txtFfmpegPath;
    @FXML private TextField txtFfprobePath;
    @FXML private TextField txtOutputDir;
    @FXML private TextField txtTempDir;
    @FXML private javafx.scene.control.ComboBox<String> cmbLanguage;
    @FXML private javafx.scene.control.ComboBox<String> cmbTheme;
    @FXML private Spinner<Integer> spinnerConcurrency;
    @FXML private Spinner<Integer> spinnerCacheLimit;
    @FXML private Spinner<Integer> spinnerLogRetention;
    @FXML private javafx.scene.control.CheckBox chkConfirmations;

    @FXML private Circle dotFfmpeg;
    @FXML private Label lblFfmpegStatus;
    @FXML private Circle dotFfprobe;
    @FXML private Label lblFfprobeStatus;

    @FXML private Label lblSaveStatus;
    @FXML private ProgressIndicator progressTesting;

    @FXML private Button btnAutoDetect;
    @FXML private Button btnTest;
    @FXML private Button btnBrowseFfmpeg;
    @FXML private Button btnBrowseFfprobe;
    @FXML private Button btnBrowseOutput;
    @FXML private Button btnSave;
    @FXML private Button btnReset;
    @FXML private Button btnProbeHardware;
    @FXML private Label lblDriStatus;

    @FXML private ListView<String> listHwAccels;
    @FXML private ListView<String> listVideoEncoders;
    @FXML private ListView<String> listAudioEncoders;
    @FXML private ListView<String> listFilters;

    private SettingsViewModel viewModel;
    private MainLayoutController mainController;

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new SettingsViewModel(
                context.getSettingsService(),
                context.getCapabilitiesService(),
                context.getFileSystemService()
        );

        // Bindings
        txtFfmpegPath.textProperty().bindBidirectional(viewModel.ffmpegPathProperty());
        txtFfprobePath.textProperty().bindBidirectional(viewModel.ffprobePathProperty());
        txtOutputDir.textProperty().bindBidirectional(viewModel.defaultOutputDirProperty());
        txtTempDir.textProperty().bindBidirectional(viewModel.tempDirProperty());

        cmbLanguage.getItems().setAll("pt_BR", "en");
        cmbLanguage.valueProperty().bindBidirectional(viewModel.languageProperty());

        cmbTheme.getItems().setAll("DARK", "LIGHT");
        cmbTheme.valueProperty().bindBidirectional(viewModel.themeProperty());

        spinnerCacheLimit.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(32, 4096, 256, 32));
        spinnerCacheLimit.getValueFactory().valueProperty().addListener((obs, o, n) -> {
            if (n != null) viewModel.cacheLimitMbProperty().set(n);
        });
        viewModel.cacheLimitMbProperty().addListener((obs, o, n) -> {
            if (n != null) spinnerCacheLimit.getValueFactory().setValue(n.intValue());
        });

        spinnerLogRetention.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 14));
        spinnerLogRetention.getValueFactory().valueProperty().addListener((obs, o, n) -> {
            if (n != null) viewModel.logRetentionDaysProperty().set(n);
        });
        viewModel.logRetentionDaysProperty().addListener((obs, o, n) -> {
            if (n != null) spinnerLogRetention.getValueFactory().setValue(n.intValue());
        });

        chkConfirmations.selectedProperty().bindBidirectional(viewModel.confirmationsEnabledProperty());

        spinnerConcurrency.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 16, 2));
        spinnerConcurrency.getValueFactory().valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                viewModel.maxConcurrentJobsProperty().set(newV);
            }
        });
        viewModel.maxConcurrentJobsProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                spinnerConcurrency.getValueFactory().setValue(newV.intValue());
            }
        });

        lblFfmpegStatus.textProperty().bind(viewModel.ffmpegStatusMessageProperty());
        lblFfprobeStatus.textProperty().bind(viewModel.ffprobeStatusMessageProperty());
        lblSaveStatus.textProperty().bind(viewModel.saveStatusMessageProperty());

        progressTesting.visibleProperty().bind(viewModel.testingProperty());
        progressTesting.managedProperty().bind(viewModel.testingProperty());

        viewModel.ffmpegValidProperty().addListener((obs, oldV, valid) -> {
            dotFfmpeg.getStyleClass().removeAll("dot-success", "dot-danger");
            dotFfmpeg.getStyleClass().add(Boolean.TRUE.equals(valid) ? "dot-success" : "dot-danger");
        });

        viewModel.ffprobeValidProperty().addListener((obs, oldV, valid) -> {
            dotFfprobe.getStyleClass().removeAll("dot-success", "dot-danger");
            dotFfprobe.getStyleClass().add(Boolean.TRUE.equals(valid) ? "dot-success" : "dot-danger");
        });

        // ListView data binding
        listHwAccels.setItems(viewModel.getHwAccels());
        listVideoEncoders.setItems(viewModel.getVideoEncoders());
        listAudioEncoders.setItems(viewModel.getAudioEncoders());
        listFilters.setItems(viewModel.getFilters());

        setupIcons();

        viewModel.init();
    }

    private void setupIcons() {
        SVGPath autoDetectIcon = IconHelper.createIcon(IconHelper.ICON_REFRESH, "button-icon", 0.8);
        btnAutoDetect.setGraphic(autoDetectIcon);

        SVGPath testIcon = IconHelper.createIcon(IconHelper.ICON_TERMINAL, "button-icon", 0.8);
        btnTest.setGraphic(testIcon);
    }

    @FXML
    public void onAutoDetect() {
        viewModel.autoDetectPaths();
    }

    @FXML
    public void onTest() {
        viewModel.testExecutables();
    }

    @FXML
    public void onBrowseFfmpeg() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Locate FFmpeg Binary");
        File selected = chooser.showOpenDialog(txtFfmpegPath.getScene().getWindow());
        if (selected != null) {
            txtFfmpegPath.setText(selected.getAbsolutePath());
            viewModel.testExecutables();
        }
    }

    @FXML
    public void onBrowseFfprobe() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Locate FFprobe Binary");
        File selected = chooser.showOpenDialog(txtFfprobePath.getScene().getWindow());
        if (selected != null) {
            txtFfprobePath.setText(selected.getAbsolutePath());
            viewModel.testExecutables();
        }
    }

    @FXML
    public void onBrowseOutput() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Default Output Directory");
        File selected = chooser.showDialog(txtOutputDir.getScene().getWindow());
        if (selected != null) {
            txtOutputDir.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    public void onSave() {
        viewModel.save();
        if (txtOutputDir.getScene() != null) {
            com.ffmpegstudio.ui.FFmpegStudioApp.applyTheme(txtOutputDir.getScene(), viewModel.themeProperty().get());
        }
        if (mainController != null) {
            mainController.applyLocalizedLabels();
            mainController.onSettingsUpdated();
        }
    }

    @FXML
    public void onReset() {
        viewModel.reset();
        if (txtOutputDir.getScene() != null) {
            com.ffmpegstudio.ui.FFmpegStudioApp.applyTheme(txtOutputDir.getScene(), viewModel.themeProperty().get());
        }
        if (mainController != null) {
            mainController.applyLocalizedLabels();
            mainController.onSettingsUpdated();
        }
    }

    @FXML
    public void onProbeHardware() {
        btnProbeHardware.setDisable(true);
        lblDriStatus.setText("Executando testes sintéticos de hardware (timeout 5s/encoder)...");
        AppContext.getInstance().getHardwareAccelerationService()
                .probeAllAsync(new java.util.concurrent.atomic.AtomicBoolean(false))
                .thenAccept(report -> javafx.application.Platform.runLater(() -> {
                    btnProbeHardware.setDisable(false);
                    String driText = report.driDevices().isEmpty()
                            ? "Nenhum dispositivo /dev/dri encontrado."
                            : "DRI: " + String.join(", ", report.driDevices())
                            + " (Acessíveis: " + report.accessibleDriDevices().size() + ")";
                    lblDriStatus.setText(driText);

                    java.util.List<String> lines = new java.util.ArrayList<>();
                    for (var r : report.encoderResults()) {
                        lines.add(String.format("[%s] %s — %s | %s",
                                r.backend().name(),
                                r.encoderName(),
                                r.statusSummary(),
                                r.diagnosticMessage()));
                    }
                    viewModel.getHwAccels().setAll(lines);
                }))
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> {
                        btnProbeHardware.setDisable(false);
                        lblDriStatus.setText("Erro no teste de hardware: " + ex.getMessage());
                    });
                    return null;
                });
    }

    public void refresh() {
        viewModel.testExecutables();
    }

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }
}
