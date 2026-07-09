# World basemap asset

`app/src/main/assets/world/world_lowpoly.geojson` is the bundled low-poly world land mass
used by `phone/map/WorldBasemap.kt` to render the app's offline basemap. Squadventure has no
`INTERNET` permission and fetches no map tiles — this file is the entire basemap.

## Source

[Natural Earth](https://www.naturalearthdata.com/) `ne_110m_land` (1:110m cultural/physical
vectors, the coarsest/lowest-poly Natural Earth scale, intended for whole-world small-scale
maps). Fetched from the `nvkelso/natural-earth-vector` GitHub mirror's pre-built GeoJSON:

```
https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson
```

## License

Natural Earth data is **public domain**. Per
[naturalearthdata.com/about/terms-of-use](https://www.naturalearthdata.com/about/terms-of-use/):
"No permission is needed to use Natural Earth. Crediting the authors is unnecessary."

## Regenerating

```bash
scripts/world-asset/generate.sh
```

This downloads the current `ne_110m_land.geojson` and rounds every coordinate to 4 decimal
places (~11 m at the equator — far finer than the 110m/low-poly source geometry itself, so no
visible precision is lost) and re-serializes without whitespace, via
`scripts/world-asset/round_geojson.py`. This roughly halves the raw download's size (138 KB →
~102 KB) by dropping the ~15 significant digits of floating point noise Natural Earth ships
coordinates with. The result is committed directly at
`app/src/main/assets/world/world_lowpoly.geojson` (~102 KB, well under the ~1.5 MB budget).

To instead render faint country borders, swap the source URL in `generate.sh` for
`ne_110m_admin_0_countries.geojson` from the same mirror and adjust `WorldBasemap.kt`'s parsing
if the geometry type differs (countries are `Polygon`/`MultiPolygon` same as land, so no other
change should be needed).
