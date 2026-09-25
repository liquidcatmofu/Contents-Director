package net.jan.moddirector.core.util;

import net.jan.moddirector.core.manage.NoOpProgressCallback;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IOOperationTest {

    @Test
    void rejectsTruncatedKnownLengthDownload() {
        byte[] partial = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IOException exception = assertThrows(IOException.class, () ->
            IOOperation.copy(
                new ByteArrayInputStream(partial),
                output,
                new NoOpProgressCallback(),
                10
            )
        );

        assertEquals("abc", new String(output.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(
            "Unexpected end of stream: expected 10 bytes but received 3",
            exception.getMessage()
        );
    }

    @Test
    void acceptsCompleteKnownLengthDownload() throws Exception {
        byte[] data = "complete".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        IOOperation.copy(
            new ByteArrayInputStream(data),
            output,
            new NoOpProgressCallback(),
            data.length
        );

        assertEquals("complete", new String(output.toByteArray(), StandardCharsets.UTF_8));
    }
}
