package net.jan.moddirector.core.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juanmuscaria.modpackdirector.ModpackDirector;
import lombok.Getter;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.configuration.type.*;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.util.IOOperation;
import net.jan.moddirector.core.util.JacksonProvider;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ConfigurationController {
    public static final ObjectMapper OBJECT_MAPPER = JacksonProvider.getObjectMapper();
    private static final int MAX_REMOTE_CONFIG_DEPTH = 16;
    private final ModpackDirector director;
    private final Path configurationDirectory;
    @Getter
    private final List<ModDirectorRemoteMod> configurations;
    @Getter
    private ModpackConfiguration modpackConfiguration;

    public ConfigurationController(ModpackDirector director, Path configurationDirectory) {
        this.director = director;
        this.configurationDirectory = configurationDirectory;
        this.configurations = new ArrayList<>();
    }



    public void load() {
        Path modpackConfigPath = configurationDirectory.resolve("modpack.json");
        if (Files.exists(modpackConfigPath) && !loadModpackConfiguration(modpackConfigPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(configurationDirectory)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equals("modpack.json"))
                .sorted()
                .forEach(this::addConfig);
        } catch (IOException e) {
            director.getLogger().error("Failed to iterate configuration directory!", e);
            director.addError(new ModDirectorError(Level.SEVERE,
                "Failed to iterate configuration directory", e));
        }
    }

    private boolean loadModpackConfiguration(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            modpackConfiguration = OBJECT_MAPPER.readValue(stream, ModpackConfiguration.class);
            return true;
        } catch (IOException e) {
            director.getLogger().error("Failed to read modpack configuration!", e);
            director.addError(new ModDirectorError(Level.SEVERE,
                "Failed to read modpack configuration!"));
            return false;
        }
    }

    private void addConfig(Path configurationPath) {
        String configName = configurationPath.toString();
        director.getLogger().info("Loading config {0}", configName);

        try (InputStream stream = Files.newInputStream(configurationPath)) {
            addConfig(configName, stream, 0, new HashSet<>());
        } catch (IOException | RuntimeException e) {
            handleConfigException(e);
        }
    }

    private void addConfig(
        String configName,
        InputStream stream,
        int remoteDepth,
        Set<String> activeRemoteUrls
    ) throws IOException {
        if (configName.endsWith(".remote.json")) {
            RemoteConfig remoteConfig = OBJECT_MAPPER.readValue(stream, RemoteConfig.class);
            handleRemoteConfig(remoteConfig, remoteDepth, activeRemoteUrls);
            return;
        }

        if (configName.endsWith(".bundle.json")) {
            handleBundleConfig(stream);
            return;
        }

        if (configName.endsWith(".modify.json")) {
            ModifyMod modifyMod = OBJECT_MAPPER.readValue(stream, ModifyMod.class);
            handleModifyConfig(modifyMod);
            return;
        }

        Class<? extends ModDirectorRemoteMod> targetType = getTypeForFileName(configName);
        if (targetType != null) {
            configurations.add(OBJECT_MAPPER.readValue(stream, targetType));
        } else {
            director.getLogger().warn("Ignoring unknown json file {0}", configName);
        }
    }

    private void handleRemoteConfig(
        RemoteConfig remoteConfig,
        int depth,
        Set<String> activeRemoteUrls
    ) throws IOException {
        if (remoteConfig.getUrl() == null) {
            throw new IOException("Remote configuration URL is missing");
        }
        if (depth >= MAX_REMOTE_CONFIG_DEPTH) {
            throw new IOException("Remote configuration nesting exceeds " + MAX_REMOTE_CONFIG_DEPTH + " levels");
        }

        String remoteUrl = remoteConfig.getUrl().toExternalForm();
        if (!activeRemoteUrls.add(remoteUrl)) {
            throw new IOException("Remote configuration cycle detected at " + remoteUrl);
        }

        try (WebGetResponse response = WebClient.get(remoteConfig.getUrl())) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            IOOperation.copy(response.getInputStream(), outputStream);

            String configName = remoteConfigFileName(remoteConfig.getUrl());
            director.getLogger().info("Loading remote config {0}", remoteUrl);
            try (InputStream downloaded = new ByteArrayInputStream(outputStream.toByteArray())) {
                addConfig(configName, downloaded, depth + 1, activeRemoteUrls);
            }
        } finally {
            activeRemoteUrls.remove(remoteUrl);
        }
    }

    static String remoteConfigFileName(java.net.URL url) throws IOException {
        String path = url.getPath();
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            throw new IOException("Remote configuration URL does not identify a file: " + url);
        }

        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        if (fileName.isEmpty()) {
            throw new IOException("Remote configuration URL does not identify a file: " + url);
        }
        return fileName;
    }

    private void handleBundleConfig(InputStream stream) throws IOException {
        JsonNode jsonTree;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            jsonTree = OBJECT_MAPPER.readTree(reader);
        }

        List<CurseRemoteMod> curseMods = new ArrayList<>();
        List<ModrinthRemoteMod> modrinthMods = new ArrayList<>();
        List<UrlRemoteMod> urlMods = new ArrayList<>();
        List<ModifyMod> modifyMods = new ArrayList<>();

        try {
            JsonNode jsonArray = jsonTree.get("curse");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    curseMods.add(OBJECT_MAPPER.convertValue(jsonNode, CurseRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modrinth");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    modrinthMods.add(OBJECT_MAPPER.convertValue(jsonNode, ModrinthRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("url");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    urlMods.add(OBJECT_MAPPER.convertValue(jsonNode, UrlRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modify");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    modifyMods.add(OBJECT_MAPPER.convertValue(jsonNode, ModifyMod.class));
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to parse bundle configuration", e);
        }

        configurations.addAll(curseMods);
        configurations.addAll(modrinthMods);
        configurations.addAll(urlMods);
        for (ModifyMod modifyMod : modifyMods) {
            handleModifyConfig(modifyMod);
        }
    }

    private void handleModifyConfig(ModifyMod modifyMod) {
        try {
            Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
            Path modifyModFolderPath = resolveModificationPath(installationRoot, installationRoot, modifyMod.getFolder());

            if (modifyMod.getFileName() == null) {
                if (Files.isDirectory(modifyModFolderPath) && modifyMod.shouldDelete()) {
                    director.getLogger().info("Deleting folder {0}", modifyModFolderPath);
                    try (Stream<Path> paths = Files.walk(modifyModFolderPath)) {
                        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                }
            } else {
                Path modifyModFilePath = resolveModificationPath(
                    installationRoot, modifyModFolderPath, modifyMod.getFileName());

                if (Files.isRegularFile(modifyModFilePath)) {
                    if (modifyMod.shouldDisable()) {
                        director.getLogger().info("Disabling file {0}", modifyModFilePath);
                        Path disabledFilePath = resolveModificationPath(
                            installationRoot,
                            modifyModFilePath.getParent(),
                            modifyModFilePath.getFileName().toString() + ".disabled-by-mod-director");
                        Files.move(modifyModFilePath, disabledFilePath);
                    } else if (modifyMod.shouldDelete()) {
                        director.getLogger().info("Deleting file {0}", modifyModFilePath);
                        Files.delete(modifyModFilePath);
                    } else {
                        Path modifyModNewFilePath = null;
                        if (modifyMod.getNewFolder() != null) {
                            director.getLogger().info("Moving file {0}", modifyModFilePath);
                            Path newFolderPath = resolveModificationPath(
                                installationRoot, installationRoot, modifyMod.getNewFolder());
                            Files.createDirectories(newFolderPath);
                            modifyModNewFilePath = resolveModificationPath(
                                installationRoot, newFolderPath, modifyMod.getFileName());
                        }
                        if (modifyMod.getNewFileName() != null) {
                            director.getLogger().info("Renaming file {0}", modifyModFilePath);
                            Path destinationParent = modifyModNewFilePath != null
                                ? modifyModNewFilePath.getParent()
                                : modifyModFilePath.getParent();
                            modifyModNewFilePath = resolveModificationPath(
                                installationRoot, destinationParent, modifyMod.getNewFileName());
                        }
                        if (modifyModNewFilePath != null) {
                            Files.createDirectories(modifyModNewFilePath.getParent());
                            if (Files.exists(modifyModNewFilePath)) {
                                Path disabledFilePath = resolveModificationPath(
                                    installationRoot,
                                    modifyModNewFilePath.getParent(),
                                    modifyModNewFilePath.getFileName().toString() + ".disabled-by-mod-director");
                                if (Files.exists(disabledFilePath)) {
                                    Files.delete(disabledFilePath);
                                }
                                Files.move(modifyModNewFilePath, disabledFilePath);
                            }
                            Files.move(modifyModFilePath, modifyModNewFilePath);
                        }
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            handleModifyException(e);
        }
    }

    static Path resolveModificationPath(Path installationRoot, Path base, String configuredPath) throws IOException {
        Path normalizedRoot = installationRoot.toAbsolutePath().normalize();
        Path normalizedBase = base.toAbsolutePath().normalize();

        if (!normalizedBase.startsWith(normalizedRoot)) {
            throw new IOException("Modify path base is outside the installation root: " + base);
        }

        final Path candidate;
        try {
            candidate = normalizedBase.resolve(configuredPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Invalid path in modify configuration: " + configuredPath, e);
        }

        if (!candidate.startsWith(normalizedRoot)) {
            throw new IOException("Modify path escapes the installation root: " + configuredPath);
        }

        Path current = normalizedRoot;
        Path relative = normalizedRoot.relativize(candidate);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Modify path traverses symbolic link: " + configuredPath);
            }
        }

        return candidate;
    }

    private void handleModifyException(Exception e) {
        director.getLogger().error("Failed to apply modify configuration!", e);
        director.addError(new ModDirectorError(Level.SEVERE,
            "Failed to apply modify configuration: " + e.getMessage(), e));
    }

    private void handleConfigException(Exception e) {
        director.getLogger().error("Failed to {0} a configuration for reading!", (e instanceof JsonParseException ? "parse" : "open"), e);
        director.addError(new ModDirectorError(Level.SEVERE,
            "Failed to " + (e instanceof JsonParseException ? "parse" : "open") + " a configuration for reading", e));
    }

    private Class<? extends ModDirectorRemoteMod> getTypeForFileName(String name) {
        if (name.endsWith(".curse.json")) {
            return CurseRemoteMod.class;
        } else if (name.endsWith(".modrinth.json")) {
            return ModrinthRemoteMod.class;
        } else if (name.endsWith(".url.json")) {
            return UrlRemoteMod.class;
        }
        return null;
    }
}
