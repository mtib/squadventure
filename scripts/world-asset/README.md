# Offline basemap asset

`app/src/main/assets/map/basemap.pmtiles` is the bundled offline vector basemap. MapLibre Native
renders it (with `assets/map/style.json`) from a copy placed in internal storage at first launch —
see `phone/map/MapView.kt`. The app has no `INTERNET` permission; this file is the whole map.

## Source & license

[Protomaps](https://protomaps.com/) daily basemap build (vector tiles derived from **OpenStreetMap**),
**ODbL** — attribution "© OpenStreetMap" is displayed by the map's attribution control (do not remove
it). Builds: https://maps.protomaps.com/builds (dated `YYYYMMDD.pmtiles`).

## Current tileset

Global, **zoom 0–6** (~43 MB). This is country/region level: it gives worldwide coverage and bearings
within the ~50 MB budget, but **no city/street detail** (streets live at z12+). MapLibre overzooms it
at high zoom, so a city looks coarse.

## Regenerating / changing zoom & coverage

Needs the [`pmtiles` CLI](https://github.com/protomaps/go-pmtiles/releases). `pmtiles extract` streams
only the requested tiles from the remote build (HTTP range requests — no full-planet download).

```bash
# Global overview (current):
MAXZOOM=6 scripts/world-asset/generate.sh

# Higher global zoom roughly doubles size per level (z7 ≈ ~85MB, over budget).
```

**For walk/bike/city detail** (the levels users actually use), bundle a *regional* high-zoom extract
and keep the global overview for country bearings — global city detail can't fit ~50 MB. Two ways:

```bash
# A regional extract at street zoom (example bbox around Denmark / Øresund):
pmtiles extract https://build.protomaps.com/<YYYYMMDD>.pmtiles region.pmtiles \
  --bbox=7.5,54.4,13.5,58.0 --maxzoom=14
```

Then either (a) ship BOTH `basemap.pmtiles` (global z0–6) and `region.pmtiles` and add a second
`vector` source + duplicated layers (min/max-zoom split) in `style.json`, or (b) if you only care
about one region, ship just the regional extract. `basemap.pmtiles` must stay `noCompress` in
`app/build.gradle.kts` (random-access reads).
