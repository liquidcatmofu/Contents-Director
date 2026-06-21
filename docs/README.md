# Contents Director

Runtime mod and file installer for Minecraft modpacks.

Contents Director downloads and installs mods/files that cannot be bundled with a modpack
(due to distribution restrictions, launcher limitations, copyright, etc.) at game startup.

## Fork lineage

```
Janrupf/mod-director
  └─ juanmuscaria/ModpackDirector
       └─ TerraFirmaGreg-Team/AutoPack-Director  (upstream)
            └─ liquidcatmofu/Contents-Director   (this fork)
```

### Changes in this fork

- Connection and read timeouts added to all HTTP requests (15 s / 30 s) — prevents the game from hanging indefinitely when the network is unreliable
- Network errors (DNS failure, connection timeout, SSL errors, etc.) now produce a human-readable message instead of a raw stack trace
- An error dialog is shown to the user before the game exits, so they know *why* it failed rather than just seeing exit code 1
- Version scheme: `1.x.y-lc.z` to distinguish from upstream releases

## Which JAR to use

| JAR | Target environment |
|-----|--------------------|
| `launchwrapper-*-all.jar` | Forge 1.7.10 – 1.12.2 (LaunchWrapper) |
| `modlauncher-*-all.jar` | Forge 1.13+ (ModLauncher) |
| `universal-*-all.jar` | Either of the above (larger, includes both) |
| `standalone-*.jar` | Run without Minecraft, for testing |

Always use the `-all.jar` variant — it bundles all required dependencies.
The plain `.jar` files are intermediate build artifacts and are not meant to be used directly.

## Installation

Place the appropriate `-all.jar` in the Minecraft instance's `mods/` folder (or wherever your launcher
expects tweakers/services).

For **LaunchWrapper** (old Forge), add the following JVM argument:

```
--tweakClass com.juanmuscaria.modpackdirector.launchwrapper.ModpackDirectorTweaker
```

For **ModLauncher** (new Forge), the service is discovered automatically via
`META-INF/services/cpw.mods.modlauncher.api.ITransformationService`.

## Configuration

Config files go in `config/mod-director/` inside the game directory.
Each file describes one or more mods to install.

### URL download (`*.url.json`)

```json
{
  "type": "url",
  "url": "https://example.com/mymod-1.0.jar",
  "fileName": "mymod-1.0.jar",
  "metadata": {
    "sha256": "abc123..."
  }
}
```

### CurseForge (`*.curse.json`)

```json
{
  "type": "curse",
  "addonId": 123456,
  "fileId": 7890123
}
```

### Optional: `modpack.json`

Place `modpack.json` in `config/mod-director/` to configure pack-level settings:

```json
{
  "packName": "My Modpack",
  "remoteVersion": "https://example.com/version.txt",
  "localVersion": "1.0.0",
  "refuseLaunch": true
}
```

`remoteVersion` points to a plain-text URL that returns the current version string.
If it does not match `localVersion`, the user is warned (and optionally blocked from launching).

## Building

Requires JDK 17 (targets Java 8 bytecode via Jabel).

```bash
./gradlew build
```

Output JARs are in each subproject's `build/libs/`.

## Credits

- [Mod Director](https://github.com/Janrupf/mod-director) — original project
- [Modpack Director](https://github.com/juanmuscaria/ModpackDirector) — UI and feature additions
- [AutoPack Director (upstream)](https://github.com/TerraFirmaGreg-Team/AutoPack-Director) — direct upstream fork
- [FileDirector](https://github.com/TerraFirmaCraft-The-Final-Frontier/FileDirector) — file processing features
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) — UI theming
