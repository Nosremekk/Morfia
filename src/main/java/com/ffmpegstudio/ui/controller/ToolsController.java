package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.AudioToolsOperation.AudioOutputFormat;
import com.ffmpegstudio.domain.model.ExtractFramesOperation.ImageFormat;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.ToolsViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

public class ToolsController {
    private static final Logger logger = LoggerFactory.getLogger(ToolsController.class);

    @FXML private HBox sourceBox;
    @FXML private Label lblFileName;
    @FXML private Label lblMediaSummary;
    @FXML private Label lblDurationBadge;

    @FXML private TabPane toolsTabPane;

    // Audio Tab
    @FXML private ComboBox<AudioOutputFormat> cmbAudioFormat;
    @FXML private ComboBox<Integer> cmbAudioBitrate;
    @FXML private ComboBox<String> cmbAudioChannels;
    @FXML private ComboBox<String> cmbAudioSampleRate;
    @FXML private ComboBox<String> cmbAudioTracks;
    @FXML private Slider sliderVolume;
    @FXML private Label lblVolumeValue;
    @FXML private CheckBox chkLoudnorm;

    // Trim Tab
    @FXML private TextField txtTrimStart;
    @FXML private TextField txtTrimEnd;
    @FXML private Label lblTrimDuration;
    @FXML private ToggleGroup trimModeGroup;
    @FXML private RadioButton rbTrimFast;
    @FXML private RadioButton rbTrimAccurate;
    @FXML private Label lblTrimExplainTitle;
    @FXML private Label lblTrimExplainDesc;

    // GIF & Frames Tab
    @FXML private TextField txtGifStart;
    @FXML private ComboBox<Integer> cmbGifDuration;
    @FXML private ComboBox<Integer> cmbGifFps;
    @FXML private ComboBox<Integer> cmbGifWidth;
    @FXML private CheckBox chkGifPalette;

    @FXML private TextField txtFrameTime;
    @FXML private ComboBox<ImageFormat> cmbFrameFormat;

    // Common
    @FXML private TextField txtCommandPreview;
    @FXML private VBox progressCard;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnCancel;
    @FXML private Button btnOpenOutput;

    private ToolsViewModel viewModel;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new ToolsViewModel(
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
        // Audio
        cmbAudioFormat.getItems().addAll(AudioOutputFormat.values());
        cmbAudioFormat.getSelectionModel().select(AudioOutputFormat.MP3);

        cmbAudioBitrate.getItems().addAll(64, 128, 192, 256, 320);
        cmbAudioBitrate.getSelectionModel().select(Integer.valueOf(192));

        cmbAudioChannels.getItems().addAll("Manter Original", "Estéreo (2.0)", "Mono (1.0)");
        cmbAudioChannels.getSelectionModel().selectFirst();

        cmbAudioSampleRate.getItems().addAll("Manter Original", "44100 Hz (CD)", "48000 Hz (Estúdio / Vídeo)");
        cmbAudioSampleRate.getSelectionModel().selectFirst();

        cmbAudioTracks.setItems(viewModel.getAudioTracksList());

        // GIF
        cmbGifDuration.getItems().addAll(1, 2, 3, 5, 8, 10, 15, 20);
        cmbGifDuration.getSelectionModel().select(Integer.valueOf(5));

        cmbGifFps.getItems().addAll(10, 12, 15, 20, 24, 30);
        cmbGifFps.getSelectionModel().select(Integer.valueOf(15));

        cmbGifWidth.getItems().addAll(320, 480, 640, 800);
        cmbGifWidth.getSelectionModel().select(Integer.valueOf(480));

        // Frames
        cmbFrameFormat.getItems().addAll(ImageFormat.values());
        cmbFrameFormat.getSelectionModel().select(ImageFormat.PNG);
    }

