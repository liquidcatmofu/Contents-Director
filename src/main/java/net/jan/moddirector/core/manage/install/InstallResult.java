package net.jan.moddirector.core.manage.install;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class InstallResult {
    private final InstallableMod installableMod;
    private final Set<Path> publishedFiles;

    public InstallResult(InstallableMod installableMod, Set<Path> publishedFiles) {
        this.installableMod = installableMod;
        this.publishedFiles = Collections.unmodifiableSet(new HashSet<>(publishedFiles));
    }

    public InstallableMod getInstallableMod() {
        return installableMod;
    }

    public Set<Path> getPublishedFiles() {
        return publishedFiles;
    }
}
