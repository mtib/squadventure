#!/usr/bin/env bash
set -euo pipefail

# Regenerates app/src/main/assets/map/basemap.pmtiles — the bundled offline vector basemap rendered
# by MapLibre (see phone/map/MapView.kt + assets/map/style.json). Source: Protomaps daily build
# (OpenStreetMap-derived, ODbL — attribution "© OpenStreetMap" is shown by the map). See README.md.
#
# Requires the `pmtiles` CLI (https://github.com/protomaps/go-pmtiles/releases). `pmtiles extract`
# streams only the requested tiles from the remote build via HTTP range requests.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEST="$REPO_ROOT/app/src/main/assets/map/basemap.pmtiles"

# Pick a recent daily build (YYYYMMDD). List: https://maps.protomaps.com/builds
BUILD_URL="${BUILD_URL:-https://build.protomaps.com/20260709.pmtiles}"

# Global vector basemap, zoom 0-7. Measured sizes for this build: z0-6 ~43MB, z0-7 ~178MB,
# z0-8 ~524MB. We ship z0-7: it gives city/region detail (water edges, forests, major roads) while
# staying within the ~400MB installed budget. The app copies the tileset to internal storage on
# first run, so installed footprint is ~2x the asset (~356MB) — see phone/map/MapView.kt.
# NOTE: the asset is git-ignored (>100MB); regenerate locally with this script and in CI via
# .github/workflows/release.yml before every build.
MAXZOOM="${MAXZOOM:-7}"

pmtiles extract "$BUILD_URL" "$DEST" --maxzoom="$MAXZOOM"
pmtiles show "$DEST" | grep -E "min zoom|max zoom|tile type"
echo "Wrote $DEST ($(du -h "$DEST" | cut -f1))"
