package net.jan.moddirector.core.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import net.jan.moddirector.core.util.HashResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public class RemoteModMetadata {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final Map<String, String> hashes;
    private final Side side;

    @JsonCreator
    public RemoteModMetadata(
        @JsonProperty(value = "hash") LinkedHashMap<String, String> hashes,
        @JsonProperty(value = "side") Side side
    ) {
        this.hashes = hashes;
        this.side = side;
    }

    public HashResult checkHashes(Path file, PlatformDelegate platform) {
        if (hashes == null || hashes.isEmpty()) {
            return HashResult.UNKNOWN;
        }

        Map<String, MessageDigest> digests = new LinkedHashMap<>();
        for (Map.Entry<String, String> hashEntry : hashes.entrySet()) {
            try {
                digests.put(hashEntry.getKey(), MessageDigest.getInstance(hashEntry.getKey()));
            } catch (NoSuchAlgorithmException e) {
                platform.logger().warn("Hash algorithm {0} not supported by JVM", hashEntry.getKey());
            }
        }

        if (digests.isEmpty()) {
            platform.logger().warn("All given hash algorithms are not supported by the JVM");
            return HashResult.UNKNOWN;
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                for (MessageDigest digest : digests.values()) {
                    digest.update(buffer, 0, length);
                }
            }
        } catch (IOException e) {
            platform.logger().warn("Failed to open {0} for hash calculation, assuming hash does not match",
                file.toString(), e);
            return HashResult.UNMATCHED;
        }

        for (Map.Entry<String, MessageDigest> digestEntry : digests.entrySet()) {
            String expected = hashes.get(digestEntry.getKey());
            String actual = toHex(digestEntry.getValue().digest());

            if (expected == null || !actual.equalsIgnoreCase(expected.trim())) {
                return HashResult.UNMATCHED;
            }
        }

        return HashResult.MATCHED;
    }

    static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = HEX[value >>> 4];
            result[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(result);
    }

    public boolean shouldTryInstall(PlatformDelegate platform) {
        Side currentSide = platform.side();
        return currentSide == null || side == Side.UNKNOWN || currentSide == side;
    }
}
