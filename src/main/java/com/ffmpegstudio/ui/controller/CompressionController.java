package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.CompressOperation.CompressionProfile;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.CompressionViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

public class CompressionController {
    private static final Logger logger = LoggerFactory.getLogger(CompressionController.class);

    @FXML private VBox dropZone;
    @FXML private VBox mediaInfoCard;
    @FXML private Label lblFileName;
    @FXML private Label lblOriginalSize;
    @FXML private Label lblMediaResolution;
    @FXML private Label lblMediaDuration;
    @FXML private Label lblMediaVideoCodec;
    @FXML private Label lblMediaAudioCodec;

    @FXML private Button btnProfileLight;
    @FXML private Button btnProfileBalanced;
    @FXML private Button btnProfileAggressive;

    @FXML private Slider sliderCrf;
    @FXML private Label lblCrfValue;
    @FXML private ComboBox<VideoCodecOption> cmbVideoCodec;
    @FXML private ComboBox<PresetSpeed> cmbSpeed;
    @FXML private ComboBox<ResolutionPreset> cmbResolution;
    @FXML private ComboBox<Integer> cmbAudioBitrate;

    @FXML private TextField txtOutputPath;
    @FXML private TextField txtCommandPreview;

    @FXML private VBox progressCard;
    @FXML private Label lblProgressTitle;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Label lblFps;
    @FXML private Label lblSpeed;

    @FXML private HBox resultBox;
    @FXML private Label lblResultOriginalSize;
    @FXML private Label lblResultFinalSize;
    @FXML private Label lblResultRatio;

    @FXML private Label lblGlobalStatus;
    @FXML private Button btnAddToQueue;
    @FXML private Button btnCancel;
    @FXML private Button btnCompress;

    private CompressionViewModel viewModel;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new CompressionViewModel(
                context.getAnalyzeMediaService(),
                context.getTranscodeMediaService(),
                context.getQueueService(),
                context.getSettingsService(),
                context.getCapabilitiesService()
        );

