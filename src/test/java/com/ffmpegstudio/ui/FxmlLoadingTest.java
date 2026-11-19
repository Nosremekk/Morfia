package com.ffmpegstudio.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaFX FXML Loading Verification Tests")
class FxmlLoadingTest {

    private static boolean fxAvailable = false;

    @BeforeAll
    static void initJavaFx() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
            } catch (IllegalStateException alreadyStarted) {
                latch.countDown();
            }
            fxAvailable = latch.await(5, TimeUnit.SECONDS);
        } catch (Throwable t) {
            fxAvailable = false;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/com/ffmpegstudio/views/dashboard.fxml",
            "/com/ffmpegstudio/views/workspace.fxml",
            "/com/ffmpegstudio/views/converter.fxml",
            "/com/ffmpegstudio/views/compression.fxml",
            "/com/ffmpegstudio/views/tools.fxml",
            "/com/ffmpegstudio/views/concat.fxml",
            "/com/ffmpegstudio/views/subtitles.fxml",
            "/com/ffmpegstudio/views/filters.fxml",
            "/com/ffmpegstudio/views/streams-metadata.fxml",
            "/com/ffmpegstudio/views/queue.fxml",
            "/com/ffmpegstudio/views/history.fxml",
            "/com/ffmpegstudio/views/settings.fxml",
            "/com/ffmpegstudio/views/setup-wizard.fxml",
            "/com/ffmpegstudio/views/main-layout.fxml"
    })
    @DisplayName("Should load FXML view without errors")
    void shouldLoadFxmlView(String fxmlPath) throws Exception {
        Assumptions.assumeTrue(fxAvailable, "JavaFX graphical toolkit not available; skipping FXML load test");

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> loaded = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                URL url = getClass().getResource(fxmlPath);
                assertNotNull(url, "Missing FXML resource: " + fxmlPath);
                FXMLLoader loader = new FXMLLoader(url);
                loaded.set(loader.load());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out loading " + fxmlPath);
        if (error.get() != null) {
            fail("Failed to load " + fxmlPath + ": " + error.get().getMessage(), error.get());
        }
        assertNotNull(loaded.get());
    }
}
