package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.ManageSettingsUseCase;
import com.ffmpegstudio.application.port.in.TranscodeMediaUseCase;
import com.ffmpegstudio.domain.model.ConversionValidator;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.ProcessingJob;
import com.ffmpegstudio.domain.model.ProcessingProgress;
import com.ffmpegstudio.domain.port.CapabilityDetectorPort;
import com.ffmpegstudio.domain.port.ProcessExecutionResult;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegCommandBuilder;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProcessExecutor;
import com.ffmpegstudio.infrastructure.ffmpeg.FFmpegProgressParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class TranscodeMediaService implements TranscodeMediaUseCase {
    private static final Logger logger = LoggerFactory.getLogger(TranscodeMediaService.class);

    private final FFmpegProcessExecutor processExecutor;
    private final ManageSettingsUseCase settingsUseCase;
    private final CapabilityDetectorPort capabilityDetector;

    public TranscodeMediaService(
            FFmpegProcessExecutor processExecutor,
            ManageSettingsUseCase settingsUseCase,
            CapabilityDetectorPort capabilityDetector
    ) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor must not be null");
        this.settingsUseCase = Objects.requireNonNull(settingsUseCase, "settingsUseCase must not be null");
        this.capabilityDetector = Objects.requireNonNull(capabilityDetector, "capabilityDetector must not be null");
    }

    @Override
    public TranscodeExecutionHandle executeJob(ProcessingJob job, Consumer<ProcessingJob> onProgressUpdate) {
        Objects.requireNonNull(job, "job must not be null");

        String ffmpegPath = settingsUseCase.getSettings().ffmpegPath();
        FFmpegCapabilities capabilities = capabilityDetector.detectCapabilities(ffmpegPath);

        MediaFile mediaFile = job.getSourceMedia() != null 
                ? job.getSourceMedia() 
                : new MediaFile(job.getInputPath(), job.getInputPath().getFileName().toString(), 0, null);

        // Validation
        List<String> validationErrors = job.getOperation().validate(mediaFile, capabilities);
        if (!validationErrors.isEmpty()) {
            String errorMsg = String.join("; ", validationErrors);
            job.fail(errorMsg);
            if (onProgressUpdate != null) {
                onProgressUpdate.accept(job);
            }
            CompletableFuture<ProcessingJob> failedFuture = CompletableFuture.completedFuture(job);
            return new SimpleTranscodeHandle(failedFuture, () -> {});
        }

        Path input = job.getInputPath();
        Path targetOutput = job.getTargetOutputPath();
        Path tempOutput = job.getTemporaryOutputPath();

        if (input.toAbsolutePath().normalize().equals(targetOutput.toAbsolutePath().normalize())) {
            job.fail("Output path must not match input path. Original file would be destroyed.");
            if (onProgressUpdate != null) onProgressUpdate.accept(job);
            return new SimpleTranscodeHandle(CompletableFuture.completedFuture(job), () -> {});
        }

        if (!Files.exists(input)) {
            job.fail("Input file does not exist: " + input);
            if (onProgressUpdate != null) onProgressUpdate.accept(job);
            return new SimpleTranscodeHandle(CompletableFuture.completedFuture(job), () -> {});
        }

        // Prepare temporary output file in target directory to avoid partial files on error
        try {
            Path targetParent = targetOutput.getParent();
            if (targetParent != null && !Files.exists(targetParent)) {
                Files.createDirectories(targetParent);
            }
        } catch (IOException e) {
            job.fail("Failed to create destination directory: " + e.getMessage());
            if (onProgressUpdate != null) onProgressUpdate.accept(job);
            return new SimpleTranscodeHandle(CompletableFuture.completedFuture(job), () -> {});
        }

        // Start job state
        job.start();
        if (onProgressUpdate != null) {
            onProgressUpdate.accept(job);
        }

        // Total duration for progress computation (if known)
        long totalDurationMillis = job.getProgress().totalDurationMillis();
        if (totalDurationMillis <= 0 && job.getOperation() instanceof com.ffmpegstudio.domain.model.ConcatOperation concatOp) {
            totalDurationMillis = concatOp.totalDuration().toMillis();
        }
        FFmpegProgressParser progressParser = new FFmpegProgressParser(totalDurationMillis);

        // Prepare temporary concat list file if needed
        com.ffmpegstudio.domain.model.MediaOperation effectiveOp = job.getOperation();
        Path tempConcatListFile = null;
        if (effectiveOp instanceof com.ffmpegstudio.domain.model.ConcatOperation concatOp
                && concatOp.mode() == com.ffmpegstudio.domain.model.ConcatOperation.ConcatMode.FAST_DEMUXER) {
            try {
                Path parentDir = targetOutput.getParent() != null ? targetOutput.getParent() : Path.of(".");
                tempConcatListFile = parentDir.resolve(".~tmp_concat_" + job.getId().substring(0, 8) + ".txt");
                Files.writeString(tempConcatListFile, concatOp.generateConcatListContent(), java.nio.charset.StandardCharsets.UTF_8);
                effectiveOp = concatOp.withConcatListFile(tempConcatListFile);
            } catch (IOException e) {
                job.fail("Failed to create temporary concat list file: " + e.getMessage());
                if (onProgressUpdate != null) onProgressUpdate.accept(job);
                return new SimpleTranscodeHandle(CompletableFuture.completedFuture(job), () -> {});
            }
        }
        final Path concatListToClean = tempConcatListFile;

        // Two-pass loudnorm Pass 1 analysis if requested
        if (effectiveOp instanceof com.ffmpegstudio.domain.model.AudioToolsOperation audioOp
                && audioOp.enableLoudnorm()
                && audioOp.loudnormMode() == com.ffmpegstudio.domain.model.AudioToolsOperation.LoudnormMode.TWO_PASS_LINEAR
                && audioOp.measuredMetrics() == null) {
            job.addLog("[Loudnorm Pass 1] Analisando LUFS, True Peak e LRA...");
            List<String> pass1Cmd = audioOp.buildFirstPassAnalysisCommand(ffmpegPath, mediaFile);
            ProcessExecutionResult pass1Res = processExecutor.execute(pass1Cmd, java.time.Duration.ofMinutes(10));
            var parsedMetrics = com.ffmpegstudio.infrastructure.ffmpeg.LoudnormMetricsParser.parseFromStderr(pass1Res.standardErrorLines());
            if (pass1Res.isSuccess() && parsedMetrics.isPresent()) {
                var m = parsedMetrics.get();
                job.addLog(String.format(java.util.Locale.US,
                        "[Loudnorm Pass 1] Medido: Integrated=%.2f LUFS, TruePeak=%.2f dBTP, LRA=%.2f LU. Aplicando Pass 2 linear.",
                        m.inputIntegratedLufs(), m.inputTruePeakDbtp(), m.inputLraLu()));
                effectiveOp = audioOp.withMeasuredMetrics(m);
            } else {
                job.addLog("[Loudnorm Pass 1] Métricas inválidas ou áudio silencioso; aplicando fallback para normalização dinâmica (Pass 1 único).");
            }
        }

        if (effectiveOp instanceof com.ffmpegstudio.domain.model.PeriodicFramesOperation periodicOp && periodicOp.outputDirectory() != null) {
            try {
                Files.createDirectories(periodicOp.outputDirectory());
            } catch (IOException e) {
                job.fail("Failed to create frames output directory: " + e.getMessage());
                if (onProgressUpdate != null) onProgressUpdate.accept(job);
                return new SimpleTranscodeHandle(CompletableFuture.completedFuture(job), () -> {});
            }
        }

        // Build structured arguments using the MediaOperation
        List<String> command = effectiveOp.buildCommand(ffmpegPath, mediaFile, tempOutput, true);

        CompletableFuture<ProcessingJob> jobFuture = new CompletableFuture<>();

        FFmpegProcessExecutor.LiveProcessHandle processHandle = processExecutor.executeStreaming(
                command,
                // Stdout listener -> FFmpeg progress lines
                line -> {
                    progressParser.processLine(line).ifPresent(progress -> {
                        job.updateProgress(progress);
                        if (onProgressUpdate != null) {
                            onProgressUpdate.accept(job);
                        }
                    });
                },
                // Stderr listener -> logs & error diagnostic
                line -> {
                    job.addLog(line);
                },
                tempOutput
        );

        processHandle.getCompletionFuture().whenComplete((result, throwable) -> {
            cleanupTempFile(concatListToClean);
            if (throwable != null) {
                logger.error("Job {} execution threw exception: {}", job.getId(), throwable.getMessage());
                cleanupTempFile(tempOutput);
                job.fail(throwable.getMessage());
            } else if (processHandle.isCancelled()) {
                logger.info("Job {} was cancelled by user.", job.getId());
                cleanupTempFile(tempOutput);
                job.cancel();
            } else if (result.isSuccess()) {
                try {
                    if (job.getOperation() instanceof com.ffmpegstudio.domain.model.PeriodicFramesOperation) {
                        cleanupTempFile(tempOutput);
                        job.complete();
                        logger.info("Job {} periodic frames completed -> {}", job.getId(), targetOutput);
                    } else if (Files.exists(tempOutput)) {
                        Files.move(tempOutput, targetOutput, StandardCopyOption.REPLACE_EXISTING);
                        job.complete();
                        logger.info("Job {} completed successfully -> {}", job.getId(), targetOutput);
                    } else {
                        job.fail("FFmpeg exited with code 0 but temporary output file was not found.");
                    }
                } catch (IOException e) {
                    logger.error("Failed to move temporary output to final target", e);
                    cleanupTempFile(tempOutput);
                    job.fail("Failed to finalize output file: " + e.getMessage());
                }
            } else {
                cleanupTempFile(tempOutput);
                String errorDetails = result.standardErrorLines().isEmpty()
                        ? "Process exited with code " + result.exitCode()
                        : result.standardErrorLines().get(result.standardErrorLines().size() - 1);
                job.fail(errorDetails);
            }

            if (onProgressUpdate != null) {
                onProgressUpdate.accept(job);
            }
            jobFuture.complete(job);
        });

        return new TranscodeExecutionHandle() {
            @Override
            public CompletableFuture<ProcessingJob> completionFuture() {
                return jobFuture;
            }

            @Override
            public void cancel() {
                processHandle.cancel();
            }

            @Override
            public boolean isRunning() {
                return processHandle.isAlive();
            }
        };
    }

    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
        }
    }

    private record SimpleTranscodeHandle(
            CompletableFuture<ProcessingJob> completionFuture,
            Runnable cancelAction
    ) implements TranscodeExecutionHandle {
        @Override
        public void cancel() {
            cancelAction.run();
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }
}
