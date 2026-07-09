# Squadventure — project guide

Pure-native **Android** app: a **fully local, offline** rework of [Squadrats](https://squadrats.com).
Record GPS activities (walk / bike / car / both / other), and "claim" the OpenStreetMap tiles you
pass through — small **squadratinhos** (zoom-17) and big **squadrats** (zoom-14). Browse a world map
of all your squares with a Strava-style trail heatmap, keep a history log, export/import GPX, receive
shared GPX from other apps, and see totals in a home-screen widget. **Phone-only, Android-only.**

This repo's engineering scaffold (Gradle setup, foreground service, widget, shortcuts, tag-driven
release CI, deterministic signing) is derived from `~/Code/android-local-transcribe`; see
`docs/android-dev.md` for the transferable how-to.

## Non-negotiable: stays offline
The app declares **no `INTERNET` permission** and `allowBackup="false"`. This is a product
guarantee. Never add a networking library, analytics, crash reporting, map-tile SDK that fetches
over the network, or the `INTERNET` permission. The world map is a **bundled low-poly vector**
(`app/src/main/assets/world/`), rendered by our own Compose Canvas in Web Mercator — no online tiles.
Location comes from `LocationManager` (GPS), **not** Play Services (which is a networked dependency).

## Stack / versions (source of truth: `app/build.gradle.kts`, `gradle/wrapper`)
- Kotlin 2.0.21, Jetpack **Compose** (BOM 2024.10.01), AGP **8.7.3**, Gradle **8.11.1**.
- `compileSdk`/`targetSdk` **35**, `minSdk` **26**. App bytecode target **Java 17**.
- **Build JDK: 21** (`gw21`). CI uses JDK 21. No version catalog — deps are inline in `app/build.gradle.kts`.
- No native code / no ABI splits (unlike transcribe): a single universal APK.

## Build & test
```bash
gw21 :app:testDebugUnitTest        # pure-Kotlin domain tests (tile math, metrics, GPX) — no device
gw21 :app:assembleDebug            # debug APK
gw21 :app:assembleRelease          # R8-minified release APK -> app/build/outputs/apk/release/app-release.apk
```
`gw21` = `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew`. The domain layer is deliberately pure
Kotlin so it runs as fast JVM unit tests (transcribe could only test on-device). Keep it that way:
put Android-free logic under `core/` and test it.

## Architecture
```
core/                       platform-agnostic, JVM-unit-tested
  geo/SlippyTile            OSM tile math: lat/lon<->tile at z14/z17, per-latitude sizes, packed Long keys
  geo/Geo                   haversine distance / path length
  geo/TileClaims            point stream -> claimed squadrat/squadratinho sets
  geo/WebMercator           normalized [0,1]^2 world projection shared by tiles + the map canvas
  metrics/SquareMetrics     total, yard (largest 4-connected cluster), übersquare (largest solid NxN)
  model/                    TransportMode, TrackPoint, ActivityMeta/Record, ActivitySource
  gpx/Gpx                   GPX read (multi-<trk>) / write / stable content hash for dedup
  activity/ActivityRepository   filesDir/activities/<id>/{track.gpx, meta.json, squares.json}
  tracking/TrackingController    singleton StateFlows: isTracking, elapsedMs, distanceMeters, path, liveSquares
  location/LocationSource        interface + GpsLocationSource (LocationManager); fakeable in tests
phone/                      Android UI + entry points
  MainActivity             single activity + Navigation Compose (map / history / active / detail)
  TrackingService          foreground service (foregroundServiceType=location), wake lock, notification, widget push
  map/                     MapCanvas (Compose Canvas Mercator map), overlays (squares, route, heatmap)
  StatsWidgetProvider      RemoteViews widget: squadrats / squadratinhos / km + "+N this week"
  CreateActivityShortcutActivity, ImportActivity (share target), ui/
```
Data flow mirrors transcribe: a **singleton `object` (`TrackingController`)** holds live state as
`StateFlow`s; the foreground service, Compose UI, and widget all read the *same* singleton — no DI,
no IPC. Persistence is plain files + `kotlinx.serialization` (no Room/DataStore). See
`docs/architecture.md`.

## Domain facts you must not get wrong (see docs/squadrats-mechanics.md)
- **squadrat = zoom 14** (big, ~1.5 km at 52°N, ~2.45 km at equator); **squadratinho = zoom 17**
  (small, ~186 m / ~306 m). One squadrat = 8×8 = **64** squadratinhos (`x14 = x17 shr 3`).
- Square sizes are **latitude-dependent** (`edge = 40075016.686 / 2^zoom · cos(lat)`). Never hardcode
  "1 mile" / "200 m".
- **Yard** = largest 4-connected cluster; **Übersquadrat** = largest solid N×N block.
- Heatmap = **trail density of actual GPX paths** (Strava-style), not per-square counts. Toggle-able.

## Naming: user-facing vs internal
Internal code keeps the precise domain names `squadrat` (z14) and `squadratinho` (z17). **User-facing
labels are different**: a squadrat is a **"Square" / "Quadrat"** and a squadratinho is a
**"Mini-Square" / "Mini-Quadrat"**. Never surface "squadrat"/"squadratinho" in the UI — go through the
`stat_squadrats` / `stat_squadratinhos` (and `map_layer_*`) string resources.

## Localization
English (default `res/values/strings.xml`) + German (`res/values-de/strings.xml`). **No hardcoded
user-facing strings** in Compose or widgets — always `stringResource`/`getString`. Add both languages
whenever you add a string.

## Releasing
Use the **`/release`** skill (`.claude/skills/release/`). Short version: bump `versionCode` +
`versionName` in `app/build.gradle.kts`, commit/push `main`, then `git tag vX.Y.Z && git push origin
vX.Y.Z`. CI (`.github/workflows/release.yml`) runs the unit tests, builds, signs with the
`DEBUG_KEYSTORE_BASE64` secret, and publishes the GitHub release with `squadventure-vX.Y.Z.apk`.

## Code style
No inline comments. Concise `/** … */` doc comments only where intent is non-obvious (why, not what).

## Environment notes
- Android SDK: `/opt/homebrew/share/android-commandlinetools` (`local.properties` `sdk.dir`; gitignored).
- macOS has no `timeout`; use per-tool flags. `gw21`/`gw25` aliases pick the JDK.
- Emulator boots headless with `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`.
- Sanity-check the offline guarantee on any APK: `apkanalyzer manifest permissions <apk>` must NOT
  list `android.permission.INTERNET`.
