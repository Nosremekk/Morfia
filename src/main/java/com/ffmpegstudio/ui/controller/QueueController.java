package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.ConversionPreset;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.IconHelper;
import com.ffmpegstudio.ui.viewmodel.QueueViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class QueueController {
    private static final Logger logger = LoggerFactory.getLogger(QueueController.class);

    @FXML private Button btnAddBatch;
    @FXML private ComboBox<ConversionPreset> cmbBatchPreset;
    @FXML private Button btnStartPause;
    @FXML private Button btnClearFinished;
    @FXML private Button btnCancelAll;

    @FXML private Label lblSummaryStatus;
    @FXML private Label lblProgressPercent;
    @FXML private ProgressBar progressAggregate;

    @FXML private Label lblJobsCount;
    @FXML private ListView<ProcessingJob> lvQueueJobs;

    private QueueViewModel viewModel;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new QueueViewModel(context.getQueueService(), context.getPresetService());

        setupPresetsComboBox();
        setupListView();
        setupBindings();
    }

    private void setupPresetsComboBox() {
        cmbBatchPreset.setItems(viewModel.getAvailablePresets());
        if (!viewModel.getAvailablePresets().isEmpty()) {
            cmbBatchPreset.getSelectionModel().selectFirst();
        }
    }

    private void setupBindings() {
        lblSummaryStatus.textProperty().bind(viewModel.statusSummaryProperty());
        progressAggregate.progressProperty().bind(viewModel.aggregateProgressProperty());

        viewModel.aggregateProgressProperty().addListener((obs, oldV, p) -> {
            int pct = (int) Math.round(p.doubleValue() * 100);
            lblProgressPercent.setText(pct + "%");
        });

        viewModel.totalJobsCountProperty().addListener((obs, oldV, total) -> {
            lblJobsCount.setText(total + " tarefas");
        });

        viewModel.isRunningProperty().addListener((obs, oldV, running) -> {
            if (Boolean.TRUE.equals(running)) {
                btnStartPause.setText("Pausar Fila");
                btnStartPause.getStyleClass().removeAll("button-success", "button-outline");
                btnStartPause.getStyleClass().add("button-outline");
            } else {
                btnStartPause.setText("Iniciar Fila");
                btnStartPause.getStyleClass().removeAll("button-success", "button-outline");
                btnStartPause.getStyleClass().add("button-success");
            }
        });
    }

    private void setupListView() {
        lvQueueJobs.setItems(viewModel.getJobsList());
        lvQueueJobs.setCellFactory(param -> new QueueJobListCell());
    }

    @FXML
    public void handleAddBatchFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Arquivos para Processamento em Lote");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Arquivos Multimídia", "*.mp4", "*.mkv", "*.mov", "*.avi", "*.webm", "*.mp3", "*.flac", "*.wav"),
                new FileChooser.ExtensionFilter("Todos os Arquivos", "*.*")
        );
        List<File> files = chooser.showOpenMultipleDialog(lvQueueJobs.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            List<Path> paths = files.stream().map(File::toPath).toList();
            ConversionPreset preset = cmbBatchPreset.getValue();
            viewModel.addBatchFiles(paths, preset);
        }
    }

    @FXML
    public void handleToggleStartPause() {
        if (viewModel.isRunningProperty().get()) {
            viewModel.pauseQueue();
        } else {
            viewModel.startQueue();
        }
    }

    @FXML
    public void handleClearFinished() {
        viewModel.clearFinished();
    }

    @FXML
    public void handleCancelAll() {
        viewModel.cancelAll();
    }

    private class QueueJobListCell extends ListCell<ProcessingJob> {
        private final HBox root = new HBox(12);
        private final VBox infoBox = new VBox(4);
        private final Label lblFiles = new Label();
        private final Label lblOperation = new Label();
        private final Label lblStatusBadge = new Label();
        private final ProgressBar jobProgress = new ProgressBar(0.0);
        private final Button btnUp = new Button("▲");
        private final Button btnDown = new Button("▼");
        private final Button btnAction = new Button("✕");

        QueueJobListCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            root.setStyle("-fx-padding: 8 10; -fx-background-color: transparent;");

            lblFiles.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc;");
            lblOperation.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

            infoBox.getChildren().addAll(lblFiles, lblOperation);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            jobProgress.setPrefWidth(120);
            jobProgress.setPrefHeight(6);

            btnUp.getStyleClass().addAll("button-outline", "button-sm");
            btnDown.getStyleClass().addAll("button-outline", "button-sm");
            btnAction.getStyleClass().addAll("button-danger", "button-sm");

            HBox controlsBox = new HBox(6, jobProgress, lblStatusBadge, btnUp, btnDown, btnAction);
            controlsBox.setAlignment(Pos.CENTER_RIGHT);

            root.getChildren().addAll(infoBox, controlsBox);
        }

        @Override
        protected void updateItem(ProcessingJob job, boolean empty) {
            super.updateItem(job, empty);

            if (empty || job == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            String inName = job.getInputFile() != null ? job.getInputFile().getFileName().toString() : "Arquivo";
            String outName = job.getOutputFile() != null ? job.getOutputFile().getFileName().toString() : "Saída";
            lblFiles.setText(inName + " ➔ " + outName);

            String opName = job.getOperation() != null ? job.getOperation().name() : "Operação";
            lblOperation.setText(opName);

            JobStatus status = job.getStatus();
            lblStatusBadge.setText(status.name());
            lblStatusBadge.getStyleClass().removeAll("badge", "badge-info", "badge-success", "badge-warning", "badge-danger", "badge-neutral");
            lblStatusBadge.getStyleClass().add("badge");

            switch (status) {
                case PENDING -> lblStatusBadge.getStyleClass().add("badge-neutral");
                case RUNNING -> lblStatusBadge.getStyleClass().add("badge-warning");
                case COMPLETED -> lblStatusBadge.getStyleClass().add("badge-success");
                case FAILED -> lblStatusBadge.getStyleClass().add("badge-danger");
                case CANCELLED -> lblStatusBadge.getStyleClass().add("badge-neutral");
            }

            ProcessingProgress p = job.getProgress();
            if (p != null) {
                jobProgress.setProgress(p.percentage() / 100.0);
            } else if (status == JobStatus.COMPLETED) {
                jobProgress.setProgress(1.0);
            } else {
                jobProgress.setProgress(0.0);
            }

            boolean isPending = status == JobStatus.PENDING;
            boolean isRunning = status == JobStatus.RUNNING;

            btnUp.setDisable(!isPending);
            btnDown.setDisable(!isPending);

            btnUp.setOnAction(e -> viewModel.moveUp(job.getId()));
            btnDown.setOnAction(e -> viewModel.moveDown(job.getId()));

            if (isRunning) {
                btnAction.setText("Parar");
                btnAction.setOnAction(e -> viewModel.cancelJob(job.getId()));
            } else {
                btnAction.setText("✕");
                btnAction.setOnAction(e -> viewModel.removeJob(job.getId()));
            }

            setGraphic(root);
        }
    }
}
