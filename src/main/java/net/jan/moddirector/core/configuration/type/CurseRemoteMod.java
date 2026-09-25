package net.jan.moddirector.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.juanmuscaria.modpackdirector.ModpackDirector;
import lombok.Getter;
import net.jan.moddirector.core.configuration.*;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;
import net.jan.moddirector.core.manage.install.InstallTransaction;
import net.jan.moddirector.core.util.HashResult;
import net.jan.moddirector.core.util.IOOperation;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Getter
public class CurseRemoteMod extends ModDirectorRemoteMod {
    private static final String CURSEFORGE_PROJECT_URL = "https://www.curseforge.com/projects/%s";

    private final int addonId;
    private final int fileId;
    private final String fileName;
    private final URL manualDownloadUrl;

    private CurseAddonFileInformation information;

    @JsonCreator
    public CurseRemoteMod(
        @JsonProperty(value = "addonId", required = true) int addonId,
        @JsonProperty(value = "fileId", required = true) int fileId,
        @JsonProperty(value = "metadata") RemoteModMetadata metadata,
        @JsonProperty(value = "installationPolicy") InstallationPolicy installationPolicy,
        @JsonProperty(value = "options") Map<String, Object> options,
        @JsonProperty(value = "folder") String folder,
        @JsonProperty(value = "inject") Boolean inject,
        @JsonProperty(value = "fileName") String fileName,
        @JsonProperty(value = "manualDownloadUrl") URL manualDownloadUrl
    ) {
        super(metadata, installationPolicy, options, folder, inject);
        this.addonId = addonId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.manualDownloadUrl = manualDownloadUrl;
    }

    public CurseRemoteMod(
        int addonId,
        int fileId,
        RemoteModMetadata metadata,
        InstallationPolicy installationPolicy,
        Map<String, Object> options,
        String folder,
        Boolean inject,
        String fileName
    ) {
        this(addonId, fileId, metadata, installationPolicy, options, folder, inject, fileName, null);
    }

    @Override
    public String remoteType() {
        return "CurseForge";
    }

    @Override
    public String offlineName() {
        return addonId + ":" + fileId;
    }

    @Override
    public String remoteUrl() {
        return manualFallbackUrl().toExternalForm();
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, ModpackDirector director, RemoteModInformation information) throws ModDirectorException {
        try {
            performAutomaticInstall(targetFile, progressCallback, director);
        } catch (ModDirectorException automaticFailure) {
            URL fallbackUrl = resolveManualFallbackUrl(director);
            director.logger().warn(
                "Automatic CurseForge download failed for {0}; falling back to manual download from {1}",
                offlineName(),
                fallbackUrl,
                automaticFailure
            );
            performManualInstall(targetFile, progressCallback, director, fallbackUrl);
        }
    }

    private void performAutomaticInstall(
        Path targetFile,
        ProgressCallback progressCallback,
        ModpackDirector director
    ) throws ModDirectorException {
        CurseAddonFileInformation remoteInformation = ensureInformationLoaded();

        try (InstallTransaction transaction = InstallTransaction.create(targetFile)) {
            try (WebGetResponse response = WebClient.get(remoteInformation.downloadUrl);
                 OutputStream outputStream = Files.newOutputStream(transaction.stagedFile())) {
                progressCallback.setSteps(1);
                IOOperation.copy(response.getInputStream(), outputStream, progressCallback, response.getStreamSize());
            }

            verifyStagedFile(transaction.stagedFile(), director);
            transaction.commit();
        } catch (IOException e) {
            throw new ModDirectorException("Failed to download file", e);
        }
    }

