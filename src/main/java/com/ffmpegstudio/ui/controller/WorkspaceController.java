package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.application.service.MediaLibraryService;
import com.ffmpegstudio.application.service.MediaLibraryService.SortOrder;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.domain.model.WorkspaceMedia;
import com.ffmpegstudio.domain.model.WorkspaceMedia.MediaKind;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.component.UiComponents;
import com.ffmpegstudio.ui.util.AsyncHelper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class WorkspaceController {

    @FXML private VBox dropContainer;
    @FXML private Label lblCount;
    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbKindFilter;
    @FXML private ComboBox<SortOrder> cmbSortOrder;
    @FXML private ListView<WorkspaceMedia> lvLibrary;

    private MediaLibraryService libraryService;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        libraryService = AppContext.getInstance().getMediaLibraryService();
        libraryService.addListener(() -> Platform.runLater(this::refreshList));

        cmbKindFilter.getItems().addAll("Todos", "Vídeo", "Áudio", "Imagem");
        cmbKindFilter.getSelectionModel().selectFirst();
        cmbKindFilter.valueProperty().addListener((obs, o, n) -> refreshList());

        cmbSortOrder.getItems().setAll(SortOrder.values());
        cmbSortOrder.getSelectionModel().select(SortOrder.IMPORTED_DESC);
        cmbSortOrder.valueProperty().addListener((obs, o, n) -> refreshList());

        txtSearch.textProperty().addListener((obs, o, n) -> refreshList());

        lvLibrary.setPlaceholder(UiComponents.createEmptyState(
                "Biblioteca da sessão vazia",
                "Importe arquivos ou pastas (Ctrl+O) para compartilhar mídias entre todas as ferramentas."));
        lvLibrary.setCellFactory(p -> new LibraryCell());

        setupDragAndDrop();
        refreshList();
    }

    private void setupDragAndDrop() {
        dropContainer.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        dropContainer.setOnDragDropped(e -> {
            if (e.getDragboard().hasFiles()) {
                importFilesAsync(e.getDragboard().getFiles().stream().map(File::toPath).toList());
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    public void focusSearch() {
        txtSearch.requestFocus();
    }

    @FXML
    public void handleImportFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importar Mídias para a Biblioteca");
        List<File> files = chooser.showOpenMultipleDialog(lvLibrary.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            importFilesAsync(files.stream().map(File::toPath).toList());
        }
    }

    @FXML
    public void handleImportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Importar Pasta de Mídias");
        File dir = chooser.showDialog(lvLibrary.getScene().getWindow());
        if (dir != null) {
            AsyncHelper.supplyAsync(() -> {
                try {
                    return libraryService.importDirectory(dir.toPath());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, res -> refreshList(), null);
        }
    }

    @FXML
    public void handleRefreshFiles() {
        AsyncHelper.runAsync(() -> libraryService.refreshAndValidateFiles(), this::refreshList, null);
    }

    private void importFilesAsync(List<Path> paths) {
        AsyncHelper.supplyAsync(() -> libraryService.importFiles(paths), res -> refreshList(), null);
    }

    private void refreshList() {
        MediaKind kind = switch (cmbKindFilter.getValue() == null ? "Todos" : cmbKindFilter.getValue()) {
            case "Vídeo" -> MediaKind.VIDEO;
            case "Áudio" -> MediaKind.AUDIO;
            case "Imagem" -> MediaKind.IMAGE;
            default -> null;
        };
        List<WorkspaceMedia> items = libraryService.query(txtSearch.getText(), kind, cmbSortOrder.getValue());
        lvLibrary.getItems().setAll(items);
        lblCount.setText(items.size() + " itens");
    }

    private class LibraryCell extends ListCell<WorkspaceMedia> {
        private final HBox root = new HBox(10);
        private final VBox info = new VBox(2);
        private final Label lblName = new Label();
        private final Label lblMeta = new Label();
        private final Button btnConv = new Button("Converter");
        private final Button btnComp = new Button("Comprimir");
        private final Button btnFilt = new Button("Filtros");
        private final Button btnRemove = new Button("✕");

        LibraryCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            lblName.setStyle("-fx-font-weight: bold;");
            lblMeta.getStyleClass().add("text-secondary");
            info.getChildren().addAll(lblName, lblMeta);
            HBox.setHgrow(info, Priority.ALWAYS);

            btnConv.getStyleClass().addAll("button-outline", "button-sm");
            btnComp.getStyleClass().addAll("button-outline", "button-sm");
            btnFilt.getStyleClass().addAll("button-outline", "button-sm");
            btnRemove.getStyleClass().addAll("button-danger", "button-sm");
            root.getChildren().addAll(info, btnConv, btnComp, btnFilt, btnRemove);
        }

        @Override
        protected void updateItem(WorkspaceMedia item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            String statusTag = item.missingOnDisk() ? " [AUSENTE NO DISCO]"
                    : (item.modifiedSinceInspection() ? " [MODIFICADO]" : "");
            lblName.setText("[" + item.kind().label() + "] " + item.fileName() + statusTag);

            String dur = (item.mediaFile() != null && item.mediaFile().metadata() != null)
                    ? FormatUtils.formatDuration(item.mediaFile().metadata().duration())
                    : "-";
            lblMeta.setText(FormatUtils.formatFileSize(item.fileSizeBytes()) + " • " + dur + " • " + item.path().getParent());

            btnConv.setDisable(item.missingOnDisk());
            btnComp.setDisable(item.missingOnDisk());
            btnFilt.setDisable(item.missingOnDisk());

            btnConv.setOnAction(e -> { if (mainController != null) mainController.openFileInConverter(item.path()); });
            btnComp.setOnAction(e -> { if (mainController != null) mainController.openFileInCompression(item.path()); });
            btnFilt.setOnAction(e -> { if (mainController != null) mainController.openFileInFilters(item.path()); });
            btnRemove.setOnAction(e -> libraryService.removeReference(item.path()));

            ContextMenu menu = new ContextMenu();
            MenuItem miTools = new MenuItem("Abrir em Ferramentas (Áudio/Trim/GIF)");
            miTools.setOnAction(e -> { if (mainController != null) mainController.openFileInTools(item.path()); });
            MenuItem miSubs = new MenuItem("Abrir em Legendas");
            miSubs.setOnAction(e -> { if (mainController != null) mainController.openFileInSubtitles(item.path()); });
            MenuItem miStreams = new MenuItem("Abrir em Streams & Metadados");
            miStreams.setOnAction(e -> { if (mainController != null) mainController.openFileInStreams(item.path()); });
            MenuItem miFolder = new MenuItem("Abrir Pasta Original");
            miFolder.setOnAction(e -> com.ffmpegstudio.infrastructure.platform.DesktopIntegrationService.openDirectory(item.path()));
            MenuItem miDel = new MenuItem("Remover Referência da Biblioteca");
            miDel.setOnAction(e -> libraryService.removeReference(item.path()));
            menu.getItems().addAll(miTools, miSubs, miStreams, miFolder, miDel);
            setContextMenu(menu);

            setGraphic(root);
        }
    }
}
