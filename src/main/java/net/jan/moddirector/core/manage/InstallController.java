package net.jan.moddirector.core.manage;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.InstalledMod;
import net.jan.moddirector.core.manage.install.InstallStagingArea;
import net.jan.moddirector.core.manage.install.PreInstallResult;
import net.jan.moddirector.core.util.HashResult;
import net.jan.moddirector.core.util.NetworkExceptions;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InstallController {

    private final ModpackDirector director;

    public InstallController(ModpackDirector director) {
        this.director = director;
    }

    private Level downloadSeverityLevelFor(ModDirectorRemoteMod mod) {
        return mod.getInstallationPolicy().shouldContinueOnFailedDownload() ?
            Level.WARNING : Level.SEVERE;
    }

    public List<Callable<PreInstallResult>> createPreInstallTasks(
        List<ModDirectorRemoteMod> allMods,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        return createPreInstallTasks(
            allMods,
            mod -> checkInstallationStatus(mod, callbackFactory)
        );
    }

    static List<Callable<PreInstallResult>> createPreInstallTasks(
        List<ModDirectorRemoteMod> allMods,
        Function<ModDirectorRemoteMod, PreInstallResult> planner
    ) {
        List<Callable<PreInstallResult>> preInstallTasks = new ArrayList<>();

        for (ModDirectorRemoteMod mod : allMods) {
            preInstallTasks.add(() -> planner.apply(mod));
        }

        return preInstallTasks;
    }

    private PreInstallResult checkInstallationStatus(
        ModDirectorRemoteMod mod,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        ProgressCallback callback = callbackFactory.apply(mod.offlineName(), "Checking installation status");

        try {
            callback.indeterminate(true);
            callback.message("Checking installation requirements");

            if (mod.getMetadata() != null && !mod.getMetadata().shouldTryInstall(director.platform())) {
                director.logger().debug(
                    "Skipping mod {0} because shouldTryInstall() returned false",
                    mod.offlineName()
                );
                return PreInstallResult.excluded(mod);
            }

            callback.message("Querying mod information");

            RemoteModInformation information;
            try {
                information = mod.queryInformation();
            } catch (ModDirectorException e) {
                String reason = NetworkExceptions.isConnectivityError(e)
                    ? " (" + NetworkExceptions.describe(e) + ")" : "";
                director.logger().error("Failed to query information for {0} from {1}}",
                    mod.offlineName(), mod.remoteType(), e);
                director.addError(new ModDirectorError(downloadSeverityLevelFor(mod),
                    "Failed to query information for mod " + mod.offlineName() + " from " + mod.remoteType()
                        + reason,
                    e));
                return PreInstallResult.failed(mod);
            }

            callback.title(information.displayName());
            Path targetFile = computeInstallationTargetPath(mod, information);
            if (targetFile == null) {
                return PreInstallResult.failed(mod);
            }

            Path disabledFile = computeDisabledPath(targetFile);
            if (Files.isRegularFile(disabledFile) || !isVersionCompliant(mod)) {
                return PreInstallResult.excluded(mod);
            }

            InstallableMod installableMod = new InstallableMod(mod, information, targetFile);
            Path bansoukouPatchedFile = computeBansoukouPatchedPath(targetFile);
            Path bansoukouDisabledFile = computeBansoukouDisabledPath(targetFile);

            if (mod.getMetadata() != null && (Files.isRegularFile(targetFile)
                || (Files.isRegularFile(bansoukouPatchedFile) && Files.isRegularFile(bansoukouDisabledFile)))) {
                HashResult hashResult = mod.getMetadata().checkHashes(
                    Files.isRegularFile(targetFile) ? targetFile : bansoukouDisabledFile,
                    director.platform()
                );

                switch (hashResult) {
                    case UNKNOWN:
                        director.logger().info(
                            "Skipping download of {0} as hashes can't be determined but file exists",
                            targetFile.toString()
                        );
                        return PreInstallResult.excluded(mod);

                    case MATCHED:
                        director.logger().info(
                            "Skipping download of {0} as the hashes match",
                            targetFile.toString()
                        );
                        return PreInstallResult.excluded(mod);

                    case UNMATCHED:
                        director.logger().warn(
                            "File {0} exists, but hashes do not match, downloading again!",
                            targetFile.toString()
                        );
                        return reinstallCandidate(installableMod, true);
                }
            }

            if (mod.getInstallationPolicy().shouldDownloadAlways() && Files.isRegularFile(targetFile)) {
                director.logger().info(
                    "Force downloading file {0} as download always option is set.",
                    targetFile.toString()
                );
                return reinstallCandidate(installableMod, false);
            }

            if (Files.isRegularFile(targetFile)) {
                director.logger().debug(
                    "File {0} exists and no metadata given, skipping download.",
                    targetFile.toString()
                );
                return PreInstallResult.excluded(mod);
            }

            return freshCandidate(installableMod);
        } finally {
            callback.done();
        }
    }

    private PreInstallResult freshCandidate(InstallableMod installableMod) {
        InstallableMod resolved = resolveCommitActions(installableMod, false);
        return resolved == null
            ? PreInstallResult.failed(installableMod.getRemoteMod())
            : PreInstallResult.fresh(resolved);
    }

    private PreInstallResult reinstallCandidate(InstallableMod installableMod, boolean cleanupBansoukouFiles) {
        InstallableMod resolved = resolveCommitActions(installableMod, cleanupBansoukouFiles);
        return resolved == null
            ? PreInstallResult.failed(installableMod.getRemoteMod())
            : PreInstallResult.reinstall(resolved, cleanupBansoukouFiles);
    }

    private InstallableMod resolveCommitActions(InstallableMod installableMod, boolean cleanupBansoukouFiles) {
        ModDirectorRemoteMod mod = installableMod.getRemoteMod();
        Path targetFile = installableMod.getTargetFile();
        List<String> patterns = mod.getInstallationPolicy().getAllSupersedePatterns();
        List<Path> supersededFiles = new ArrayList<>();

        if (!patterns.isEmpty()) {
            Path targetDir = targetFile.getParent();

            try {
                FileSystem fs = targetDir.getFileSystem();
                List<PathMatcher> matchers = patterns.stream()
                    .map(pattern -> fs.getPathMatcher("glob:" + pattern))
                    .collect(Collectors.toList());

                if (Files.isDirectory(targetDir)) {
                    try (Stream<Path> entries = Files.list(targetDir)) {
                        supersededFiles = entries
                            .filter(Files::isRegularFile)
                            .filter(path -> !path.equals(targetFile))
                            .filter(path -> matchers.stream().anyMatch(matcher -> matcher.matches(path.getFileName())))
                            .collect(Collectors.toList());
                    }
                }
            } catch (IOException e) {
                director.logger().warn("Failed to scan directory for superseded files {0}", targetDir, e);
            } catch (RuntimeException e) {
                director.logger().error("Invalid supersede configuration for {0}", targetFile, e);
                director.addError(new ModDirectorError(
                    Level.SEVERE,
                    "Invalid supersede configuration for " + targetFile,
                    e
                ));
                return null;
            }
        }

        return installableMod.withCommitActions(cleanupBansoukouFiles, supersededFiles);
    }

    private void applyPostInstallFilesystemChanges(
        InstallableMod installableMod,
        Set<Path> publishedFiles
    ) {
        Path targetFile = installableMod.getTargetFile();

        if (installableMod.shouldCleanupBansoukouFiles()) {
            try {
                Path patched = computeBansoukouPatchedPath(targetFile).toAbsolutePath().normalize();
                Path disabled = computeBansoukouDisabledPath(targetFile).toAbsolutePath().normalize();
                if (!publishedFiles.contains(patched)) {
                    Files.deleteIfExists(patched);
                }
                if (!publishedFiles.contains(disabled)) {
                    Files.deleteIfExists(disabled);
                }
            } catch (IOException e) {
                director.logger().error("Failed to clean up Bansoukou files for {0}", targetFile, e);
                director.addError(new ModDirectorError(
                    Level.SEVERE,
                    "Failed to clean up Bansoukou files for " + targetFile,
                    e
                ));
            }
        }

        processResolvedSupersededFiles(installableMod, publishedFiles);
    }

    private void processResolvedSupersededFiles(
        InstallableMod installableMod,
        Set<Path> publishedFiles
    ) {
        ModDirectorRemoteMod mod = installableMod.getRemoteMod();

        for (Path old : installableMod.getSupersededFiles()) {
            Path normalizedOld = old.toAbsolutePath().normalize();
            if (publishedFiles.contains(normalizedOld) || !Files.isRegularFile(normalizedOld)) {
                continue;
            }

            try {
                if (mod.getInstallationPolicy().isDeleteSuperseded()) {
                    Files.delete(normalizedOld);
                    director.logger().info("Deleted superseded file {0}", normalizedOld);
                } else {
                    Path disabled = normalizedOld.resolveSibling(
                        normalizedOld.getFileName() + ".disabled-by-mod-director"
                    );
                    Files.deleteIfExists(disabled);
                    Files.move(normalizedOld, disabled);
                    director.logger().info("Disabled superseded file {0}", normalizedOld);
                }
            } catch (IOException e) {
                director.logger().warn("Failed to process superseded file {0}", normalizedOld, e);
            }
        }
    }

    private Path computeInstallationTargetPath(ModDirectorRemoteMod mod, RemoteModInformation information) {
        Path installationRoot = director.platform().installationRoot().toAbsolutePath().normalize();

        Path targetFile = (mod.getFolder() == null ?
            director.platform().modFile(information.targetFilename())
            : mod.getFolder().equalsIgnoreCase(".") ?
            director.platform().rootFile(information.targetFilename())
            : director.platform().customFile(information.targetFilename(), mod.getFolder()))
            .toAbsolutePath().normalize();

        if (!targetFile.startsWith(installationRoot)) {
            director.logger().error("Tried to install a file to {0}, which is outside the installation root of {1}!",
                targetFile.toString(), director.platform().installationRoot());
            director.addError(new ModDirectorError(Level.SEVERE,
                "Tried to install a file to " + targetFile + ", which is outside of " +
                    "the installation root " + installationRoot));
            return null;
        }

        return targetFile;
    }

    private Path computeDisabledPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName() + ".disabled-by-mod-director");
    }

    private Path computeBansoukouPatchedPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName().toString().replace(".jar", "-patched.jar"));
    }

    private Path computeBansoukouDisabledPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName().toString().replace(".jar", ".disabled"));
    }

    private boolean isVersionCompliant(ModDirectorRemoteMod mod) {
        String versionMod = mod.getInstallationPolicy().getModpackVersion();
        String versionModpackRemote = director.getModpackRemoteVersion();

        ModpackConfiguration modpackConfiguration = director.getConfigurationController().getModpackConfiguration();
        String versionModpackLocal = null;
        if (modpackConfiguration != null) {
            versionModpackLocal = modpackConfiguration.localVersion();
        }

        if (versionMod != null) {
            if (versionModpackRemote != null) {
                return Objects.equals(versionMod, versionModpackRemote);
            } else if (versionModpackLocal != null) {
                return Objects.equals(versionMod, versionModpackLocal);
            }
        }
        return true;
    }

    public void markDisabledMods(List<InstallableMod> mods) {
        for (InstallableMod mod : mods) {
            try {
                Path disabledFile = computeDisabledPath(mod.getTargetFile());

                Files.createDirectories(disabledFile.getParent());
                Files.createFile(disabledFile);
            } catch (IOException e) {
                director.logger().warn(
                    "Failed to create disabled file, the user might be asked again if he wants to install the mod", e
                );

                director.addError(new ModDirectorError(
                    Level.WARNING,
                    "Failed to create disabled file",
                    e
                ));
            }
        }
    }

    public List<Callable<Void>> createInstallTasks(
        List<InstallableMod> mods,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        List<Callable<Void>> installTasks = new ArrayList<>();

        for (InstallableMod mod : mods) {
            installTasks.add(() -> {
                install(mod, callbackFactory.apply(mod.getRemoteInformation().targetFilename(), "Installing"));
                return null;
            });
        }

        return installTasks;
    }

    public void install(InstallableMod mod, ProgressCallback callback) {
        try {
            ModDirectorRemoteMod remoteMod = mod.getRemoteMod();

            director.logger().debug("Now handling {0} from backend {1}}", remoteMod.offlineName(), remoteMod.remoteType());

            Path targetFile = mod.getTargetFile();
            Set<Path> publishedFiles;

            try (InstallStagingArea staging = InstallStagingArea.create(targetFile)) {
                try {
                    remoteMod.performInstall(
                        staging.stagedTarget(),
                        callback,
                        director,
                        mod.getRemoteInformation()
                    );
                } catch (ModDirectorException e) {
                    String reason = NetworkExceptions.isConnectivityError(e)
                        ? " (" + NetworkExceptions.describe(e) + ")" : "";
                    director.logger().log(
                        downloadSeverityLevelFor(remoteMod),
                        "Failed to stage mod {0}",
                        remoteMod.offlineName(),
                        e
                    );
                    director.addError(new ModDirectorError(
                        downloadSeverityLevelFor(remoteMod),
                        "Failed to stage mod " + remoteMod.offlineName() + reason,
                        e
                    ));
                    return;
                }

                if (remoteMod.getMetadata() != null
                    && remoteMod.getMetadata().checkHashes(staging.stagedTarget(), director.platform())
                        == HashResult.UNMATCHED) {
                    director.logger().error("Staged mod did not match configured hash, aborting!");
                    director.addError(new ModDirectorError(
                        Level.SEVERE,
                        "Staged mod did not match configured hash"
                    ));
                    return;
                }

                staging.commit(
                    remoteMod.shouldCommitPrimaryFile(),
                    remoteMod.shouldDeletePrimaryFile()
                );
                publishedFiles = new HashSet<>(staging.publishedDestinations());
            } catch (IOException e) {
                director.logger().error("Failed to stage or commit mod {0}", remoteMod.offlineName(), e);
                director.addError(new ModDirectorError(
                    Level.SEVERE,
                    "Failed to stage or commit mod " + remoteMod.offlineName(),
                    e
                ));
                return;
            }

            applyPostInstallFilesystemChanges(mod, publishedFiles);

            if (remoteMod.getInstallationPolicy().shouldExtract()) {
                director.logger().info("Extracted mod file {0}", targetFile.toString());
            } else {
                director.logger().info("Installed mod file {0}", targetFile.toString());
            }
            director.getInstalledMods().add(
                new InstalledMod(targetFile, remoteMod.getOptions(), remoteMod.forceInject())
            );
        } finally {
            callback.done();
        }
    }
}
