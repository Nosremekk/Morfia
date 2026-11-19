package com.ffmpegstudio.domain.port;

import java.util.List;

public record ProcessExecutionResult(
        int exitCode,
        List<String> standardOutputLines,
        List<String> standardErrorLines,
        boolean timedOut
) {
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    public String standardOutputText() {
        return String.join(System.lineSeparator(), standardOutputLines);
    }

    public String standardErrorText() {
        return String.join(System.lineSeparator(), standardErrorLines);
    }
}
