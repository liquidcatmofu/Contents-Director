package net.jan.moddirector.core.configuration;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public abstract class ModDirectorRemoteMod {
    private final RemoteModMetadata metadata;
    private final InstallationPolicy installationPolicy;
    private final Map<String, Object> options;
    private final String folder;
    private final boolean inject;

    public ModDirectorRemoteMod(
        RemoteModMetadata metadata,
        InstallationPolicy installationPolicy,
        Map<String, Object> options,
        String folder,
        Boolean inject
    ) {
        this.metadata = metadata;
        this.installationPolicy = installationPolicy == null ? new InstallationPolicy(
            false,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            null,
            null,
            false,
            null
        ) : installationPolicy;
        this.options = options == null ? Collections.emptyMap() : options;
        this.folder = folder;
        if (inject == null) {
            this.inject = folder == null;
        } else {
            this.inject = inject;
        }
    }

    public abstract String remoteType();

    public abstract String offlineName();

    public abstract String remoteUrl();

    public abstract RemoteModInformation queryInformation() throws ModDirectorException;

    public abstract void performInstall(Path targetFile, ProgressCallback progressCallback, ModpackDirector director,
                                        RemoteModInformation information) throws ModDirectorException;

    /**
     * Whether the primary downloaded file should be moved from staging into the live installation.
     * Backends that consume the primary file while staging (for example extract-and-delete URL entries)
     * may return false while still committing their staged derived files.
     */
    public boolean shouldCommitPrimaryFile() {
        return true;
    }

    /**
     * Whether commit should remove an existing live primary file instead of publishing
     * the staged primary file. This is used by extract-and-delete backends.
     */
    public boolean shouldDeletePrimaryFile() {
        return false;
    }

    public RemoteModMetadata getMetadata() {
        return metadata;
    }

    public InstallationPolicy getInstallationPolicy() {
        return installationPolicy;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public boolean forceInject() {
        return inject;
    }

    public String getFolder() {
        return folder;
    }
}
