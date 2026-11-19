package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.DetectCapabilitiesUseCase;
import com.ffmpegstudio.domain.model.ExecutableInfo;
import com.ffmpegstudio.domain.model.FFmpegCapabilities;
import com.ffmpegstudio.domain.port.CapabilityDetectorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class DetectCapabilitiesService implements DetectCapabilitiesUseCase {
    private static final Logger logger = LoggerFactory.getLogger(DetectCapabilitiesService.class);

    private final CapabilityDetectorPort capabilityDetector;
    private final java.util.Map<String, FFmpegCapabilities> capabilitiesCache = new java.util.concurrent.ConcurrentHashMap<>();

    public DetectCapabilitiesService(CapabilityDetectorPort capabilityDetector) {
        this.capabilityDetector = Objects.requireNonNull(capabilityDetector, "capabilityDetector must not be null");
    }

    @Override
    public ExecutableInfo checkFFmpeg(String path) {
        logger.debug("Checking FFmpeg executable at: {}", path);
        return capabilityDetector.checkExecutable(path, "FFmpeg", "-version");
    }

    @Override
    public ExecutableInfo checkFFprobe(String path) {
        logger.debug("Checking FFprobe executable at: {}", path);
        return capabilityDetector.checkExecutable(path, "FFprobe", "-version");
    }

    @Override
    public FFmpegCapabilities getCapabilities(String ffmpegPath) {
        String key = ffmpegPath == null ? "ffmpeg" : ffmpegPath.trim();
        return capabilitiesCache.computeIfAbsent(key, k -> {
            logger.info("Detecting FFmpeg capabilities for: {}", k);
            return capabilityDetector.detectCapabilities(k);
        });
    }

    public void invalidateCache() {
        capabilitiesCache.clear();
    }
}
