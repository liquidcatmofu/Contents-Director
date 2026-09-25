package net.jan.moddirector.core.manage;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.InstallationPolicy;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.PreInstallPlan;
import net.jan.moddirector.core.manage.install.PreInstallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstallControllerPreInstallTaskTest {

    @TempDir
    Path tempDir;

    @Test
    void productionTaskFactoryAggregatesManyConcurrentResultsWithoutSharedMutation() throws Exception {
        int taskCount = 2000;
        List<ModDirectorRemoteMod> mods = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            mods.add(new TestRemoteMod(i));
        }

        List<Callable<PreInstallResult>> tasks = InstallController.createPreInstallTasks(
            mods,
            mod -> resultFor((TestRemoteMod) mod)
        );

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<PreInstallResult>> futures = executor.invokeAll(tasks);
            List<PreInstallResult> results = new ArrayList<>();
            for (Future<PreInstallResult> future : futures) {
                results.add(future.get());
            }

            PreInstallPlan plan = PreInstallPlan.from(results);

            assertEquals(taskCount, tasks.size());
            assertEquals(taskCount, results.size());
            assertEquals(500, plan.getExcludedMods().size());
            assertEquals(500, plan.getFreshInstalls().size());
            assertEquals(500, plan.getReInstalls().size());
        } finally {
            executor.shutdownNow();
        }
    }

    private PreInstallResult resultFor(TestRemoteMod mod) {
        InstallableMod installable = new InstallableMod(
            mod,
            new RemoteModInformation(mod.offlineName(), mod.offlineName() + ".jar"),
            tempDir.resolve(mod.offlineName() + ".jar")
        );

        switch (mod.index % 4) {
            case 0:
                return PreInstallResult.excluded(mod);
            case 1:
                return PreInstallResult.fresh(installable);
            case 2:
                return PreInstallResult.reinstall(installable, false);
            default:
                return PreInstallResult.failed(mod);
        }
    }

    private static final class TestRemoteMod extends ModDirectorRemoteMod {
        private final int index;

        private TestRemoteMod(int index) {
            super(null, (InstallationPolicy) null, null, null, null);
            this.index = index;
        }

        @Override
        public String remoteType() {
            return "test";
        }

        @Override
        public String offlineName() {
            return "mod-" + index;
        }

        @Override
        public String remoteUrl() {
            return "test://" + offlineName();
        }

        @Override
        public RemoteModInformation queryInformation() {
            return new RemoteModInformation(offlineName(), offlineName() + ".jar");
        }

        @Override
        public void performInstall(
            Path targetFile,
            ProgressCallback progressCallback,
            ModpackDirector director,
            RemoteModInformation information
        ) throws ModDirectorException {
        }
    }
}
