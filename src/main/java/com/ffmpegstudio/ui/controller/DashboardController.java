package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.IconHelper;
import com.ffmpegstudio.ui.viewmodel.DashboardViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;

public class DashboardController {

    @FXML private VBox bannerContainer;
    @FXML private Label lblBannerTitle;
    @FXML private Label lblBannerSubtitle;
    @FXML private Label lblVideoEncoders;
    @FXML private Label lblAudioEncoders;
    @FXML private Label lblHwAccels;
    @FXML private Label lblFilters;
    @FXML private Label lblHwList;
    @FXML private Button btnOpenSettings;
    @FXML private Button btnRefresh;
    @FXML private Button btnBrowseFiles;
    @FXML private VBox dropZone;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private HBox statsBox;

    private DashboardViewModel viewModel;
    private MainLayoutController mainController;

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new DashboardViewModel(context.getCapabilitiesService(), context.getSettingsService());

        // Bindings
        loadingSpinner.visibleProperty().bind(viewModel.loadingProperty());
        loadingSpinner.managedProperty().bind(viewModel.loadingProperty());

        lblVideoEncoders.textProperty().bind(viewModel.videoEncodersCountProperty().asString());
        lblAudioEncoders.textProperty().bind(viewModel.audioEncodersCountProperty().asString());
        lblHwAccels.textProperty().bind(viewModel.hwAccelsCountProperty().asString());
        lblFilters.textProperty().bind(viewModel.filtersCountProperty().asString());
        lblHwList.textProperty().bind(viewModel.hwAccelsSummaryProperty());

        viewModel.readyProperty().addListener((obs, oldVal, ready) -> {
            bannerContainer.getStyleClass().removeAll("banner-ready", "banner-warning");
            if (Boolean.TRUE.equals(ready)) {
                bannerContainer.getStyleClass().add("banner-ready");
                lblBannerTitle.setText("FFmpeg Engine Ready");
                lblBannerSubtitle.setText(viewModel.ffmpegVersionTextProperty().get() + " detected on this system.");
            } else {
                bannerContainer.getStyleClass().add("banner-warning");
                lblBannerTitle.setText("FFmpeg Not Configured");
                lblBannerSubtitle.setText("FFmpeg was not found in your PATH. Please configure it in Settings.");
            }
        });

        setupIcons();
        setupDropZone();
        refresh();
    }

    private void setupIcons() {
        SVGPath refreshIcon = IconHelper.createIcon(IconHelper.ICON_REFRESH, "button-icon", 0.8);
        btnRefresh.setGraphic(refreshIcon);

        SVGPath settingsIcon = IconHelper.createIcon(IconHelper.ICON_SETTINGS, "button-icon", 0.8);
        btnOpenSettings.setGraphic(settingsIcon);

        SVGPath uploadIcon = IconHelper.createIcon(IconHelper.ICON_UPLOAD, "dropzone-icon", 1.8);
        dropZone.getChildren().add(0, uploadIcon);
    }

    private void setupDropZone() {
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                dropZone.getStyleClass().add("dropzone-hover");
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.getStyleClass().remove("dropzone-hover");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            dropZone.getStyleClass().remove("dropzone-hover");
            var db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                if (mainController != null) {
                    mainController.openFileInConverter(file.toPath());
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    public void onBrowseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Media Files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Media Files", "*.mp4", "*.mkv", "*.avi", "*.mov", "*.mp3", "*.flac", "*.wav", "*.webm", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File selected = chooser.showOpenDialog(dropZone.getScene().getWindow());
        if (selected != null && mainController != null) {
            mainController.openFileInConverter(selected.toPath());
        }
    }

    @FXML
    public void onOpenSettings() {
        if (mainController != null) {
            mainController.showSettings();
        }
    }

    @FXML
    public void refresh() {
        viewModel.loadStats();
    }

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }
}
