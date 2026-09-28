package com.juanmuscaria.modpackdirector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModpackDirectorProgressTest {

    @Test
    void usesGenericInstallProgressWhenModpackConfigurationIsMissing() {
        assertEquals(
            "modpack_director.progress.install_default",
            ModpackDirector.installProgressKey(true)
        );
    }

    @Test
    void keepsNamedInstallProgressWhenModpackConfigurationIsPresent() {
        assertEquals(
            "modpack_director.progress.install",
            ModpackDirector.installProgressKey(false)
        );
    }
}
