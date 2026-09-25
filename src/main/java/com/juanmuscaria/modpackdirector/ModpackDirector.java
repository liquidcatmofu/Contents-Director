package com.juanmuscaria.modpackdirector;

import com.juanmuscaria.autumn.messages.MessageSourceSupport;
import com.juanmuscaria.modpackdirector.i18n.Messages;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.ui.DirectorMainGUI;
import com.juanmuscaria.modpackdirector.ui.ExternalUiClient;
import com.juanmuscaria.modpackdirector.ui.ManualDownloadDialog;
import com.juanmuscaria.modpackdirector.ui.SwingDispatch;
import com.juanmuscaria.modpackdirector.ui.theme.UITheme;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import lombok.Getter;
import net.jan.moddirector.core.configuration.ConfigurationController;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.InstallController;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.manage.NoOpProgressCallback;
import net.jan.moddirector.core.manage.ProgressCallback;
import net.jan.moddirector.core.manage.check.StopModReposts;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.InstallResult;
import net.jan.moddirector.core.manage.install.InstalledMod;
import net.jan.moddirector.core.manage.install.PreInstallPlan;
import net.jan.moddirector.core.manage.install.PreInstallResult;
import net.jan.moddirector.core.manage.select.InstallSelector;
import net.jan.moddirector.core.util.ImageLoader;
import net.jan.moddirector.core.util.NetworkExceptions;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class ModpackDirector implements Callable<Boolean> {
    private static final TimeUnit DEFAULT_UNIT = TimeUnit.DAYS;
    private static final int DEFAULT_TIME = 1;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final NoOpProgressCallback NO_OP_PROGRESS_CALLBACK = new NoOpProgressCallback();
    private final ScheduledExecutorService taskExecutor = Executors.newScheduledThreadPool(Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors())),
        r -> new Thread(r, "ModpackDirector Worker " + THREAD_NUMBER.incrementAndGet()));
    private final ConcurrentLinkedDeque<ModDirectorError> errors = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<InstalledMod> installedMods = new ConcurrentLinkedDeque<>();
    private final InstallSelector installSelector = new InstallSelector();
    private final PlatformDelegate platform;
    private final LoggerDelegate logger;
    private final ExternalUiClient externalUi;
    private final ConfigurationController configurationController;
    private final InstallController installController;
    private final StopModReposts stopModReposts;
    private String modpackRemoteVersion;
    private DirectorMainGUI ui;

    public ModpackDirector(PlatformDelegate platform) {
        this.platform = platform;
        this.logger = platform.logger();
        this.externalUi = ExternalUiClient.shouldUseExternalUi(platform)
            ? new ExternalUiClient(logger)
            : null;
        this.configurationController = new ConfigurationController(this, platform.configurationDirectory());
        this.installController = new InstallController(this);
        this.stopModReposts = new StopModReposts(this);
    }

    @Override
    public Boolean call() throws Exception {
        var log = Logger.getLogger(MessageSourceSupport.class.getName());
        log.setLevel(Level.FINEST);
        configurationController.load();
        List<ModDirectorRemoteMod> mods = configurationController.getConfigurations();
        ModpackConfiguration modpackConfiguration = configurationController.getModpackConfiguration();

        if (modpackConfiguration == null) {
            logger.warn("This modpack does not contain a modpack.json, if you are the author, consider adding one!");
            modpackConfiguration = ModpackConfiguration.createDefault();
        } else if (modpackConfiguration.remoteVersion() != null) {
            try (WebGetResponse response = WebClient.get(modpackConfiguration.remoteVersion());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                modpackRemoteVersion = reader.readLine();
            } catch (IOException e) {
                String detail = NetworkExceptions.describe(e);
                Level level = remoteVersionFailureLevel(modpackConfiguration);
                logger.log(level, "Failed to check modpack version from {0}: {1}",
                    modpackConfiguration.remoteVersion(), detail, e);
                addError(new ModDirectorError(level,
                    "Failed to check the modpack version from " + modpackConfiguration.remoteVersion()
                        + ": " + detail, e));
            }
        }
        if (!platform.headless() && externalUi == null) {
            String uiTheme = modpackConfiguration.uiTheme();
            SwingDispatch.runAndWait(() -> UITheme.apply(uiTheme, logger));
        }

        if (hasFatalError()) {
            return false;
        }

        var messages = new Messages(platform, true);
        if (!platform.headless() && externalUi == null) {
            var icon = modpackConfiguration.icon();
            Image iconImage = null;
            if (icon != null) {
                try {
                    iconImage = ImageLoader.getImage(icon.path(), icon.width(), icon.height());
                } catch (Throwable e) {
                    logger.error("Unable to load modpack icon {0}", icon.path(), e);
                }
            }

            Image finalIconImage = iconImage;
            Dimension iconDimension = icon == null ? null : new Dimension(icon.width(), icon.height());
            String packName = modpackConfiguration.packName();

            ui = SwingDispatch.callAndWait(() -> {
                DirectorMainGUI window = new DirectorMainGUI(messages, logger);
                window.getModpackName().setText(packName);
                window.setModpackIcon(finalIconImage, iconDimension);
                window.setLocationRelativeTo(null);
                window.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        logger.info("User asked to exit");
                        UnsafeExit.exit(0);
                    }
                });
                window.setTitle(packName);
                window.pack();
                window.setVisible(true);
                return window;
            });
        }

        var preInstallationPage = ui == null ? null
            : SwingDispatch.callAndWait(() -> ui.progressPage("modpack_director.progress.check_install"));

        List<Callable<PreInstallResult>> preInstallTasks = installController.createPreInstallTasks(
            mods,
            preInstallationPage != null ?
                preInstallationPage::createProgressCallback :
                this::noOpCallback
        );

        List<PreInstallResult> preInstallResults = awaitAllResults(taskExecutor.invokeAll(preInstallTasks));

        if (hasFatalError()) {
            errorExit();
        }

        PreInstallPlan preInstallPlan = PreInstallPlan.from(preInstallResults);
        List<InstallableMod> freshInstalls = preInstallPlan.getFreshInstalls();
        installSelector.accept(
            preInstallPlan.getExcludedMods(),
            freshInstalls,
            preInstallPlan.getReInstalls()
        );

        if (installSelector.hasSelectableOptions()) {
            if (externalUi != null) {
                if (!externalUi.select(installSelector, messages, modpackConfiguration.packName())) {
                    UnsafeExit.exit(0);
                }
            } else if (ui != null) {
                var selection = SwingDispatch.callAndWait(() -> ui.selectionPage(installSelector));
                selection.waitForNext();
            }
        }

        List<InstallableMod> toInstall = installSelector.computeModsToInstall();
        if (!toInstall.isEmpty()) {
            if (externalUi != null) {
                if (!externalUi.consent(toInstall, messages, modpackConfiguration.packName())) {
                    UnsafeExit.exit(0);
                }
            } else if (ui != null) {
                var consent = SwingDispatch.callAndWait(() -> ui.consent(toInstall));
                consent.waitForNext();
            }
        }

        String installPackName = modpackConfiguration.packName();
        var installProgressPage = ui == null ? null :
            SwingDispatch.callAndWait(() ->
                ui.progressPage("modpack_director.progress.install", installPackName));

        List<Callable<InstallResult>> installTasks = installController.createInstallTasks(
            toInstall,
            installProgressPage != null ?
                installProgressPage::createProgressCallback :
                this::noOpCallback
        );

        List<InstallResult> installResults = awaitAllResults(taskExecutor.invokeAll(installTasks));
        installController.applyDeferredInstallFilesystemChanges(installResults);

        installController.markDisabledMods(installSelector.computeDisabledMods());

        if (hasFatalError()) {
            errorExit();
        }

        taskExecutor.shutdown();
        if (!taskExecutor.awaitTermination(DEFAULT_TIME, DEFAULT_UNIT)) {
            logger.warn("Unable to terminate all tasks.");
        }

        if (modpackConfiguration.remoteVersion() != null && modpackConfiguration.localVersion() != null && modpackRemoteVersion != null && !modpackRemoteVersion.contains(modpackConfiguration.localVersion())) {
            logger.error("Modpack version mismatch!");
            var baseKey = modpackConfiguration.refuseLaunch() ? "modpack_director.modpack_outdated_refuse_launch" : "modpack_director.modpack_outdated";
            if (externalUi != null) {
                externalUi.message(
                    modpackConfiguration.packName(),
                    messages.get(baseKey + ".title"),
                    messages.get(baseKey),
                    messages.get(baseKey + ".button")
                );
            } else if (ui != null) {
                var page = SwingDispatch.callAndWait(() ->
                    ui.messagePage(baseKey + ".title", baseKey, baseKey + ".button"));
                page.waitForButton();
            }

            if (modpackConfiguration.refuseLaunch()) {
                logger.error("Please update before continuing!");
                UnsafeExit.exit(1);
            }
        }

        if (modpackConfiguration.requiresRestart() && !freshInstalls.isEmpty()) {
            logger.info("Installation complete, a restart is required to complete initialization.");
            if (externalUi != null) {
                externalUi.message(
                    modpackConfiguration.packName(),
                    messages.get("modpack_director.restart_required.title"),
                    messages.get("modpack_director.restart_required"),
                    messages.get("modpack_director.restart_required.button")
                );
            } else if (ui != null) {
                var page = SwingDispatch.callAndWait(() ->
                    ui.messagePage("modpack_director.restart_required.title", "modpack_director.restart_required",
                        "modpack_director.restart_required.button"));
                page.waitForButton();
            }
            UnsafeExit.exit(0);
        }

        if (ui != null) {
            SwingDispatch.runAndWait(ui::dispose);
        }
        return !hasFatalError();
    }

    public List<InstalledMod> getInstalledMods() {
        return new ArrayList<>(installedMods);
    }

    public void addError(ModDirectorError error) {
        errors.add(error);
    }

    public boolean hasFatalError() {
        return errors.stream().anyMatch(e -> e.getLevel() == Level.SEVERE);
    }

    private ProgressCallback noOpCallback(String title, String info) {
        return NO_OP_PROGRESS_CALLBACK;
    }

    public void errorExit() {
        logger.error("============================================================");
        logger.error("Summary of {0} encountered errors:", errors.size());
        errors.forEach(e -> {
            if (e.getException() != null) {
                logger.log(e.getLevel(), e.getMessage(), e.getException());
            } else {
                logger.log(e.getLevel(), e.getMessage());
            }
        });
        logger.error("============================================================");

        if (!platform.headless()) {
            try {
                if (externalUi != null) {
                    externalUi.errors(errors);
                } else if (ui != null) {
                    var page = SwingDispatch.callAndWait(() -> ui.errorPage(errors));
                    page.waitForClose();
                } else {
                    // UI was never created (failure before the GUI was shown); fall back to a plain dialog.
                    StringBuilder msg = new StringBuilder("<html><b>Installation Failed</b><br><br>");
                    errors.forEach(e -> msg.append("&bull; ").append(e.getMessage()).append("<br>"));
                    msg.append("</html>");
                    String dialogMessage = msg.toString();
                    SwingDispatch.runAndWait(() -> JOptionPane.showMessageDialog(null, dialogMessage,
                        "Modpack Director", JOptionPane.ERROR_MESSAGE));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
                // Never let UI errors block the exit.
            }
        }

        UnsafeExit.exit(1);
    }

    static Level remoteVersionFailureLevel(ModpackConfiguration configuration) {
        return configuration.refuseLaunch() ? Level.SEVERE : Level.WARNING;
    }

    public LoggerDelegate logger() {
        return logger;
    }

    public PlatformDelegate platform() {
        return platform;
    }

    private <T> List<T> awaitAllResults(List<Future<T>> futures) throws InterruptedException {
        List<T> results = new ArrayList<>();

        for (Future<T> future : futures) {
            try {
                T result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (CancellationException e) {
                logger.error("A future task was cancelled unexpectedly", e);
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "A future task was cancelled unexpectedly",
                    e
                ));
            } catch (ExecutionException e) {
                logger.error("An exception occurred while performing asynchronous work", e);
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "An exception occurred while performing asynchronous work",
                    e
                ));
            }
        }

        return results;
    }

    private void awaitAll(List<Future<Void>> futures) throws InterruptedException {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (CancellationException e) {
                logger.error("A future task was cancelled unexpectedly", e);
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "A future task was cancelled unexpectedly",
                    e
                ));
            } catch (ExecutionException e) {
                logger.error("An exception occurred while performing asynchronous work", e);
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "An exception occurred while performing asynchronous work",
                    e
                ));
            }
        }
    }

    public synchronized Path requestManualDownload(URL manualDownloadUrl, Path targetFile) throws ModDirectorException {
        String instructions = "Manual download required. Download " + manualDownloadUrl
            + " and select the file for " + targetFile.getFileName()
            + " (target: " + targetFile + ").";

        if (platform.headless()) {
            logger.error(instructions);
            throw new ModDirectorException(instructions);
        }

        final Path selectedFile;
        try {
            if (externalUi != null) {
                selectedFile = externalUi.manualDownload(
                    manualDownloadUrl,
                    targetFile,
                    targetFile.getFileName().toString()
                );
            } else {
                selectedFile = SwingDispatch.callAndWait(() ->
                    ManualDownloadDialog.show(
                        ui,
                        manualDownloadUrl.toExternalForm(),
                        targetFile.getFileName().toString(),
                        targetFile.toString()
                    )
                );
            }
        } catch (Exception e) {
            throw new ModDirectorException("Failed to present manual download fallback", e);
        }

        if (selectedFile == null) {
            throw new ModDirectorException("Manual download was cancelled");
        }

        Path normalized = selectedFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new ModDirectorException("Selected manual download is not a regular file: " + normalized);
        }
        return normalized;
    }

    public void checkUrl(URL url) throws ModDirectorException {
        this.stopModReposts.check(url);
    }
}
