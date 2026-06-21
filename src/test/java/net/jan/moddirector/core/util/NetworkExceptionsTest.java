package net.jan.moddirector.core.util;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkExceptionsTest {

    // --- isConnectivityError ---

    @Test
    void unknownHostIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new UnknownHostException("example.com")));
    }

    @Test
    void connectExceptionIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new ConnectException("Connection refused")));
    }

    @Test
    void noRouteIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new NoRouteToHostException("unreachable")));
    }

    @Test
    void socketTimeoutIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new SocketTimeoutException("timed out")));
    }

    @Test
    void sslExceptionIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new SSLException("handshake failed")));
    }

    @Test
    void socketExceptionIsConnectivity() {
        assertTrue(NetworkExceptions.isConnectivityError(new SocketException("reset")));
    }

    @Test
    void wrappedUnknownHostIsConnectivity() {
        IOException wrapper = new IOException("Failed to download", new UnknownHostException("example.com"));
        assertTrue(NetworkExceptions.isConnectivityError(wrapper));
    }

    @Test
    void wrappedConnectExceptionIsConnectivity() {
        RuntimeException wrapper = new RuntimeException("install failed", new ConnectException("refused"));
        assertTrue(NetworkExceptions.isConnectivityError(wrapper));
    }

    @Test
    void plainIoExceptionIsNotConnectivity() {
        assertFalse(NetworkExceptions.isConnectivityError(new IOException("file not found")));
    }

    @Test
    void nullMessageExceptionIsNotConnectivity() {
        assertFalse(NetworkExceptions.isConnectivityError(new IOException()));
    }

    @Test
    void illegalArgumentIsNotConnectivity() {
        assertFalse(NetworkExceptions.isConnectivityError(new IllegalArgumentException("bad arg")));
    }

    // --- describe ---

    @Test
    void describeUnknownHost() {
        String msg = NetworkExceptions.describe(new UnknownHostException("example.com"));
        assertTrue(msg.contains("example.com"), "should mention the host: " + msg);
        assertTrue(msg.toLowerCase().contains("resolv") || msg.toLowerCase().contains("dns"),
            "should mention DNS or resolve: " + msg);
    }

    @Test
    void describeSocketTimeout() {
        String msg = NetworkExceptions.describe(new SocketTimeoutException("timed out"));
        assertTrue(msg.toLowerCase().contains("time"), "should mention timeout: " + msg);
    }

    @Test
    void describeConnectException() {
        String msg = NetworkExceptions.describe(new ConnectException("Connection refused: connect"));
        assertTrue(msg.toLowerCase().contains("connect"), "should mention connect: " + msg);
    }

    @Test
    void describeWrappedCause() {
        IOException wrapper = new IOException("outer", new UnknownHostException("cdn.example.com"));
        String msg = NetworkExceptions.describe(wrapper);
        assertTrue(msg.contains("cdn.example.com"), "should drill into cause: " + msg);
    }

    @Test
    void describeFallbackForPlainIoException() {
        IOException e = new IOException("something weird happened");
        String msg = NetworkExceptions.describe(e);
        assertNotNull(msg);
        assertFalse(msg.isEmpty());
    }

    @Test
    void describeFallbackWhenMessageIsNull() {
        IOException e = new IOException();
        String msg = NetworkExceptions.describe(e);
        assertNotNull(msg);
        assertFalse(msg.isEmpty());
    }
}
