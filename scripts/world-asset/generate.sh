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

# Global overview, zoom 0-6 (~43MB) — country/region level, fits the ~50MB budget but has no
# city/street detail. For walk/bike/city detail, ADD a regional high-zoom extract and wire a second
# MapLibre source (see README.md), e.g.:
#   pmtiles extract "$BUILD_URL" region.pmtiles --bbox=7.5,54.4,13.5,58.0 --maxzoom=14
MAXZOOM="${MAXZOOM:-6}"

pmtiles extract "$BUILD_URL" "$DEST" --maxzoom="$MAXZOOM"
pmtiles show "$DEST" | grep -E "min zoom|max zoom|tile type"
echo "Wrote $DEST ($(du -h "$DEST" | cut -f1))"
