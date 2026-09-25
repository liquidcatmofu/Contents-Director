package net.jan.moddirector.core.configuration.type;

import net.jan.moddirector.core.configuration.ConfigurationController;
import net.jan.moddirector.core.exception.ModDirectorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderResponseValidationTest {

    @Test
    void curseRejectsMissingDataObject() throws Exception {
        ModDirectorException exception = assertThrows(
            ModDirectorException.class,
            () -> CurseRemoteMod.parseInformation(
                ConfigurationController.OBJECT_MAPPER.readTree("{\"unexpected\":{}}")
            )
        );

        assertEquals(
            "CurseForge response did not contain a valid data object",
            exception.getMessage()
        );
    }

    @Test
    void curseRejectsMissingRequiredFileMetadata() throws Exception {
        ModDirectorException exception = assertThrows(
            ModDirectorException.class,
            () -> CurseRemoteMod.parseInformation(
                ConfigurationController.OBJECT_MAPPER.readTree(
                    "{\"data\":{\"displayName\":\"Example\"}}"
                )
            )
        );

        assertEquals(
            "CurseForge response was missing required file metadata",
            exception.getMessage()
        );
    }

    @Test
    void modrinthRejectsMissingFilesArray() throws Exception {
        ModDirectorException exception = assertThrows(
            ModDirectorException.class,
            () -> ModrinthRemoteMod.parseInformation(
                ConfigurationController.OBJECT_MAPPER.readTree("{}"),
                0
            )
        );

        assertEquals(
            "Modrinth response did not contain a files array",
            exception.getMessage()
        );
    }

    @Test
    void modrinthRejectsOutOfRangeFileIndex() throws Exception {
        ModDirectorException exception = assertThrows(
            ModDirectorException.class,
            () -> ModrinthRemoteMod.parseInformation(
                ConfigurationController.OBJECT_MAPPER.readTree(
                    "{\"files\":[{\"filename\":\"example.jar\",\"url\":\"https://example.invalid/example.jar\"}]}"
                ),
                1
            )
        );

        assertEquals("No such file at index 1", exception.getMessage());
    }

    @Test
    void modrinthRejectsMissingRequiredFileMetadata() throws Exception {
        ModDirectorException exception = assertThrows(
            ModDirectorException.class,
            () -> ModrinthRemoteMod.parseInformation(
                ConfigurationController.OBJECT_MAPPER.readTree(
                    "{\"files\":[{\"filename\":\"example.jar\"}]}"
                ),
                0
            )
        );

        assertEquals(
            "Modrinth response was missing required file metadata",
            exception.getMessage()
        );
    }
}
