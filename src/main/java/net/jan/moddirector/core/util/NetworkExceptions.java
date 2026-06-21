package net.jan.moddirector.core.util;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Helper to turn raw network/IO exceptions into human-readable explanations.
 * <p>
 * Mod Director performs its work very early during launch, where a bare stack trace of an
 * {@link java.io.IOException} gives the user no clue that the real problem is simply a broken
 * internet connection. These helpers inspect the exception chain and produce a message that points
 * the user at the actual cause.
 */
public final class NetworkExceptions {
    private NetworkExceptions() {
    }

    /**
     * @return {@code true} if the given throwable (or any of its causes) looks like a connectivity
     * problem rather than a logic/server error.
     */
    public static boolean isConnectivityError(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException
                || t instanceof ConnectException
                || t instanceof NoRouteToHostException
                || t instanceof SocketTimeoutException
                || t instanceof SSLException
                || t instanceof SocketException) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    /**
     * Produces a short, user-facing explanation of the given throwable, geared towards network
     * problems. Falls back to the exception message for non-network errors.
     */
    public static String describe(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) {
                return "Could not resolve host \"" + t.getMessage() + "\". "
                    + "Please check your internet connection or DNS settings.";
            }
            if (t instanceof SocketTimeoutException) {
                return "The connection timed out. The server may be unreachable or your internet "
                    + "connection may be too slow/unstable.";
            }
            if (t instanceof ConnectException) {
                return "Could not connect to the server (" + t.getMessage() + "). "
                    + "Please check your internet connection.";
            }
            if (t instanceof NoRouteToHostException) {
                return "No route to host (" + t.getMessage() + "). "
                    + "Please check your internet connection or firewall settings.";
            }
            if (t instanceof SSLException) {
                return "A secure connection could not be established (" + t.getMessage() + "). "
                    + "This may be caused by an outdated system, a proxy, or antivirus interference.";
            }
            if (t instanceof SocketException) {
                return "The network connection was interrupted (" + t.getMessage() + "). "
                    + "Please check your internet connection.";
            }
            if (t == t.getCause()) {
                break;
            }
        }

        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
