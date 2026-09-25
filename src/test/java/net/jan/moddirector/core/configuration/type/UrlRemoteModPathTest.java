package net.jan.moddirector.core.configuration.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlRemoteModPathTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesNestedEntryInsideExtractionRoot() throws Exception {
        Path root = tempDir.resolve("extract");
        Files.createDirectories(root);

        Path resolved = UrlRemoteMod.resolveZipEntryPath(root, "nested/example.jar");

        assertEquals(root.resolve("nested/example.jar").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void rejectsEntryResolvingToExtractionRoot() throws Exception {
        Path root = tempDir.resolve("extract");
        Files.createDirectories(root);

        assertThrows(IOException.class,
            () -> UrlRemoteMod.resolveZipEntryPath(root, "."));
        assertThrows(IOException.class,
            () -> UrlRemoteMod.resolveZipEntryPath(root, "foo/.."));
    }

    @Test
    void rejectsEntryEscapingExtractionRoot() throws Exception {
        Path root = tempDir.resolve("extract");
        Files.createDirectories(root);

        assertThrows(IOException.class,
            () -> UrlRemoteMod.resolveZipEntryPath(root, "../outside.jar"));
    }

    @Test
    void rejectsAbsoluteEntryOutsideExtractionRoot() throws Exception {
        Path root = tempDir.resolve("extract");
        Path outside = tempDir.resolve("outside.jar").toAbsolutePath();
        Files.createDirectories(root);

        assertThrows(IOException.class,
            () -> UrlRemoteMod.resolveZipEntryPath(root, outside.toString()));
    }

    @Test
    void rejectsExistingSymbolicLinkInEntryPath() throws Exception {
        Path root = tempDir.resolve("extract");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);

        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return;
        }

        assertThrows(IOException.class,
            () -> UrlRemoteMod.resolveZipEntryPath(root, "link/example.jar"));
    }
}
