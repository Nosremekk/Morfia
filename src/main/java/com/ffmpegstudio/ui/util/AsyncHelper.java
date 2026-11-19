package com.ffmpegstudio.ui.util;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AsyncHelper {
    private static final Logger logger = LoggerFactory.getLogger(AsyncHelper.class);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "ffmpeg-studio-worker");
                t.setDaemon(true);
                return t;
            }
    );

    public static <T> CompletableFuture<T> supplyAsync(
            Supplier<T> supplier,
            Consumer<T> onSuccessOnFxThread,
            Consumer<Throwable> onErrorOnFxThread
    ) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error executing background task: {}", throwable.getMessage(), throwable);
                        if (onErrorOnFxThread != null) {
                            Platform.runLater(() -> onErrorOnFxThread.accept(throwable));
                        }
                    } else {
                        if (onSuccessOnFxThread != null) {
                            Platform.runLater(() -> onSuccessOnFxThread.accept(result));
                        }
                    }
                });
    }

    public static CompletableFuture<Void> runAsync(
            Runnable runnable,
            Runnable onSuccessOnFxThread,
            Consumer<Throwable> onErrorOnFxThread
    ) {
        return CompletableFuture.runAsync(runnable, EXECUTOR)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error("Error executing background task: {}", throwable.getMessage(), throwable);
                        if (onErrorOnFxThread != null) {
                            Platform.runLater(() -> onErrorOnFxThread.accept(throwable));
                        }
                    } else {
                        if (onSuccessOnFxThread != null) {
                            Platform.runLater(onSuccessOnFxThread);
                        }
                    }
                });
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
