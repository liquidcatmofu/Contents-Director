# Contents Director

[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1587715?style=flat-square&logo=curseforge&logoColor=white&label=CurseForge)](https://www.curseforge.com/minecraft/mc-mods/contents-director)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/contents-director?style=flat-square&logo=modrinth&logoColor=white&label=Modrinth)](https://modrinth.com/mod/contents-director)

Contents Director is a runtime mod and file installer for Minecraft modpacks. It downloads files that cannot be bundled directly with a pack and installs them during startup.

## Compatibility and JARs

Use the shaded `-all.jar` artifacts. The plain JARs are intermediate build outputs and do not include all runtime dependencies.

| JAR | Target environment |
| --- | --- |
| `ContentsDirector-launchwrapper-*-all.jar` | Forge 1.7.10–1.12.2 using LaunchWrapper |
| `ContentsDirector-modlauncher-*-all.jar` | Forge / compatible ModLauncher-era loaders |
| `ContentsDirector-*-all.jar` | Combined LaunchWrapper + ModLauncher artifact |
| `ContentsDirector-standalone-*.jar` | Standalone/testing entry point |

For LaunchWrapper-era Forge, add:

```text
--tweakClass com.juanmuscaria.modpackdirector.launchwrapper.ModpackDirectorTweaker
```

For the existing ModLauncher integration, the service is discovered through:

```text
META-INF/services/cpw.mods.modlauncher.api.ITransformationService
```

> **Known loader limitation:** the current JAR is verified to load on NeoForge through Minecraft 1.21.8, but NeoForge 1.21.9+ changed loader behavior and is tracked in [issue #19](https://github.com/liquidcatmofu/Contents-Director/issues/19).

## Configuration

Place configuration files in the platform configuration directory, normally:

```text
config/mod-director/
```

A minimal direct-download entry can be written as `example.url.json`:

```json
{
  "url": "https://example.invalid/example-mod.jar",
  "fileName": "example-mod.jar"
}
```

Contents Director also supports CurseForge, Modrinth, remote configuration files, bundles, file modification rules, side filtering, hashes, optional selections, superseding old files, archive extraction, and modpack version checks.

See **[Configuration](configuration.md)** for the complete format.

## Documentation

- **[Configuration](configuration.md)** — file types, provider entries, metadata, installation policy, bundles, remote configs, and modify rules
- **[Localization](localization.md)** — bundled languages, locale behavior, external message overrides, and translation maintenance
- **[Troubleshooting](troubleshooting.md)** — network/TLS failures, manual CurseForge fallback, offline startup, hash failures, and known limitations
- **[Changelog](../CHANGELOG.md)** — unreleased changes prepared for the next release

The original projects' wikis remain useful historical references, but Contents Director's current behavior is documented in this repository.

## Fork lineage

```text
Janrupf/mod-director
  └─ juanmuscaria/ModpackDirector
       └─ TerraFirmaGreg-Team/AutoPack-Director
            └─ liquidcatmofu/Contents-Director
```

## Notable changes in this fork

Current unreleased work includes stronger download/hash validation, staged installation and commit handling, safer remote configuration loading, offline-friendly provider metadata handling, a CurseForge manual-download fallback, macOS Swing isolation, expanded regression coverage, and Japanese/Simplified Chinese localization.

See the changelog for a release-oriented summary.

## Building

Building requires JDK 17. The project compiles Java sources as UTF-8 and targets Java 8 bytecode through Jabel. Tests execute with a Java 8 toolchain.

```bash
./gradlew clean build --no-daemon
```

Output JARs are written under the root/subproject `build/libs/` directories.

## Credits

- [Mod Director](https://github.com/Janrupf/mod-director) — original project
- [Modpack Director](https://github.com/juanmuscaria/ModpackDirector) — UI and feature additions
- [AutoPack Director](https://github.com/TerraFirmaGreg-Team/AutoPack-Director) — direct upstream fork
- [FileDirector](https://github.com/TerraFirmaCraft-The-Final-Frontier/FileDirector) — file-processing features
- [FlatLaf](https://github.com/JFormDesigner/FlatLaf) — UI theming
