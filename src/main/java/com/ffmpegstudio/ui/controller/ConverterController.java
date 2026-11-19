package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.AudioCodecOption;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.PresetSpeed;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.domain.model.VideoCodecOption;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.IconHelper;
import com.ffmpegstudio.ui.viewmodel.ConverterViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ConverterController {

    @FXML private VBox mediaDropZone;
    @FXML private Label lblMediaTitle;
    @FXML private Label lblMediaDetails;
    @FXML private Label lblVideoDetails;
    @FXML private Label lblAudioDetails;
    @FXML private ProgressIndicator inspectProgress;
    @FXML private Button btnSelectInput;

    @FXML private ComboBox<ContainerFormat> comboContainer;
    @FXML private ComboBox<VideoCodecOption> comboVideoCodec;
    @FXML private ComboBox<AudioCodecOption> comboAudioCodec;
    @FXML private ComboBox<PresetSpeed> comboPreset;
    @FXML private ComboBox<ResolutionPreset> comboResolution;
    @FXML private ComboBox<com.ffmpegstudio.domain.model.ConversionPreset> comboPresets;
    @FXML private Slider sliderCrf;
    @FXML private Label lblCrfValue;

    @FXML private TextField txtOutputPath;
    @FXML private Button btnBrowseOutput;
    @FXML private TextArea txtPreviewCommand;

    @FXML private VBox progressSection;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgressPercent;
    @FXML private Label lblFps;
    @FXML private Label lblSpeed;
    @FXML private Label lblEta;
    @FXML private Label lblStatus;
    @FXML private Button btnAddToQueue;
    @FXML private Button btnConvert;
    @FXML private Button btnCancel;

    @FXML private ListView<String> listLogs;
    @FXML private VBox logsContainer;

    private ConverterViewModel viewModel;
    private MainLayoutController mainController;

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new ConverterViewModel(
                context.getAnalyzeMediaService(),
                context.getTranscodeMediaService(),
                context.getSettingsService(),
                context.getPresetService(),
                context.getQueueService()
        );

        // Bindings for Media Info
        lblMediaTitle.textProperty().bind(viewModel.mediaTitleProperty());
        lblMediaDetails.textProperty().bind(viewModel.mediaDetailsProperty());
        lblVideoDetails.textProperty().bind(viewModel.videoDetailsProperty());
        lblAudioDetails.textProperty().bind(viewModel.audioDetailsProperty());

        inspectProgress.visibleProperty().bind(viewModel.isInspectingProperty());
        inspectProgress.managedProperty().bind(viewModel.isInspectingProperty());

        // Setup Dropdown Combos
        comboContainer.getItems().setAll(ContainerFormat.values());
        comboContainer.valueProperty().bindBidirectional(viewModel.selectedContainerProperty());

        comboVideoCodec.getItems().setAll(VideoCodecOption.values());
        comboVideoCodec.valueProperty().bindBidirectional(viewModel.selectedVideoCodecProperty());

        comboAudioCodec.getItems().setAll(AudioCodecOption.values());
        comboAudioCodec.valueProperty().bindBidirectional(viewModel.selectedAudioCodecProperty());

        comboPreset.getItems().setAll(PresetSpeed.values());
        comboPreset.valueProperty().bindBidirectional(viewModel.selectedPresetProperty());

        comboResolution.getItems().setAll(ResolutionPreset.values());
        comboResolution.valueProperty().bindBidirectional(viewModel.selectedResolutionProperty());

        // CRF slider
        sliderCrf.setValue(viewModel.crfValueProperty().get());
        sliderCrf.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                int val = n.intValue();
                viewModel.crfValueProperty().set(val);
                lblCrfValue.setText(String.valueOf(val));
            }
        });
        lblCrfValue.setText(String.valueOf(viewModel.crfValueProperty().get()));

        // Output destination & command preview
        txtOutputPath.textProperty().bindBidirectional(viewModel.outputPathProperty());
        txtPreviewCommand.textProperty().bind(viewModel.previewCommandProperty());

        // Progress bindings
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());
        lblProgressPercent.textProperty().bind(viewModel.progressTextProperty());
        lblFps.textProperty().bind(viewModel.fpsTextProperty());
        lblSpeed.textProperty().bind(viewModel.speedTextProperty());
        lblEta.textProperty().bind(viewModel.etaTextProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());

        btnConvert.disableProperty().bind(viewModel.isTranscodingProperty().or(viewModel.isMediaLoadedProperty().not()));
        btnCancel.disableProperty().bind(viewModel.isTranscodingProperty().not());
        btnAddToQueue.disableProperty().bind(viewModel.isTranscodingProperty().or(viewModel.isMediaLoadedProperty().not()));

        // Presets
        comboPresets.setItems(viewModel.getAvailablePresets());
        comboPresets.getSelectionModel().selectedItemProperty().addListener((obs, oldV, preset) -> {
            if (preset != null) {
                viewModel.applyPreset(preset);
            }
        });

        // Logs
        listLogs.setItems(viewModel.getLogLines());

        setupIcons();
        setupDragAndDrop();
    }

    private void setupIcons() {
        SVGPath playIcon = IconHelper.createIcon(IconHelper.ICON_PLAY, "button-icon", 0.8);
        btnConvert.setGraphic(playIcon);

        SVGPath stopIcon = IconHelper.createIcon(IconHelper.ICON_STOP, "button-icon", 0.8);
        btnCancel.setGraphic(stopIcon);

        SVGPath browseIcon = IconHelper.createIcon(IconHelper.ICON_FOLDER, "button-icon", 0.8);
        btnSelectInput.setGraphic(browseIcon);
    }

    private void setupDragAndDrop() {
        mediaDropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                mediaDropZone.getStyleClass().add("dropzone-hover");
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        mediaDropZone.setOnDragExited(event -> {
            mediaDropZone.getStyleClass().remove("dropzone-hover");
            event.consume();
        });

        mediaDropZone.setOnDragDropped(event -> {
            mediaDropZone.getStyleClass().remove("dropzone-hover");
            var db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                loadMedia(file.toPath());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    public void onSelectInput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Media to Transcode");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files", "*.mp4", "*.mkv", "*.mov", "*.avi", "*.webm", "*.flv", "*.mp3", "*.wav"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selected = chooser.showOpenDialog(mediaDropZone.getScene().getWindow());
        if (selected != null) {
            loadMedia(selected.toPath());
        }
    }

    public void loadMedia(Path path) {
        viewModel.loadMediaFile(path);
    }

    @FXML
    public void onBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Output File Destination");
        ContainerFormat c = viewModel.selectedContainerProperty().get();
        String ext = "*." + (c != null ? c.extension() : "mp4");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Target Format (" + ext + ")", ext));

        File currentOut = new File(txtOutputPath.getText());
        if (currentOut.getParentFile() != null && currentOut.getParentFile().exists()) {
            chooser.setInitialDirectory(currentOut.getParentFile());
            chooser.setInitialFileName(currentOut.getName());
        }

        File selected = chooser.showSaveDialog(txtOutputPath.getScene().getWindow());
        if (selected != null) {
            txtOutputPath.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    public void onStartConversion() {
        viewModel.startConversion();
    }

    @FXML
    public void onAddToQueue() {
        viewModel.addToQueue();
    }

    @FXML
    public void onCancelConversion() {
        viewModel.cancelConversion();
    }

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }
}
