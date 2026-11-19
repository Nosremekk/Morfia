package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.ConcatOperation.ConcatMode;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ResolutionPreset;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.ConcatViewModel;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ConcatController {

    @FXML private VBox clipsCard;
    @FXML private Label lblTotalDuration;
    @FXML private ListView<MediaFile> lvClips;
    @FXML private ComboBox<ConcatMode> cmbMode;
    @FXML private ComboBox<ContainerFormat> cmbContainer;
    @FXML private ComboBox<ResolutionPreset> cmbResolution;
    @FXML private VBox compatBox;
    @FXML private Label lblCompatibility;
    @FXML private TextField txtOutputPath;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnCancel;
    @FXML private Button btnQueue;
    @FXML private Button btnExecute;

    private ConcatViewModel viewModel;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext ctx = AppContext.getInstance();
        viewModel = new ConcatViewModel(
                ctx.getAnalyzeMediaService(),
                ctx.getTranscodeMediaService(),
                ctx.getQueueService(),
                ctx.getSettingsService(),
                ctx.getCapabilitiesService()
        );

        cmbMode.getItems().setAll(ConcatMode.values());
        cmbMode.valueProperty().bindBidirectional(viewModel.selectedModeProperty());

        cmbContainer.getItems().setAll(ContainerFormat.values());
        cmbContainer.valueProperty().bindBidirectional(viewModel.targetContainerProperty());

        cmbResolution.getItems().setAll(ResolutionPreset.values());
        cmbResolution.valueProperty().bindBidirectional(viewModel.targetResolutionProperty());

        txtOutputPath.textProperty().bindBidirectional(viewModel.outputPathProperty());
        lblTotalDuration.textProperty().bind(viewModel.totalDurationTextProperty());
        lblCompatibility.textProperty().bind(viewModel.compatibilityMessageProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());

        viewModel.isCompatibleProperty().addListener((obs, o, ok) -> {
            compatBox.getStyleClass().removeAll("callout-info", "callout-warning");
            compatBox.getStyleClass().add(Boolean.TRUE.equals(ok) ? "callout-info" : "callout-warning");
        });

        viewModel.isProcessingProperty().addListener((obs, o, proc) -> {
            btnCancel.setVisible(proc);
            btnCancel.setManaged(proc);
            btnExecute.setDisable(proc);
            btnQueue.setDisable(proc);
        });

        lvClips.setItems(viewModel.getClips());
        lvClips.setCellFactory(param -> new ClipCell());

        setupDragAndDrop();
    }

    private void setupDragAndDrop() {
        clipsCard.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        clipsCard.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                List<Path> paths = e.getDragboard().getFiles().stream().map(File::toPath).toList();
                viewModel.addFiles(paths);
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    @FXML
    public void handleAddClips() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Clipes para Concatenar");
        List<File> files = chooser.showOpenMultipleDialog(lvClips.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            viewModel.addFiles(files.stream().map(File::toPath).toList());
        }
    }

    @FXML
    public void handleClearClips() {
        viewModel.clearAll();
    }

    @FXML
    public void handleBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Arquivo Concatenado Como");
        File f = chooser.showSaveDialog(lvClips.getScene().getWindow());
        if (f != null) {
            viewModel.outputPathProperty().set(f.getAbsolutePath());
        }
    }

    @FXML
    public void handleExecute() {
        viewModel.executeConcat();
    }

    @FXML
    public void handleAddToQueue() {
        viewModel.addToQueue();
    }

    @FXML
    public void handleCancel() {
        viewModel.cancel();
    }

    private class ClipCell extends ListCell<MediaFile> {
        private final HBox root = new HBox(10);
        private final VBox info = new VBox(2);
        private final Label lblName = new Label();
        private final Label lblMeta = new Label();
        private final Button btnUp = new Button("▲");
        private final Button btnDown = new Button("▼");
        private final Button btnDel = new Button("✕");

        ClipCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            lblName.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc;");
            lblMeta.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            info.getChildren().addAll(lblName, lblMeta);
            HBox.setHgrow(info, Priority.ALWAYS);

            btnUp.getStyleClass().addAll("button-outline", "button-sm");
            btnDown.getStyleClass().addAll("button-outline", "button-sm");
            btnDel.getStyleClass().addAll("button-danger", "button-sm");
            root.getChildren().addAll(info, btnUp, btnDown, btnDel);
        }

        @Override
        protected void updateItem(MediaFile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            int idx = getIndex();
            lblName.setText((idx + 1) + ". " + item.fileName());
            StringBuilder meta = new StringBuilder();
            if (item.metadata() != null) {
                meta.append(FormatUtils.formatDuration(item.metadata().duration()));
                item.metadata().primaryVideoStream().ifPresent(v ->
                        meta.append(" • ").append(v.width()).append("x").append(v.height()).append(" ").append(v.codecName()));
                if (!item.metadata().audioStreams().isEmpty()) {
                    var a = item.metadata().audioStreams().get(0);
                    meta.append(" • Áudio: ").append(a.codecName()).append(" ").append(a.sampleRate()).append("Hz");
                } else {
                    meta.append(" • Sem Áudio");
                }
            }
            lblMeta.setText(meta.toString());
            btnUp.setDisable(idx == 0);
            btnDown.setDisable(idx >= viewModel.getClips().size() - 1);
            btnUp.setOnAction(e -> viewModel.moveUp(idx));
            btnDown.setOnAction(e -> viewModel.moveDown(idx));
            btnDel.setOnAction(e -> viewModel.removeAt(idx));
            setGraphic(root);
        }
    }
}
