# Squadventure architecture & spec

## Product spec (what we're building)

A fully local Squadrats rework. Features, from the brief:

1. **World map** with your claimed squares (both z14 squadrats and z17 squadratinhos) overlaid,
   aligned to OSM tile bounds, on a **bundled low-poly world basemap** (no online tiles).
2. **Record an activity**: pick transport mode (**walk / bike / car / both / other**), record location
   while the app runs; store the full **GPX path** and the squares passed through.
3. **Per-activity view**: a screen like Squadrats' — the route it took and the squares it covered, plus
   stats. Transport mode is **editable** here. **Export as GPX**.
4. **History log**: all past activities, newest first.
5. **Global map layers**: all your current squares + a **Strava-style trail heatmap** of actual GPX
   paths (frequently-travelled routes glow brighter), **toggle-able** on/off.
6. **Filter** the map by transport mode (walk / bike / car / both / other / all).
7. **Import GPX** from the file system (SAF picker, multi-select). Imported activities default to
   **other**; re-taggable in history.
8. **Share target**: the app appears when sharing GPX from other apps (e.g. a health app). Accept
   `application/gpx+xml` **and `*/*`** (health apps mis-tag), and `ACTION_SEND_MULTIPLE`. Multi-`<trk>`
   files import as multiple activities. **Dedup**: if the exact same track was seen before, reject as a
   duplicate; otherwise import.
9. **Widget**: one widget showing **total squadrats (big)**, **total squadratinhos (small)**, and
   **total km**, each with a **"+N in the last week"** delta below.
10. **Localized** in English + German.

## Key decisions

- **Zero network, bundled map.** Strict offline like `android-local-transcribe`: no `INTERNET`
  permission. The world map is a **bundled offline PMTiles vector basemap**
  (`assets/map/basemap.pmtiles`, global, z0-6, + `assets/map/style.json`) rendered by
  **MapLibre Native** (`org.maplibre.gl:android-sdk`) via `asset://map/style.json` — no online
  tiles, no telemetry. App data (squares, route, heatmap, current-location marker) is added as
  runtime GeoJSON sources/layers on top of the style's own layers, using plain lat/lon; the
  `core/geo/WebMercator`/`SlippyTile` normalized-`[0,1]²` projection is still the shared coordinate
  system for squadrat/squadratinho tile math, but MapLibre — not our own canvas — now owns
  projection/pan/zoom for rendering.
- **Location via `LocationManager` (GPS)**, not FusedLocationProvider (Play Services is networked).
- **No DI, plain files + kotlinx.serialization** (see `android-dev.md`).
- **Domain logic is Android-free** under `core/` and JVM-unit-tested.

## Component contracts

### `core/tracking/TrackingController` (singleton `object`)
Live capture hub, read by the service, UI, and widget. Exposes `StateFlow`s:
`isTracking`, `isPaused`, `startedAtMs`, `elapsedMs`, `distanceMeters`, `path: List<TrackPoint>`,
`liveSquadratinhos: Set<Long>`, `transportMode`. Methods: `start(mode)`, `onLocation(TrackPoint)`,
`pause()/resume()`, `stop(): List<TrackPoint>` (returns the recorded path for the caller to persist),
`reset()`. Owns a `CoroutineScope`. No Android imports beyond nothing (keep it pure if possible; a
`SystemClock`-free design takes timestamps in).

### `core/location/LocationSource`
`interface LocationSource { fun start(onFix: (TrackPoint) -> Unit); fun stop() }` with
`GpsLocationSource(context)` using `LocationManager.requestLocationUpdates(GPS_PROVIDER, ...)`. Fakeable
for tests. The service wires `GpsLocationSource` → `TrackingController.onLocation`.

### `phone/TrackingService`
Foreground service (`foregroundServiceType=location`), owns a `GpsLocationSource`, feeds the
controller, shows the ongoing notification (Stop action), holds a partial wake lock, and pushes widget
updates on a 1s loop. On stop, persists the activity via `ActivityRepository.saveRecorded(...)`.

### `phone/map/MapView.kt` — the MapLibre map
- Hosts a `org.maplibre.android.maps.MapView` (`AndroidView`), lifecycle-forwarded via a
  `rememberMapViewWithLifecycle()` helper, loading the bundled `asset://map/style.json` style.
  `MapLibre.getInstance(context)` is called once per `MapView` instance, before construction —
  single-arg, no API key, fully offline.
- Claimed squares (z14 squadrats, z17 squadratinhos), the route/heatmap, and the current-location
  marker are each a `GeoJsonSource` + style layer (`FillLayer`/`LineLayer`/`HeatmapLayer`/
  `CircleLayer`) added above the basemap's own layers once the style loads, then updated in place
  (`setGeoJson(...)`, `setProperties(PropertyFactory...)`) as `claims`/`routes`/`showHeatmap`/
  `showSquares`/`currentLocation` change — no per-frame drawing code of our own.
- The heatmap uses MapLibre's built-in `HeatmapLayer` (point density, not per-square counts) over
  every activity's points; filter by transport mode applies to which activities contribute.

### `phone` screens (Navigation Compose, single activity)
- `map` — global: basemap + all claimed squares + heatmap toggle + transport-mode filter chips.
- `history` — list of `ActivityMeta` (mode icon, date, distance, square counts). Tap → detail.
- `active` — mode picker + Start; while tracking shows live map + stats; Stop persists.
- `detail/{id}` — per-activity map (route + its squares), stats, editable transport mode, delete,
  **export GPX** (FileProvider share).

### Widget (`phone/StatsWidgetProvider`)
Reads persisted totals from `ActivityRepository`: sum of distinct squadrats, distinct squadratinhos
(union across activities), total km, and the deltas gained in the last 7 days (compare all-time vs
activities older than 7 days). Re-rendered on data change and from the service loop.

## Map basemap detail (data tradeoff)

The bundled basemap is Protomaps global vector tiles **z0–6** (~43 MB, `pmtiles extract --maxzoom=6`).
This covers the whole world within the ~50 MB budget, but at city zoom it is overzoomed and coarse
(no streets, generalized coastline) — real city/street detail globally is impossible offline at this
size. MapLibre does proper LOD, so the fix is purely data: either bump the global maxzoom (roughly
doubles size per level — z7 ≈ ~85 MB) or bundle a higher-zoom regional extract (e.g. a Denmark/Europe
`--bbox … --maxzoom=13` extract) merged with the global overview. Regenerate via
`scripts/world-asset` (see that dir) / the `pmtiles` CLI.

## Status

Shipped & verified (emulator + unit tests): full app — core domain (tile math/metrics/GPX,
JVM-tested), tracking service + controller, Map/History/Record/Detail screens, GPX import + share
target, stats widget, shortcuts, en/de localization, first-open location permission, and the
**MapLibre offline PMTiles map** with square/route/heatmap/marker overlays. Open follow-up: basemap
zoom/coverage tuning (above).
