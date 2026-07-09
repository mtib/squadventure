# World basemap asset

`app/src/main/assets/world/world_lowpoly.geojson` (coastlines) and
`app/src/main/assets/world/world_lakes.geojson` (lakes) are the bundled world basemap used by
`phone/map/WorldBasemap.kt`. Squadventure has no `INTERNET` permission and fetches no map tiles —
these two files are the entire basemap.

## Source

[Natural Earth](https://www.naturalearthdata.com/) **10m** (1:10,000,000) physical vectors — the
highest-detail Natural Earth scale — fetched from the `nvkelso/natural-earth-vector` GitHub mirror:

```
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_land.geojson
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_minor_islands.geojson
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_lakes.geojson
```

Land = `ne_10m_land` + `ne_10m_minor_islands` merged into `world_lowpoly.geojson`; lakes =
`ne_10m_lakes` in `world_lakes.geojson`. Rounded to 5 decimals (~1 m) and minified (geometry only,
no properties). Result: ~9.7 MB land + ~3.3 MB lakes ≈ **13 MB** total.

10m is Natural Earth's finest resolution. Going meaningfully beyond it (toward a ~50 MB budget)
would require OpenStreetMap-derived coastline/water polygons (much larger, shapefile → GeoJSON via
GDAL), which is a separate pipeline not wired up here.

## License

Natural Earth data is **public domain**. Per
[naturalearthdata.com/about/terms-of-use](https://www.naturalearthdata.com/about/terms-of-use/):
"No permission is needed to use Natural Earth. Crediting the authors is unnecessary."

## Regenerating

```bash
scripts/world-asset/generate.sh
```

Downloads the three 10m sources, rounds/minifies via `round_geojson.py` (which merges multiple
inputs into one FeatureCollection and emits geometry `type` before `coordinates` so the on-device
streaming parser can read type-first), and writes the two committed assets.

`WorldBasemap.kt` stream-parses both files with `android.util.JsonReader` (no full JSON tree in
memory, so the ~13 MB load stays cheap) into per-polygon ring lists, and renders lakes in the
sea/background color on top of land so large water bodies (Caspian Sea, Great Lakes, …) read as
cut-outs.

To add faint country borders, also fetch `ne_10m_admin_0_countries.geojson` and render it as a
separate line layer in `MapView` (borders are lines, not filled land).
