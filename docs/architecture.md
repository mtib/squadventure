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
  permission. The world map is a **bundled low-poly vector** (Natural Earth land/country polygons as
  GeoJSON in `assets/world/`) rendered by our own **Compose Canvas in Web Mercator**. Low-poly/not
  terrain is fine and keeps the APK small. This unifies the coordinate system: the basemap, the square
  overlay, the route, and the heatmap all project through `core/geo/WebMercator`, which is consistent
  with the slippy-tile math squares are defined on.
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

### `phone/map` — the Canvas map
- `WorldBasemap`: loads and caches the bundled GeoJSON once; exposes polygons as normalized
  `[0,1]²` Mercator coordinates.
- `MapCanvas`: a Compose `Canvas` with pan + pinch-zoom state (a viewport over `[0,1]²`). Draws, in
  order: basemap land polygons → square overlay (z14 grid lines + z17/z14 filled claimed tiles) →
  trail heatmap (when toggled) → current route (on active/detail screens).
- The heatmap renders every activity's polyline with additive alpha so overlapping/repeat routes
  accumulate brightness. Filter by transport mode applies to which activities contribute.

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

## Status (scaffold delivered)

Done & unit-tested: Gradle/build/CI scaffold, `core/geo` (SlippyTile, Geo, TileClaims, WebMercator),
`core/metrics`, `core/model`, `core/gpx`, `core/activity/ActivityRepository`, theme, launchable
skeleton `MainActivity`, notification channel. Remaining (tracked as tasks): TrackingController +
location + service, map canvas + world asset, full screens + nav, import + share target, widget,
shortcuts, German strings.
