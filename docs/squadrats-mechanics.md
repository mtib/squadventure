# Squadrats mechanics (research)

How the original [Squadrats](https://squadrats.com) works, cross-checked across sources, and how we
mirror it. This is the authority for the domain model in `core/geo` and `core/metrics`.

## The tile grid

Squadrats is built directly on the standard OSM/Google "slippy map" XYZ tile grid (Web Mercator).
No custom grid — overlay lines fall on real OSM tile boundaries.

| Concept | Meaning | OSM zoom | Tile width (° lon) = 360/2^z |
|---|---|---|---|
| **Squadrat** | the **big** square | **14** | 0.02197265625° |
| **Squadratinho** | the **small** square (`-inho` = "little") | **17** | 0.00274658203° |

Zoom 17 − zoom 14 = 3 levels → **one squadrat = 8×8 = 64 squadratinhos**. So a squadratinho `(x17,y17)`
lives in squadrat `(x17 shr 3, y17 shr 3)`, at position `(x17 and 7, y17 and 7)` inside the 8×8 block.

### Coordinate → tile (standard slippy-map formulas, verified)

```
n = 2^zoom
xtile = floor( n · (lon + 180) / 360 )
ytile = floor( n · (1 − asinh(tan(lat_rad)) / π) / 2 )     // asinh(tan φ) = ln(tan φ + sec φ)
```
Reverse (NW corner of the tile):
```
lon = xtile / n · 360 − 180
lat = atan( sinh( π · (1 − 2·ytile / n) ) )
```
Implemented in `core/geo/SlippyTile.kt`; tiles are packed into a `Long` key (x high 32 bits, y low 32).

### Real-world square sizes (latitude-dependent — do NOT hardcode)

Ground edge = `40 075 016.686 m / 2^zoom · cos(latitude)`.

| Latitude | Squadrat (z14) | Squadratinho (z17) |
|---|---|---|
| 0° equator | ~2446 m (2.45 km) | ~306 m |
| ~49°N | ~1609 m (= 1 mile — the marketing figure) | ~210 m |
| 52.5°N Berlin | ~1490 m (~1.5 km) | ~186 m |

The commonly quoted "1 mile" / "200 m" are just mid-latitude approximations. We compute per-latitude
via `SlippyTile.tileEdgeMeters(zoom, lat)`.

## Gameplay

- **Claim**: any recorded GPS point inside a tile claims that tile. (Original: human-powered activities
  only, "cutting through" a tile; e-bikes OK, motors excluded. We don't restrict transport — the user
  tags each activity walk/bike/car/both/other and can filter later.)
- **Metrics** (Squadrats' names, VeloViewer's "Explorer squares" concepts), computed for both zoom
  levels in `core/metrics/SquareMetrics.kt`:
  - **Total** — distinct claimed tiles.
  - **Yard** (VeloViewer "cluster") — largest group of tiles connected via 4-way edge adjacency
    (N/E/S/W). We compute the largest connected component (flood fill).
  - **Übersquadrat** (VeloViewer "max square") — largest solid N×N block where every tile is claimed;
    reported as side length N. We compute it with a sparse maximal-square DP.
  - Small-tile variants: **Yardinho**, **Übersquadratinho**.

## Visualization

- Claimed tiles drawn as **filled, semi-transparent squares aligned to tile boundaries**, with grid
  lines on tile edges. Squadrats (z14) coarse grid, squadratinhos (z17) finer fill; the Übersquadrat
  and Yard can be highlighted.
- **Heatmap** (our requirement, Strava-style): the density of the **actual GPX trails** — the same
  route travelled many times glows brighter (additive/opacity blend of the polylines). This is a
  *line-density* heatmap of paths, **not** a per-square visit count. It is a **toggle-able** map layer.

## Data / import

- Original imports from Strava / Garmin (OAuth backfill of history) and integrates with route
  planners; fundamentally it consumes **GPS tracks**.
- Squadventure is offline: it records its own tracks and imports **GPX** (single or multi-`<trk>`),
  via a file picker and as a **share target**. No cloud, no accounts.

## Sources
- OSM wiki, *Slippy map tilenames* — formulas, tile width, ground resolution `156543.03·cos(lat)/2^z`.
- marcusjaschen.de (2022) — z14 vs z17, 64 tiles/squadrat, ~1.5 km / 186 m at Berlin, cluster & max square.
- hugovk.dev/tiles — explorer tile = z14, squadratinho = z17, 64 per tile, latitude dependence.
- pqrs.in Squadrats review — Squadrat/Yard/Übersquadrat + `-inho` variants, ~1 mile / ~200 m.
- statshunters.com / rideeverytile.com — comparable z14 "explorer tile" ecosystem, heatmaps.
