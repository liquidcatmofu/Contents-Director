package net.jan.moddirector.core.manage.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallStagingAreaTest {

    @TempDir
    Path tempDir;

    @Test
    void targetRemainsUnchangedUntilStagingAreaCommits() throws Exception {
        Path target = tempDir.resolve("mods").resolve("example.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes("old"));

        Path staged;
        try (InstallStagingArea staging = InstallStagingArea.create(target)) {
            staged = staging.stagedTarget();
            Files.write(staged, bytes("new"));

            assertEquals("old", read(target));
            assertEquals("new", read(staged));

            staging.commit(true);
        }

        assertEquals("new", read(target));
        assertFalse(Files.exists(staged));
    }

    @Test
    void abandoningStagingAreaPreservesLiveFiles() throws Exception {
        Path target = tempDir.resolve("mods").resolve("example.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes("known-good"));

        Path staged;
        try (InstallStagingArea staging = InstallStagingArea.create(target)) {
            staged = staging.stagedTarget();
            Files.write(staged, bytes("partial"));
        }

        assertEquals("known-good", read(target));
        assertFalse(Files.exists(staged));
    }

    @Test
    void extractedFilesArePublishedOnlyAtCommitAndOldVersionsAreDisabled() throws Exception {
        Path target = tempDir.resolve("pack.zip");
        Path liveExtracted = tempDir.resolve("nested").resolve("config.txt");
        Path disabled = liveExtracted.resolveSibling("config.txt.disabled-by-mod-director");
        Files.createDirectories(liveExtracted.getParent());
        Files.write(liveExtracted, bytes("old"));
        Files.write(disabled, bytes("older-disabled"));

        try (InstallStagingArea staging = InstallStagingArea.create(target)) {
            Files.write(staging.stagedTarget(), bytes("archive"));
            Path stagedExtracted = staging.stagedTarget().getParent()
                .resolve("nested").resolve("config.txt");
            Files.createDirectories(stagedExtracted.getParent());
            Files.write(stagedExtracted, bytes("new"));

            assertEquals("old", read(liveExtracted));
            assertFalse(Files.exists(target));

            staging.commit(false);
        }

        assertFalse(Files.exists(target));
        assertEquals("new", read(liveExtracted));
        assertEquals("old", read(disabled));
    }

    @Test
    void failedCommitRestoresPrimaryFileBackedUpEarlierInCommit() throws Exception {
        Path target = tempDir.resolve("example.jar");
        Path conflictingDestination = tempDir.resolve("conflict.txt");
        Files.write(target, bytes("old"));
        Files.createDirectories(conflictingDestination);

        try (InstallStagingArea staging = InstallStagingArea.create(target)) {
            Files.write(staging.stagedTarget(), bytes("new"));
            Files.write(staging.stagedTarget().getParent().resolve("conflict.txt"), bytes("staged"));

            assertThrows(IOException.class, () -> staging.commit(true));
        }

        assertEquals("old", read(target));
        assertTrue(Files.isDirectory(conflictingDestination));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
