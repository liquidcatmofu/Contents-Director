package net.jan.moddirector.core.configuration;

import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.util.HashResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteModMetadataTest {

    private static final LoggerDelegate NOOP_LOGGER = (level, message, format) -> {
    };

    @TempDir
    Path tempDir;

    @Test
    void matchesMultipleSupportedHashes() throws Exception {
        Path file = write("abc");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "900150983cd24fb0d6963f7d28e17f72");
        hashes.put("SHA-1", "a9993e364706816aba3e25717850c26c9cd0d89d");
        hashes.put("SHA-256", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        assertEquals(HashResult.MATCHED,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void rejectsWhenAnySupportedHashDoesNotMatch() throws Exception {
        Path file = write("abc");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "900150983cd24fb0d6963f7d28e17f72");
        hashes.put("SHA-256", "0000000000000000000000000000000000000000000000000000000000000000");

        assertEquals(HashResult.UNMATCHED,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void preservesLeadingZeroesInDigest() throws Exception {
        Path file = write("x84");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("SHA-256", "009d9e88ce13770ca5fc05097eb32a9576e1b989c0584f9174f31fe70aadc342");

        assertEquals(HashResult.MATCHED,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void acceptsUppercaseExpectedHash() throws Exception {
        Path file = write("abc");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("SHA-1", "A9993E364706816ABA3E25717850C26C9CD0D89D");

        assertEquals(HashResult.MATCHED,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void ignoresUnsupportedAlgorithmsWhenSupportedHashesMatch() throws Exception {
        Path file = write("abc");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("not-a-real-digest", "anything");
        hashes.put("SHA-256", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        assertEquals(HashResult.MATCHED,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void returnsUnknownWhenNoConfiguredAlgorithmIsSupported() throws Exception {
        Path file = write("abc");

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("not-a-real-digest", "anything");

        assertEquals(HashResult.UNKNOWN,
            new RemoteModMetadata(hashes, null).checkHashes(file, platform()));
    }

    @Test
    void omittedSideIsUnrestrictedOnClientAndServer() throws Exception {
        RemoteModMetadata metadata = ConfigurationController.OBJECT_MAPPER.readValue(
            "{\"hash\":{\"SHA-256\":\"anything\"}}",
            RemoteModMetadata.class
        );

        assertTrue(metadata.shouldTryInstall(platform(Side.CLIENT)));
        assertTrue(metadata.shouldTryInstall(platform(Side.SERVER)));
    }

    @Test
    void unknownConfiguredSideIsUnrestricted() {
        RemoteModMetadata metadata = new RemoteModMetadata(null, Side.UNKNOWN);

        assertTrue(metadata.shouldTryInstall(platform(Side.CLIENT)));
        assertTrue(metadata.shouldTryInstall(platform(Side.SERVER)));
    }

    @Test
    void clientConfiguredSideMatchesClientButNotServer() {
        RemoteModMetadata metadata = new RemoteModMetadata(null, Side.CLIENT);

        assertTrue(metadata.shouldTryInstall(platform(Side.CLIENT)));
        assertFalse(metadata.shouldTryInstall(platform(Side.SERVER)));
    }

    @Test
    void serverConfiguredSideMatchesServerButNotClient() {
        RemoteModMetadata metadata = new RemoteModMetadata(null, Side.SERVER);

        assertTrue(metadata.shouldTryInstall(platform(Side.SERVER)));
        assertFalse(metadata.shouldTryInstall(platform(Side.CLIENT)));
    }

    @Test
    void unknownPlatformSideDoesNotRejectRestrictedMetadata() {
        assertTrue(new RemoteModMetadata(null, Side.CLIENT)
            .shouldTryInstall(platform(Side.UNKNOWN)));
        assertTrue(new RemoteModMetadata(null, Side.SERVER)
            .shouldTryInstall(platform(Side.UNKNOWN)));
    }

    @Test
    void nullPlatformSideDoesNotRejectRestrictedMetadata() {
        assertTrue(new RemoteModMetadata(null, Side.CLIENT)
            .shouldTryInstall(platform(null)));
        assertTrue(new RemoteModMetadata(null, Side.SERVER)
            .shouldTryInstall(platform(null)));
    }

    private Path write(String contents) throws Exception {
        Path file = tempDir.resolve("test.bin");
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private PlatformDelegate platform() {
        return platform(Side.UNKNOWN);
    }

    private PlatformDelegate platform(Side side) {
        return new PlatformDelegate() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public Path configurationDirectory() {
                return tempDir;
            }

            @Override
            public Path modFile(String modFileName) {
                return tempDir.resolve(modFileName);
            }

            @Override
            public Path rootFile(String modFileName) {
                return tempDir.resolve(modFileName);
            }

            @Override
            public Path customFile(String modFileName, String modFolderName) {
                return tempDir.resolve(modFolderName).resolve(modFileName);
            }

            @Override
            public Path installationRoot() {
                return tempDir;
            }

            @Override
            public LoggerDelegate logger() {
                return NOOP_LOGGER;
            }

            @Override
            public Side side() {
                return side;
            }

            @Override
            public boolean headless() {
                return true;
            }
        };
    }
}
