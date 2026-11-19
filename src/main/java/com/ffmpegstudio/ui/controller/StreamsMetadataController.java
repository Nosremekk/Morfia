package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.ChapterInfo;
import com.ffmpegstudio.domain.model.ContainerFormat;
import com.ffmpegstudio.domain.model.FormatUtils;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.StreamsMetadataViewModel;
import com.ffmpegstudio.ui.viewmodel.StreamsMetadataViewModel.StreamItem;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

public class StreamsMetadataController {

    @FXML private Label lblMediaName;
    @FXML private ComboBox<ContainerFormat> cmbContainer;
    @FXML private ListView<StreamItem> lvStreams;
    @FXML private ListView<ChapterInfo> lvChapters;

    @FXML private TextField txtTitle;
    @FXML private TextField txtArtist;
    @FXML private TextField txtAlbum;
    @FXML private TextField txtDate;
    @FXML private TextField txtGenre;
    @FXML private TextField txtLanguage;
    @FXML private TextField txtCopyright;
    @FXML private TextField txtComment;
    @FXML private TextField txtDescription;
    @FXML private CheckBox chkClearAll;
    @FXML private CheckBox chkPreserveChapters;

    @FXML private TextField txtOutputPath;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnCancel;

    private StreamsMetadataViewModel viewModel;
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
        viewModel = new StreamsMetadataViewModel(
                ctx.getAnalyzeMediaService(),
                ctx.getTranscodeMediaService(),
                ctx.getQueueService()
        );

        cmbContainer.getItems().setAll(ContainerFormat.values());
        cmbContainer.valueProperty().bindBidirectional(viewModel.targetContainerProperty());

        txtTitle.textProperty().bindBidirectional(viewModel.tagTitleProperty());
        txtArtist.textProperty().bindBidirectional(viewModel.tagArtistProperty());
        txtAlbum.textProperty().bindBidirectional(viewModel.tagAlbumProperty());
        txtDate.textProperty().bindBidirectional(viewModel.tagDateProperty());
        txtGenre.textProperty().bindBidirectional(viewModel.tagGenreProperty());
        txtLanguage.textProperty().bindBidirectional(viewModel.tagLanguageProperty());
        txtCopyright.textProperty().bindBidirectional(viewModel.tagCopyrightProperty());
        txtComment.textProperty().bindBidirectional(viewModel.tagCommentProperty());
        txtDescription.textProperty().bindBidirectional(viewModel.tagDescriptionProperty());
        chkClearAll.selectedProperty().bindBidirectional(viewModel.clearAllMetadataProperty());
        chkPreserveChapters.selectedProperty().bindBidirectional(viewModel.preserveChaptersProperty());

        txtOutputPath.textProperty().bindBidirectional(viewModel.outputPathProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());

        viewModel.currentMediaProperty().addListener((obs, o, mf) -> {
            if (mf != null) lblMediaName.setText(mf.fileName());
        });

        viewModel.isProcessingProperty().addListener((obs, o, proc) -> {
            btnCancel.setVisible(proc);
            btnCancel.setManaged(proc);
        });

        lvStreams.setItems(viewModel.getStreamItems());
        lvStreams.setCellFactory(p -> new StreamItemCell());

        lvChapters.setItems(viewModel.getChapters());
        lvChapters.setCellFactory(p -> new ListCell<>() {
            @Override
            protected void updateItem(ChapterInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("Capítulo #%d: %s (%s -> %s)",
                            item.id(), item.title(),
                            FormatUtils.formatDuration(item.startTime()),
                            FormatUtils.formatDuration(item.endTime())));
                }
            }
        });
    }

    @FXML
    public void handleSelectMedia() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Mídia");
        File f = chooser.showOpenDialog(lvStreams.getScene().getWindow());
        if (f != null) viewModel.loadMedia(f.toPath());
    }

    @FXML
    public void handleBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Arquivo Como");
        File f = chooser.showSaveDialog(lvStreams.getScene().getWindow());
        if (f != null) viewModel.outputPathProperty().set(f.getAbsolutePath());
    }

    @FXML public void handleExecuteRemux() { viewModel.executeRemux(); }
    @FXML public void handleQueueRemux() { viewModel.queueRemux(); }
    @FXML public void handleExecuteMeta() { viewModel.executeMetadataSave(); }
    @FXML public void handleQueueMeta() { viewModel.queueMetadataSave(); }
    @FXML public void handleCancel() { viewModel.cancel(); }

    private class StreamItemCell extends ListCell<StreamItem> {
        private final HBox root = new HBox(10);
        private final CheckBox chkInclude = new CheckBox();
        private final VBox info = new VBox(2);
        private final Label lblMain = new Label();
        private final Label lblSub = new Label();
        private final TextField txtStreamTitle = new TextField();
        private final TextField txtStreamLang = new TextField();
        private final CheckBox chkDefault = new CheckBox("Default");
        private final Button btnUp = new Button("▲");
        private final Button btnDown = new Button("▼");

        StreamItemCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            lblMain.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc;");
            lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            info.getChildren().addAll(lblMain, lblSub);
            HBox.setHgrow(info, Priority.ALWAYS);

            txtStreamTitle.setPromptText("Título");
            txtStreamTitle.setPrefWidth(130);
            txtStreamLang.setPromptText("Idioma");
            txtStreamLang.setPrefWidth(60);

            btnUp.getStyleClass().addAll("button-outline", "button-sm");
            btnDown.getStyleClass().addAll("button-outline", "button-sm");

            root.getChildren().addAll(chkInclude, info, txtStreamTitle, txtStreamLang, chkDefault, btnUp, btnDown);
        }

        @Override
        protected void updateItem(StreamItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            chkInclude.selectedProperty().unbind();
            txtStreamTitle.textProperty().unbind();
            txtStreamLang.textProperty().unbind();
            chkDefault.selectedProperty().unbind();

            lblMain.setText(String.format("0:%d [%s] %s", item.getGlobalIndex(), item.getStreamType().toUpperCase(), item.getCodecName()));
            lblSub.setText(item.getTechnicalDetails());

            chkInclude.selectedProperty().bindBidirectional(item.includedProperty());
            txtStreamTitle.textProperty().bindBidirectional(item.titleProperty());
            txtStreamLang.textProperty().bindBidirectional(item.languageProperty());
            chkDefault.selectedProperty().bindBidirectional(item.defaultFlagProperty());

            int idx = getIndex();
            btnUp.setDisable(idx == 0);
            btnDown.setDisable(idx >= viewModel.getStreamItems().size() - 1);
            btnUp.setOnAction(e -> viewModel.moveStreamUp(idx));
            btnDown.setOnAction(e -> viewModel.moveStreamDown(idx));
            setGraphic(root);
        }
    }
}
