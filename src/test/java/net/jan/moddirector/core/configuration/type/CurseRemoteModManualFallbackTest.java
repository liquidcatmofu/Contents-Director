package net.jan.moddirector.core.configuration.type;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.configuration.RemoteModMetadata;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.NoOpProgressCallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurseRemoteModManualFallbackTest {

    @TempDir
    Path tempDir;

    @Test
    void providerFailureFallsBackToSelectedFileAndCommitsIt() throws Exception {
        Path selected = tempDir.resolve("downloaded.jar");
        Files.write(selected, "manual-file".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("mods").resolve("example.jar");

        TestDirector director = new TestDirector(new TestPlatform(tempDir), selected);
        TrackingCurseRemoteMod mod = new TrackingCurseRemoteMod(metadataFor("manual-file"));

        mod.performInstall(
            target,
            new NoOpProgressCallback(),
            director,
            new RemoteModInformation("example", "example.jar")
        );

        assertEquals("manual-file", read(target));
        assertEquals(1, director.manualRequestCount);
    }

    @Test
    void wrongManualFileDoesNotReplaceExistingTarget() throws Exception {
        Path selected = tempDir.resolve("wrong.jar");
        Files.write(selected, "wrong-file".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("mods").resolve("example.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, "known-good".getBytes(StandardCharsets.UTF_8));

        TestDirector director = new TestDirector(new TestPlatform(tempDir), selected);
        TrackingCurseRemoteMod mod = new TrackingCurseRemoteMod(metadataFor("expected-file"));

        assertThrows(ModDirectorException.class, () ->
            mod.performInstall(
                target,
                new NoOpProgressCallback(),
                director,
                new RemoteModInformation("example", "example.jar")
            )
        );

        assertEquals("known-good", read(target));
        assertEquals(1, director.manualRequestCount);
    }

    private static RemoteModMetadata metadataFor(String content) throws Exception {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("SHA-256", sha256(content));
        return new RemoteModMetadata(hashes, Side.UNKNOWN);
    }

    private static String sha256(String content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static final class TrackingCurseRemoteMod extends CurseRemoteMod {
        private TrackingCurseRemoteMod(RemoteModMetadata metadata) throws Exception {
            super(
                1,
                2,
                metadata,
                null,
                null,
                null,
                null,
                "example.jar",
                new URL("https://www.curseforge.com/minecraft/mc-mods/example/files/2")
            );
        }

        @Override
        CurseAddonFileInformation fetchInformation() throws ModDirectorException {
            throw new ModDirectorException("provider unavailable");
        }
    }

    private static final class TestDirector extends ModpackDirector {
        private final Path selectedFile;
        private int manualRequestCount;

        private TestDirector(PlatformDelegate platform, Path selectedFile) {
            super(platform);
            this.selectedFile = selectedFile;
        }

        @Override
        public Path requestManualDownload(URL manualDownloadUrl, Path targetFile) {
            manualRequestCount++;
            return selectedFile;
        }
    }

    private static final class TestPlatform implements PlatformDelegate {
        private final Path root;
        private final LoggerDelegate logger = new LoggerDelegate() {
            @Override
            public void log(Level level, String message, Object... format) {
            }
        };

        private TestPlatform(Path root) {
            this.root = root;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Path configurationDirectory() {
            return root.resolve("config");
        }

        @Override
        public Path modFile(String modFileName) {
            return root.resolve("mods").resolve(modFileName);
        }

        @Override
        public Path rootFile(String modFileName) {
            return root.resolve(modFileName);
        }

        @Override
        public Path customFile(String modFileName, String modFolderName) {
            return root.resolve(modFolderName).resolve(modFileName);
        }

        @Override
        public Path installationRoot() {
            return root;
        }

        @Override
        public LoggerDelegate logger() {
            return logger;
        }

        @Override
        public Side side() {
            return Side.UNKNOWN;
        }

        @Override
        public boolean headless() {
            return true;
        }
    }
}
