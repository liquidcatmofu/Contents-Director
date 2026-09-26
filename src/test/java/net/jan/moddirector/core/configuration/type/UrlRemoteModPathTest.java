package net.jan.moddirector.core.configuration.type;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
    @Test
    void acceptsValidZipWithZipCompatibleOrUnknownExtension() throws Exception {
        Path zip = createZip("archive.jar");
        try (ZipFile ignored = UrlRemoteMod.openValidatedZipArchive(zip, "archive.jar")) {
            // validated
        }

        Path custom = createZip("archive.mrpack");
        try (ZipFile ignored = UrlRemoteMod.openValidatedZipArchive(custom, "archive.mrpack")) {
            // validated
        }
    }

    @Test
    void rejectsKnownNonZipExtensionEvenWhenContentIsZip() throws Exception {
        Path disguised = createZip("archive.tar.gz");

        assertThrows(IOException.class,
            () -> UrlRemoteMod.openValidatedZipArchive(disguised, "archive.tar.gz"));
    }

    @Test
    void rejectsMalformedArchiveEvenWhenExtensionLooksZipCompatible() throws Exception {
        Path invalid = tempDir.resolve("archive.zip");
        Files.write(invalid, "not a zip archive".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(IOException.class,
            () -> UrlRemoteMod.openValidatedZipArchive(invalid, "archive.zip"));
    }

    private Path createZip(String fileName) throws Exception {
        Path archive = tempDir.resolve(fileName);
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("example.txt"));
            zip.write("example".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

}
