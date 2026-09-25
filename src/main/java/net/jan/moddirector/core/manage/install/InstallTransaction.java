package net.jan.moddirector.core.manage.install;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class InstallTransaction implements AutoCloseable {
    private final Path targetFile;
    private final Path stagedFile;
    private boolean committed;

    private InstallTransaction(Path targetFile, Path stagedFile) {
        this.targetFile = targetFile;
        this.stagedFile = stagedFile;
    }

    public static InstallTransaction create(Path targetFile) throws IOException {
        Path absoluteTarget = targetFile.toAbsolutePath().normalize();
        Path parent = absoluteTarget.getParent();
        if (parent == null) {
            throw new IOException("Install target has no parent directory: " + targetFile);
        }

        Files.createDirectories(parent);
        Path stagedFile = Files.createTempFile(parent, ".mod-director-", ".download");
        return new InstallTransaction(absoluteTarget, stagedFile);
    }

    public Path stagedFile() {
        return stagedFile;
    }

    public void commit() throws IOException {
        try {
            Files.move(stagedFile, targetFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(stagedFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        committed = true;
    }

    @Override
    public void close() throws IOException {
        if (!committed) {
            Files.deleteIfExists(stagedFile);
        }
    }
}
