package net.jan.moddirector.core.manage.install;

import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;

public final class PreInstallResult {
    public enum Status {
        EXCLUDED,
        FRESH,
        REINSTALL,
        FAILED
    }

    private final Status status;
    private final ModDirectorRemoteMod remoteMod;
    private final InstallableMod installableMod;
    private final boolean cleanupBansoukouFiles;

    private PreInstallResult(
        Status status,
        ModDirectorRemoteMod remoteMod,
        InstallableMod installableMod,
        boolean cleanupBansoukouFiles
    ) {
        this.status = status;
        this.remoteMod = remoteMod;
        this.installableMod = installableMod;
        this.cleanupBansoukouFiles = cleanupBansoukouFiles;
    }

    public static PreInstallResult excluded(ModDirectorRemoteMod remoteMod) {
        return new PreInstallResult(Status.EXCLUDED, remoteMod, null, false);
    }

    public static PreInstallResult fresh(InstallableMod installableMod) {
        return new PreInstallResult(
            Status.FRESH,
            installableMod.getRemoteMod(),
            installableMod,
            false
        );
    }

    public static PreInstallResult reinstall(InstallableMod installableMod, boolean cleanupBansoukouFiles) {
        return new PreInstallResult(
            Status.REINSTALL,
            installableMod.getRemoteMod(),
            installableMod,
            cleanupBansoukouFiles
        );
    }

    public static PreInstallResult failed(ModDirectorRemoteMod remoteMod) {
        return new PreInstallResult(Status.FAILED, remoteMod, null, false);
    }

    public Status getStatus() {
        return status;
    }

    public ModDirectorRemoteMod getRemoteMod() {
        return remoteMod;
    }

    public InstallableMod getInstallableMod() {
        return installableMod;
    }

    public boolean shouldCleanupBansoukouFiles() {
        return cleanupBansoukouFiles;
    }

    public boolean isInstallCandidate() {
        return status == Status.FRESH || status == Status.REINSTALL;
    }
}
