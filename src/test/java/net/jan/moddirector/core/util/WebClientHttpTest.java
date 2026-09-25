package net.jan.moddirector.core.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientHttpTest {

    @Test
    void followsRelativeRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/start");
            try (WebGetResponse response = WebClient.get(url)) {
                assertEquals("ok", read(response.getInputStream()));
            }
        } finally {
            server.stop(0);
        }
    }

    private static String read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void rejectsHttpErrorStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> {
            byte[] body = "missing".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/missing");
            IOException exception = assertThrows(IOException.class, () -> WebClient.get(url));
            assertTrue(exception.getMessage().contains("404"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsRedirectWithoutLocation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/broken");
            IOException exception = assertThrows(IOException.class, () -> WebClient.get(url));
            assertTrue(exception.getMessage().contains("Location"));
        } finally {
            server.stop(0);
        }
    }
}
