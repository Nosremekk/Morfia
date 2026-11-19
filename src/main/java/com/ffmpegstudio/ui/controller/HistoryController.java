package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.application.service.DiagnosticsService;
import com.ffmpegstudio.application.service.JobQueueService;
import com.ffmpegstudio.domain.model.JobHistoryEntry;
import com.ffmpegstudio.domain.model.JobStatus;
import com.ffmpegstudio.infrastructure.config.JsonQueueHistoryRepository;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.component.UiComponents;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatusFilter;
    @FXML private ListView<JobHistoryEntry> lvHistory;

    @FXML private CheckBox chkIncludePaths;
    @FXML private TextArea txtDiagnostics;

    private JsonQueueHistoryRepository historyRepo;
    private JobQueueService queueService;
    private DiagnosticsService diagnosticsService;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        AppContext ctx = AppContext.getInstance();
        historyRepo = ctx.getQueueHistoryRepository();
        queueService = (JobQueueService) ctx.getQueueService();
        diagnosticsService = ctx.getDiagnosticsService();

        cmbStatusFilter.getItems().addAll("Todos", "COMPLETED", "FAILED", "CANCELLED");
        cmbStatusFilter.getSelectionModel().selectFirst();
        cmbStatusFilter.valueProperty().addListener((obs, o, n) -> refresh());
        txtSearch.textProperty().addListener((obs, o, n) -> refresh());

        lvHistory.setPlaceholder(UiComponents.createEmptyState(
                "Nenhuma operação registrada no histórico",
                "Tarefas concluídas, falhadas ou canceladas aparecerão aqui automaticamente."));
        lvHistory.setCellFactory(p -> new HistoryCell());

        refresh();
    }

    @FXML
    public void handleRefresh() {
        refresh();
    }

    public void refresh() {
        if (historyRepo == null) return;
        String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String st = cmbStatusFilter.getValue();

        List<JobHistoryEntry> filtered = historyRepo.loadHistory().stream()
                .filter(e -> st == null || "Todos".equals(st) || e.status().name().equals(st))
                .filter(e -> q.isEmpty()
                        || (e.operationName() != null && e.operationName().toLowerCase().contains(q))
                        || (e.outputPath() != null && e.outputPath().toLowerCase().contains(q)))
                .toList();
        lvHistory.getItems().setAll(filtered);
    }

    @FXML
    public void handleClearHistory() {
        if (UiComponents.confirmAction("Limpar Histórico",
                "Deseja limpar todos os registros do histórico?",
                "Esta ação remove apenas os registros de log; os arquivos multimídia no disco serão preservados.")) {
            historyRepo.clearHistory();
            refresh();
        }
    }

    @FXML
    public void handleGenerateDiagnostics() {
        boolean includePaths = chkIncludePaths.isSelected();
        txtDiagnostics.setText("Gerando diagnóstico local...");
        AsyncHelper.supplyAsync(
                () -> diagnosticsService.generateReport(includePaths),
                report -> txtDiagnostics.setText(report),
                err -> txtDiagnostics.setText("Erro ao gerar diagnóstico: " + err.getMessage())
        );
    }

    @FXML
    public void handleCopyDiagnostics() {
        UiComponents.copyToClipboard(txtDiagnostics.getText());
    }

    @FXML
    public void handleExportDiagnostics() {
        if (txtDiagnostics.getText().isBlank()) {
            handleGenerateDiagnostics();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Relatório de Diagnóstico");
        chooser.setInitialFileName(com.ffmpegstudio.domain.model.AppIdentity.diagnosticsFilename());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto (*.txt)", "*.txt"));
        File f = chooser.showSaveDialog(txtDiagnostics.getScene().getWindow());
        if (f != null) {
            try {
                Files.writeString(f.toPath(), txtDiagnostics.getText());
            } catch (Exception e) {
                UiComponents.showErrorWithDetails("Erro ao Exportar", "Não foi possível salvar o relatório.", e.getMessage());
            }
        }
    }

    private class HistoryCell extends ListCell<JobHistoryEntry> {
        private final HBox root = new HBox(10);
        private final VBox info = new VBox(2);
        private final Label lblTitle = new Label();
        private final Label lblDetails = new Label();
        private final Label lblBadge = new Label();
        private final Button btnOpenDir = new Button("Pasta");
        private final Button btnDup = new Button("Duplicar na Fila");
        private final Button btnDel = new Button("✕");

        HistoryCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            lblTitle.setStyle("-fx-font-weight: bold;");
            lblDetails.getStyleClass().add("text-secondary");
            info.getChildren().addAll(lblTitle, lblDetails);
            HBox.setHgrow(info, Priority.ALWAYS);

            btnOpenDir.getStyleClass().addAll("button-outline", "button-sm");
            btnDup.getStyleClass().addAll("button-outline", "button-sm");
            btnDel.getStyleClass().addAll("button-danger", "button-sm");
            root.getChildren().addAll(info, lblBadge, btnOpenDir, btnDup, btnDel);
        }

        @Override
        protected void updateItem(JobHistoryEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            Path outPath = Path.of(item.outputPath());
            lblTitle.setText(item.operationName() + " ➔ " + outPath.getFileName());
            String when = item.createdAt() != null ? FMT.format(item.createdAt()) : "";
            String err = item.errorMessage() != null && !item.errorMessage().isBlank() ? " • " + item.errorMessage() : "";
            lblDetails.setText(when + " • " + item.operationDescription() + err);

            lblBadge.setText(item.status().name());
            lblBadge.getStyleClass().removeAll("badge", "badge-success", "badge-danger", "badge-neutral", "badge-warning");
            lblBadge.getStyleClass().add("badge");
            if (item.status() == JobStatus.COMPLETED) lblBadge.getStyleClass().add("badge-success");
            else if (item.status() == JobStatus.FAILED) lblBadge.getStyleClass().add("badge-danger");
            else lblBadge.getStyleClass().add("badge-neutral");

            btnOpenDir.setOnAction(e -> com.ffmpegstudio.infrastructure.platform.DesktopIntegrationService.openDirectory(outPath));

            btnDup.setOnAction(e -> {
                historyRepo.reconstructJob(item).ifPresent(job -> {
                    queueService.duplicateJob(job);
                });
            });

            btnDel.setOnAction(e -> {
                historyRepo.deleteHistoryEntry(item.id());
                refresh();
            });

            setGraphic(root);
        }
    }
}
