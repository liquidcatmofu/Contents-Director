package com.juanmuscaria.modpackdirector.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DownloadDirectoriesTest {

    @TempDir
    Path tempDir;

    @Test
    void xdgDownloadDirectoryIsPreferredAndHomeDownloadsRemainsFallback() throws Exception {
        Path home = tempDir.resolve("home");
        Path config = tempDir.resolve("xdg-config");
        Files.createDirectories(config);
        Files.write(
            config.resolve("user-dirs.dirs"),
            "XDG_DOWNLOAD_DIR=\"$HOME/My Downloads\"\n".getBytes(StandardCharsets.UTF_8)
        );

        List<Path> directories = DownloadDirectories.resolve(home, config);

        assertEquals(home.resolve("My Downloads").normalize(), directories.get(0));
        assertEquals(home.resolve("Downloads").normalize(), directories.get(1));
    }

    @Test
    void absoluteXdgDownloadDirectoryIsSupported() throws Exception {
        Path home = tempDir.resolve("home");
        Path config = tempDir.resolve("xdg-config");
        Path downloads = tempDir.resolve("downloads elsewhere").toAbsolutePath().normalize();
        Files.createDirectories(config);
        Files.write(
            config.resolve("user-dirs.dirs"),
            ("XDG_DOWNLOAD_DIR=\"" + downloads + "\"\n").getBytes(StandardCharsets.UTF_8)
        );

        List<Path> directories = DownloadDirectories.resolve(home, config);

        assertEquals(downloads, directories.get(0));
    }
}
