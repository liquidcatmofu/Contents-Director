package net.jan.moddirector.core.configuration;

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
        String configName = remoteConfigFileName(remoteConfig.getUrl());
        if (!activeRemoteUrls.add(remoteUrl)) {
            throw new IOException("Remote configuration cycle detected at " + remoteUrl);
        }

        try (WebGetResponse response = WebClient.get(remoteConfig.getUrl())) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            IOOperation.copy(response.getInputStream(), outputStream);

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

        List<ModifyPlan> modifyPlans = new ArrayList<>();
        for (ModifyMod modifyMod : modifyMods) {
            modifyPlans.add(resolveModifyPlan(modifyMod));
        }

        configurations.addAll(curseMods);
        configurations.addAll(modrinthMods);
        configurations.addAll(urlMods);
        for (ModifyPlan modifyPlan : modifyPlans) {
            applyModifyPlanSafely(modifyPlan);
        }
    }

    private void handleModifyConfig(ModifyMod modifyMod) {
        try {
            applyModifyPlanSafely(resolveModifyPlan(modifyMod));
        } catch (IOException e) {
            handleModifyException(e);
        }
    }

    private ModifyPlan resolveModifyPlan(ModifyMod modifyMod) throws IOException {
        if (modifyMod.getFolder() == null) {
            throw new IOException("Modify configuration folder is missing");
        }

        Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
        Path folderPath = resolveModificationPath(
            installationRoot,
            installationRoot,
            modifyMod.getFolder()
        );

        Path configuredNewFolder = null;
        if (modifyMod.getNewFolder() != null) {
            configuredNewFolder = resolveModificationPath(
                installationRoot,
                installationRoot,
                modifyMod.getNewFolder()
            );
        }

        Path filePath = null;
        if (modifyMod.getFileName() != null) {
            filePath = resolveModificationPath(
                installationRoot,
                folderPath,
                modifyMod.getFileName()
            );
        }

        // Validate every configured destination path before any bundle mutation starts,
        // even when a flag such as delete/disable means the destination will not be used.
        if (modifyMod.getNewFileName() != null) {
            Path validationBase = configuredNewFolder != null
                ? configuredNewFolder
                : filePath != null ? filePath.getParent() : folderPath;
            resolveModificationPath(
                installationRoot,
                validationBase,
                modifyMod.getNewFileName()
            );
        }

        Path disabledPath = null;
        if (filePath != null && modifyMod.shouldDisable()) {
            disabledPath = resolveModificationPath(
                installationRoot,
                filePath.getParent(),
                filePath.getFileName().toString() + ".disabled-by-mod-director"
            );
        }

        Path destinationPath = null;
        Path destinationDisabledPath = null;
        if (filePath != null && !modifyMod.shouldDisable() && !modifyMod.shouldDelete()) {
            if (configuredNewFolder != null) {
                destinationPath = resolveModificationPath(
                    installationRoot,
                    configuredNewFolder,
                    modifyMod.getFileName()
                );
            }

            if (modifyMod.getNewFileName() != null) {
                Path destinationParent = destinationPath != null
                    ? destinationPath.getParent()
                    : filePath.getParent();
                destinationPath = resolveModificationPath(
                    installationRoot,
                    destinationParent,
                    modifyMod.getNewFileName()
                );
            }

            if (destinationPath != null) {
                destinationDisabledPath = resolveModificationPath(
                    installationRoot,
                    destinationPath.getParent(),
                    destinationPath.getFileName().toString() + ".disabled-by-mod-director"
                );
            }
        }

        return new ModifyPlan(
            modifyMod,
            folderPath,
            filePath,
            disabledPath,
            destinationPath,
            destinationDisabledPath
        );
    }

    private void applyModifyPlanSafely(ModifyPlan modifyPlan) {
        try {
            applyModifyPlan(modifyPlan);
        } catch (IOException | UncheckedIOException e) {
            handleModifyException(e);
        }
    }

    private void applyModifyPlan(ModifyPlan plan) throws IOException {
        ModifyMod modifyMod = plan.modifyMod;

        if (plan.filePath == null) {
            if (Files.isDirectory(plan.folderPath) && modifyMod.shouldDelete()) {
                director.getLogger().info("Deleting folder {0}", plan.folderPath);
                try (Stream<Path> paths = Files.walk(plan.folderPath)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            }
            return;
        }

        if (!Files.isRegularFile(plan.filePath)) {
            return;
        }

        if (modifyMod.shouldDisable()) {
            director.getLogger().info("Disabling file {0}", plan.filePath);
            Files.move(plan.filePath, plan.disabledPath);
            return;
        }

        if (modifyMod.shouldDelete()) {
            director.getLogger().info("Deleting file {0}", plan.filePath);
            Files.delete(plan.filePath);
            return;
        }

        if (plan.destinationPath == null) {
            return;
        }

        if (modifyMod.getNewFolder() != null) {
            director.getLogger().info("Moving file {0}", plan.filePath);
        }
        if (modifyMod.getNewFileName() != null) {
            director.getLogger().info("Renaming file {0}", plan.filePath);
        }

        Files.createDirectories(plan.destinationPath.getParent());
        if (Files.exists(plan.destinationPath)) {
            if (Files.exists(plan.destinationDisabledPath)) {
                Files.delete(plan.destinationDisabledPath);
            }
            Files.move(plan.destinationPath, plan.destinationDisabledPath);
        }
        Files.move(plan.filePath, plan.destinationPath);
    }

    private static final class ModifyPlan {
        private final ModifyMod modifyMod;
        private final Path folderPath;
        private final Path filePath;
        private final Path disabledPath;
        private final Path destinationPath;
        private final Path destinationDisabledPath;

        private ModifyPlan(
            ModifyMod modifyMod,
            Path folderPath,
            Path filePath,
            Path disabledPath,
            Path destinationPath,
            Path destinationDisabledPath
        ) {
            this.modifyMod = modifyMod;
            this.folderPath = folderPath;
            this.filePath = filePath;
            this.disabledPath = disabledPath;
            this.destinationPath = destinationPath;
            this.destinationDisabledPath = destinationDisabledPath;
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
        director.getLogger().error("Failed to load configuration!", e);
        director.addError(new ModDirectorError(
            Level.SEVERE,
            "Failed to load configuration: " + e.getMessage(),
            e
        ));
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