    private void setupDragAndDrop() {
        sourceBox.setOnDragOver(event -> {
            if (event.getGestureSource() != sourceBox && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        sourceBox.setOnDragDropped(event -> {
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
        // Audio bindings
        cmbAudioFormat.valueProperty().bindBidirectional(viewModel.audioFormatProperty());
        cmbAudioBitrate.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) viewModel.audioBitrateProperty().set(newV);
        });
        cmbAudioChannels.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            int idx = newV.intValue();
            if (idx == 1) viewModel.audioChannelsProperty().set(2);
            else if (idx == 2) viewModel.audioChannelsProperty().set(1);
            else viewModel.audioChannelsProperty().set(null);
        });
        cmbAudioSampleRate.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            int idx = newV.intValue();
            if (idx == 1) viewModel.audioSampleRateProperty().set(44100);
            else if (idx == 2) viewModel.audioSampleRateProperty().set(48000);
            else viewModel.audioSampleRateProperty().set(null);
        });
        cmbAudioTracks.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.intValue() >= 0) {
                viewModel.selectedAudioTrackIndexProperty().set(newV.intValue());
            }
        });

        sliderVolume.valueProperty().addListener((obs, oldV, newV) -> {
            double vol = newV.doubleValue();
            viewModel.audioVolumeProperty().set(vol);
            lblVolumeValue.setText(String.format("%d%%", Math.round(vol * 100)));
        });
        chkLoudnorm.selectedProperty().bindBidirectional(viewModel.audioLoudnormProperty());

        // Trim bindings
        txtTrimStart.textProperty().bindBidirectional(viewModel.trimStartTimeProperty());
        txtTrimEnd.textProperty().bindBidirectional(viewModel.trimEndTimeProperty());
        lblTrimDuration.textProperty().bind(viewModel.trimDurationTextProperty());

        txtTrimStart.textProperty().addListener((obs, oldV, newV) -> viewModel.updateTrimDuration());
        txtTrimEnd.textProperty().addListener((obs, oldV, newV) -> viewModel.updateTrimDuration());

        rbTrimAccurate.selectedProperty().addListener((obs, oldV, accurate) -> {
            viewModel.trimAccurateReencodeProperty().set(Boolean.TRUE.equals(accurate));
            if (Boolean.TRUE.equals(accurate)) {
                lblTrimExplainTitle.setText("Corte Preciso (Recodificação):");
                lblTrimExplainDesc.setText("Recodifica frame a frame nos timestamps milissegundos exatos. Garante início e fim perfeitos.");
            } else {
                lblTrimExplainTitle.setText("Corte Rápido sem Perda:");
                lblTrimExplainDesc.setText("Executado instantaneamente sem recodificação (-c copy). O corte é alinhado ao keyframe mais próximo.");
            }
        });

        // GIF bindings
        txtGifStart.textProperty().bindBidirectional(viewModel.gifStartTimeProperty());
        cmbGifDuration.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) viewModel.gifDurationSecProperty().set(newV);
        });
        cmbGifFps.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) viewModel.gifFpsProperty().set(newV);
        });
        cmbGifWidth.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) viewModel.gifWidthProperty().set(newV);
        });
        chkGifPalette.selectedProperty().bindBidirectional(viewModel.gifPaletteOptimizationProperty());

        // Frame bindings
        txtFrameTime.textProperty().bindBidirectional(viewModel.frameTimestampProperty());
        cmbFrameFormat.valueProperty().bindBidirectional(viewModel.frameFormatProperty());

        // Common bindings
        txtCommandPreview.textProperty().bind(viewModel.commandPreviewProperty());
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());

        viewModel.isProcessingProperty().addListener((obs, oldV, processing) -> {
            progressCard.setVisible(processing || viewModel.operationSuccessProperty().get());
            progressCard.setManaged(processing || viewModel.operationSuccessProperty().get());
            btnCancel.setVisible(processing);
            btnCancel.setManaged(processing);
        });

        viewModel.operationSuccessProperty().addListener((obs, oldV, success) -> {
            boolean done = Boolean.TRUE.equals(success);
            btnOpenOutput.setVisible(done);
            btnOpenOutput.setManaged(done);
            if (done) {
                progressCard.setVisible(true);
                progressCard.setManaged(true);
            }
        });

        viewModel.currentMediaFileProperty().addListener((obs, oldV, media) -> {
            if (media != null) {
                lblFileName.setText(media.path().getFileName().toString());
                if (media.metadata() != null) {
                    lblDurationBadge.setText(FormatUtils.formatDuration(media.metadata().duration()));
                    String summary = String.format("%s • %s",
                            media.metadata().formatName() != null ? media.metadata().formatName().toUpperCase() : "Mídia",
                            FormatUtils.formatFileSize(media.metadata().sizeBytes()));
                    lblMediaSummary.setText(summary);
                }
            }
        });
    }

    public void loadMedia(Path path) {
        viewModel.loadMedia(path);
    }

    @FXML
    public void handleSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Arquivo Multimídia");
        File file = chooser.showOpenDialog(sourceBox.getScene().getWindow());
        if (file != null) {
            loadMedia(file.toPath());
        }
    }

    @FXML
    public void handleAudioExecute() {
        viewModel.executeAudioOperation();
    }

    @FXML
    public void handleAudioAddToQueue() {
        viewModel.addAudioToQueue();
    }

    @FXML
    public void handleTrimExecute() {
        viewModel.executeTrimOperation();
    }

    @FXML
    public void handleTrimAddToQueue() {
        viewModel.addTrimToQueue();
    }

    @FXML
    public void handleGifExecute() {
        viewModel.executeGifOperation();
    }

    @FXML
    public void handleGifAddToQueue() {
        viewModel.addGifToQueue();
    }

    @FXML
    public void handleFrameExecute() {
        viewModel.executeFrameOperation();
    }

    @FXML
    public void handleFrameAddToQueue() {
        viewModel.addFrameToQueue();
    }

    @FXML
    public void handleCancel() {
        viewModel.cancelOperation();
    }

    @FXML
    public void handleOpenOutput() {
        Path path = viewModel.lastGeneratedOutputProperty().get();
        if (path != null) {
            com.ffmpegstudio.infrastructure.platform.DesktopIntegrationService.openDirectory(path);
        }
    }
}
