package net.jan.moddirector.core.manage.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Common staging boundary for every install backend.
 *
 * Backends write to {@link #stagedTarget()} and may create additional files below the
 * staging directory (for example archive extraction). Nothing is published to the live
 * installation until {@link #commit(boolean, boolean)} is called.
 */
public final class InstallStagingArea implements AutoCloseable {
    private final Path targetFile;
    private final Path targetDirectory;
    private final Path stagingDirectory;
    private final Path stagedTarget;
    private Path rollbackDirectory;
    private boolean committed;

    private InstallStagingArea(
        Path targetFile,
        Path targetDirectory,
        Path stagingDirectory,
        Path stagedTarget
    ) {
        this.targetFile = targetFile;
        this.targetDirectory = targetDirectory;
        this.stagingDirectory = stagingDirectory;
        this.stagedTarget = stagedTarget;
    }

    public static InstallStagingArea create(Path targetFile) throws IOException {
        Path absoluteTarget = targetFile.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Install target has no parent directory: " + targetFile);
        }

        Files.createDirectories(parent);
        Path stagingDirectory = Files.createTempDirectory(parent, ".mod-director-stage-");
        return new InstallStagingArea(
            absoluteTarget,
            parent,
            stagingDirectory,
            stagingDirectory.resolve(absoluteTarget.getFileName())
        );
    }

    public Path stagedTarget() {
        return stagedTarget;
    }

    public void commit(boolean commitPrimaryFile, boolean deletePrimaryFile) throws IOException {
        if (commitPrimaryFile && deletePrimaryFile) {
            throw new IllegalArgumentException("Primary file cannot be both committed and deleted");
        }

        List<Path> stagedDirectories;
        List<Path> stagedFiles;
        try (Stream<Path> paths = Files.walk(stagingDirectory)) {
            List<Path> all = paths.collect(Collectors.toList());
            stagedDirectories = all.stream()
                .filter(path -> !path.equals(stagingDirectory))
                .filter(Files::isDirectory)
                .sorted(Comparator.comparingInt(Path::getNameCount))
                .collect(Collectors.toList());
            stagedFiles = all.stream()
                .filter(Files::isRegularFile)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        if (commitPrimaryFile && !stagedFiles.contains(stagedTarget)) {
            throw new IOException("Staged primary file does not exist: " + stagedTarget);
        }

        List<Path> destinationDirectories = new ArrayList<>();
        for (Path directory : stagedDirectories) {
            destinationDirectories.add(resolveDestination(stagingDirectory.relativize(directory)));
        }

        List<PublishEntry> entries = new ArrayList<>();
        if (commitPrimaryFile) {
            entries.add(new PublishEntry(stagedTarget, targetFile, false, false));
        } else if (deletePrimaryFile) {
            entries.add(new PublishEntry(null, targetFile, false, true));
        }
        stagedFiles.remove(stagedTarget);

        for (Path stagedFile : stagedFiles) {
            Path relative = stagingDirectory.relativize(stagedFile);
            entries.add(new PublishEntry(stagedFile, resolveDestination(relative), true, false));
        }

        rollbackDirectory = Files.createTempDirectory(targetDirectory, ".mod-director-rollback-");
        try {
            prepareBackups(entries, rollbackDirectory);

            for (Path directory : destinationDirectories) {
                Files.createDirectories(directory);
            }

            for (PublishEntry entry : entries) {
                if (entry.deleteOnly) {
                    entry.published = true;
                    continue;
                }

                Files.createDirectories(entry.destination.getParent());
                InstallTransaction.replaceStagedFile(entry.stagedFile, entry.destination, Files::move);
                entry.published = true;
            }

            // Preserve the previous version of extracted files using the historical
            // .disabled-by-mod-director convention. Keep rollback backups until every
            // copy succeeds so a commit failure can restore the complete old state.
            for (PublishEntry entry : entries) {
                if (entry.preserveExisting && entry.previousFile != null) {
                    Path disabled = disabledPath(entry.destination);
                    Files.copy(
                        entry.previousFile,
                        disabled,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                    entry.disabledReplacementWritten = true;
                }
            }

            committed = true;
        } catch (IOException commitFailure) {
            IOException rollbackFailure = rollback(entries);
            if (rollbackFailure != null) {
                commitFailure.addSuppressed(rollbackFailure);
            }
            throw commitFailure;
        }
    }

    private void prepareBackups(List<PublishEntry> entries, Path rollbackDirectory) throws IOException {
        int index = 0;
        for (PublishEntry entry : entries) {
            if (Files.exists(entry.destination)) {
                if (!Files.isRegularFile(entry.destination)) {
                    throw new IOException("Cannot replace non-regular path: " + entry.destination);
                }

                Path previousFile = rollbackDirectory.resolve("old-" + index);
                Files.move(entry.destination, previousFile);
                entry.previousFile = previousFile;
            }

            if (entry.preserveExisting && entry.previousFile != null) {
                Path disabled = disabledPath(entry.destination);
                if (Files.exists(disabled)) {
                    if (!Files.isRegularFile(disabled)) {
                        throw new IOException("Cannot replace non-regular disabled path: " + disabled);
                    }

                    Path previousDisabledFile = rollbackDirectory.resolve("disabled-" + index);
                    Files.move(disabled, previousDisabledFile);
                    entry.previousDisabledFile = previousDisabledFile;
                }
            }

            index++;
        }
    }

    private IOException rollback(List<PublishEntry> entries) {
        IOException failure = null;

        for (int i = entries.size() - 1; i >= 0; i--) {
            PublishEntry entry = entries.get(i);

            try {
                if (entry.published && !entry.deleteOnly) {
                    Files.deleteIfExists(entry.destination);
                }

                Path disabled = disabledPath(entry.destination);
                if (entry.disabledReplacementWritten) {
                    Files.deleteIfExists(disabled);
                }

                if (entry.previousFile != null && Files.exists(entry.previousFile)) {
                    Files.createDirectories(entry.destination.getParent());
                    Files.move(entry.previousFile, entry.destination, StandardCopyOption.REPLACE_EXISTING);
                }

                if (entry.previousDisabledFile != null && Files.exists(entry.previousDisabledFile)) {
                    Files.move(
                        entry.previousDisabledFile,
                        disabled,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        return failure;
    }

    private Path resolveDestination(Path relative) throws IOException {
        Path destination = targetDirectory.resolve(relative).normalize();
        if (destination.equals(targetDirectory) || !destination.startsWith(targetDirectory)) {
            throw new IOException("Staged path escapes installation target directory: " + relative);
        }

        Path current = targetDirectory;
        for (Path component : targetDirectory.relativize(destination)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Staged path traverses symbolic link: " + relative);
            }
        }

        return destination;
    }

    private static Path disabledPath(Path destination) {
        return destination.resolveSibling(
            destination.getFileName().toString() + ".disabled-by-mod-director"
        );
    }

    @Override
    public void close() throws IOException {
        IOException failure = cleanupTree(stagingDirectory);
        IOException rollbackCleanupFailure = cleanupTree(rollbackDirectory);
        if (failure == null) {
            failure = rollbackCleanupFailure;
        } else if (rollbackCleanupFailure != null) {
            failure.addSuppressed(rollbackCleanupFailure);
        }

        // Once live commit has completed, failure to remove private temporary
        // directories is a cleanup leak rather than an installation failure.
        if (failure != null && !committed) {
            throw failure;
        }
    }

    private static IOException cleanupTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return null;
        }

        IOException failure = null;
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> cleanup = paths
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
            for (Path path : cleanup) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        } catch (IOException e) {
            failure = e;
        }
        return failure;
    }

    private static final class PublishEntry {
        private final Path stagedFile;
        private final Path destination;
        private final boolean preserveExisting;
        private final boolean deleteOnly;
        private Path previousFile;
        private Path previousDisabledFile;
        private boolean published;
        private boolean disabledReplacementWritten;

        private PublishEntry(
            Path stagedFile,
            Path destination,
            boolean preserveExisting,
            boolean deleteOnly
        ) {
            this.stagedFile = stagedFile;
            this.destination = destination;
            this.preserveExisting = preserveExisting;
            this.deleteOnly = deleteOnly;
        }
    }
}
