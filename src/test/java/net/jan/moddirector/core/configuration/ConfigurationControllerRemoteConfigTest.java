package net.jan.moddirector.core.configuration;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.configuration.type.UrlRemoteMod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationControllerRemoteConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void remoteConfigDoesNotOverwriteOrDeleteCollidingLocalConfig() throws Exception {
        Path root = tempDir.resolve("game");
        Path configDir = root.resolve("config").resolve("mod-director");
        Path remoteDir = tempDir.resolve("remote");
        Files.createDirectories(configDir);
        Files.createDirectories(remoteDir);

        String localConfig = urlConfig("local.jar", "https://example.invalid/local.jar");
        Path localCollision = configDir.resolve("collision.url.json");
        Files.write(localCollision, bytes(localConfig));

        Path remotePayload = remoteDir.resolve("collision.url.json");
        Files.write(remotePayload, bytes(urlConfig("remote.jar", "https://example.invalid/remote.jar")));
        Files.write(
            configDir.resolve("00.remote.json"),
            bytes(remoteConfig(remotePayload.toUri().toURL()))
        );

        ModpackDirector director = new ModpackDirector(new TestPlatform(root, configDir));
        ConfigurationController controller = new ConfigurationController(director, configDir);
        controller.load();

        assertEquals(localConfig, read(localCollision));
        assertEquals(
            Arrays.asList("local.jar", "remote.jar"),
            controller.getConfigurations().stream()
                .filter(UrlRemoteMod.class::isInstance)
                .map(UrlRemoteMod.class::cast)
                .map(UrlRemoteMod::getFileName)
                .sorted()
                .collect(Collectors.toList())
        );
    }

    @Test
    void malformedRemoteConfigLeavesCollidingLocalConfigUntouched() throws Exception {
        Path root = tempDir.resolve("game");
        Path configDir = root.resolve("config").resolve("mod-director");
        Path remoteDir = tempDir.resolve("remote");
        Files.createDirectories(configDir);
        Files.createDirectories(remoteDir);

        String localConfig = urlConfig("local.jar", "https://example.invalid/local.jar");
        Path localCollision = configDir.resolve("collision.url.json");
        Files.write(localCollision, bytes(localConfig));

        Path remotePayload = remoteDir.resolve("collision.url.json");
        Files.write(remotePayload, bytes("{ malformed"));
        Files.write(
            configDir.resolve("00.remote.json"),
            bytes(remoteConfig(remotePayload.toUri().toURL()))
        );

        ModpackDirector director = new ModpackDirector(new TestPlatform(root, configDir));
        ConfigurationController controller = new ConfigurationController(director, configDir);
        controller.load();

        assertEquals(localConfig, read(localCollision));
        assertTrue(director.hasFatalError());
    }

    @Test
    void malformedRemoteBundleIsValidatedBeforeModifyActionsRun() throws Exception {
        Path root = tempDir.resolve("game");
        Path configDir = root.resolve("config").resolve("mod-director");
        Path remoteDir = tempDir.resolve("remote");
        Path victim = root.resolve("mods").resolve("victim.jar");
        Files.createDirectories(configDir);
        Files.createDirectories(remoteDir);
        Files.createDirectories(victim.getParent());
        Files.write(victim, bytes("keep-me"));

        Path remotePayload = remoteDir.resolve("actions.bundle.json");
        Files.write(remotePayload, bytes(
            "{\"modify\":["
                + "{\"folder\":\"mods\",\"fileName\":\"victim.jar\",\"delete\":true},"
                + "{\"folder\":{\"invalid\":true},\"fileName\":\"other.jar\",\"delete\":true}"
                + "]}"
        ));
        Files.write(
            configDir.resolve("00.remote.json"),
            bytes(remoteConfig(remotePayload.toUri().toURL()))
        );

        ModpackDirector director = new ModpackDirector(new TestPlatform(root, configDir));
        ConfigurationController controller = new ConfigurationController(director, configDir);
        controller.load();

        assertEquals("keep-me", read(victim));
        assertTrue(director.hasFatalError());
    }

    @Test
    void nestedRemoteConfigsLoadWithoutCreatingLocalCopies() throws Exception {
        Path root = tempDir.resolve("game");
        Path configDir = root.resolve("config").resolve("mod-director");
        Path remoteDir = tempDir.resolve("remote");
        Files.createDirectories(configDir);
        Files.createDirectories(remoteDir);

        Path payload = remoteDir.resolve("payload.url.json");
        Files.write(payload, bytes(urlConfig("nested.jar", "https://example.invalid/nested.jar")));

        Path nested = remoteDir.resolve("nested.remote.json");
        Files.write(nested, bytes(remoteConfig(payload.toUri().toURL())));

        Files.write(
            configDir.resolve("00.remote.json"),
            bytes(remoteConfig(nested.toUri().toURL()))
        );

        ModpackDirector director = new ModpackDirector(new TestPlatform(root, configDir));
        ConfigurationController controller = new ConfigurationController(director, configDir);
        controller.load();

        assertEquals(1, controller.getConfigurations().size());
        assertEquals("nested.jar", ((UrlRemoteMod) controller.getConfigurations().get(0)).getFileName());
        assertEquals(1L, regularFileCount(configDir));
    }

    @Test
    void nestedRemoteConfigCycleIsRejectedWithoutFilesystemArtifacts() throws Exception {
        Path root = tempDir.resolve("game");
        Path configDir = root.resolve("config").resolve("mod-director");
        Path remoteDir = tempDir.resolve("remote");
        Files.createDirectories(configDir);
        Files.createDirectories(remoteDir);

        Path first = remoteDir.resolve("first.remote.json");
        Path second = remoteDir.resolve("second.remote.json");
        Files.write(first, bytes(remoteConfig(second.toUri().toURL())));
        Files.write(second, bytes(remoteConfig(first.toUri().toURL())));
        Files.write(
            configDir.resolve("00.remote.json"),
            bytes(remoteConfig(first.toUri().toURL()))
        );

        ModpackDirector director = new ModpackDirector(new TestPlatform(root, configDir));
        ConfigurationController controller = new ConfigurationController(director, configDir);
        controller.load();

        assertTrue(director.hasFatalError());
        assertEquals(1L, regularFileCount(configDir));
    }

    private static String urlConfig(String fileName, String url) {
        return "{\"fileName\":\"" + fileName + "\",\"url\":\"" + url + "\"}";
    }

    private static String remoteConfig(URL url) {
        return "{\"url\":\"" + url.toExternalForm() + "\"}";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static long regularFileCount(Path directory) throws Exception {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static final class TestPlatform implements PlatformDelegate {
        private final Path root;
        private final Path configurationDirectory;
        private final LoggerDelegate logger = new LoggerDelegate() {
            @Override
            public void log(Level level, String message, Object... format) {
            }
        };

        private TestPlatform(Path root, Path configurationDirectory) {
            this.root = root;
            this.configurationDirectory = configurationDirectory;
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Path configurationDirectory() {
            return configurationDirectory;
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
