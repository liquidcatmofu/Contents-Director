# Contents Director

[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1587715?style=flat-square&logo=curseforge&logoColor=white&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/contents-director)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/contents-director?style=flat-square&logo=modrinth&logoColor=white&label=Modrinth)](https://modrinth.com/mod/contents-director)

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
- Versioning restarted at `1.0.0` as an independent project

## Which JAR to use

| JAR | Target environment |
|-----|--------------------|
| `ContentsDirector-launchwrapper-*-all.jar` | Forge 1.7.10 – 1.12.2 (LaunchWrapper) |
| `ContentsDirector-modlauncher-*-all.jar` | Forge 1.13+ (ModLauncher) |
| `ContentsDirector-*-all.jar` | Either of the above (larger, includes both) |
| `ContentsDirector-standalone-*.jar` | Run without Minecraft, for testing |

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

## Documentation

- **[Localization](localization.md)** — bundled languages, locale selection, message overrides, and translation maintenance

Configuration format and available options are currently documented in the upstream wikis:

- **[Mod Director wiki](https://github.com/Janrupf/mod-director/wiki)** — config file format, installation policy, modpack.json, supported mod types (CurseForge, raw URL, …)
- **[FileDirector wiki](https://github.com/TerraFirmaCraft-The-Final-Frontier/FileDirector/wiki)** — bundle config, modify config (rename/disable/delete files), Modrinth support

Config files go in `config/mod-director/` inside the game directory.

### Manual CurseForge fallback

CurseForge entries may optionally provide `manualDownloadUrl` together with an explicit `fileName`.
If `manualDownloadUrl` is omitted, Contents Director uses CurseForge's website download endpoint derived
directly from `addonId` and `fileId`:
`https://www.curseforge.com/api/v1/mods/{addonId}/files/{fileId}/download`.
This avoids requiring a project slug. Automatic provider download is attempted first. If that fails on a
graphical client, Contents Director asks
whether to open the configured page in the default browser or copy the download URL to the clipboard, then
watches the user's Downloads directory for the expected filename. When a newly downloaded file becomes
stable, the dialog offers a **Use downloaded file** action; the user can always choose **Select downloaded
file...** instead. On Linux, `XDG_DOWNLOAD_DIR` from `user-dirs.dirs` is honored (including
`XDG_CONFIG_HOME`), with `~/Downloads` as a fallback. The selected file is staged and any configured
hashes are verified before the existing installation is replaced.

```json
{
  "addonId": 12345,
  "fileId": 67890,
  "fileName": "example-mod.jar",
  "manualDownloadUrl": "https://www.curseforge.com/minecraft/mc-mods/example-mod/files/67890"
}
```

On headless/server environments, the URL and expected destination are logged instead of opening a browser.
For reliable offline/manual fallback, `fileName` should be specified because the target must be known without
querying provider metadata. `manualDownloadUrl` is only needed when the pack author wants to point users to
a more specific page than the automatically generated CurseForge project page.

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
