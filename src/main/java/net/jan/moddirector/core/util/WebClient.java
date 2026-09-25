package net.jan.moddirector.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class WebClient {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36";
    /** Maximum time to wait while establishing a connection, in milliseconds. */
    public static final int CONNECT_TIMEOUT = 15_000;
    /** Maximum time to wait between data packets while reading, in milliseconds. */
    public static final int READ_TIMEOUT = 30_000;
    private static final int MAX_REDIRECTS = 10;

    public static WebGetResponse get(URL url) throws IOException {
        URL currentUrl = url;
        String cookies = null;
        int redirectCount = 0;

        while (true) {
            URLConnection connection = currentUrl.openConnection();
            applyTimeouts(connection);

            if (!(connection instanceof HttpURLConnection)) {
                return new WebGetResponse(connection.getInputStream(), connection.getContentLengthLong());
            }

            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setInstanceFollowRedirects(false);
            httpConnection.setRequestProperty("User-Agent", USER_AGENT);
            if (cookies != null) {
                httpConnection.setRequestProperty("Cookie", cookies);
            }
            httpConnection.connect();

            int status = httpConnection.getResponseCode();
            if (status >= 300 && status <= 399) {
                if (redirectCount >= MAX_REDIRECTS) {
                    httpConnection.disconnect();
                    throw new IOException("Server tried to redirect too many times");
                }

                String location = httpConnection.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) {
                    httpConnection.disconnect();
                    throw new IOException("Server returned redirect without a Location header");
                }

                String newCookies = httpConnection.getHeaderField("Set-Cookie");
                if (newCookies != null) {
                    cookies = newCookies;
                }

                final URL nextUrl;
                try {
                    nextUrl = new URL(currentUrl, location);
                } catch (MalformedURLException e) {
                    httpConnection.disconnect();
                    throw new IOException("Server sent invalid redirect url", e);
                }

                String protocol = nextUrl.getProtocol();
                if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                    httpConnection.disconnect();
                    throw new IOException("Server sent a redirect url which was not http: " + location);
                }

                httpConnection.disconnect();
                currentUrl = nextUrl;
                redirectCount++;
                continue;
            }

            if (status < 200 || status >= 300) {
                InputStream errorStream = httpConnection.getErrorStream();
                if (errorStream != null) {
                    errorStream.close();
                }
                httpConnection.disconnect();
                throw new IOException("Server returned HTTP status " + status + " for " + currentUrl);
            }

            return new WebGetResponse(httpConnection.getInputStream(), httpConnection.getContentLengthLong());
        }
    }

    private static void applyTimeouts(URLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
    }
}
