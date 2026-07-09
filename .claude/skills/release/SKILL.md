---
name: release
description: Cut a new release of Squadventure. Use when asked to "release", "ship a version", "bump the version", "cut a release", or publish a new APK. Bumps the version, tags, and lets CI build + publish the APK.
---

# Releasing Squadventure

Releases are built and published by CI on tag push. You bump the version and push a tag; GitHub
Actions runs the tests, builds, signs, and publishes the APK.

## Steps

1. **Make sure `main` is green and committed** (`git status` clean, pushed; `gw21 :app:testDebugUnitTest` passes).

2. **Bump the version** in `app/build.gradle.kts` (`defaultConfig`):
   - `versionCode` = previous + 1 (monotonic integer),
   - `versionName` = the new semver (e.g. `"0.2.0"`).

3. **Commit and push** the bump to `main`:
   ```bash
   git add app/build.gradle.kts
   git commit -m "x.y.z: <one-line summary>"
   git push origin main
   ```

4. **Tag and push the tag** (this triggers the release):
   ```bash
   git tag vX.Y.Z          # tag MUST equal versionName, prefixed with v
   git push origin vX.Y.Z
   ```

## What happens on tag push

`.github/workflows/release.yml` (`ubuntu-latest`):
1. JDK 21 + Android SDK set up.
2. If the `DEBUG_KEYSTORE_BASE64` repo secret is set, it's decoded to a temp file and pinned via
   `DEBUG_KEYSTORE_PATH` (read by the `debug` signingConfig), so APKs are signed with the **same key as
   every prior release** (installs as an in-place update). Without the secret a fresh debug key is
   generated and users must uninstall before updating. The keystore is never committed.
3. `./gradlew :app:testReleaseUnitTest` runs the tile-math / metrics / GPX tests.
4. `./gradlew :app:assembleRelease` builds the single universal APK.
5. A GitHub release named `Squadventure vX.Y.Z` is published with auto-generated notes and one asset:
   `squadventure-vX.Y.Z.apk`.

## Verify

```bash
gh run watch --repo mtib/squadventure
gh release view vX.Y.Z --repo mtib/squadventure --json assets --jq '.assets[].name'
```

## Manual trigger (no new tag)

```bash
gh workflow run release.yml --repo mtib/squadventure -f tag=vX.Y.Z
```

## Notes / gotchas

- **Tag must equal versionName** (prefixed `v`). The asset filename uses the tag.
- **Signing:** keep the `DEBUG_KEYSTORE_BASE64` secret set. If lost, new releases get a different
  signature and users must uninstall before updating.
- **Never add a network dependency / the `INTERNET` permission** — the offline guarantee is a product
  requirement; verify with `apkanalyzer manifest permissions <apk>`.
