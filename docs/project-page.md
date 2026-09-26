# Contents Director

Contents Director is a runtime mod and file installer for Minecraft modpacks. It downloads and installs files that cannot be bundled directly with a modpack, using configuration supplied by the pack author.

It is intended primarily for **modpack authors** who need a reproducible way to fetch optional, external, or provider-hosted files when a pack starts.

## Features

- Download files from direct URLs
- Install files from CurseForge and Modrinth references
- Verify downloaded files with hashes
- Stage downloads before replacing existing files
- Support optional user-selectable components
- Supersede, disable, move, rename, or delete old files
- Extract ZIP-compatible archives
- Load bundled and remote configuration
- Filter entries by client/server side
- Check modpack versions
- Provide a manual CurseForge download fallback on graphical clients
- Localized UI with English, Japanese, and Simplified Chinese support

## Compatibility

The distributed project file is the combined shaded artifact:

```text
ContentsDirector-<version>-all.jar
```

It contains both the LaunchWrapper and ModLauncher integrations and is the recommended artifact for normal use.

Contents Director supports:

- Forge 1.7.10–1.12.2 through LaunchWrapper
- Forge and compatible ModLauncher-era loaders
- NeoForge through Minecraft 1.21.8

> **Known limitation:** NeoForge 1.21.9 and newer changed loader behavior and are not currently supported.

Contents Director targets **Java 8 bytecode** for runtime compatibility.

## Installation

Place the Contents Director `-all.jar` in the modpack's normal mods directory.

For LaunchWrapper-era Forge (1.7.10–1.12.2), add the following launch argument:

```text
--tweakClass com.juanmuscaria.modpackdirector.launchwrapper.ModpackDirectorTweaker
```

ModLauncher-era environments discover the integration automatically through the transformation service.

## Configuration

Configuration files normally belong in:

```text
config/mod-director/
```

A minimal direct-download entry, for example `example.url.json`, looks like:

```json
{
  "url": "https://example.invalid/example-mod.jar",
  "fileName": "example-mod.jar"
}
```

Contents Director supports several configuration types:

- `*.url.json` — direct downloads
- `*.curse.json` — CurseForge files
- `*.modrinth.json` — Modrinth versions
- `*.modify.json` — move, rename, disable, or delete files
- `*.bundle.json` — combine multiple entries
- `*.remote.json` — load configuration from HTTP(S)
- `modpack.json` — pack-level UI and version settings

For the complete format and examples, see the [configuration documentation](https://github.com/liquidcatmofu/Contents-Director/blob/main/docs/configuration.md).

## Safety and reliability

Downloads are staged before being committed to the live installation. Configured hashes are checked before replacement, and failed staging or validation does not overwrite an existing working file.

Archive extraction and file modification operations validate paths to prevent escaping the intended installation directory.

## Documentation

- [Project documentation](https://github.com/liquidcatmofu/Contents-Director/blob/main/docs/README.md)
- [Configuration reference](https://github.com/liquidcatmofu/Contents-Director/blob/main/docs/configuration.md)
- [Troubleshooting](https://github.com/liquidcatmofu/Contents-Director/blob/main/docs/troubleshooting.md)
- [Localization](https://github.com/liquidcatmofu/Contents-Director/blob/main/docs/localization.md)
- [Changelog](https://github.com/liquidcatmofu/Contents-Director/blob/main/CHANGELOG.md)
- [Issue tracker](https://github.com/liquidcatmofu/Contents-Director/issues)

## Credits

Contents Director continues the work of:

- [Mod Director](https://github.com/Janrupf/mod-director)
- [Modpack Director](https://github.com/juanmuscaria/ModpackDirector)
- [AutoPack Director](https://github.com/TerraFirmaGreg-Team/AutoPack-Director)
- [FileDirector](https://github.com/TerraFirmaCraft-The-Final-Frontier/FileDirector)
