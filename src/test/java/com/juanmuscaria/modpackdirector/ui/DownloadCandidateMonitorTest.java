package com.juanmuscaria.modpackdirector.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadCandidateMonitorTest {

    @TempDir
    Path tempDir;

    @Test
    void existingUnchangedFileIsNotOffered() throws Exception {
        Path file = tempDir.resolve("example.zip");
        Files.write(file, "old".getBytes(StandardCharsets.UTF_8));
        DownloadCandidateMonitor monitor =
            new DownloadCandidateMonitor("example.zip", Collections.singletonList(tempDir));

        assertFalse(monitor.findStableCandidate().isPresent());
        assertFalse(monitor.findStableCandidate().isPresent());
    }

    @Test
    void changedFileMustBeStableAcrossTwoPolls() throws Exception {
        Path file = tempDir.resolve("example.zip");
        Files.write(file, "old".getBytes(StandardCharsets.UTF_8));
        DownloadCandidateMonitor monitor =
            new DownloadCandidateMonitor("example.zip", Collections.singletonList(tempDir));

        Files.write(file, "new content".getBytes(StandardCharsets.UTF_8));

        assertFalse(monitor.findStableCandidate().isPresent());
        assertTrue(monitor.findStableCandidate().isPresent());
        assertEquals(file.toAbsolutePath().normalize(), monitor.findStableCandidate().get());
    }

    @Test
    void stableCandidateIsWithdrawnWhenFileChangesAgain() throws Exception {
        Path file = tempDir.resolve("example.zip");
        DownloadCandidateMonitor monitor =
            new DownloadCandidateMonitor("example.zip", Collections.singletonList(tempDir));

        Files.write(file, "first chunk".getBytes(StandardCharsets.UTF_8));

        assertFalse(monitor.findStableCandidate().isPresent());
        assertTrue(monitor.findStableCandidate().isPresent());

        Files.write(file, "first chunk and more data".getBytes(StandardCharsets.UTF_8));

        assertFalse(monitor.findStableCandidate().isPresent());
    }

    @Test
    void newlyCreatedFileIsDetectedAfterItBecomesStable() throws Exception {
        DownloadCandidateMonitor monitor =
            new DownloadCandidateMonitor("example.zip", Collections.singletonList(tempDir));
        Path file = tempDir.resolve("example.zip");
        Files.write(file, "download".getBytes(StandardCharsets.UTF_8));

        assertFalse(monitor.findStableCandidate().isPresent());
        assertEquals(file.toAbsolutePath().normalize(), monitor.findStableCandidate().get());
    }
}
