package com.juanmuscaria.modpackdirector.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DownloadCandidateMonitor {
    private final Map<Path, FileSignature> baseline = new LinkedHashMap<>();
    private final Map<Path, FileSignature> previous = new LinkedHashMap<>();
    private final Map<Path, Integer> stablePolls = new LinkedHashMap<>();

    DownloadCandidateMonitor(String expectedFileName, List<Path> downloadDirectories) {
        if (expectedFileName == null || expectedFileName.trim().isEmpty()) {
            return;
        }

        String safeFileName = Paths.get(expectedFileName).getFileName().toString();
        for (Path directory : downloadDirectories) {
            Path candidate = directory.resolve(safeFileName).toAbsolutePath().normalize();
            baseline.put(candidate, signature(candidate));
        }
    }

    Optional<Path> findStableCandidate() {
        for (Map.Entry<Path, FileSignature> entry : baseline.entrySet()) {
            Path path = entry.getKey();
            FileSignature current = signature(path);
            FileSignature original = entry.getValue();

            if (current == null || current.equals(original)) {
                previous.remove(path);
                stablePolls.remove(path);
                continue;
            }

            FileSignature before = previous.put(path, current);
            if (current.equals(before)) {
                int count = stablePolls.getOrDefault(path, 0) + 1;
                stablePolls.put(path, count);
                if (count >= 1) {
                    return Optional.of(path);
                }
            } else {
                stablePolls.put(path, 0);
            }
        }

        return Optional.empty();
    }

    private static FileSignature signature(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }

            FileTime modified = Files.getLastModifiedTime(path);
            return new FileSignature(Files.size(path), modified.toMillis());
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static final class FileSignature {
        private final long size;
        private final long modifiedMillis;

        private FileSignature(long size, long modifiedMillis) {
            this.size = size;
            this.modifiedMillis = modifiedMillis;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FileSignature)) {
                return false;
            }
            FileSignature that = (FileSignature) other;
            return size == that.size && modifiedMillis == that.modifiedMillis;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(size);
            result = 31 * result + Long.hashCode(modifiedMillis);
            return result;
        }
    }
}
