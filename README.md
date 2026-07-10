# Squadventure

A fully local, offline **Squadrats** rework for Android. Record your walks, rides and drives, claim the
OpenStreetMap tiles you pass through — small **squadratinhos** and big **squadrats** — and watch your
territory grow on a bundled world map with a Strava-style trail heatmap. No accounts, no cloud, no
network: your tracks never leave the device.

## Features

- **Record activities** by walk / bike / car / both / other, with a live map and stats.
- **Claim squares** on the OSM slippy-tile grid: squadratinhos (zoom 17, ~200 m) and squadrats
  (zoom 14, ~1.5 km). Track total squares, your biggest **yard** (cluster) and **übersquadrat** (max
  solid block).
- **World map** of all your squares on a bundled offline vector basemap, with a toggle-able **trail heatmap**
  and **transport-mode filters**.
- **History log** of every activity; tap any to see its route, squares, and stats, re-tag its transport
  mode, or **export GPX**.
- **Import GPX** from files, or **share GPX to Squadventure** from any app (health apps included).
  Duplicates are detected and rejected; multi-track files import as multiple activities.
- **Home-screen widget**: total squadrats, squadratinhos and kilometres, with weekly gains.
- **English + German.**

## Privacy

No `INTERNET` permission. No analytics, no crash reporting, no cloud. The map is bundled in the APK;
location comes from the device GPS via `LocationManager`. `allowBackup="false"`.

## Offline basemap

The map is a global **PMTiles** vector basemap (Protomaps daily build, OpenStreetMap-derived, ODbL)
rendered by MapLibre Native — `app/src/main/assets/map/basemap.pmtiles` (zoom 0-7, ~178 MB). It is
**git-ignored** (too large for git, >100 MB) and generated before every build; CI does this
automatically. To build locally, generate it once first:

```bash
scripts/world-asset/generate.sh   # needs the `pmtiles` CLI on PATH
```

The app copies the tileset into internal storage on first launch (MapLibre's PMTiles reader can't
random-access an `asset://` file), so the installed footprint is ~2× the asset (~356 MB). PMTiles is
already gzip-compressed internally, so the APK stores it uncompressed (`noCompress`) — deflate would
save nothing.

## Build

```bash
gw21 :app:testDebugUnitTest     # domain unit tests (no device)
gw21 :app:assembleDebug         # debug APK -> app/build/outputs/apk/debug/
```

See `CLAUDE.md` and `docs/` for architecture, the Squadrats mechanics, and the Android dev setup.

## Releases

Push a `vX.Y.Z` tag (see the `/release` skill). CI builds and publishes `squadventure-vX.Y.Z.apk`.
Sideload it (enable "install unknown apps"). Signed with a debug key for frictionless personal use.
