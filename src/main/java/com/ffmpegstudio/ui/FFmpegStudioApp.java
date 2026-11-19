package com.ffmpegstudio.ui;

import com.ffmpegstudio.domain.model.AppSettings;
import com.ffmpegstudio.ui.component.UiComponents;
import com.ffmpegstudio.ui.controller.MainLayoutController;
import com.ffmpegstudio.ui.util.AsyncHelper;
import com.ffmpegstudio.ui.util.I18n;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FFmpegStudioApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegStudioApp.class);

    public static final String APP_NAME = com.ffmpegstudio.domain.model.AppIdentity.DISPLAY_NAME;
    public static final String APP_VERSION = com.ffmpegstudio.domain.model.AppIdentity.VERSION;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting {} v{}", APP_NAME, APP_VERSION);
        try {
            AppContext context = AppContext.getInstance();
            AppSettings settings = context.getSettingsService().getSettings();
            I18n.setLocale(settings.language());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ffmpegstudio/views/main-layout.fxml"));
            Parent root = loader.load();
            MainLayoutController mainController = loader.getController();

            Scene scene = new Scene(root, 1200, 740);
            applyTheme(scene, settings.theme());
            mainController.registerShortcuts(scene);

            primaryStage.setTitle(com.ffmpegstudio.domain.model.AppIdentity.windowTitle());
            try (var iconStream = getClass().getResourceAsStream("/com/ffmpegstudio/icons/morfia-128.png")) {
                if (iconStream != null) {
                    primaryStage.getIcons().add(new javafx.scene.image.Image(iconStream));
                }
            } catch (Exception ignored) {
            }
            primaryStage.setMinWidth(960);
            primaryStage.setMinHeight(620);
            primaryStage.setScene(scene);

            primaryStage.setOnCloseRequest(event -> {
                int activeJobs = context.getQueueService().getActiveJobsCount();
                if (activeJobs > 0) {
                    event.consume();
                    boolean confirmExit = UiComponents.confirmAction(
                            I18n.get("shutdown.title"),
                            I18n.format("shutdown.header", activeJobs),
                            I18n.get("shutdown.content")
                    );
                    if (confirmExit) {
                        context.shutdown();
                        AsyncHelper.shutdown();
                        Platform.exit();
                    }
                } else {
                    context.shutdown();
                }
            });

            primaryStage.show();
            logger.info("Application window initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to start application", e);
            throw new RuntimeException("Application startup failed", e);
        }
    }

    public static void applyTheme(Scene scene, String theme) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        String cssPath = "LIGHT".equalsIgnoreCase(theme)
                ? "/com/ffmpegstudio/css/light-theme.css"
                : "/com/ffmpegstudio/css/dark-theme.css";
        var url = FFmpegStudioApp.class.getResource(cssPath);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    @Override
    public void stop() {
        logger.info("Shutting down {}...", APP_NAME);
        AppContext.getInstance().shutdown();
        AsyncHelper.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
