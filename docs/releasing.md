# Releasing

Contents Director releases are created by pushing a matching Git tag.

The current release workflow is defined in `.github/workflows/release.yml`.

## Version scheme

Contents Director uses SemVer for the project version.

- Gradle project version: `X.Y.Z`
- Git tag: `vX.Y.Z`
- GitHub Release: `vX.Y.Z`
- JARs:
  - `ContentsDirector-X.Y.Z-all.jar`
  - `ContentsDirector-launchwrapper-X.Y.Z-all.jar`
  - `ContentsDirector-modlauncher-X.Y.Z-all.jar`

The version used in JAR filenames comes from the root `build.gradle`:

```groovy
version '1.0.0'
```

The release workflow currently does **not** verify that the Git tag matches this Gradle version. Always update `build.gradle` before creating a new tag.

## Published artifacts

A release tag builds the project and publishes:

### GitHub Release

All three shaded runtime artifacts:

- universal
- LaunchWrapper
- ModLauncher

### CurseForge and Modrinth

Only the universal shaded artifact:

```text
ContentsDirector-X.Y.Z-all.jar
```

The current publishing metadata is configured as:

- loader: `forge`
- game versions: `>=1.7.10`

Known compatibility limitations documented elsewhere still apply; in particular, NeoForge 1.21.9+ is not currently supported.

## Repository configuration

The workflow uses these repository secrets:

- `CURSEFORGE_TOKEN`
- `MODRINTH_TOKEN`

The GitHub Release uses the workflow-provided `GITHUB_TOKEN`.

The CurseForge and Modrinth project IDs are currently defined directly in `.github/workflows/release.yml`.

## Release channels

The current workflow triggers for every tag matching:

```text
v*
```

It does not currently derive alpha/beta/release channels from SemVer prerelease suffixes. Unless the workflow is extended first, use stable `vX.Y.Z` tags for public releases.

## Release procedure

1. Choose the next SemVer version.
2. Update the root `build.gradle` version to that exact version.
3. Rename the `## Unreleased` section in `CHANGELOG.md` to `## [X.Y.Z] - YYYY-MM-DD`.
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
    git tag vX.Y.Z
    git push origin vX.Y.Z
    ```

11. Monitor the **Release** GitHub Actions workflow.
12. Verify the resulting GitHub Release contains all three expected shaded JARs.
13. Verify Modrinth and CurseForge received the universal JAR with the intended version.
14. Smoke-test the published artifact rather than only the locally built copy.

## Verification checklist

Before considering a release complete, verify:

- tag is `vX.Y.Z`;
- `build.gradle` contains `version 'X.Y.Z'`;
- GitHub Release is attached to the intended commit;
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

If the workflow fails before anything is publicly published, fix the release configuration and create the release again only after confirming the failed state is safe to retry.

If GitHub, CurseForge, or Modrinth succeeds while another target fails, treat it as a partial release. Prefer repairing/re-running the publishing workflow for the same immutable source commit rather than rebuilding different source under the same version.

## Current workflow limitations

Compared with stricter release automation, the current workflow does not yet validate:

- tag/version equality;
- SemVer syntax or prerelease channel;
- presence of a matching changelog section;
- expected JAR filenames before publishing;
- Java class-file target compatibility;
- publishing credentials before the build/publish stage.

These are candidates for future hardening before or after the next release.
