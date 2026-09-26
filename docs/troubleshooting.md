# Troubleshooting

## Start with the visible error

Contents Director reports fatal installation errors in its UI before exiting when a graphical UI is available. The underlying exception is also logged.

Network failures are normalized into more useful descriptions where possible, including DNS, timeout, connection, and TLS/certificate failures.

## TLS / PKIX certificate failures

A message containing terms such as:

```text
PKIX path building failed
unable to find valid certification path to requested target
```

means the Java runtime could not build a trusted certificate chain for the HTTPS connection.

This is not necessarily a problem with the configured download URL. Check:

1. Open the exact URL in a browser on the same machine.
2. Confirm the Java runtime used by Minecraft/your launcher is current for that environment.
3. Check whether antivirus/security software is performing HTTPS inspection.
4. Check for a corporate/school proxy or custom certificate authority.
5. Compare the result using the same Java runtime outside Minecraft if possible.

If a proxy or HTTPS-inspection product inserts its own certificate, that certificate must be trusted by the Java runtime actually running Contents Director.

The repository is currently tracking one environment-specific PKIX report in [issue #1](https://github.com/liquidcatmofu/Contents-Director/issues/1).

## CurseForge automatic download failed

CurseForge entries fall back to a manual-download dialog on graphical clients when automatic provider download fails.

For the most reliable fallback, include an explicit `fileName`:

```json
{
  "addonId": 12345,
  "fileId": 67890,
  "fileName": "example-mod.jar"
}
```

Optionally set `manualDownloadUrl` to direct the user to a particular page.

The dialog can:

- open the download URL in the default browser;
- copy the URL to the clipboard;
- watch the user's Downloads directory for the expected filename;
- use a detected stable file;
- let the user choose the downloaded file manually.

On Linux, Downloads discovery honors `XDG_DOWNLOAD_DIR` from `user-dirs.dirs` (including `XDG_CONFIG_HOME`) and otherwise falls back to `~/Downloads`.

The selected file is copied into staging and any configured hashes are checked before the live installation is replaced.

### Headless/server environments

The manual-download dialog is interactive and therefore cannot complete on a headless server. Instructions/URL information are logged, then the manual fallback fails the installation.

For servers, prefer sources that can be downloaded automatically or pre-provision the expected file.

## Offline startup still queries a provider

For CurseForge and Modrinth entries, set `fileName` explicitly when you want Contents Director to identify an already-installed target without first requesting provider metadata.

Without `fileName`, the provider must be queried to learn the target filename before Contents Director can decide whether the file is already installed.

## Hash mismatch

When metadata contains supported hashes and an existing file does not match, Contents Director plans a reinstall.

A newly downloaded file is also hash-checked while staged. If it does not match, the staged install is rejected and the existing live file is not replaced.

Check that:

- the digest corresponds to the exact file configured by the provider entry;
- the algorithm name is supported by the JVM (for example `SHA-256`);
- the expected digest contains only the intended hexadecimal value.

Hash comparison is case-insensitive.

## A URL download is truncated or returns an HTTP error

HTTP redirects are handled explicitly, including relative redirect targets. Redirects without a `Location` header, unsupported redirect schemes, excessive redirect chains, and non-success final HTTP statuses fail the request.

When the server supplies a known `Content-Length`, a shorter-than-declared body is rejected instead of being accepted as a successful file.

Retrying may help with transient network failures; persistent failures should be checked at the configured endpoint.

## Archive extraction fails

URL entries with `installationPolicy.extract: true` currently support ZIP-compatible containers only.

The archive must pass ZIP structure validation. Known non-ZIP archive names such as `.tar`, `.tar.gz`, `.7z`, `.xz`, or `.zst` are unsupported and should fail rather than being treated as ZIP. Additional archive-format support is tracked in [issue #34](https://github.com/liquidcatmofu/Contents-Director/issues/34).

Archive entries are rejected if their normalized path escapes the extraction root or if an entry path traverses a symbolic link. This is intentional path-traversal protection.

## Modify configuration fails

`*.modify.json` paths are confined to the installation root and reject symbolic-link traversal.

For a bundle, all modify paths are validated before the bundle begins applying modify actions. If one path is unsafe or invalid, earlier modify entries from that bundle are not executed first.

Check `folder`, `fileName`, `newFolder`, and `newFileName` for absolute paths, `..` traversal, invalid platform-specific path characters, and symlinked path components.

## Language or custom UI text is not applied

Contents Director selects the UI locale from `Locale.getDefault()` and then falls back directly to the canonical English bundle for missing keys. It does not switch to a different system-language bundle while resolving a deliberately selected locale.

Modpack overrides belong in the configuration directory, for example:

```text
config/mod-director/messages.xml
config/mod-director/messages_ja.xml
config/mod-director/messages_zh_CN.xml
```

See [Localization](localization.md) for format and precedence details.

## macOS UI

macOS interactive Swing UI is run in a separate helper JVM to avoid the Minecraft JVM's AWT/GLFW first-thread conflict.

Localization is passed from the main process to the helper and is supported. The configured FlatLaf theme is not yet propagated to the helper; see [issue #23](https://github.com/liquidcatmofu/Contents-Director/issues/23).

## NeoForge 1.21.9 and newer

The current ModLauncher service integration is verified through NeoForge on Minecraft 1.21.8. NeoForge 1.21.9 changed loader/FML behavior and the current JAR is not discovered as before.

Support for the newer loader path is tracked in [issue #19](https://github.com/liquidcatmofu/Contents-Director/issues/19).

This limitation does not describe older LaunchWrapper-era Forge or already-working ModLauncher-era environments.
