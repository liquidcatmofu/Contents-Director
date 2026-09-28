package net.jan.moddirector.core.configuration.modpack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModpackConfigurationDefaultTest {

    @Test
    void usesContentsDirectorAsDefaultPackName() {
        assertEquals("Contents Director", ModpackConfiguration.createDefault().packName());
    }
}
