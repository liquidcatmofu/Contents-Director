package net.jan.moddirector.core.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationControllerPathTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesPathInsideInstallationRoot() throws Exception {
        Path root = tempDir.resolve("game");
        Path mods = root.resolve("mods");
        Files.createDirectories(mods);

        Path resolved = ConfigurationController.resolveModificationPath(root, mods, "example.jar");

        assertEquals(mods.resolve("example.jar").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void rejectsPathEscapingInstallationRoot() throws Exception {
        Path root = tempDir.resolve("game");
        Path mods = root.resolve("mods");
        Files.createDirectories(mods);

        assertThrows(IOException.class,
            () -> ConfigurationController.resolveModificationPath(root, mods, "../../outside.jar"));
    }

    @Test
    void rejectsAbsolutePathOutsideInstallationRoot() throws Exception {
        Path root = tempDir.resolve("game");
        Path outside = tempDir.resolve("outside.jar").toAbsolutePath();
        Files.createDirectories(root);

        assertThrows(IOException.class,
            () -> ConfigurationController.resolveModificationPath(root, root, outside.toString()));
    }

    @Test
    void rejectsExistingSymbolicLinkInModifyPath() throws Exception {
        Path root = tempDir.resolve("game");
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
            () -> ConfigurationController.resolveModificationPath(root, root, "link/example.jar"));
    }
}
