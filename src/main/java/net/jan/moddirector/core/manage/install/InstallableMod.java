package net.jan.moddirector.core.manage.install;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstallableMod {
    private final ModDirectorRemoteMod remoteMod;
    private final RemoteModInformation remoteInformation;
    private final Path targetFile;
    private final boolean cleanupBansoukouFiles;
    private final List<Path> supersededFiles;

    public InstallableMod(ModDirectorRemoteMod remoteMod, RemoteModInformation remoteInformation, Path targetFile) {
        this(remoteMod, remoteInformation, targetFile, false, Collections.emptyList());
    }

    private InstallableMod(
        ModDirectorRemoteMod remoteMod,
        RemoteModInformation remoteInformation,
        Path targetFile,
        boolean cleanupBansoukouFiles,
        List<Path> supersededFiles
    ) {
        this.remoteMod = remoteMod;
        this.remoteInformation = remoteInformation;
        this.targetFile = targetFile;
        this.cleanupBansoukouFiles = cleanupBansoukouFiles;
        this.supersededFiles = Collections.unmodifiableList(new ArrayList<>(supersededFiles));
    }

    public ModDirectorRemoteMod getRemoteMod() {
        return remoteMod;
    }

    public RemoteModInformation getRemoteInformation() {
        return remoteInformation;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public boolean shouldCleanupBansoukouFiles() {
        return cleanupBansoukouFiles;
    }

    public List<Path> getSupersededFiles() {
        return supersededFiles;
    }

    public InstallableMod withCommitActions(boolean cleanupBansoukouFiles, List<Path> supersededFiles) {
        return new InstallableMod(
            remoteMod,
            remoteInformation,
            targetFile,
            cleanupBansoukouFiles,
            supersededFiles
        );
    }

    /**
     * Compatibility entry point. Installation is always routed through the controller so
     * callers cannot bypass the common staging, validation, and commit phases.
     */
    public void performInstall(ModpackDirector director, ProgressCallback callback) throws ModDirectorException {
        director.getInstallController().install(this, callback);
    }
}
