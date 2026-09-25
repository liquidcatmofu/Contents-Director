package com.juanmuscaria.modpackdirector;

import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteVersionPolicyTest {

    @Test
    void optionalRemoteVersionFailureIsWarning() {
        ModpackConfiguration configuration = new ModpackConfiguration(
            "test",
            null,
            "1.0",
            null,
            false,
            false,
            null
        );

        assertEquals(Level.WARNING, ModpackDirector.remoteVersionFailureLevel(configuration));
    }

    @Test
    void requiredRemoteVersionFailureIsFatal() {
        ModpackConfiguration configuration = new ModpackConfiguration(
            "test",
            null,
            "1.0",
            null,
            true,
            false,
            null
        );

        assertEquals(Level.SEVERE, ModpackDirector.remoteVersionFailureLevel(configuration));
    }
}
