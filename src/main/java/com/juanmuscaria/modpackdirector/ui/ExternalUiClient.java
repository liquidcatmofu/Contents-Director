package com.juanmuscaria.modpackdirector.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juanmuscaria.modpackdirector.i18n.Messages;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.select.InstallSelector;
import net.jan.moddirector.core.manage.select.SelectableInstallOption;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExternalUiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LoggerDelegate logger;

    public ExternalUiClient(LoggerDelegate logger) {
        this.logger = logger;
    }

    public static boolean shouldUseExternalUi(PlatformDelegate platform) {
        return !platform.headless()
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    public boolean select(InstallSelector selector, Messages messages, String packName) throws Exception {
        ExternalUiProtocol.Request request = new ExternalUiProtocol.Request();
        request.type = "selection";
        request.packName = packName;
        request.title = messages.get("modpack_director.selection_page.title");
        request.buttonLabel = messages.get("modpack_director.selection_page.next_button_label");
        request.cancelLabel = messages.get("modpack_director.consent.cancel_button_label");

        int index = 0;
        for (SelectableInstallOption option : selector.getSingleOptions()) {
            request.options.add(toOption("single-" + index++, option));
        }

        int groupIndex = 0;
        for (Map.Entry<String, List<SelectableInstallOption>> entry : selector.getGroupOptions().entrySet()) {
            ExternalUiProtocol.Group group = new ExternalUiProtocol.Group();
            group.name = entry.getKey();
            int optionIndex = 0;
            for (SelectableInstallOption option : entry.getValue()) {
                group.options.add(toOption("group-" + groupIndex + "-" + optionIndex++, option));
            }
            request.groups.add(group);
            groupIndex++;
        }

        ExternalUiProtocol.Response response = invoke(request);
        if (response.cancelled) {
            return false;
        }

        index = 0;
        for (SelectableInstallOption option : selector.getSingleOptions()) {
            Boolean selected = response.selections.get("single-" + index++);
            if (selected != null) {
                option.setSelected(selected);
            }
        }

        groupIndex = 0;
        for (Map.Entry<String, List<SelectableInstallOption>> entry : selector.getGroupOptions().entrySet()) {
            int optionIndex = 0;
            for (SelectableInstallOption option : entry.getValue()) {
                Boolean selected = response.selections.get("group-" + groupIndex + "-" + optionIndex++);
                if (selected != null) {
                    option.setSelected(selected);
                }
            }
            groupIndex++;
        }

        return true;
    }

    public boolean consent(List<InstallableMod> mods, Messages messages, String packName) throws Exception {
        ExternalUiProtocol.Request request = new ExternalUiProtocol.Request();
        request.type = "consent";
        request.packName = packName;
        request.title = messages.get("modpack_director.consent.title");
        request.message = messages.get("modpack_director.consent.info");
        request.acceptLabel = messages.get("modpack_director.consent.accept_button_label");
        request.cancelLabel = messages.get("modpack_director.consent.cancel_button_label");

        for (InstallableMod mod : mods) {
            ExternalUiProtocol.ModEntry entry = new ExternalUiProtocol.ModEntry();
            entry.name = mod.getRemoteInformation().displayName();
            entry.url = mod.getRemoteMod().remoteUrl();
            entry.target = mod.getTargetFile().toString();
            entry.source = messages.get("modpack_director.consent.source", mod.getRemoteMod().remoteType());
            request.mods.add(entry);
        }

        ExternalUiProtocol.Response response = invoke(request);
        return response.accepted && !response.cancelled;
    }

    public Path manualDownload(
        URL url,
        Path targetFile,
        String expectedFileName,
        Messages messages
    ) throws Exception {
        ExternalUiProtocol.Request request =
            createManualDownloadRequest(url, targetFile, expectedFileName, messages);

        ExternalUiProtocol.Response response = invoke(request);
        if (response.cancelled || response.selectedFile == null || response.selectedFile.isEmpty()) {
            return null;
        }
        return Paths.get(response.selectedFile).toAbsolutePath().normalize();
    }

    public void message(String packName, String title, String message, String buttonLabel) throws Exception {
        ExternalUiProtocol.Request request = new ExternalUiProtocol.Request();
        request.type = "message";
        request.packName = packName;
        request.title = title;
        request.message = message;
        request.buttonLabel = buttonLabel;
        invoke(request);
    }

    public void errors(Collection<ModDirectorError> errors, Messages messages) throws Exception {
        invoke(createErrorRequest(errors, messages));
    }

    static ExternalUiProtocol.Request createManualDownloadRequest(
        URL url,
        Path targetFile,
        String expectedFileName,
        Messages messages
    ) {
        ExternalUiProtocol.Request request = new ExternalUiProtocol.Request();
        request.type = "manual-download";
        request.packName = "Contents Director";
        request.title = messages.get("modpack_director.manual_download.title");
        request.url = url.toExternalForm();
        request.target = targetFile.toString();
        request.expectedFileName = expectedFileName;

        addLocalizedText(
            request,
            messages,
            "modpack_director.manual_download.title",
            "modpack_director.manual_download.explanation",
            "modpack_director.manual_download.download_url",
            "modpack_director.manual_download.open_browser",
            "modpack_director.manual_download.copy_url",
            "modpack_director.manual_download.expected_file",
            "modpack_director.manual_download.target",
            "modpack_director.manual_download.waiting",
            "modpack_director.manual_download.use_downloaded_file",
            "modpack_director.manual_download.cancel",
            "modpack_director.manual_download.select_file",
            "modpack_director.manual_download.no_url",
            "modpack_director.manual_download.opened_browser",
            "modpack_director.manual_download.browser_unavailable",
            "modpack_director.manual_download.browser_failed",
            "modpack_director.manual_download.copied",
            "modpack_director.manual_download.clipboard_failed",
            "modpack_director.manual_download.detected",
            "modpack_director.manual_download.chooser_title"
        );
        return request;
    }

    static ExternalUiProtocol.Request createErrorRequest(
        Collection<ModDirectorError> errors,
        Messages messages
    ) {
        ExternalUiProtocol.Request request = new ExternalUiProtocol.Request();
        request.type = "error";
        request.packName = "Contents Director";
        request.title = messages.get("modpack_director.error.title");
        request.message = messages.get("modpack_director.error.intro")
            + "\n" + messages.get("modpack_director.error.help");
        request.buttonLabel = messages.get("modpack_director.error.close");
        addLocalizedText(request, messages, "modpack_director.error.cause");

        for (ModDirectorError error : errors) {
            ExternalUiProtocol.ErrorEntry entry = new ExternalUiProtocol.ErrorEntry();
            entry.level = error.getLevel() == java.util.logging.Level.SEVERE
                ? messages.get("modpack_director.error.level_error")
                : messages.get("modpack_director.error.level_warning");
            entry.message = error.getMessage();
            Throwable cause = error.getException() == null ? null : error.getException().getCause();
            entry.cause = cause == null ? null : cause.getMessage();
            request.errors.add(entry);
        }
        return request;
    }

    private static void addLocalizedText(
        ExternalUiProtocol.Request request,
        Messages messages,
        String... keys
    ) {
        for (String key : keys) {
            request.localizedText.put(key, messages.get(key));
        }
    }

    private ExternalUiProtocol.Option toOption(String id, SelectableInstallOption option) {
        ExternalUiProtocol.Option result = new ExternalUiProtocol.Option();
        result.id = id;
        result.name = option.getName();
        result.description = option.getDescription();
        result.selected = option.isSelected();
        return result;
    }

    private ExternalUiProtocol.Response invoke(ExternalUiProtocol.Request request) throws Exception {
        Path tempDir = Files.createTempDirectory("contents-director-ui-");
        Path requestFile = tempDir.resolve("request.json");
        Path responseFile = tempDir.resolve("response.json");

        try {
            MAPPER.writeValue(requestFile.toFile(), request);

            Path javaExecutable = Paths.get(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
            );
            Path classPathEntry = locateOwnClasspathEntry();

            ProcessBuilder builder = new ProcessBuilder(
                javaExecutable.toString(),
                "-cp",
                classPathEntry.toString(),
                ExternalUiHelperMain.class.getName(),
                requestFile.toString(),
                responseFile.toString()
            );
            builder.inheritIO();

            logger.debug("Starting external UI helper from {0}", classPathEntry);
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("External UI helper exited with code " + exitCode);
            }
            if (!Files.isRegularFile(responseFile)) {
                throw new IOException("External UI helper did not produce a response");
            }

            return MAPPER.readValue(responseFile.toFile(), ExternalUiProtocol.Response.class);
        } finally {
            try {
                Files.deleteIfExists(responseFile);
                Files.deleteIfExists(requestFile);
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                logger.debug("Unable to clean external UI temporary files: {0}", e.getMessage());
            }
        }
    }

    static Path locateOwnClasspathEntry() throws Exception {
        URL location = ExternalUiHelperMain.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            String external = location.toExternalForm();
            if ("file".equalsIgnoreCase(location.getProtocol())) {
                return Paths.get(location.toURI());
            }
            Path parsed = parseNonFileLocation(external);
            if (parsed != null) {
                return parsed;
            }
        }

        String[] entries = System.getProperty("java.class.path", "").split(File.pathSeparator);
        for (String entry : entries) {
            if (entry.toLowerCase(Locale.ROOT).contains("contentsdirector")) {
                Path path = Paths.get(entry);
                if (Files.exists(path)) {
                    return path.toAbsolutePath().normalize();
                }
            }
        }

        throw new IOException("Unable to locate the Contents Director runtime artifact for the external UI helper");
    }

    private static Path parseNonFileLocation(String location) {
        try {
            String value = location;
            if (value.startsWith("jar:")) {
                value = value.substring(4);
            } else if (value.startsWith("union:")) {
                value = value.substring(6);
            } else {
                return null;
            }

            int bang = value.indexOf("!/");
            if (bang >= 0) {
                value = value.substring(0, bang);
            }

            value = value.replaceFirst("%23\\d+$", "");
            value = value.replaceFirst("#\\d+$", "");

            URI uri;
            if (value.startsWith("file:")) {
                uri = URI.create(value);
            } else {
                uri = URI.create("file:" + value);
            }
            Path path = Paths.get(uri).toAbsolutePath().normalize();
            return Files.exists(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
