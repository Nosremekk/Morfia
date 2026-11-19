package com.ffmpegstudio.infrastructure.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Cross-platform process termination helper ensuring child processes are cleaned up
 * on cancellation or application shutdown.
 */
public final class ProcessLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessLifecycleService.class);

    private ProcessLifecycleService() {
    }

    public static void terminateGracefullyThenForce(Process process, long graceSeconds) {
        if (process == null || !process.isAlive()) {
            return;
        }
        try {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            if (!process.waitFor(Math.max(1, graceSeconds), TimeUnit.SECONDS)) {
                logger.warn("Process {} did not terminate gracefully; forcing destruction.", process.pid());
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        } catch (Exception e) {
            logger.warn("Error terminating process {}", process.pid(), e);
        }
    }
}
