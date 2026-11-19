package com.ffmpegstudio.domain.port;

import java.time.Duration;
import java.util.List;

/**
 * Port for safely executing external processes using structured arguments.
 */
public interface ProcessExecutorPort {
    ProcessExecutionResult execute(List<String> command, Duration timeout);
}
