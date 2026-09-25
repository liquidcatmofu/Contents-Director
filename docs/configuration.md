# Configuration

Contents Director loads configuration from the platform configuration directory, normally `config/mod-director/`.

`modpack.json` is loaded first. Contents Director then recursively scans the directory for other `.json` files, sorts their paths, and dispatches them by filename suffix.

## File types

| Suffix / name | Purpose |
| --- | --- |
| `modpack.json` | Pack-level UI/version settings |
| `*.curse.json` | CurseForge file |
| `*.modrinth.json` | Modrinth version file |
| `*.url.json` | Direct URL / followed URL download |
| `*.modify.json` | Move, rename, disable, or delete an existing file/folder |
| `*.bundle.json` | Multiple CurseForge, Modrinth, URL, and modify entries in one file |
| `*.remote.json` | Load another recognized config from an HTTP(S) URL |

Unknown `.json` suffixes are ignored with a warning.

## `modpack.json`

Example:

```json
{
  "packName": "Example Pack",
  "localVersion": "1.2.0",
  "remoteVersion": "https://example.invalid/version.txt",
  "refuseLaunch": true,
  "requiresRestart": true,
  "uiTheme": "material-dark",
  "icon": {
    "path": "config/mod-director/icon.png",
    "width": 64,
    "height": 64
  }
}
```

| Field | Type | Behavior |
| --- | --- | --- |
| `packName` | string | Required pack name shown by the UI. |
| `icon` | object | Optional `path`, `width`, and `height`. |
| `localVersion` | string | Local pack version used by version checks and `installationPolicy.modpackVersion`. |
| `remoteVersion` | URL | Optional text endpoint. The first line is read as the remote pack version. |
| `refuseLaunch` | boolean | When a known local/remote version mismatch exists, refuse launch instead of only showing a warning. |
| `requiresRestart` | boolean | Request a restart after fresh installations complete. |
| `uiTheme` | string | UI theme. Defaults to `material-dark`. |

Accepted theme aliases currently include `material-dark` / `dark`, `intellij-light` / `intellij`, `intellij-dark` / `dracula`, `mac-light`, and `mac-dark`. Unknown names fall back to FlatLaf's light theme.