        setupComboBoxes();
        setupDragAndDrop();
        setupBindings();
    }

    private void setupComboBoxes() {
        cmbVideoCodec.setItems(viewModel.getAvailableVideoCodecs());
        if (!viewModel.getAvailableVideoCodecs().isEmpty()) {
            cmbVideoCodec.getSelectionModel().selectFirst();
        }

        cmbSpeed.setItems(viewModel.getAvailableSpeeds());
        cmbSpeed.getSelectionModel().select(PresetSpeed.MEDIUM);

        cmbResolution.setItems(viewModel.getAvailableResolutions());
        cmbResolution.getSelectionModel().select(ResolutionPreset.ORIGINAL);

        cmbAudioBitrate.getItems().addAll(64, 96, 128, 192, 256);
        cmbAudioBitrate.getSelectionModel().select(Integer.valueOf(128));
    }

    private void setupDragAndDrop() {
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                loadMedia(db.getFiles().get(0).toPath());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupBindings() {
        // Output path
        viewModel.outputFilePathProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) txtOutputPath.setText(newV.toAbsolutePath().toString());
        });
        txtOutputPath.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                try {
                    viewModel.outputFilePathProperty().set(Path.of(newV));
                } catch (Exception ignored) {}
            }
        });

        // CRF slider
        sliderCrf.valueProperty().addListener((obs, oldV, newV) -> {
            int val = newV.intValue();
            lblCrfValue.setText(String.valueOf(val));
            viewModel.crfValueProperty().set(val);
        });
        lblCrfValue.setText(String.valueOf((int) sliderCrf.getValue()));

        // Options
        cmbVideoCodec.valueProperty().bindBidirectional(viewModel.selectedVideoCodecProperty());
        cmbSpeed.valueProperty().bindBidirectional(viewModel.selectedSpeedProperty());
        cmbResolution.valueProperty().bindBidirectional(viewModel.selectedResolutionProperty());
        cmbAudioBitrate.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) viewModel.audioBitrateKbpsProperty().set(newV);
        });

        // Command Preview
        txtCommandPreview.textProperty().bind(viewModel.commandPreviewProperty());

        // Processing & Progress
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());
        lblFps.textProperty().bind(viewModel.currentFpsProperty());
        lblSpeed.textProperty().bind(viewModel.currentSpeedProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());
        lblGlobalStatus.textProperty().bind(viewModel.statusMessageProperty());

        viewModel.isProcessingProperty().addListener((obs, oldV, processing) -> {
            progressCard.setVisible(processing || viewModel.compressionSuccessProperty().get());
            progressCard.setManaged(processing || viewModel.compressionSuccessProperty().get());
            btnCancel.setVisible(processing);
            btnCancel.setManaged(processing);
            btnCompress.setDisable(processing);
            btnAddToQueue.setDisable(processing);
        });

        // Results
        lblOriginalSize.textProperty().bind(viewModel.originalSizeTextProperty());
        lblResultOriginalSize.textProperty().bind(viewModel.originalSizeTextProperty());
        lblResultFinalSize.textProperty().bind(viewModel.finalSizeTextProperty());
        lblResultRatio.textProperty().bind(viewModel.compressionRatioTextProperty());

        viewModel.compressionSuccessProperty().addListener((obs, oldV, success) -> {
            resultBox.setVisible(Boolean.TRUE.equals(success));
            resultBox.setManaged(Boolean.TRUE.equals(success));
            if (Boolean.TRUE.equals(success)) {
                progressCard.setVisible(true);
                progressCard.setManaged(true);
            }
        });

        // Media Info visibility
        viewModel.currentMediaFileProperty().addListener((obs, oldV, media) -> {
            boolean hasMedia = media != null;
            mediaInfoCard.setVisible(hasMedia);
            mediaInfoCard.setManaged(hasMedia);
            if (hasMedia) {
                updateMediaInfoDisplay(media);
            }
        });
    }

    private void updateMediaInfoDisplay(MediaFile media) {
        lblFileName.setText(media.path().getFileName().toString());
        if (media.metadata() != null) {
            lblMediaDuration.setText(FormatUtils.formatDuration(media.metadata().duration()));
            if (!media.metadata().videoStreams().isEmpty()) {
                var v = media.metadata().videoStreams().get(0);
                lblMediaResolution.setText(v.width() + "x" + v.height());
                lblMediaVideoCodec.setText(v.codecName() != null ? v.codecName() : "-");
            } else {
                lblMediaResolution.setText("-");
                lblMediaVideoCodec.setText("Nenhum vídeo");
            }
            if (!media.metadata().audioStreams().isEmpty()) {
                var a = media.metadata().audioStreams().get(0);
                lblMediaAudioCodec.setText(a.codecName() != null ? a.codecName() : "-");
            } else {
                lblMediaAudioCodec.setText("Nenhum áudio");
            }
        }
    }

    public void loadMedia(Path path) {
        viewModel.loadMedia(path);
    }

    @FXML
    public void handleSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Arquivo de Vídeo para Compressão");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Arquivos de Vídeo", "*.mp4", "*.mkv", "*.mov", "*.avi", "*.webm", "*.flv", "*.wmv"),
                new FileChooser.ExtensionFilter("Todos os Arquivos", "*.*")
        );
        File file = chooser.showOpenDialog(dropZone.getScene().getWindow());
        if (file != null) {
            loadMedia(file.toPath());
        }
    }

    @FXML
    public void handleBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Arquivo Comprimido Como");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vídeo MP4 (*.mp4)", "*.mp4"));
        Path current = viewModel.outputFilePathProperty().get();
        if (current != null && current.getParent() != null) {
            chooser.setInitialDirectory(current.getParent().toFile());
            chooser.setInitialFileName(current.getFileName().toString());
        }
        File file = chooser.showSaveDialog(dropZone.getScene().getWindow());
        if (file != null) {
            viewModel.outputFilePathProperty().set(file.toPath());
        }
    }

    @FXML
    public void handleProfileLight() {
        highlightProfileButton(btnProfileLight);
        viewModel.selectedProfileProperty().set(CompressionProfile.LIGHT);
        sliderCrf.setValue(CompressionProfile.LIGHT.suggestedCrf());
    }

    @FXML
    public void handleProfileBalanced() {
        highlightProfileButton(btnProfileBalanced);
        viewModel.selectedProfileProperty().set(CompressionProfile.BALANCED);
        sliderCrf.setValue(CompressionProfile.BALANCED.suggestedCrf());
    }

    @FXML
    public void handleProfileAggressive() {
        highlightProfileButton(btnProfileAggressive);
        viewModel.selectedProfileProperty().set(CompressionProfile.AGGRESSIVE);
        sliderCrf.setValue(CompressionProfile.AGGRESSIVE.suggestedCrf());
    }

    private void highlightProfileButton(Button active) {
        btnProfileLight.getStyleClass().removeAll("button-primary", "button-outline");
        btnProfileBalanced.getStyleClass().removeAll("button-primary", "button-outline");
        btnProfileAggressive.getStyleClass().removeAll("button-primary", "button-outline");

        btnProfileLight.getStyleClass().add(active == btnProfileLight ? "button-primary" : "button-outline");
        btnProfileBalanced.getStyleClass().add(active == btnProfileBalanced ? "button-primary" : "button-outline");
        btnProfileAggressive.getStyleClass().add(active == btnProfileAggressive ? "button-primary" : "button-outline");
    }

    @FXML
    public void handleCompress() {
        viewModel.startCompression();
    }

    @FXML
    public void handleCancel() {
        viewModel.cancelCompression();
    }

    @FXML
    public void handleAddToQueue() {
        viewModel.addToQueue();
    }

    @FXML
    public void handleOpenOutputFolder() {
        Path output = viewModel.outputFilePathProperty().get();
        if (output != null) {
            com.ffmpegstudio.infrastructure.platform.DesktopIntegrationService.openDirectory(output);
        }
    }
}
