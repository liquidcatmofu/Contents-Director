package net.jan.moddirector.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.juanmuscaria.modpackdirector.ModpackDirector;
import lombok.Getter;
import net.jan.moddirector.core.configuration.InstallationPolicy;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.configuration.RemoteModMetadata;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;
import net.jan.moddirector.core.util.IOOperation;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Getter
public class UrlRemoteMod extends ModDirectorRemoteMod {
    private final String fileName;
    private final URL url;
    private final String[] follows;

    @JsonCreator
    public UrlRemoteMod(
        @JsonProperty(value = "fileName") String fileName,
        @JsonProperty(value = "url", required = true) URL url,
        @JsonProperty(value = "follows") String[] follows,
        @JsonProperty(value = "metadata") RemoteModMetadata metadata,
        @JsonProperty(value = "installationPolicy") InstallationPolicy installationPolicy,
        @JsonProperty(value = "options") Map<String, Object> options,
        @JsonProperty(value = "folder") String folder,
        @JsonProperty(value = "inject") Boolean inject
    ) {
        super(metadata, installationPolicy, options, folder, inject);
        this.fileName = fileName;
        this.url = url;
        this.follows = follows == null ? new String[0] : follows;
    }

    @Override
    public String remoteType() {
        return url.getHost();
    }

    @Override
    public String offlineName() {
        return url.getFile().isEmpty() ? "<no name>" : url.getFile();
    }

    @Override
    public String remoteUrl() {
        return url.toString();
    }

    // TODO: Move URL following to query instead
    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, ModpackDirector director, RemoteModInformation information) throws ModDirectorException {
        byte[] data = null;

        progressCallback.setSteps(follows.length + 1);

        URL urlToFollow = null;
        for (int i = -1; i < follows.length; i++) {
            if (i < 0) {
                urlToFollow = url;
            } else {
                String html = new String(data);

                int startIndex = html.indexOf(follows[i]);
                if (startIndex < 0) {
                    throw new ModDirectorException("Unable to find follow string " + follows[i] + " in html from " +
                        urlToFollow);
                }

                int href = html.substring(0, startIndex).lastIndexOf("href=") + 5;
                char hrefEnclose = html.charAt(href);
                int hrefEnd = html.indexOf(hrefEnclose, href + 2);

                String newUrl = html.substring(href + 1, hrefEnd);
                if (newUrl.isEmpty()) {
                    throw new ModDirectorException("Result url was empty when matching " + follows[i] +
                        " in html from " + urlToFollow);
                }

                try {
                    if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                        if (!newUrl.startsWith("/")) {
                            newUrl = "/" + newUrl;
                        }
                        urlToFollow = new URL(urlToFollow.getProtocol(), urlToFollow.getHost(), newUrl);
                    } else {
                        urlToFollow = new URL(newUrl);
                    }
                } catch (MalformedURLException e) {
                    throw new ModDirectorException("Failed to create follow url when using follow " + follows[i], e);
                }
            }

            if (i + 1 == follows.length) {
                progressCallback.message("Downloading final file");
            } else {
                progressCallback.message("Following redirect " + (i + 2) + " out of " + follows.length);
            }

            director.checkUrl(urlToFollow);

            try (WebGetResponse response = WebClient.get(urlToFollow)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOOperation.copy(response.getInputStream(), outputStream, progressCallback, response.getStreamSize());
                data = outputStream.toByteArray();
            } catch (IOException e) {
                throw new ModDirectorException("Failed to follow URLs to download file", e);
            }

            progressCallback.step();
        }

        try {
            Files.write(targetFile, data);

            if (this.getInstallationPolicy().shouldExtract()) {
                Path extractionRoot = targetFile.getParent().toAbsolutePath().normalize();
                try (ZipFile zipFile = openValidatedZipArchive(targetFile, information.targetFilename())) {
                    byte[] buffer = new byte[8192];
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry zipEntry = entries.nextElement();
                        Path newFilePath = resolveZipEntryPath(extractionRoot, zipEntry.getName());
                        if (!zipEntry.isDirectory()) {
                            Files.createDirectories(newFilePath.getParent());
                            progressCallback.message("Unzipping " + newFilePath.getFileName());
                            try (InputStream inputStream = zipFile.getInputStream(zipEntry);
                                 java.io.OutputStream outputStream = Files.newOutputStream(newFilePath)) {
                                int length;
                                while ((length = inputStream.read(buffer)) > 0) {
                                    outputStream.write(buffer, 0, length);
                                }
                            }
                        } else {
                            Files.createDirectories(newFilePath);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new ModDirectorException("Failed to write file to disk", e);
        }

        progressCallback.done();
    }

    static ZipFile openValidatedZipArchive(Path archive, String fileName) throws IOException {
        rejectKnownNonZipArchiveExtension(fileName);
        return new ZipFile(archive.toFile());
    }

    private static void rejectKnownNonZipArchiveExtension(String fileName) throws IOException {
        if (fileName == null) {
            return;
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String[] unsupportedArchiveSuffixes = {
            ".tar.gz", ".tgz",
            ".tar.xz", ".txz",
            ".tar.zst", ".tzst",
            ".tar.bz2", ".tbz2", ".tbz",
            ".tar", ".7z", ".rar",
            ".gz", ".xz", ".zst", ".bz2"
        };

        for (String suffix : unsupportedArchiveSuffixes) {
            if (lowerName.endsWith(suffix)) {
                throw new IOException(
                    "Unsupported archive format for extract: " + fileName
                        + " (only ZIP-compatible archives are currently supported)"
                );
            }
        }
    }

    static Path resolveZipEntryPath(Path extractionRoot, String entryName) throws IOException {
        Path normalizedRoot = extractionRoot.toAbsolutePath().normalize();

        final Path destination;
        try {
            destination = normalizedRoot.resolve(entryName).normalize();
        } catch (InvalidPathException e) {
            throw new IOException("Invalid path in zip entry: " + entryName, e);
        }

        if (destination.equals(normalizedRoot) || !destination.startsWith(normalizedRoot)) {
            throw new IOException("Invalid zip entry path: " + entryName);
        }

        Path current = normalizedRoot;
        Path relative = normalizedRoot.relativize(destination);
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Zip entry traverses symbolic link: " + entryName);
            }
        }

        return destination;
    }

    @Override
    public boolean shouldCommitPrimaryFile() {
        return !(getInstallationPolicy().shouldExtract()
            && getInstallationPolicy().shouldDeleteAfterExtract());
    }

    @Override
    public boolean shouldDeletePrimaryFile() {
        return getInstallationPolicy().shouldExtract()
            && getInstallationPolicy().shouldDeleteAfterExtract();
    }

    @Override
    public RemoteModInformation queryInformation() {
        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        } else {
            String name = Paths.get(url.getFile()).getFileName().toString();

            return new RemoteModInformation(name, name);
        }
    }
}
