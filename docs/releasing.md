# Releasing

Contents Director releases are created by pushing a matching Git tag.

The release workflow is defined in `.github/workflows/release.yml`.

## Version scheme

Contents Director uses SemVer for the project version.

- Gradle project version: `X.Y.Z` or a supported prerelease such as `X.Y.Z-beta.1`
- Git tag: `v<Gradle version>`
- GitHub Release: `v<Gradle version>`
- JARs:
  - `ContentsDirector-<version>-all.jar`
  - `ContentsDirector-launchwrapper-<version>-all.jar`
  - `ContentsDirector-modlauncher-<version>-all.jar`

The version used in JAR filenames comes from the root `build.gradle`:

```groovy
version '1.0.0'
```

The release workflow verifies that the pushed Git tag exactly matches this Gradle version.

## Published artifacts

A release tag builds and validates the project before publishing.

### GitHub Release

All three shaded runtime artifacts:

- universal
- LaunchWrapper
- ModLauncher

### CurseForge and Modrinth

Only the universal shaded artifact:

```text
ContentsDirector-<version>-all.jar
```

Publishing metadata is configured as:

- loader: `forge`
- game versions: `>=1.7.10`

Known compatibility limitations documented elsewhere still apply; in particular, NeoForge 1.21.9+ is not currently supported.

## Repository configuration

The workflow requires these repository secrets:

- `CURSEFORGE_TOKEN`
- `MODRINTH_TOKEN`

It also requires these repository variables:

- `CURSEFORGE_PROJECT_ID`
- `MODRINTH_PROJECT_ID`

The workflow validates all four publishing settings before creating the GitHub Release.

The GitHub Release uses the workflow-provided `GITHUB_TOKEN`.

## Release channels

The workflow triggers for tags matching:

```text
v*
```

The version itself must be valid SemVer. Supported release channels are:

- stable: `X.Y.Z` -> provider type `release`;
- alpha: `X.Y.Z-alpha` or `X.Y.Z-alpha.N` -> provider type `alpha`;
- beta: `X.Y.Z-beta` or `X.Y.Z-beta.N` -> provider type `beta`;
- release candidate: `X.Y.Z-rc` or `X.Y.Z-rc.N` -> provider type `beta`.

Other prerelease identifiers are rejected by the workflow.

GitHub Releases for alpha, beta, and rc versions are marked as prereleases and are not marked as the latest release.

## Workflow validation

Before publishing, the workflow verifies:

- `build.gradle` contains a valid SemVer version;
- the Git tag is exactly `v<Gradle version>`;
- `CHANGELOG.md` contains a matching `## [<version>]` section;
- the clean Gradle build and tests succeed;
- all three expected shaded JAR filenames exist;
- release notes extracted from the matching changelog section are non-empty;
- base class files in the shaded JARs do not exceed the Java 8 class-file target;
- CurseForge and Modrinth project IDs and credentials are configured.

The build output is uploaded once as a short-lived GitHub Actions artifact. GitHub, Modrinth, and CurseForge publishing then run as separate jobs so a failed publishing target can be retried independently.

## Release procedure

1. Choose the next SemVer version.
2. Update the root `build.gradle` version to that exact version.
3. Rename the `## Unreleased` section in `CHANGELOG.md` to `## [<version>] - YYYY-MM-DD`.
4. Add a new empty `## Unreleased` section above it for future work.
5. Review the changelog and documentation for known limitations and compatibility claims.
6. Run a clean local build:

   ```bash
   ./gradlew clean build --no-daemon
   ```

7. Merge the release-preparation changes into `main`.
8. Confirm CI succeeds for the exact `main` commit that will be tagged.
9. Confirm there is no existing conflicting Git tag or provider version.
10. Create an annotated or lightweight tag matching the Gradle version:

    ```bash
    git switch main
    git pull --ff-only
    git tag v<version>
    git push origin v<version>
    ```

11. Monitor the **Release** GitHub Actions workflow.
12. Verify the resulting GitHub Release contains all three expected shaded JARs.
13. Verify Modrinth and CurseForge received the universal JAR with the intended version and release channel.
14. Smoke-test the published artifact rather than only the locally built copy.

## Verification checklist

Before considering a release complete, verify:

- tag is exactly `v<Gradle version>`;
- `build.gradle` contains the intended version;
- `CHANGELOG.md` has a matching version section;
- GitHub Release is attached to the intended commit;
- prerelease state matches the SemVer suffix;
- GitHub Release contains the universal, LaunchWrapper, and ModLauncher `-all.jar` files;
- CurseForge and Modrinth contain the universal `-all.jar`;
- published filenames contain the correct version;
- SHA-256 values can be obtained from the final published files;
- LaunchWrapper and ModLauncher artifacts start successfully in representative supported environments;
- macOS interactive UI still works where applicable;
- localized UI still resolves correctly;
- documented known limitations remain accurate.

## Failed or partial releases

Do not move or reuse a tag after users may have downloaded artifacts from it.

If validation or the build fails before anything is publicly published, fix the release configuration and create the release again only after confirming the failed state is safe to retry.

If GitHub, CurseForge, or Modrinth succeeds while another target fails, treat it as a partial release. The publishing targets are separate jobs; prefer retrying the failed job for the same immutable source commit rather than rebuilding different source under the same version.

## Current workflow limitations

The workflow does not currently perform a published-artifact smoke test against every supported Minecraft/runtime combination. Compatibility still needs representative manual verification after publishing.
