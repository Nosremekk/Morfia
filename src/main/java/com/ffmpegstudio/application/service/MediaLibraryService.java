package com.ffmpegstudio.application.service;

import com.ffmpegstudio.application.port.in.AnalyzeMediaUseCase;
import com.ffmpegstudio.domain.model.MediaFile;
import com.ffmpegstudio.domain.model.WorkspaceMedia;
import com.ffmpegstudio.domain.model.WorkspaceMedia.MediaKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared session media library storing references to local files,
 * caching FFprobe inspection results, and detecting moved/modified files.
 * Removing entries from the library never deletes files on disk.
 */
public class MediaLibraryService {
    private static final Logger logger = LoggerFactory.getLogger(MediaLibraryService.class);

    public enum SortOrder {
        NAME_ASC, SIZE_DESC, DURATION_DESC, IMPORTED_DESC
    }

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".mp4", ".mkv", ".mov", ".avi", ".webm", ".flv", ".wmv", ".ts",
            ".mp3", ".flac", ".wav", ".aac", ".m4a", ".ogg", ".opus",
            ".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff"
    );

    private final AnalyzeMediaUseCase analyzeUseCase;
    private final Map<Path, WorkspaceMedia> library = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public MediaLibraryService(AnalyzeMediaUseCase analyzeUseCase) {
        this.analyzeUseCase = Objects.requireNonNull(analyzeUseCase);
    }

    public WorkspaceMedia importFile(Path rawPath) {
        Objects.requireNonNull(rawPath, "path must not be null");
        Path normalized = rawPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + normalized);
        }

        long lastMod = readLastModified(normalized);
        long size = readFileSize(normalized);

        WorkspaceMedia existing = library.get(normalized);
        if (existing != null && !existing.missingOnDisk() && existing.lastModifiedMillis() == lastMod && existing.fileSizeBytes() == size) {
            return existing; // Cache hit
        }

        MediaFile inspected = analyzeUseCase.analyze(normalized);
        MediaKind kind = WorkspaceMedia.inferKind(inspected, normalized);
        WorkspaceMedia item = new WorkspaceMedia(normalized, inspected, kind, lastMod, size, false, false, Instant.now());
        library.put(normalized, item);
        notifyListeners();
        return item;
    }

    public List<WorkspaceMedia> importFiles(List<Path> paths) {
        List<WorkspaceMedia> imported = new ArrayList<>();
        if (paths == null) return imported;
        for (Path p : paths) {
            try {
                imported.add(importFile(p));
            } catch (Exception e) {
                logger.warn("Skipping unreadable file {}: {}", p, e.getMessage());
            }
        }
        return imported;
    }

    public List<WorkspaceMedia> importDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Diretório inválido: " + directory);
        }
        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String lower = p.getFileName().toString().toLowerCase();
                int dot = lower.lastIndexOf('.');
                if (dot >= 0 && SUPPORTED_EXTENSIONS.contains(lower.substring(dot))) {
                    candidates.add(p);
                }
            });
        }
        return importFiles(candidates);
    }

    /**
     * Removes only the session reference; never deletes the file on disk.
     */
    public boolean removeReference(Path path) {
        if (path == null) return false;
        WorkspaceMedia removed = library.remove(path.toAbsolutePath().normalize());
        if (removed != null) {
            notifyListeners();
            return true;
        }
        return false;
    }

    public void clearReferences() {
        library.clear();
        notifyListeners();
    }

    /**
     * Checks disk existence and modification timestamps, invalidating stale cache entries.
     */
    public void refreshAndValidateFiles() {
        boolean changed = false;
        for (Map.Entry<Path, WorkspaceMedia> entry : library.entrySet()) {
            Path p = entry.getKey();
            WorkspaceMedia cur = entry.getValue();
            if (!Files.exists(p)) {
                if (!cur.missingOnDisk()) {
                    library.put(p, new WorkspaceMedia(p, cur.mediaFile(), cur.kind(), cur.lastModifiedMillis(),
                            cur.fileSizeBytes(), true, cur.modifiedSinceInspection(), cur.importedAt()));
                    changed = true;
                }
            } else {
                long newMod = readLastModified(p);
                long newSize = readFileSize(p);
                if (cur.missingOnDisk() || newMod != cur.lastModifiedMillis() || newSize != cur.fileSizeBytes()) {
                    MediaFile reAnalyzed = analyzeUseCase.analyze(p);
                    MediaKind newKind = WorkspaceMedia.inferKind(reAnalyzed, p);
                    library.put(p, new WorkspaceMedia(p, reAnalyzed, newKind, newMod, newSize, false, true, cur.importedAt()));
                    changed = true;
                }
            }
        }
        if (changed) notifyListeners();
    }

    public List<WorkspaceMedia> query(String searchQuery, MediaKind kindFilter, SortOrder sortOrder) {
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        Comparator<WorkspaceMedia> cmp = switch (sortOrder == null ? SortOrder.IMPORTED_DESC : sortOrder) {
            case NAME_ASC -> Comparator.comparing(WorkspaceMedia::fileName, String.CASE_INSENSITIVE_ORDER);
            case SIZE_DESC -> Comparator.comparingLong(WorkspaceMedia::fileSizeBytes).reversed();
            case DURATION_DESC -> Comparator.<WorkspaceMedia>comparingLong(m ->
                    m.mediaFile() != null && m.mediaFile().metadata() != null
                            ? m.mediaFile().metadata().duration().toMillis() : 0L).reversed();
            case IMPORTED_DESC -> Comparator.comparing(WorkspaceMedia::importedAt).reversed();
        };

        return library.values().stream()
                .filter(m -> kindFilter == null || m.kind() == kindFilter)
                .filter(m -> q.isEmpty() || m.fileName().toLowerCase().contains(q))
                .sorted(cmp)
                .toList();
    }

    public List<WorkspaceMedia> getAll() {
        return query(null, null, SortOrder.IMPORTED_DESC);
    }

    public void addListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    private long readLastModified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; }
    }

    private long readFileSize(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0L; }
    }
}
