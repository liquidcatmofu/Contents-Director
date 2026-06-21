package net.jan.moddirector.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that WebClient respects its timeout settings.
 * <p>
 * Opens a local server socket that accepts the TCP connection but never sends any data, then
 * asserts that WebClient.get() throws rather than hanging indefinitely.
 */
class WebClientTimeoutTest {

    @Test
    void getThrowsOnReadTimeout() throws Exception {
        // Bind to any free port; accept the connection silently (never write a response).
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            Thread acceptThread = new Thread(() -> {
                try {
                    // Accept and hold the connection open without sending anything
                    // so the client's read timeout fires.
                    java.net.Socket accepted = server.accept();
                    Thread.sleep(60_000);
                    accepted.close();
                } catch (Exception ignored) {
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            URL url = new URL("http://127.0.0.1:" + port + "/test");

            // Should throw within READ_TIMEOUT milliseconds (30 s), not hang forever.
            assertThrows(IOException.class, () -> WebClient.get(url),
                "WebClient.get() should throw IOException on read timeout");
        }
    }
}
