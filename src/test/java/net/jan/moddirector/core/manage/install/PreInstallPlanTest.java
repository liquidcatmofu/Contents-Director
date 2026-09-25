package net.jan.moddirector.core.manage.install;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.InstallationPolicy;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;
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

class PreInstallPlanTest {

    @TempDir
    Path tempDir;

    @Test
    void combinesManyConcurrentPreInstallResultsWithoutSharedMutation() throws Exception {
        int taskCount = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Callable<PreInstallResult>> tasks = new ArrayList<>();
            int expectedExcluded = 0;
            int expectedFresh = 0;
            int expectedReinstall = 0;

            for (int i = 0; i < taskCount; i++) {
                TestRemoteMod mod = new TestRemoteMod("mod-" + i);
                InstallableMod installable = new InstallableMod(
                    mod,
                    new RemoteModInformation(mod.offlineName(), mod.offlineName() + ".jar"),
                    tempDir.resolve(mod.offlineName() + ".jar")
                );

                switch (i % 4) {
                    case 0:
                        tasks.add(() -> PreInstallResult.excluded(mod));
                        expectedExcluded++;
                        break;
                    case 1:
                        tasks.add(() -> PreInstallResult.fresh(installable));
                        expectedFresh++;
                        break;
                    case 2:
                        tasks.add(() -> PreInstallResult.reinstall(installable, false));
                        expectedReinstall++;
                        break;
                    default:
                        tasks.add(() -> PreInstallResult.failed(mod));
                        break;
                }
            }

            List<Future<PreInstallResult>> futures = executor.invokeAll(tasks);
            List<PreInstallResult> results = new ArrayList<>();
            for (Future<PreInstallResult> future : futures) {
                results.add(future.get());
            }

            PreInstallPlan plan = PreInstallPlan.from(results);

            assertEquals(taskCount, results.size());
            assertEquals(expectedExcluded, plan.getExcludedMods().size());
            assertEquals(expectedFresh, plan.getFreshInstalls().size());
            assertEquals(expectedReinstall, plan.getReInstalls().size());
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class TestRemoteMod extends ModDirectorRemoteMod {
        private final String name;

        private TestRemoteMod(String name) {
            super(null, (InstallationPolicy) null, null, null, null);
            this.name = name;
        }

        @Override
        public String remoteType() {
            return "test";
        }

        @Override
        public String offlineName() {
            return name;
        }

        @Override
        public String remoteUrl() {
            return "test://" + name;
        }

        @Override
        public RemoteModInformation queryInformation() {
            return new RemoteModInformation(name, name + ".jar");
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