On macOS, the interactive UI runs in an external helper process. Theme propagation to that helper is still tracked in [issue #23](https://github.com/liquidcatmofu/Contents-Director/issues/23).

## Common remote-entry fields

The following fields are shared by `*.curse.json`, `*.modrinth.json`, and `*.url.json`.

| Field | Type | Behavior |
| --- | --- | --- |
| `metadata` | object | Optional side/hash metadata. |
| `installationPolicy` | object | Optional installation/selection policy. |
| `options` | object | Optional loader-specific options retained with the installed entry. Defaults to an empty object. |
| `folder` | string | Target folder. Omitted means the platform's normal mods directory; `"."` means the installation root; another value selects a custom folder under the installation root. |
| `inject` | boolean | Advanced loader-injection flag. When omitted it defaults to `true` if `folder` is omitted, otherwise `false`. |

Resolved install targets are normalized and must remain inside the installation root.

## Metadata

Example:

```json
{
  "metadata": {
    "side": "CLIENT",
    "hash": {
      "SHA-256": "0123456789abcdef..."
    }
  }
}
```

### `side`

Supported enum values are:

- `CLIENT`
- `SERVER`
- `UNKNOWN`

If `side` is omitted or `UNKNOWN`, the entry is unrestricted. A platform side of `UNKNOWN` is also treated as unrestricted.

### `hash`

`hash` maps Java `MessageDigest` algorithm names to expected hexadecimal digests. Hash comparison is case-insensitive and ignores surrounding whitespace on the expected digest.

Every configured algorithm supported by the running JVM must match. Unsupported algorithms are skipped with a warning. If no configured algorithm is supported, hash status is treated as unknown rather than a mismatch.

Downloads are staged first; configured hashes are checked against the staged file before it replaces the live installation.

## Installation policy

Example:

```json
{
  "installationPolicy": {
    "optionalKey": "$",
    "selectedByDefault": true,
    "name": "Example optional mod",
    "description": "Adds an optional client feature",
    "downloadAlways": false,
    "supersedes": [
      "example-old-*.jar"
    ],
    "deleteSuperseded": false
  }
}
```

| Field | Default | Behavior |
| --- | --- | --- |
| `continueOnFailedDownload` | `false` | Provider query/download failures are severe by default. When true, the failure is recorded as a warning so processing may continue. |
| `optionalKey` | `null` | `null`: always install when needed. `"$"`: independent checkbox. Any other string groups entries under the same mutually exclusive selection group. |
| `selectedByDefault` | `true` when `optionalKey` is present, otherwise `false` | Initial state for optional choices. |
| `name` | provider/offline name | Display name for an optional choice. |
| `description` | `null` | Optional choice description. |
| `extract` | `false` | For URL entries, extract the downloaded ZIP into the target directory during staging. |
| `deleteAfterExtract` | `false` | With URL extraction, do not keep/publish the downloaded archive as the primary file. |
| `downloadAlways` | `false` | Redownload an existing target when it has not already been accepted by the earlier metadata/hash check. A matching (or indeterminate) configured hash can still cause the existing file to be kept first. |
| `supersede` | `null` | Legacy single glob pattern for old files in the target directory. |
| `supersedes` | `null` | Preferred list of glob patterns. When non-empty, it takes precedence over `supersede`. |
| `deleteSuperseded` | `false` | Delete matched old files. When false, matched files are renamed with `.disabled-by-mod-director`. |
| `modpackVersion` | `null` | When a pack version is known, include the entry only when this value matches the remote pack version, or the local version when no remote version was obtained. If neither pack version is available, this field does not exclude the entry. |

Files deselected through the optional-selection UI are marked with a sibling `.disabled-by-mod-director` file so the choice can be retained.

## CurseForge: `*.curse.json`

Example:

```json
{
  "addonId": 12345,
  "fileId": 67890,
  "fileName": "example-mod.jar",
  "manualDownloadUrl": "https://www.curseforge.com/minecraft/mc-mods/example-mod/files/67890",
  "metadata": {
    "side": "CLIENT"
  }
}
```

| Field | Type | Behavior |
| --- | --- | --- |
| `addonId` | integer | Required CurseForge project/addon ID. |
| `fileId` | integer | Required CurseForge file ID. |
| `fileName` | string | Optional explicit target filename. When present, pre-install target resolution does not require provider metadata. |
| `manualDownloadUrl` | URL | Optional page/URL to present when automatic download fails. |

Provider metadata is queried through the curse.tools API. Automatic download is attempted first. If it fails on a graphical client, Contents Director switches to the manual-download flow.

When `manualDownloadUrl` is omitted, the fallback download URL is derived from `addonId` and `fileId`.

For offline-friendly startup, specify `fileName` so an already-installed file can be located without a metadata query.

## Modrinth: `*.modrinth.json`

Example:

```json
{
  "versionId": "example-version-id",
  "fileIndex": 0,
  "fileName": "example-mod.jar"
}
```

| Field | Type | Behavior |
| --- | --- | --- |
| `versionId` | string | Required Modrinth version ID. |
| `fileIndex` | integer | Index into the version's `files` array. Defaults to `0`. |
| `fileName` | string | Optional explicit target filename, allowing target resolution without an initial metadata request. |

The actual download URL is obtained from Modrinth's version API when an installation is required.

## Direct URL: `*.url.json`

Example:

```json
{
  "url": "https://example.invalid/files/example.zip",
  "fileName": "example.zip",
  "installationPolicy": {
    "extract": true,
    "deleteAfterExtract": true
  }
}
```

| Field | Type | Behavior |
| --- | --- | --- |
| `url` | URL | Required starting URL. |
| `fileName` | string | Optional target filename. If omitted, the filename is derived from the URL path. |
| `follows` | string[] | Optional legacy HTML-follow sequence. Each string is located in the fetched HTML and the preceding `href` is followed. |

ZIP extraction rejects entries that escape the extraction root and entries whose path traverses a symbolic link.

## Modify: `*.modify.json`

Modify entries operate on existing installation files.

Rename a file:

```json
{
  "folder": "mods",
  "fileName": "old-name.jar",
  "newFileName": "new-name.jar"
}
```

Move and rename:

```json
{
  "folder": "mods",
  "fileName": "old-name.jar",
  "newFolder": "disabled-mods",
  "newFileName": "new-name.jar"
}
```

Disable a file:

```json
{
  "folder": "mods",
  "fileName": "example.jar",
  "disable": true
}
```

Delete a directory recursively:

```json
{
  "folder": "old-directory",
  "delete": true
}
```

| Field | Behavior |
| --- | --- |
| `folder` | Required source folder, resolved from the installation root. |
| `fileName` | Source filename. When omitted, only directory deletion has an action. |
| `disable` | Renames the source file to `<name>.disabled-by-mod-director`. This takes precedence over delete/move/rename. |
| `delete` | Deletes the source file, or recursively deletes `folder` when `fileName` is omitted. |
| `newFolder` | Destination folder under the installation root. |
| `newFileName` | Destination filename. |

If a move/rename destination already exists, the existing destination is moved to `.disabled-by-mod-director` before the source is published there.

All configured source and destination paths are validated to remain inside the installation root. Symbolic-link traversal is rejected.

## Bundles: `*.bundle.json`

A bundle can combine arrays named `curse`, `modrinth`, `url`, and `modify`:

```json
{
  "curse": [
    {
      "addonId": 12345,
      "fileId": 67890,
      "fileName": "curse-example.jar"
    }
  ],
  "modrinth": [
    {
      "versionId": "example-version"
    }
  ],
  "url": [
    {
      "url": "https://example.invalid/example.jar"
    }
  ],
  "modify": [
    {
      "folder": "mods",
      "fileName": "obsolete.jar",
      "delete": true
    }
  ]
}
```

All modify entries in a bundle are resolved and path-validated before any of those modify actions are applied. This prevents an invalid later path from allowing earlier modify actions to run first.

## Remote configs: `*.remote.json`

Example:

```json
{
  "url": "https://example.invalid/config/example.bundle.json"
}
```

The remote URL's **path basename** determines how the downloaded data is parsed, so the URL must identify a file with a recognized suffix such as `.bundle.json`, `.curse.json`, `.modrinth.json`, `.url.json`, `.modify.json`, or `.remote.json`.

Remote configs are parsed in memory rather than written into the live configuration directory. Nested remote configs are limited to 16 levels, and active recursion cycles are rejected.

## Processing and safety notes

- Local `modpack.json` is processed before other configuration files.
- Other local JSON files are recursively discovered and processed in sorted path order.
- Downloads are staged before hash validation and commit.
- A failed stage/hash does not replace an existing live target.
- Modify paths and ZIP extraction paths are confined to the installation/extraction root.
- Remote configuration downloads do not create files based on remote filenames.
