package net.jan.moddirector.core.manage.install;

import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PreInstallPlan {
    private final List<ModDirectorRemoteMod> excludedMods;
    private final List<InstallableMod> freshInstalls;
    private final List<InstallableMod> reInstalls;

    private PreInstallPlan(
        List<ModDirectorRemoteMod> excludedMods,
        List<InstallableMod> freshInstalls,
        List<InstallableMod> reInstalls
    ) {
        this.excludedMods = Collections.unmodifiableList(excludedMods);
        this.freshInstalls = Collections.unmodifiableList(freshInstalls);
        this.reInstalls = Collections.unmodifiableList(reInstalls);
    }

    public static PreInstallPlan from(List<PreInstallResult> results) {
        List<ModDirectorRemoteMod> excludedMods = new ArrayList<>();
        List<InstallableMod> freshInstalls = new ArrayList<>();
        List<InstallableMod> reInstalls = new ArrayList<>();

        for (PreInstallResult result : results) {
            switch (result.getStatus()) {
                case EXCLUDED:
                    excludedMods.add(result.getRemoteMod());
                    break;
                case FRESH:
                    freshInstalls.add(result.getInstallableMod());
                    break;
                case REINSTALL:
                    reInstalls.add(result.getInstallableMod());
                    break;
                case FAILED:
                    break;
            }
        }

        return new PreInstallPlan(excludedMods, freshInstalls, reInstalls);
    }

    public List<ModDirectorRemoteMod> getExcludedMods() {
        return excludedMods;
    }

    public List<InstallableMod> getFreshInstalls() {
        return freshInstalls;
    }

    public List<InstallableMod> getReInstalls() {
        return reInstalls;
    }
}
