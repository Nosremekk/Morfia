package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.domain.model.filter.AudioFilters;
import com.ffmpegstudio.domain.model.filter.MediaFilter;
import com.ffmpegstudio.domain.model.filter.VideoFilters;
import com.ffmpegstudio.domain.model.filter.VideoFilters.RotationAngle;
import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.viewmodel.FiltersViewModel;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Locale;

public class FiltersController {

    @FXML private Label lblMediaName;
    @FXML private Label lblImpact;
    @FXML private Slider sliderTimestamp;
    @FXML private Label lblTimestamp;
    @FXML private ImageView imgBefore;
    @FXML private ImageView imgAfter;
    @FXML private Label lblPreviewNotice;

    @FXML private ComboBox<String> cmbFilterPreset;
    @FXML private TextField txtParam1;
    @FXML private TextField txtParam2;
    @FXML private TextField txtParam3;
    @FXML private TextField txtParam4;
    @FXML private Label lblParamHint;

    @FXML private ListView<MediaFilter> lvFilterChain;
    @FXML private Label lblValidation;
    @FXML private TextField txtOutputPath;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnCancel;
    @FXML private Button btnQueue;
    @FXML private Button btnExecute;

    private FiltersViewModel viewModel;
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
        viewModel = new FiltersViewModel(
                ctx.getAnalyzeMediaService(),
                ctx.getTranscodeMediaService(),
                ctx.getQueueService(),
                ctx.getSettingsService(),
                ctx.getCapabilitiesService(),
                ctx.getFilterPreviewService()
        );

        cmbFilterPreset.getItems().addAll(
                "Resize (Largura)",
                "Crop (W, H, X, Y)",
                "Pad (W, H)",
                "Rotação 90° Horário",
                "Rotação 90° Anti-horário",
                "Rotação 180°",
                "Espelhar Horizontal",
                "Espelhar Vertical",
                "Proporção DAR (16/9)",
                "Cor (Brilho, Contraste, Sat, Gamma)",
                "Escala de Cinza",
                "Nitidez (Sharpen)",
                "Desfoque (Blur)",
                "Redução de Ruído",
                "Desentrelaçamento (YADIF)",
                "Alterar FPS",
                "Velocidade Vídeo (Multiplier)",
                "Fade In Vídeo (Início, Duração)",
                "Fade Out Vídeo (Início, Duração)",
                "Reverter Vídeo (Clipes curtos!)",
                "Áudio: Volume (Multiplier)",
                "Áudio: Velocidade (Multiplier)",
                "Áudio: Fade In (Início, Duração)",
                "Áudio: Fade Out (Início, Duração)",
                "Áudio: Equalizador (Graves, Médios, Agudos)",
                "Áudio: Normalização EBU R128",
                "Áudio: Mono (1.0)",
                "Áudio: Estéreo (2.0)"
        );
        cmbFilterPreset.getSelectionModel().selectFirst();
        cmbFilterPreset.valueProperty().addListener((obs, o, sel) -> updateParamDefaults(sel));
        updateParamDefaults(cmbFilterPreset.getValue());

        sliderTimestamp.maxProperty().bind(viewModel.maxDurationSecProperty());
        sliderTimestamp.valueProperty().bindBidirectional(viewModel.previewTimestampSecProperty());
        viewModel.previewTimestampSecProperty().addListener((obs, o, val) ->
                lblTimestamp.setText(String.format(Locale.US, "%.1fs", val.doubleValue())));

        imgBefore.imageProperty().bind(viewModel.beforeImageProperty());
        imgAfter.imageProperty().bind(viewModel.afterImageProperty());
        lblPreviewNotice.textProperty().bind(viewModel.previewNoticeProperty());

