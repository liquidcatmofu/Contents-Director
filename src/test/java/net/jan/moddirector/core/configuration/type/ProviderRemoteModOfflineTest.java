package net.jan.moddirector.core.configuration.type;

import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.exception.ModDirectorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProviderRemoteModOfflineTest {

    @Test
    void curseUsesConfiguredFilenameWithoutFetchingProviderMetadata() throws Exception {
        TrackingCurseRemoteMod mod = new TrackingCurseRemoteMod("configured.jar");

        RemoteModInformation information = mod.queryInformation();

        assertEquals("configured.jar", information.displayName());
        assertEquals("configured.jar", information.targetFilename());
        assertEquals("https://www.curseforge.com/projects/1", assertDoesNotThrow(mod::remoteUrl));
        assertFalse(mod.fetchCalled);
    }

    @Test
    void modrinthUsesConfiguredFilenameWithoutFetchingProviderMetadata() throws Exception {
        TrackingModrinthRemoteMod mod = new TrackingModrinthRemoteMod("configured.jar");

        RemoteModInformation information = mod.queryInformation();

        assertEquals("configured.jar", information.displayName());
        assertEquals("configured.jar", information.targetFilename());
        assertEquals("https://api.modrinth.com/v2/version/version", assertDoesNotThrow(mod::remoteUrl));
        assertFalse(mod.fetchCalled);
    }

    private static final class TrackingCurseRemoteMod extends CurseRemoteMod {
        private boolean fetchCalled;

        private TrackingCurseRemoteMod(String fileName) {
            super(1, 2, null, null, null, null, null, fileName);
        }

        @Override
        CurseAddonFileInformation fetchInformation() throws ModDirectorException {
            fetchCalled = true;
            throw new AssertionError("Provider metadata should not be fetched");
        }
    }

    private static final class TrackingModrinthRemoteMod extends ModrinthRemoteMod {
        private boolean fetchCalled;

        private TrackingModrinthRemoteMod(String fileName) {
            super("version", 0, null, null, null, null, null, fileName);
        }

        @Override
        ModrinthFileInformation fetchInformation() throws ModDirectorException {
            fetchCalled = true;
            throw new AssertionError("Provider metadata should not be fetched");
        }
    }
}
