package net.jan.moddirector.core.manage.install;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * installation until {@link #commit(boolean)} is called.
 */
public final class InstallStagingArea implements AutoCloseable {
    private final Path targetFile;
    private final Path targetDirectory;
    private final Path stagingDirectory;
    private final Path stagedTarget;
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

    public void commit(boolean commitPrimaryFile) throws IOException {
        if (commitPrimaryFile && !Files.isRegularFile(stagedTarget)) {
            throw new IOException("Staged primary file does not exist: " + stagedTarget);
        }

        List<Path> directories;
        List<Path> files;
        try (Stream<Path> paths = Files.walk(stagingDirectory)) {
            List<Path> all = paths.collect(Collectors.toList());
            directories = all.stream()
                .filter(path -> !path.equals(stagingDirectory))
                .filter(Files::isDirectory)
                .sorted(Comparator.comparingInt(Path::getNameCount))
                .collect(Collectors.toList());
            files = all.stream()
                .filter(Files::isRegularFile)
                .collect(Collectors.toCollection(ArrayList::new));
        }

        for (Path directory : directories) {
            Path destination = resolveDestination(stagingDirectory.relativize(directory));
            Files.createDirectories(destination);
        }

        // Publish the primary file first. Supersede/Bansoukou cleanup happens only after
        // the whole staging area has committed successfully.
        if (commitPrimaryFile) {
            publishFile(stagedTarget, targetFile, false);
            files.remove(stagedTarget);
        } else {
            files.remove(stagedTarget);
        }

        for (Path stagedFile : files) {
            Path relative = stagingDirectory.relativize(stagedFile);
            Path destination = resolveDestination(relative);
            publishFile(stagedFile, destination, true);
        }

        committed = true;
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

    private void publishFile(Path stagedFile, Path destination, boolean preserveExisting) throws IOException {
        Files.createDirectories(destination.getParent());

        if (preserveExisting && Files.exists(destination)) {
            if (!Files.isRegularFile(destination)) {
                throw new IOException("Cannot replace non-regular extracted path: " + destination);
            }

            Path disabled = destination.resolveSibling(
                destination.getFileName().toString() + ".disabled-by-mod-director"
            );
            Files.deleteIfExists(disabled);
            Files.move(destination, disabled);
        }

        InstallTransaction.replaceStagedFile(stagedFile, destination, Files::move);
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(stagingDirectory)) {
            return;
        }

        IOException failure = null;
        try (Stream<Path> paths = Files.walk(stagingDirectory)) {
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
        }

        if (failure != null) {
            throw failure;
        }
    }
}
