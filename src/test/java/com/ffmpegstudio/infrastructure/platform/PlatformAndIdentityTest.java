package com.ffmpegstudio.infrastructure.platform;

import com.ffmpegstudio.domain.model.AppIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6 Platform Abstraction & Centralized Identity Tests")
class PlatformAndIdentityTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should expose centralized Morfia identity, version consistency with pom.xml, and bundled resources")
    void shouldExposeCentralizedIdentity() throws Exception {
        assertEquals("Morfia", AppIdentity.DISPLAY_NAME);
        assertEquals("morfia", AppIdentity.TECHNICAL_NAME);
        assertEquals("1.0.0-rc.1", AppIdentity.VERSION);
        assertEquals("1.0.0", AppIdentity.NATIVE_VERSION);
        assertEquals("MIT", AppIdentity.LICENSE_SPDX);
        assertEquals("Emerson Vinicius Santos de Santana", AppIdentity.COPYRIGHT_HOLDER);
        assertEquals("https://github.com/Nosremekk/Morfia", AppIdentity.OFFICIAL_URL);
        assertEquals("ffmpeg-studio", AppIdentity.LEGACY_CONFIG_DIR_NAME);
        assertEquals("morfia", AppIdentity.CONFIG_DIR_NAME);

        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<artifactId>" + AppIdentity.ARTIFACT_ID + "</artifactId>"));
        assertTrue(pom.contains("<version>" + AppIdentity.VERSION + "</version>"));

        assertNotNull(getClass().getResource("/com/ffmpegstudio/css/dark-theme.css"));
        assertNotNull(getClass().getResource("/com/ffmpegstudio/css/light-theme.css"));
        assertNotNull(getClass().getResource("/com/ffmpegstudio/i18n/messages_pt_BR.properties"));
        assertNotNull(getClass().getResource("/com/ffmpegstudio/i18n/messages_en.properties"));
        assertNotNull(getClass().getResource("/com/ffmpegstudio/icons/morfia-128.png"));
        assertNotNull(getClass().getResource("/com/ffmpegstudio/icons/morfia-logo.svg"));

        // Native version progression (RC1 < RC2 < 1.0.0 < 1.0.1)
        assertEquals("0.99.1", AppIdentity.toWindowsMsiVersion("1.0.0-rc.1"));
        assertEquals("0.99.2", AppIdentity.toWindowsMsiVersion("1.0.0-rc.2"));
        assertEquals("1.0.0", AppIdentity.toWindowsMsiVersion("1.0.0"));
        assertEquals("1.0.1", AppIdentity.toWindowsMsiVersion("1.0.1"));
        assertEquals("0.1.rc.1", AppIdentity.toRpmRelease("1.0.0-rc.1"));
        assertEquals("0.2.rc.2", AppIdentity.toRpmRelease("1.0.0-rc.2"));
        assertEquals("1", AppIdentity.toRpmRelease("1.0.0"));
    }

    @Test
    @DisplayName("Should resolve OS-specific config, cache, and log directories")
    void shouldResolveOsDirectories() {
        Path home = tempDir.resolve("home");

        PlatformDirectories linuxDirs = new PlatformDirectories(
                new PlatformInfo(PlatformInfo.OsFamily.LINUX, "Linux", "6.0", "amd64"),
                home,
                Map.of("XDG_CONFIG_HOME", tempDir.resolve("xdg-cfg").toString(),
                        "XDG_CACHE_HOME", tempDir.resolve("xdg-cache").toString())
        );
        assertEquals(tempDir.resolve("xdg-cfg").resolve(AppIdentity.CONFIG_DIR_NAME), linuxDirs.configDir());
        assertEquals(tempDir.resolve("xdg-cache").resolve(AppIdentity.CONFIG_DIR_NAME), linuxDirs.cacheDir());

        PlatformDirectories winDirs = new PlatformDirectories(
                new PlatformInfo(PlatformInfo.OsFamily.WINDOWS, "Windows 11", "10.0", "amd64"),
                home,
                Map.of("APPDATA", tempDir.resolve("AppData/Roaming").toString(),
                        "LOCALAPPDATA", tempDir.resolve("AppData/Local").toString())
        );
        assertEquals(tempDir.resolve("AppData/Roaming").resolve(AppIdentity.CONFIG_DIR_NAME), winDirs.configDir());
        assertEquals(tempDir.resolve("AppData/Local").resolve(AppIdentity.CONFIG_DIR_NAME).resolve("cache"), winDirs.cacheDir());

        PlatformDirectories macDirs = new PlatformDirectories(
                new PlatformInfo(PlatformInfo.OsFamily.MACOS, "Mac OS X", "14.0", "aarch64"),
                home,
                Map.of()
        );
        assertEquals(home.resolve("Library/Application Support").resolve(AppIdentity.CONFIG_DIR_NAME), macDirs.configDir());
        assertEquals(home.resolve("Library/Caches").resolve(AppIdentity.CONFIG_DIR_NAME), macDirs.cacheDir());
        assertEquals(home.resolve("Library/Logs").resolve(AppIdentity.CONFIG_DIR_NAME), macDirs.logsDir());
    }

    @Test
    @DisplayName("Should perform non-destructive migration preserving legacy config directory")
    void shouldMigrateLegacyDirectoryNonDestructively() throws Exception {
        Path legacy = tempDir.resolve(".config/ffmpeg-studio");
        Path target = tempDir.resolve(".config/new-brand");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("settings.json"), "{\"theme\":\"DARK\"}");

        PlatformDirectories.migrateLegacyDirectoryIfNeeded(legacy, target);

        assertTrue(Files.exists(legacy.resolve("settings.json")), "Original legacy file must remain untouched");
        assertTrue(Files.exists(target.resolve("settings.json")), "Target should receive copied config");
    }

    @Test
    @DisplayName("Should respect executable precedence and ignore untrusted temp directories")
    void shouldRespectPrecedenceAndRejectUntrustedDirs() throws Exception {
        Path workspaceTmp = Path.of("target", "test-tmp-exec-" + System.nanoTime()).toAbsolutePath().normalize();
        Files.createDirectories(workspaceTmp);
        try {
            Path safeBinDir = workspaceTmp.resolve("safe-bin");
            Files.createDirectories(safeBinDir);
            Path safeFfmpeg = safeBinDir.resolve("mock-ffmpeg");
            Files.writeString(safeFfmpeg, "#!/bin/sh\necho ok\n");
            safeFfmpeg.toFile().setExecutable(true);

            Path explicitBinDir = workspaceTmp.resolve("explicit-bin");
            Files.createDirectories(explicitBinDir);
            Path explicitFfmpeg = explicitBinDir.resolve("mock-ffmpeg");
            Files.writeString(explicitFfmpeg, "#!/bin/sh\necho ok\n");
            explicitFfmpeg.toFile().setExecutable(true);

            ExecutableResolver resolver = new ExecutableResolver(
                    new PlatformInfo(PlatformInfo.OsFamily.LINUX, "Linux", "6.0", "amd64"),
                    "/tmp" + File.pathSeparator + safeBinDir,
                    workspaceTmp,
                    null
            );

            // Explicit overrides PATH
            Optional<ExecutableResolver.ResolvedExecutable> resExplicit = resolver.resolve("mock-ffmpeg", explicitFfmpeg.toString());
            assertTrue(resExplicit.isPresent());
            assertEquals(ExecutableResolver.ResolutionSource.EXPLICIT_CONFIG, resExplicit.get().source());
            assertEquals(explicitFfmpeg.toAbsolutePath().normalize(), resExplicit.get().path());

            // system tmpdir is rejected; safeBinDir in PATH is selected
            Optional<ExecutableResolver.ResolvedExecutable> resPath = resolver.resolve("mock-ffmpeg", null);
            assertTrue(resPath.isPresent());
            assertEquals(ExecutableResolver.ResolutionSource.SYSTEM_PATH, resPath.get().source());
            assertEquals(safeFfmpeg.toAbsolutePath().normalize(), resPath.get().path());
            assertTrue(ExecutableResolver.isUntrustedAutoDiscoveryDir(Path.of(System.getProperty("java.io.tmpdir"))));
        } finally {
            try (var walk = Files.walk(workspaceTmp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
