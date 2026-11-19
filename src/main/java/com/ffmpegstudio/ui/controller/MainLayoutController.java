package com.ffmpegstudio.ui.controller;

import com.ffmpegstudio.ui.AppContext;
import com.ffmpegstudio.ui.util.IconHelper;
import com.ffmpegstudio.ui.viewmodel.MainViewModel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class MainLayoutController {
    private static final Logger logger = LoggerFactory.getLogger(MainLayoutController.class);

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavWorkspace;
    @FXML private Button btnNavConverter;
    @FXML private Button btnNavCompression;
    @FXML private Button btnNavTools;
    @FXML private Button btnNavConcat;
    @FXML private Button btnNavSubtitles;
    @FXML private Button btnNavFilters;
    @FXML private Button btnNavStreams;
    @FXML private Button btnNavQueue;
    @FXML private Button btnNavHistory;
    @FXML private Button btnNavSettings;
    @FXML private Button btnNavSetupWizard;
    @FXML private StackPane contentArea;
    @FXML private Circle statusDot;
    @FXML private Label lblStatusText;
    @FXML private Label lblVersionBadge;
    @FXML private HBox brandBox;

    private MainViewModel viewModel;
    private Parent dashboardView;
    private Parent workspaceView;
    private Parent converterView;
    private Parent compressionView;
    private Parent toolsView;
    private Parent concatView;
    private Parent subtitlesView;
    private Parent filtersView;
    private Parent streamsView;
    private Parent queueView;
    private Parent historyView;
    private Parent settingsView;
    private Parent setupWizardView;
    private DashboardController dashboardController;
    private WorkspaceController workspaceController;
    private ConverterController converterController;
    private CompressionController compressionController;
    private ToolsController toolsController;
    private ConcatController concatController;
    private SubtitlesController subtitlesController;
    private FiltersController filtersController;
    private StreamsMetadataController streamsController;
    private QueueController queueController;
    private HistoryController historyController;
    private SettingsController settingsController;
    private SetupWizardController setupWizardController;

    @FXML
    public void initialize() {
        AppContext context = AppContext.getInstance();
        viewModel = new MainViewModel(context.getCapabilitiesService(), context.getSettingsService());

        // Bindings
        lblStatusText.textProperty().bind(viewModel.statusMessageProperty());
        lblVersionBadge.textProperty().bind(viewModel.ffmpegVersionProperty());
        lblVersionBadge.visibleProperty().bind(viewModel.ffmpegVersionProperty().isNotEmpty());
        lblVersionBadge.managedProperty().bind(viewModel.ffmpegVersionProperty().isNotEmpty());

        viewModel.systemReadyProperty().addListener((obs, oldVal, ready) -> {
            statusDot.getStyleClass().removeAll("dot-success", "dot-danger", "dot-warning");
            boolean isReady = Boolean.TRUE.equals(ready);
            if (isReady) {
                statusDot.getStyleClass().add("dot-success");
            } else {
                statusDot.getStyleClass().add("dot-danger");
            }
            updateFfmpegDependentNav(isReady);
        });

        // Set up icons for navigation buttons
        setupNavIcons();
        applyLocalizedLabels();

        // Load initial views
        loadViews();

        // Show dashboard by default
        showDashboard();

        // Initial check
        viewModel.refreshStatus();

        // Check interrupted jobs from previous session after UI is ready
        javafx.application.Platform.runLater(this::checkInterruptedJobs);
    }

    public void applyLocalizedLabels() {
        btnNavDashboard.setText(com.ffmpegstudio.ui.util.I18n.get("nav.dashboard"));
        btnNavWorkspace.setText(com.ffmpegstudio.ui.util.I18n.get("nav.workspace"));
        btnNavConverter.setText(com.ffmpegstudio.ui.util.I18n.get("nav.converter"));
        btnNavCompression.setText(com.ffmpegstudio.ui.util.I18n.get("nav.compression"));
        btnNavTools.setText(com.ffmpegstudio.ui.util.I18n.get("nav.tools"));
        btnNavConcat.setText(com.ffmpegstudio.ui.util.I18n.get("nav.concat"));
        btnNavSubtitles.setText(com.ffmpegstudio.ui.util.I18n.get("nav.subtitles"));
        btnNavFilters.setText(com.ffmpegstudio.ui.util.I18n.get("nav.filters"));
        btnNavStreams.setText(com.ffmpegstudio.ui.util.I18n.get("nav.streams"));
        btnNavQueue.setText(com.ffmpegstudio.ui.util.I18n.get("nav.queue"));
        btnNavHistory.setText(com.ffmpegstudio.ui.util.I18n.get("nav.history"));
        btnNavSettings.setText(com.ffmpegstudio.ui.util.I18n.get("nav.settings"));
    }

    public void registerShortcuts(javafx.scene.Scene scene) {
        if (scene == null) return;
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                () -> { showWorkspace(); if (workspaceController != null) workspaceController.handleImportFiles(); }
        );
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN, javafx.scene.input.KeyCombination.SHIFT_DOWN),
                () -> { showWorkspace(); if (workspaceController != null) workspaceController.handleImportFolder(); }
        );
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                () -> { showWorkspace(); if (workspaceController != null) workspaceController.focusSearch(); }
        );
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.COMMA, javafx.scene.input.KeyCombination.CONTROL_DOWN),
                this::showSettings
        );
        scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.ESCAPE),
                () -> contentArea.requestFocus()
        );
    }

    private void checkInterruptedJobs() {
        var qs = AppContext.getInstance().getQueueService();
        if (qs instanceof com.ffmpegstudio.application.service.JobQueueService jobQueue) {
            var interrupted = jobQueue.getInterruptedJobs();
            if (!interrupted.isEmpty()) {
                boolean requeue = com.ffmpegstudio.ui.component.UiComponents.confirmAction(
                        com.ffmpegstudio.ui.util.I18n.get("queue.interrupted_confirm.title"),
                        com.ffmpegstudio.ui.util.I18n.format("queue.interrupted_confirm.header", interrupted.size()),
                        com.ffmpegstudio.ui.util.I18n.get("queue.interrupted_confirm.content")
                );
                if (requeue) {
                    jobQueue.requeueInterruptedJobs();
                    showQueue();
                } else {
                    jobQueue.discardInterruptedJobs();
                }
            }
        }
    }

    private void setupNavIcons() {
        SVGPath dashIcon = IconHelper.createIcon(IconHelper.ICON_DASHBOARD, "nav-icon", 0.9);
        btnNavDashboard.setGraphic(dashIcon);
        btnNavWorkspace.setGraphic(IconHelper.createIcon(IconHelper.ICON_FOLDER, "nav-icon", 0.9));

        SVGPath convIcon = IconHelper.createIcon(IconHelper.ICON_CONVERT, "nav-icon", 0.9);
        btnNavConverter.setGraphic(convIcon);

        SVGPath compIcon = IconHelper.createIcon(IconHelper.ICON_COMPRESS, "nav-icon", 0.9);
        btnNavCompression.setGraphic(compIcon);

        SVGPath toolsIcon = IconHelper.createIcon(IconHelper.ICON_TOOLS, "nav-icon", 0.9);
        btnNavTools.setGraphic(toolsIcon);

        btnNavConcat.setGraphic(IconHelper.createIcon(IconHelper.ICON_CONCAT, "nav-icon", 0.9));
        btnNavSubtitles.setGraphic(IconHelper.createIcon(IconHelper.ICON_SUBTITLES, "nav-icon", 0.9));
        btnNavFilters.setGraphic(IconHelper.createIcon(IconHelper.ICON_FILTERS, "nav-icon", 0.9));
        btnNavStreams.setGraphic(IconHelper.createIcon(IconHelper.ICON_STREAMS, "nav-icon", 0.9));

        SVGPath queueIcon = IconHelper.createIcon(IconHelper.ICON_QUEUE, "nav-icon", 0.9);
        btnNavQueue.setGraphic(queueIcon);

        btnNavHistory.setGraphic(IconHelper.createIcon(IconHelper.ICON_REFRESH, "nav-icon", 0.9));

        SVGPath settingsIcon = IconHelper.createIcon(IconHelper.ICON_SETTINGS, "nav-icon", 0.9);
        btnNavSettings.setGraphic(settingsIcon);
        if (btnNavSetupWizard != null) {
            btnNavSetupWizard.setGraphic(IconHelper.createIcon(IconHelper.ICON_TOOLS, "nav-icon", 0.9));
        }
    }

    private void loadViews() {
        try {
            FXMLLoader dashLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/dashboard.fxml"));
            dashboardView = dashLoader.load();
            dashboardController = dashLoader.getController();
            dashboardController.setMainController(this);

            FXMLLoader wsLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/workspace.fxml"));
            workspaceView = wsLoader.load();
            workspaceController = wsLoader.getController();
            workspaceController.setMainController(this);

            FXMLLoader convLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/converter.fxml"));
            converterView = convLoader.load();
            converterController = convLoader.getController();
            converterController.setMainController(this);

            FXMLLoader compLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/compression.fxml"));
            compressionView = compLoader.load();
            compressionController = compLoader.getController();
            compressionController.setMainController(this);

            FXMLLoader toolsLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/tools.fxml"));
            toolsView = toolsLoader.load();
            toolsController = toolsLoader.getController();
            toolsController.setMainController(this);

            FXMLLoader concatLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/concat.fxml"));
            concatView = concatLoader.load();
            concatController = concatLoader.getController();
            concatController.setMainController(this);

            FXMLLoader subLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/subtitles.fxml"));
            subtitlesView = subLoader.load();
            subtitlesController = subLoader.getController();
            subtitlesController.setMainController(this);

            FXMLLoader filtersLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/filters.fxml"));
            filtersView = filtersLoader.load();
            filtersController = filtersLoader.getController();
            filtersController.setMainController(this);

            FXMLLoader streamsLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/streams-metadata.fxml"));
            streamsView = streamsLoader.load();
            streamsController = streamsLoader.getController();
            streamsController.setMainController(this);

            FXMLLoader queueLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/queue.fxml"));
            queueView = queueLoader.load();
            queueController = queueLoader.getController();
            queueController.setMainController(this);

            FXMLLoader histLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/history.fxml"));
            historyView = histLoader.load();
            historyController = histLoader.getController();
            historyController.setMainController(this);

            FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/settings.fxml"));
            settingsView = settingsLoader.load();
            settingsController = settingsLoader.getController();
            settingsController.setMainController(this);

            FXMLLoader wizardLoader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/setup-wizard.fxml"));
            setupWizardView = wizardLoader.load();
            setupWizardController = wizardLoader.getController();
            setupWizardController.setMainController(this);
        } catch (IOException e) {
            logger.error("Failed to load initial FXML views", e);
        }
    }

    @FXML
    public void showDashboard() {
        setActiveNav(btnNavDashboard);
        contentArea.getChildren().setAll(dashboardView);
        if (dashboardController != null) {
            dashboardController.refresh();
        }
        viewModel.refreshStatus();
    }

    @FXML
    public void showConverter() {
        setActiveNav(btnNavConverter);
        contentArea.getChildren().setAll(converterView);
    }

    @FXML
    public void showCompression() {
        setActiveNav(btnNavCompression);
        contentArea.getChildren().setAll(compressionView);
    }

    @FXML
    public void showTools() {
        setActiveNav(btnNavTools);
        contentArea.getChildren().setAll(toolsView);
    }

    @FXML
    public void showConcat() {
        setActiveNav(btnNavConcat);
        contentArea.getChildren().setAll(concatView);
    }

    @FXML
    public void showSubtitles() {
        setActiveNav(btnNavSubtitles);
        contentArea.getChildren().setAll(subtitlesView);
    }

    @FXML
    public void showFilters() {
        setActiveNav(btnNavFilters);
        contentArea.getChildren().setAll(filtersView);
    }

    @FXML
    public void showStreams() {
        setActiveNav(btnNavStreams);
        contentArea.getChildren().setAll(streamsView);
    }

    @FXML
    public void showWorkspace() {
        setActiveNav(btnNavWorkspace);
        contentArea.getChildren().setAll(workspaceView);
    }

    @FXML
    public void showQueue() {
        setActiveNav(btnNavQueue);
        contentArea.getChildren().setAll(queueView);
    }

    @FXML
    public void showHistory() {
        setActiveNav(btnNavHistory);
        contentArea.getChildren().setAll(historyView);
        if (historyController != null) historyController.refresh();
    }

    @FXML
    public void showSetupWizard() {
        if (btnNavSetupWizard != null) {
            setActiveNav(btnNavSetupWizard);
        }
        contentArea.getChildren().setAll(setupWizardView);
    }

    private void updateFfmpegDependentNav(boolean ready) {
        boolean disable = !ready;
        btnNavConverter.setDisable(disable);
        btnNavCompression.setDisable(disable);
        btnNavTools.setDisable(disable);
        btnNavConcat.setDisable(disable);
        btnNavSubtitles.setDisable(disable);
        btnNavFilters.setDisable(disable);
        btnNavStreams.setDisable(disable);
    }

    public void openFileInConverter(Path path) {
        showConverter();
        if (converterController != null && path != null) {
            converterController.loadMedia(path);
        }
    }

    public void openFileInCompression(Path path) {
        showCompression();
        if (compressionController != null && path != null) {
            compressionController.loadMedia(path);
        }
    }

    public void openFileInTools(Path path) {
        showTools();
        if (toolsController != null && path != null) {
            toolsController.loadMedia(path);
        }
    }

    public void openFileInFilters(Path path) {
        showFilters();
        if (filtersController != null && path != null) {
            filtersController.loadMedia(path);
        }
    }

    public void openFileInSubtitles(Path path) {
        showSubtitles();
        if (subtitlesController != null && path != null) {
            subtitlesController.loadMedia(path);
        }
    }

    public void openFileInStreams(Path path) {
        showStreams();
        if (streamsController != null && path != null) {
            streamsController.loadMedia(path);
        }
    }

    @FXML
    public void showSettings() {
        setActiveNav(btnNavSettings);
        contentArea.getChildren().setAll(settingsView);
        if (settingsController != null) {
            settingsController.refresh();
        }
    }

    private void setActiveNav(Button activeButton) {
        btnNavDashboard.getStyleClass().remove("nav-button-active");
        btnNavWorkspace.getStyleClass().remove("nav-button-active");
        btnNavConverter.getStyleClass().remove("nav-button-active");
        btnNavCompression.getStyleClass().remove("nav-button-active");
        btnNavTools.getStyleClass().remove("nav-button-active");
        btnNavConcat.getStyleClass().remove("nav-button-active");
        btnNavSubtitles.getStyleClass().remove("nav-button-active");
        btnNavFilters.getStyleClass().remove("nav-button-active");
        btnNavStreams.getStyleClass().remove("nav-button-active");
        btnNavQueue.getStyleClass().remove("nav-button-active");
        btnNavHistory.getStyleClass().remove("nav-button-active");
        btnNavSettings.getStyleClass().remove("nav-button-active");
        activeButton.getStyleClass().add("nav-button-active");
    }

    public void onSettingsUpdated() {
        viewModel.refreshStatus();
        if (dashboardController != null) {
            dashboardController.refresh();
        }
    }
}
