package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.SubtitleFormat;
import com.ffmpegstudio.domain.model.SubtitleOperation.SubtitleAction;
import com.ffmpegstudio.domain.model.SubtitleStream;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.SubtitlesViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;

public class SubtitlesController {

    @FXML private Label lblMediaName;
    @FXML private Label lblSubCount;
    @FXML private ListView<SubtitleStream> lvSubtitles;
    @FXML private ComboBox<SubtitleAction> cmbAction;
    @FXML private ComboBox<SubtitleFormat> cmbFormat;
    @FXML private ComboBox<ContainerFormat> cmbContainer;
    @FXML private TextField txtExternalSub;
    @FXML private TextField txtLanguage;
    @FXML private TextField txtTitle;
    @FXML private TextField txtOffset;
    @FXML private CheckBox chkDefault;
    @FXML private CheckBox chkForced;
    @FXML private TextField txtOutputPath;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnCancel;
    @FXML private Button btnQueue;
    @FXML private Button btnExecute;

    private SubtitlesViewModel viewModel;
    private MainLayoutController mainController;

    public void setMainController(MainLayoutController mainController) {
        this.mainController = mainController;
    }

    public void loadMedia(java.nio.file.Path path) {
        viewModel.loadMedia(path);
    }

    @FXML
    public void initialize() {
        AppContext ctx = AppContext.getInstance();
        viewModel = new SubtitlesViewModel(
                ctx.getAnalyzeMediaService(),
                ctx.getTranscodeMediaService(),
                ctx.getQueueService(),
                ctx.getSettingsService(),
                ctx.getCapabilitiesService()
        );

        cmbAction.getItems().setAll(SubtitleAction.values());
        cmbAction.valueProperty().bindBidirectional(viewModel.selectedActionProperty());

        cmbFormat.getItems().setAll(SubtitleFormat.values());
        cmbFormat.valueProperty().bindBidirectional(viewModel.targetFormatProperty());

        cmbContainer.getItems().setAll(ContainerFormat.values());
        cmbContainer.valueProperty().bindBidirectional(viewModel.targetContainerProperty());

        txtExternalSub.textProperty().bindBidirectional(viewModel.externalSubPathProperty());
        txtLanguage.textProperty().bindBidirectional(viewModel.languageProperty());
        txtTitle.textProperty().bindBidirectional(viewModel.titleProperty());
        txtOffset.textProperty().bindBidirectional(viewModel.timeOffsetTextProperty());
        chkDefault.selectedProperty().bindBidirectional(viewModel.defaultFlagProperty());
        chkForced.selectedProperty().bindBidirectional(viewModel.forcedFlagProperty());
        txtOutputPath.textProperty().bindBidirectional(viewModel.outputPathProperty());

        lblStatus.textProperty().bind(viewModel.statusMessageProperty());
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());

        viewModel.currentMediaProperty().addListener((obs, o, mf) -> {
            if (mf != null) {
                lblMediaName.setText(mf.fileName());
                lblSubCount.setText(viewModel.getSubtitleStreams().size() + " legendas");
            }
        });

        viewModel.isProcessingProperty().addListener((obs, o, proc) -> {
            btnCancel.setVisible(proc);
            btnCancel.setManaged(proc);
            btnExecute.setDisable(proc);
            btnQueue.setDisable(proc);
        });

        lvSubtitles.setItems(viewModel.getSubtitleStreams());
        lvSubtitles.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(SubtitleStream item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                SubtitleFormat fmt = SubtitleFormat.fromCodecOrExtension(item.codecName());
                String kind = fmt.isTextBased() ? "Texto" : "Imagem";
                String flags = (item.isDefault() ? " [DEFAULT]" : "") + (item.isForced() ? " [FORCED]" : "");
                setText(String.format("Faixa #%d (Global %d) — %s (%s) • Idioma: %s • %s%s",
                        getIndex(), item.index(), item.codecName(), kind, item.language(),
                        item.title().isBlank() ? "Sem título" : item.title(), flags));
            }
        });

        lvSubtitles.getSelectionModel().selectedIndexProperty().addListener((obs, o, idx) -> {
            if (idx != null && idx.intValue() >= 0) {
                viewModel.selectedStreamIndexProperty().set(idx.intValue());
                SubtitleStream sel = lvSubtitles.getItems().get(idx.intValue());
                viewModel.languageProperty().set(sel.language());
                viewModel.titleProperty().set(sel.title());
                viewModel.defaultFlagProperty().set(sel.isDefault());
                viewModel.forcedFlagProperty().set(sel.isForced());
            }
        });
    }

    @FXML
    public void handleSelectMedia() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Vídeo");
        File f = chooser.showOpenDialog(lvSubtitles.getScene().getWindow());
        if (f != null) {
            viewModel.loadMedia(f.toPath());
        }
    }

    @FXML
    public void handleSelectExternalSub() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Arquivo de Legenda");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Legendas", "*.srt", "*.ass", "*.ssa", "*.vtt", "*.sup", "*.sub"));
        File f = chooser.showOpenDialog(lvSubtitles.getScene().getWindow());
        if (f != null) {
            viewModel.externalSubPathProperty().set(f.getAbsolutePath());
        }
    }

    @FXML
    public void handleBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Arquivo Como");
        File f = chooser.showSaveDialog(lvSubtitles.getScene().getWindow());
        if (f != null) {
            viewModel.outputPathProperty().set(f.getAbsolutePath());
        }
    }

    @FXML public void handleExecute() { viewModel.execute(); }
    @FXML public void handleAddToQueue() { viewModel.addToQueue(); }
    @FXML public void handleCancel() { viewModel.cancel(); }
}