        lblImpact.textProperty().bind(viewModel.impactSummaryProperty());
        lblValidation.textProperty().bind(viewModel.validationSummaryProperty());
        txtOutputPath.textProperty().bindBidirectional(viewModel.outputPathProperty());
        lblStatus.textProperty().bind(viewModel.statusMessageProperty());
        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());

        viewModel.currentMediaProperty().addListener((obs, o, mf) -> {
            if (mf != null) lblMediaName.setText(mf.fileName());
        });

        viewModel.isProcessingProperty().addListener((obs, o, proc) -> {
            btnCancel.setVisible(proc);
            btnCancel.setManaged(proc);
            btnExecute.setDisable(proc);
            btnQueue.setDisable(proc);
        });

        lvFilterChain.setItems(viewModel.getFilterChain());
        lvFilterChain.setCellFactory(param -> new FilterCell());
    }

    private void updateParamDefaults(String type) {
        if (type == null) return;
        txtParam1.clear();
        txtParam2.clear();
        txtParam3.clear();
        txtParam4.clear();

        if (type.startsWith("Resize")) {
            txtParam1.setText("1280");
            lblParamHint.setText("Param 1: Largura alvo (mantém proporção).");
        } else if (type.startsWith("Crop")) {
            txtParam1.setText("640");
            txtParam2.setText("480");
            txtParam3.setText("0");
            txtParam4.setText("0");
            lblParamHint.setText("Param 1: Largura | Param 2: Altura | Param 3: X | Param 4: Y.");
        } else if (type.startsWith("Pad")) {
            txtParam1.setText("1920");
            txtParam2.setText("1080");
            lblParamHint.setText("Param 1: Largura final | Param 2: Altura final (centraliza com barras pretas).");
        } else if (type.startsWith("Proporção")) {
            txtParam1.setText("16/9");
            lblParamHint.setText("Param 1: Proporção (ex: 16/9, 4/3).");
        } else if (type.startsWith("Cor")) {
            txtParam1.setText("0.05");
            txtParam2.setText("1.10");
            txtParam3.setText("1.15");
            txtParam4.setText("1.00");
            lblParamHint.setText("P1: Brilho (-1..1) | P2: Contraste (0..2) | P3: Saturação (0..3) | P4: Gamma (0.1..10).");
        } else if (type.startsWith("Nitidez")) {
            txtParam1.setText("1.0");
            lblParamHint.setText("Param 1: Intensidade (0.0 a 2.5).");
        } else if (type.startsWith("Desfoque")) {
            txtParam1.setText("2.0");
            lblParamHint.setText("Param 1: Sigma do desfoque gaussiano (0.5 a 10.0).");
        } else if (type.startsWith("Redução de Ruído")) {
            txtParam1.setText("4.0");
            lblParamHint.setText("Param 1: Força espacial (1.0 a 10.0).");
        } else if (type.startsWith("Alterar FPS")) {
            txtParam1.setText("30");
            lblParamHint.setText("Param 1: FPS de destino.");
        } else if (type.contains("Velocidade")) {
            txtParam1.setText("1.5");
            lblParamHint.setText("Param 1: Multiplicador de velocidade (ex: 0.5, 1.5, 2.0).");
        } else if (type.contains("Fade")) {
            txtParam1.setText("0.0");
            txtParam2.setText("1.0");
            lblParamHint.setText("Param 1: Início (s) | Param 2: Duração (s).");
        } else if (type.contains("Volume")) {
            txtParam1.setText("1.5");
            lblParamHint.setText("Param 1: Multiplicador de volume (ex: 1.5 = 150%).");
        } else if (type.contains("Equalizador")) {
            txtParam1.setText("3.0");
            txtParam2.setText("0.0");
            txtParam3.setText("2.0");
            lblParamHint.setText("P1: Graves (dB) | P2: Médios (dB) | P3: Agudos (dB).");
        } else {
            lblParamHint.setText("Filtro sem parâmetros adicionais obrigatórios.");
        }
    }

    @FXML
    public void handleAddFilter() {
        String sel = cmbFilterPreset.getValue();
        if (sel == null) return;

        MediaFilter f = switch (sel) {
            case "Resize (Largura)" -> new VideoFilters.Resize(parseInt(txtParam1, 1280), -1, true);
            case "Crop (W, H, X, Y)" -> new VideoFilters.Crop(parseInt(txtParam1, 640), parseInt(txtParam2, 480), parseInt(txtParam3, 0), parseInt(txtParam4, 0));
            case "Pad (W, H)" -> new VideoFilters.Pad(parseInt(txtParam1, 1920), parseInt(txtParam2, 1080), "black");
            case "Rotação 90° Horário" -> new VideoFilters.Rotate(RotationAngle.CW_90);
            case "Rotação 90° Anti-horário" -> new VideoFilters.Rotate(RotationAngle.CCW_90);
            case "Rotação 180°" -> new VideoFilters.Rotate(RotationAngle.ROTATE_180);
            case "Espelhar Horizontal" -> new VideoFilters.Flip(true, false);
            case "Espelhar Vertical" -> new VideoFilters.Flip(false, true);
            case "Proporção DAR (16/9)" -> new VideoFilters.AspectRatio(txtParam1.getText().isBlank() ? "16/9" : txtParam1.getText());
            case "Cor (Brilho, Contraste, Sat, Gamma)" -> new VideoFilters.ColorAdjust(
                    parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 1.0), parseDouble(txtParam3, 1.0), parseDouble(txtParam4, 1.0));
            case "Escala de Cinza" -> new VideoFilters.Grayscale();
            case "Nitidez (Sharpen)" -> new VideoFilters.Sharpen(parseDouble(txtParam1, 1.0));
            case "Desfoque (Blur)" -> new VideoFilters.Blur(parseDouble(txtParam1, 2.0));
            case "Redução de Ruído" -> new VideoFilters.Denoise(parseDouble(txtParam1, 4.0));
            case "Desentrelaçamento (YADIF)" -> new VideoFilters.Deinterlace();
            case "Alterar FPS" -> new VideoFilters.ChangeFps(parseDouble(txtParam1, 30.0));
            case "Velocidade Vídeo (Multiplier)" -> new VideoFilters.Speed(parseDouble(txtParam1, 1.5));
            case "Fade In Vídeo (Início, Duração)" -> new VideoFilters.VideoFade(true, parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 1.0));
            case "Fade Out Vídeo (Início, Duração)" -> new VideoFilters.VideoFade(false, parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 1.0));
            case "Reverter Vídeo (Clipes curtos!)" -> new VideoFilters.Reverse();
            case "Áudio: Volume (Multiplier)" -> new AudioFilters.Volume(parseDouble(txtParam1, 1.5));
            case "Áudio: Velocidade (Multiplier)" -> new AudioFilters.AudioSpeed(parseDouble(txtParam1, 1.5));
            case "Áudio: Fade In (Início, Duração)" -> new AudioFilters.AudioFade(true, parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 1.0));
            case "Áudio: Fade Out (Início, Duração)" -> new AudioFilters.AudioFade(false, parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 1.0));
            case "Áudio: Equalizador (Graves, Médios, Agudos)" -> new AudioFilters.Equalizer(parseDouble(txtParam1, 0.0), parseDouble(txtParam2, 0.0), parseDouble(txtParam3, 0.0));
            case "Áudio: Normalização EBU R128" -> AudioFilters.Loudnorm.standardEbuR128();
            case "Áudio: Mono (1.0)" -> new AudioFilters.Channels(1);
            case "Áudio: Estéreo (2.0)" -> new AudioFilters.Channels(2);
            default -> null;
        };

        if (f != null) {
            viewModel.addFilter(f);
        }
    }

    private int parseInt(TextField tf, int def) {
        try { return Integer.parseInt(tf.getText().trim()); } catch (Exception e) { return def; }
    }

    private double parseDouble(TextField tf, double def) {
        try { return Double.parseDouble(tf.getText().trim().replace(",", ".")); } catch (Exception e) { return def; }
    }

    @FXML
    public void handleSelectMedia() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar Vídeo para Filtros");
        File f = chooser.showOpenDialog(lvFilterChain.getScene().getWindow());
        if (f != null) viewModel.loadMedia(f.toPath());
    }

    @FXML
    public void handleBrowseOutput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Salvar Arquivo Filtrado");
        File f = chooser.showSaveDialog(lvFilterChain.getScene().getWindow());
        if (f != null) viewModel.outputPathProperty().set(f.getAbsolutePath());
    }

    @FXML public void handleClearFilters() { viewModel.clearFilters(); }
    @FXML public void handleExecute() { viewModel.execute(); }
    @FXML public void handleAddToQueue() { viewModel.addToQueue(); }
    @FXML public void handleCancel() { viewModel.cancel(); }

    private class FilterCell extends ListCell<MediaFilter> {
        private final HBox root = new HBox(10);
        private final Label lblName = new Label();
        private final Button btnUp = new Button("▲");
        private final Button btnDown = new Button("▼");
        private final Button btnDel = new Button("✕");

        FilterCell() {
            root.setAlignment(Pos.CENTER_LEFT);
            lblName.setStyle("-fx-text-fill: #f8fafc;");
            lblName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lblName, Priority.ALWAYS);

            btnUp.getStyleClass().addAll("button-outline", "button-sm");
            btnDown.getStyleClass().addAll("button-outline", "button-sm");
            btnDel.getStyleClass().addAll("button-danger", "button-sm");
            root.getChildren().addAll(lblName, btnUp, btnDown, btnDel);
        }

        @Override
        protected void updateItem(MediaFilter item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            int idx = getIndex();
            lblName.setText((idx + 1) + ". [" + item.category().label() + "] " + item.displayName() + " (" + item.toFilterExpression() + ")");
            btnUp.setDisable(idx == 0);
            btnDown.setDisable(idx >= viewModel.getFilterChain().size() - 1);
            btnUp.setOnAction(e -> viewModel.moveUp(idx));
            btnDown.setOnAction(e -> viewModel.moveDown(idx));
            btnDel.setOnAction(e -> viewModel.removeAt(idx));
            setGraphic(root);
        }
    }
}
