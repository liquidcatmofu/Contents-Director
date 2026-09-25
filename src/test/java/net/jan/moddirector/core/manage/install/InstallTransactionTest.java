package net.jan.moddirector.core.manage.install;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void fallsBackWhenAtomicReplacementReportsExistingTarget() throws Exception {
        Path target = tempDir.resolve("existing.jar");
        Path staged = tempDir.resolve("staged.download");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));
        Files.write(staged, "new".getBytes(StandardCharsets.UTF_8));

        AtomicInteger attempts = new AtomicInteger();
        InstallTransaction.replaceStagedFile(staged, target, (source, destination, options) -> {
            if (attempts.getAndIncrement() == 0) {
                throw new FileAlreadyExistsException(destination.toString());
            }
            return Files.move(source, destination, options);
        });

        assertEquals(2, attempts.get());
        assertEquals("new", read(target));
        assertFalse(Files.exists(staged));
    }

    @Test
    void doesNotRetryUnrelatedIoFailures() throws Exception {
        Path target = tempDir.resolve("target.jar");
        Path staged = tempDir.resolve("staged.download");
        Files.write(staged, "new".getBytes(StandardCharsets.UTF_8));

        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IOException.class, () ->
            InstallTransaction.replaceStagedFile(staged, target, (source, destination, options) -> {
                attempts.incrementAndGet();
                throw new IOException("simulated I/O failure");
            })
        );

        assertEquals(1, attempts.get());
        assertTrue(Files.exists(staged));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
