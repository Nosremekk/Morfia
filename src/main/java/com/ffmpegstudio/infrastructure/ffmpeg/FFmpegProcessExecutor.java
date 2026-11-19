package com.ffmpegstudio.infrastructure.ffmpeg;

import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.domain.port.ProcessExecutorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Executes system processes safely with argument isolation (no shell interpretation),
 * asynchronous stream consumption, live progress callbacks, and controlled cancellation.
 */
public class FFmpegProcessExecutor implements ProcessExecutorPort {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegProcessExecutor.class);

    @Override
    public ProcessExecutionResult execute(List<String> command, Duration timeout) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        logger.debug("Executing command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        List<String> stdoutLines = Collections.synchronizedList(new ArrayList<>());
        List<String> stderrLines = Collections.synchronizedList(new ArrayList<>());
        boolean timedOut = false;
        int exitCode = -1;

        Process process = null;
        try {
            process = processBuilder.start();

            CompletableFuture<Void> stdoutConsumer = readStreamAsync(process.getInputStream(), stdoutLines, null);
            CompletableFuture<Void> stderrConsumer = readStreamAsync(process.getErrorStream(), stderrLines, null);

            long timeoutMillis = (timeout != null && !timeout.isZero() && !timeout.isNegative())
                    ? timeout.toMillis()
                    : 15_000;

            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                logger.warn("Process timed out after {} ms: {}", timeoutMillis, command);
                timedOut = true;
                killProcessTree(process);
            } else {
                exitCode = process.exitValue();
            }

            CompletableFuture.allOf(stdoutConsumer, stderrConsumer).get(2, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Process execution interrupted: {}", command);
            if (process != null) {
                killProcessTree(process);
            }
        } catch (Exception e) {
            logger.error("Failed to execute process: {}", command, e);
            if (process != null) {
                killProcessTree(process);
            }
        }

        return new ProcessExecutionResult(exitCode, stdoutLines, stderrLines, timedOut);
    }

    /**
     * Executes an FFmpeg command with real-time stdout/stderr streaming, cancellation support,
     * and automatic cleanup of the temporary output file on failure or cancellation.
     */
    public LiveProcessHandle executeStreaming(
            List<String> command,
            Consumer<String> stdoutLineConsumer,
            Consumer<String> stderrLineConsumer,
            Path tempOutputFile
    ) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        logger.info("Starting streaming execution: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);

        CompletableFuture<ProcessExecutionResult> completionFuture = new CompletableFuture<>();
        List<String> stdoutLines = Collections.synchronizedList(new ArrayList<>());
        List<String> stderrLines = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Process process;
        try {
            process = processBuilder.start();
        } catch (Exception e) {
            logger.error("Failed to start streaming process: {}", command, e);
            cleanupTempFile(tempOutputFile);
            completionFuture.completeExceptionally(e);
            return new LiveProcessHandle(null, completionFuture, cancelled, tempOutputFile);
        }

        CompletableFuture<Void> stdoutReader = readStreamAsync(process.getInputStream(), stdoutLines, stdoutLineConsumer);
        CompletableFuture<Void> stderrReader = readStreamAsync(process.getErrorStream(), stderrLines, stderrLineConsumer);

        CompletableFuture.runAsync(() -> {
            try {
                int exitCode = process.waitFor();
                CompletableFuture.allOf(stdoutReader, stderrReader).get(2, TimeUnit.SECONDS);

                boolean wasCancelled = cancelled.get();
                if (wasCancelled || exitCode != 0) {
                    logger.warn("Process ended with exitCode {} (cancelled={})", exitCode, wasCancelled);
                    cleanupTempFile(tempOutputFile);
                }

                ProcessExecutionResult result = new ProcessExecutionResult(exitCode, stdoutLines, stderrLines, false);
                completionFuture.complete(result);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                killProcessTree(process);
                cleanupTempFile(tempOutputFile);
                completionFuture.complete(new ProcessExecutionResult(-1, stdoutLines, stderrLines, true));
            } catch (Exception e) {
                logger.error("Error waiting for process termination", e);
                killProcessTree(process);
                cleanupTempFile(tempOutputFile);
                completionFuture.completeExceptionally(e);
            }
        });

        return new LiveProcessHandle(process, completionFuture, cancelled, tempOutputFile);
    }

    private CompletableFuture<Void> readStreamAsync(
            InputStream inputStream,
            List<String> targetList,
            Consumer<String> lineConsumer
    ) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    targetList.add(line);
                    if (lineConsumer != null) {
                        try {
                            lineConsumer.accept(line);
                        } catch (Exception ex) {
                            logger.trace("Error in lineConsumer: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Error while reading process stream: {}", e.getMessage());
            }
        });
    }

    private void killProcessTree(Process process) {
        if (process == null) return;
        try {
            process.descendants().forEach(ph -> {
                try {
                    ph.destroyForcibly();
                } catch (Exception ignored) {}
            });
            process.destroy();
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {}
        }
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                if (Files.exists(tempFile)) {
                    Files.deleteIfExists(tempFile);
                    logger.info("Cleaned up temporary file: {}", tempFile);
                }
            } catch (Exception e) {
                logger.warn("Failed to delete temporary file {}: {}", tempFile, e.getMessage());
            }
        }
    }

    /**
     * Handle to a running process, allowing controlled cancellation and status inspection.
     */
    public class LiveProcessHandle {
        private final Process process;
        private final CompletableFuture<ProcessExecutionResult> completionFuture;
        private final AtomicBoolean cancelled;
        private final Path tempFile;

        public LiveProcessHandle(
                Process process,
                CompletableFuture<ProcessExecutionResult> completionFuture,
                AtomicBoolean cancelled,
                Path tempFile
        ) {
            this.process = process;
            this.completionFuture = completionFuture;
            this.cancelled = cancelled;
            this.tempFile = tempFile;
        }

        public CompletableFuture<ProcessExecutionResult> getCompletionFuture() {
            return completionFuture;
        }

        public boolean isAlive() {
            return process != null && process.isAlive();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                logger.info("Cancelling running process...");
                if (process != null) {
                    killProcessTree(process);
                }
                cleanupTempFile(tempFile);
            }
        }
    }
}
