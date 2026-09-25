package com.juanmuscaria.modpackdirector.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class DownloadDirectories {
    private DownloadDirectories() {
    }

    static List<Path> resolve() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.trim().isEmpty()) {
            return Collections.emptyList();
        }

        Path home = Paths.get(userHome).toAbsolutePath().normalize();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path xdgConfigHome = null;

        if (!osName.contains("win")) {
            String configured = System.getenv("XDG_CONFIG_HOME");
            if (configured != null && !configured.trim().isEmpty()) {
                xdgConfigHome = Paths.get(configured).toAbsolutePath().normalize();
            } else {
                xdgConfigHome = home.resolve(".config");
            }
        }

        return resolve(home, xdgConfigHome);
    }

    static List<Path> resolve(Path home, Path xdgConfigHome) {
        Set<Path> directories = new LinkedHashSet<>();

        Path xdgDownload = readXdgDownloadDirectory(home, xdgConfigHome);
        if (xdgDownload != null) {
            directories.add(xdgDownload.toAbsolutePath().normalize());
        }

        directories.add(home.resolve("Downloads").toAbsolutePath().normalize());
        return new ArrayList<>(directories);
    }

    static Path readXdgDownloadDirectory(Path home, Path xdgConfigHome) {
        if (xdgConfigHome == null) {
            return null;
        }

        Path config = xdgConfigHome.resolve("user-dirs.dirs");
        if (!Files.isRegularFile(config)) {
            return null;
        }

        try {
            for (String rawLine : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (!line.startsWith("XDG_DOWNLOAD_DIR=")) {
                    continue;
                }

                String value = line.substring("XDG_DOWNLOAD_DIR=".length()).trim();
                value = unquote(value);
                value = value.replace("${HOME}", home.toString());
                value = value.replace("$HOME", home.toString());
                value = unescape(value);

                if (value.isEmpty()) {
                    return null;
                }

                Path path = Paths.get(value);
                if (!path.isAbsolute()) {
                    path = home.resolve(path);
                }
                return path.normalize();
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }

        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == '\\' || next == '"' || next == 36 || next == '`') {
                    result.append(next);
                    i++;
                    continue;
                }
            }

            result.append(current);
        }

        return result.toString();
    }
}
