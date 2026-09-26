# Changelog

This changelog starts with the current unreleased work. Older release history has not been reconstructed here.

## Unreleased

### Added

- Manual CurseForge download fallback with browser/copy/select and Downloads-directory detection.
- Japanese and Simplified Chinese UI translations.
- Localization documentation and regression coverage for complete key sets, placeholders, external overrides, and fallback behavior.
- Linux and Windows CI coverage with a Java 8 test runtime.
- Regression tests for installer staging/commit behavior, network redirects/errors, partial downloads, provider response validation, side metadata, remote configuration safety, and offline startup paths.

### Changed

- Installation now separates pre-install resolution, staging, hash validation, commit, and deferred cleanup responsibilities.
- Provider configuration can use explicit filenames to avoid unnecessary metadata queries when resolving already-installed files.
- Jackson dependencies are aligned through a common BOM.
- Java compilation explicitly uses UTF-8 for cross-platform source consistency.
- macOS interactive Swing UI is isolated in an external helper JVM.

### Fixed

- Existing live files are preserved when staging or hash validation fails.
- Hash validation checks all supported configured digests and accepts case-insensitive hexadecimal input.
- Archive extraction and modify operations reject paths that escape their allowed roots or traverse symbolic links.
- URL extraction rejects unsupported/malformed non-ZIP archives instead of silently treating them as empty ZIP content.
- Cross-task supersede cleanup no longer removes files published by another install task.
- Omitted metadata `side` is treated as unrestricted.
- Remote configuration files are parsed in memory, use URL basenames only for type dispatch, reject recursion cycles/depth overflow, and validate bundle modify paths before mutations.
- External localization overrides now resolve through absolute filesystem paths consistently across Windows, Linux, and macOS.
- Partial translations fall back to canonical English instead of an unrelated JVM system-locale bundle.

### Known limitations

- NeoForge 1.21.9+ loader integration is not yet supported; tracked in [#19](https://github.com/liquidcatmofu/Contents-Director/issues/19).
- The macOS external UI helper does not yet inherit the configured UI theme; tracked in [#23](https://github.com/liquidcatmofu/Contents-Director/issues/23).
