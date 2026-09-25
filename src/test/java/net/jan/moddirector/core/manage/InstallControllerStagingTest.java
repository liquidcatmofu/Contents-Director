package net.jan.moddirector.core.manage;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.configuration.InstallationPolicy;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.configuration.RemoteModMetadata;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.PreInstallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallControllerStagingTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveCollectsSupersededFilesWithoutMutatingThem() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path old = platform.modFile("old.jar");
        Files.createDirectories(old.getParent());
        Files.write(old, bytes("old"));

        TestRemoteMod remote = new TestRemoteMod(policy("old.jar"), false);
        List<Callable<PreInstallResult>> tasks = controller.createPreInstallTasks(
            Collections.singletonList(remote),
            (title, message) -> new NoOpProgressCallback()
        );

        PreInstallResult result = tasks.get(0).call();

        assertTrue(result.isInstallCandidate());
        assertEquals(Collections.singletonList(old.toAbsolutePath().normalize()),
            result.getInstallableMod().getSupersededFiles());
        assertEquals("old", read(old));
    }

    @Test
    void stageFailureLeavesSupersededAndBansoukouFilesUntouched() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path target = platform.modFile("example.jar").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path old = target.resolveSibling("old.jar");
        Path patched = target.resolveSibling("example-patched.jar");
        Path disabled = target.resolveSibling("example.disabled");
        Files.write(old, bytes("old"));
        Files.write(patched, bytes("patched"));
        Files.write(disabled, bytes("disabled"));

        TestRemoteMod remote = new TestRemoteMod(policy("old.jar"), true);
        InstallableMod installable = new InstallableMod(
            remote,
            new RemoteModInformation("example", "example.jar"),
            target
        ).withCommitActions(true, Collections.singletonList(old));

        controller.createInstallTasks(
            Collections.singletonList(installable),
            (title, message) -> new NoOpProgressCallback()
        ).get(0).call();

        assertFalse(Files.exists(target));
        assertEquals("old", read(old));
        assertEquals("patched", read(patched));
        assertEquals("disabled", read(disabled));
    }

    @Test
    void hashFailureLeavesLiveTargetAndSupersededFilesUntouched() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path target = platform.modFile("example.jar").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path old = target.resolveSibling("old.jar");
        Files.write(target, bytes("known-good"));
        Files.write(old, bytes("old"));

        TestRemoteMod remote = new TestRemoteMod(
            policy("old.jar"),
            false,
            metadataFor("expected")
        );
        InstallableMod installable = new InstallableMod(
            remote,
            new RemoteModInformation("example", "example.jar"),
            target
        ).withCommitActions(false, Collections.singletonList(old));

        controller.createInstallTasks(
            Collections.singletonList(installable),
            (title, message) -> new NoOpProgressCallback()
        ).get(0).call();

        assertEquals("known-good", read(target));
        assertEquals("old", read(old));
        assertFalse(Files.exists(old.resolveSibling("old.jar.disabled-by-mod-director")));
    }

    @Test
    void deferredSupersedeDoesNotRemoveFilePublishedBySameCommit() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path target = platform.modFile("example.jar").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path replacedSuperseded = target.resolveSibling("old.jar");
        Path disabled = target.resolveSibling("old.jar.disabled-by-mod-director");
        Files.write(replacedSuperseded, bytes("old"));

        TestRemoteMod remote = new TestRemoteMod(
            policy("old.jar"),
            false,
            null,
            "old.jar"
        );
        InstallableMod installable = new InstallableMod(
            remote,
            new RemoteModInformation("example", "example.jar"),
            target
        ).withCommitActions(false, Collections.singletonList(replacedSuperseded));

        controller.createInstallTasks(
            Collections.singletonList(installable),
            (title, message) -> new NoOpProgressCallback()
        ).get(0).call();

        assertEquals("new", read(target));
        assertEquals("derived-new", read(replacedSuperseded));
        assertEquals("old", read(disabled));
    }

    @Test
    void deferredBansoukouCleanupDoesNotRemoveFilePublishedBySameCommit() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path target = platform.modFile("example.jar").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path oldPatched = target.resolveSibling("example-patched.jar");
        Path oldDisabled = target.resolveSibling("example.disabled");
        Files.write(oldPatched, bytes("old-patched"));
        Files.write(oldDisabled, bytes("old-disabled"));

        TestRemoteMod remote = new TestRemoteMod(
            policy(null),
            false,
            null,
            "example-patched.jar"
        );
        InstallableMod installable = new InstallableMod(
            remote,
            new RemoteModInformation("example", "example.jar"),
            target
        ).withCommitActions(true, Collections.emptyList());

        controller.createInstallTasks(
            Collections.singletonList(installable),
            (title, message) -> new NoOpProgressCallback()
        ).get(0).call();

        assertEquals("new", read(target));
        assertEquals("derived-new", read(oldPatched));
        assertFalse(Files.exists(oldDisabled));
    }

    @Test
    void successfulCommitRunsDeferredSupersedeAndBansoukouCleanup() throws Exception {
        TestPlatform platform = new TestPlatform(tempDir);
        ModpackDirector director = new ModpackDirector(platform);
        InstallController controller = director.getInstallController();

        Path target = platform.modFile("example.jar").toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path old = target.resolveSibling("old.jar");
        Path oldDisabled = target.resolveSibling("old.jar.disabled-by-mod-director");
        Path patched = target.resolveSibling("example-patched.jar");
        Path disabled = target.resolveSibling("example.disabled");
        Files.write(old, bytes("old"));
        Files.write(patched, bytes("patched"));
        Files.write(disabled, bytes("disabled"));

        TestRemoteMod remote = new TestRemoteMod(policy("old.jar"), false);
        InstallableMod installable = new InstallableMod(
            remote,
            new RemoteModInformation("example", "example.jar"),
            target
        ).withCommitActions(true, Collections.singletonList(old));

        controller.createInstallTasks(
            Collections.singletonList(installable),
            (title, message) -> new NoOpProgressCallback()
        ).get(0).call();

        assertEquals("new", read(target));
        assertFalse(Files.exists(old));
        assertEquals("old", read(oldDisabled));
        assertFalse(Files.exists(patched));
        assertFalse(Files.exists(disabled));
    }

    private static InstallationPolicy policy(String supersede) {
        return new InstallationPolicy(
            false,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            supersede,
            null,
            false,
            null
        );
    }

    private static RemoteModMetadata metadataFor(String content) throws Exception {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("SHA-256", sha256(content));
        return new RemoteModMetadata(hashes, Side.UNKNOWN);
    }

    private static String sha256(String content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes(content));
        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static final class TestRemoteMod extends ModDirectorRemoteMod {
        private final boolean failDuringStage;
        private final String derivedFileName;

        private TestRemoteMod(InstallationPolicy policy, boolean failDuringStage) {
            this(policy, failDuringStage, null, null);
        }

        private TestRemoteMod(
            InstallationPolicy policy,
            boolean failDuringStage,
            RemoteModMetadata metadata
        ) {
            this(policy, failDuringStage, metadata, null);
        }

        private TestRemoteMod(
            InstallationPolicy policy,
            boolean failDuringStage,
            RemoteModMetadata metadata,
            String derivedFileName
        ) {
            super(metadata, policy, null, null, null);
            this.failDuringStage = failDuringStage;
            this.derivedFileName = derivedFileName;
        }

        @Override
        public String remoteType() {
            return "test";
        }

        @Override
        public String offlineName() {
            return "test-mod";
        }

        @Override
        public String remoteUrl() {
            return "test://mod";
        }

        @Override
        public RemoteModInformation queryInformation() {
            return new RemoteModInformation("example", "example.jar");
        }

        @Override
        public void performInstall(
            Path targetFile,
            ProgressCallback progressCallback,
            ModpackDirector director,
            RemoteModInformation information
        ) throws ModDirectorException {
            try {
                Files.write(targetFile, bytes(failDuringStage ? "partial" : "new"));
                if (derivedFileName != null) {
                    Files.write(targetFile.getParent().resolve(derivedFileName), bytes("derived-new"));
                }
            } catch (Exception e) {
                throw new ModDirectorException("failed to write staged test file", e);
            }

            if (failDuringStage) {
                throw new ModDirectorException("simulated stage failure");
            }
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
