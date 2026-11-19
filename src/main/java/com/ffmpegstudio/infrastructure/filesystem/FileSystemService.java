package com.ffmpegstudio.infrastructure.filesystem;

import com.ffmpegstudio.infrastructure.platform.ExecutableResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class FileSystemService {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemService.class);

    public boolean isExecutable(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return false;
        }
        Path path = Paths.get(pathStr);
        if (Files.isRegularFile(path) && Files.isExecutable(path)) {
            return true;
        }
        return findInPath(pathStr).isPresent();
    }

    public Optional<Path> findInPath(String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return Optional.empty();
        }
        Path directPath = Paths.get(binaryName);
        if (Files.isRegularFile(directPath) && Files.isExecutable(directPath)) {
            return Optional.of(directPath.toAbsolutePath().normalize());
        }
        return ExecutableResolver.current().resolve(binaryName, null)
                .map(ExecutableResolver.ResolvedExecutable::path);
    }

    public void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                logger.debug("Created directory: {}", directory);
            }
        } catch (Exception e) {
            logger.error("Failed to create directory: {}", directory, e);
        }
    }
}