    private void performManualInstall(
        Path targetFile,
        ProgressCallback progressCallback,
        ModpackDirector director,
        URL fallbackUrl
    ) throws ModDirectorException {
        progressCallback.message("Waiting for manual download");
        Path selectedFile = director.requestManualDownload(fallbackUrl, targetFile);

        try (InstallTransaction transaction = InstallTransaction.create(targetFile)) {
            Files.copy(selectedFile, transaction.stagedFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            verifyStagedFile(transaction.stagedFile(), director);
            transaction.commit();
        } catch (IOException e) {
            throw new ModDirectorException("Failed to install manually selected file", e);
        }
    }

    private void verifyStagedFile(Path stagedFile, ModpackDirector director) throws ModDirectorException {
        if (getMetadata() != null
            && getMetadata().checkHashes(stagedFile, director.platform()) == HashResult.UNMATCHED) {
            throw new ModDirectorException("Selected or downloaded file did not match configured hash");
        }
    }

    @Override
    public RemoteModInformation queryInformation() throws ModDirectorException {
        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        }

        CurseAddonFileInformation remoteInformation = ensureInformationLoaded();
        return new RemoteModInformation(remoteInformation.displayName, remoteInformation.fileName);
    }

    private URL manualFallbackUrl() {
        if (manualDownloadUrl != null) {
            return manualDownloadUrl;
        }

        try {
            return new URL(String.format(CURSEFORGE_PROJECT_URL, addonId));
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid built-in CurseForge project URL", e);
        }
    }

    private URL resolveManualFallbackUrl(ModpackDirector director) {
        URL projectPage = manualFallbackUrl();
        if (manualDownloadUrl != null) {
            return projectPage;
        }

        try {
            URL canonicalProjectPage = resolveProjectPageUrl(projectPage);
            URL downloadPage = buildDownloadPageUrl(canonicalProjectPage, fileId);
            if (downloadPage != null) {
                return downloadPage;
            }

            director.logger().warn(
                "CurseForge project redirect for {0} did not resolve to a canonical project page; using {1}",
                addonId,
                projectPage
            );
        } catch (IOException e) {
            director.logger().warn(
                "Failed to resolve CurseForge project page for {0}; using {1}",
                addonId,
                projectPage,
                e
            );
        }

        return projectPage;
    }

    URL resolveProjectPageUrl(URL projectPage) throws IOException {
        return WebClient.resolveRedirects(projectPage);
    }

    static URL buildDownloadPageUrl(URL canonicalProjectPage, int fileId) {
        String protocol = canonicalProjectPage.getProtocol();
        String host = canonicalProjectPage.getHost();
        String path = canonicalProjectPage.getPath();

        if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))) {
            return null;
        }
        if (!("www.curseforge.com".equalsIgnoreCase(host)
            || "curseforge.com".equalsIgnoreCase(host))) {
            return null;
        }
        if (path == null || path.isEmpty() || path.matches("/projects/\\d+/?")) {
            return null;
        }

        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return null;
        }

        try {
            return new URL(
                canonicalProjectPage.getProtocol(),
                canonicalProjectPage.getHost(),
                canonicalProjectPage.getPort(),
                path + "/download/" + fileId
            );
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private synchronized CurseAddonFileInformation ensureInformationLoaded() throws ModDirectorException {
        if (information == null) {
            information = fetchInformation();
        }
        return information;
    }

    CurseAddonFileInformation fetchInformation() throws ModDirectorException {
        try {
            URL apiUrl = new URL(String.format("https://api.curse.tools/v1/cf/mods/%s/files/%s", addonId, fileId));
            JsonNode jsonObject;
            try (WebGetResponse response = WebClient.get(apiUrl);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                jsonObject = ConfigurationController.OBJECT_MAPPER.readTree(reader).get("data");
            }
            return ConfigurationController.OBJECT_MAPPER.convertValue(jsonObject, CurseAddonFileInformation.class);
        } catch (MalformedURLException e) {
            throw new ModDirectorException("Failed to create curse.tools api url", e);
        } catch (JsonParseException e) {
            throw new ModDirectorException("Failed to parse Json response from curse", e);
        } catch (JsonMappingException e) {
            throw new ModDirectorException("Failed to map Json response from curse, did they change their api?", e);
        } catch (IOException e) {
            throw new ModDirectorException("Failed to open connection to curse", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class CurseAddonFileInformation {
        @JsonProperty
        private String displayName;

        @JsonProperty
        private String fileName;

        @JsonProperty
        private URL downloadUrl;

        @JsonProperty
        private String[] gameVersions;
    }
}
