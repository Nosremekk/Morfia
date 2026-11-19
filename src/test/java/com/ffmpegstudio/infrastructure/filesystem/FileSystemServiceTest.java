package com.ffmpegstudio.infrastructure.filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemServiceTest {

    private FileSystemService fileSystemService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileSystemService = new FileSystemService();
    }

    @Test
    @DisplayName("Should detect standard system binary in PATH")
    void shouldFindStandardBinaryInPath() {
        // 'sh' is guaranteed to exist on Linux systems
        Optional<Path> shPath = fileSystemService.findInPath("sh");

        assertThat(shPath).isPresent();
        assertThat(Files.isExecutable(shPath.get())).isTrue();
    }

    @Test
    @DisplayName("Should return empty optional for non-existent binary")
    void shouldReturnEmptyForNonExistentBinary() {
        Optional<Path> missing = fileSystemService.findInPath("non_existent_binary_123456");

        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("Should create directory structure if not exists")
    void shouldEnsureDirectoryExists() {
        Path deepDir = tempDir.resolve("level1").resolve("level2").resolve("test");

        assertThat(Files.exists(deepDir)).isFalse();
        fileSystemService.ensureDirectoryExists(deepDir);
        assertThat(Files.exists(deepDir)).isTrue();
    }
}
