package net.jan.moddirector.core.manage.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallTransactionTest {

    @TempDir
    Path tempDir;

    @Test
    void targetIsUnchangedUntilCommit() throws Exception {
        Path target = tempDir.resolve("example.jar");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));

        Path staged;
        try (InstallTransaction transaction = InstallTransaction.create(target)) {
            staged = transaction.stagedFile();
            Files.write(staged, "new".getBytes(StandardCharsets.UTF_8));

            assertEquals("old", read(target));
            assertTrue(Files.exists(staged));

            transaction.commit();
        }

        assertEquals("new", read(target));
        assertFalse(Files.exists(staged));
    }

    @Test
    void abandoningTransactionDeletesPartialFileAndPreservesTarget() throws Exception {
        Path target = tempDir.resolve("example.jar");
        Files.write(target, "known-good".getBytes(StandardCharsets.UTF_8));

        Path staged;
        try (InstallTransaction transaction = InstallTransaction.create(target)) {
            staged = transaction.stagedFile();
            Files.write(staged, "partial".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("known-good", read(target));
        assertFalse(Files.exists(staged));
    }

    @Test
    void commitCreatesTargetWhenItDidNotExist() throws Exception {
        Path target = tempDir.resolve("new.jar");

        try (InstallTransaction transaction = InstallTransaction.create(target)) {
            Files.write(transaction.stagedFile(), "downloaded".getBytes(StandardCharsets.UTF_8));
            transaction.commit();
        }

        assertEquals("downloaded", read(target));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
