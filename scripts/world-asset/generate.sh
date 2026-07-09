#!/usr/bin/env bash
set -euo pipefail

# Regenerates app/src/main/assets/world/{world_lowpoly,world_lakes}.geojson from Natural Earth's
# high-detail 10m datasets: land + minor islands (coastlines) and lakes (inland water).
# See scripts/world-asset/README.md for provenance and license.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ASSETS_DIR="$REPO_ROOT/app/src/main/assets/world"
LAND_DEST="$ASSETS_DIR/world_lowpoly.geojson"
LAKES_DEST="$ASSETS_DIR/world_lakes.geojson"

BASE="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson"
LAND_URL="$BASE/ne_10m_land.geojson"
ISLANDS_URL="$BASE/ne_10m_minor_islands.geojson"
LAKES_URL="$BASE/ne_10m_lakes.geojson"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

curl -sSL -o "$WORKDIR/land.geojson" "$LAND_URL"
curl -sSL -o "$WORKDIR/islands.geojson" "$ISLANDS_URL"
curl -sSL -o "$WORKDIR/lakes.geojson" "$LAKES_URL"

# Land coastlines = mainland + minor islands, merged into one file.
python3 "$SCRIPT_DIR/round_geojson.py" "$LAND_DEST" "$WORKDIR/land.geojson" "$WORKDIR/islands.geojson"
python3 "$SCRIPT_DIR/round_geojson.py" "$LAKES_DEST" "$WORKDIR/lakes.geojson"

echo "Wrote $LAND_DEST ($(du -h "$LAND_DEST" | cut -f1))"
echo "Wrote $LAKES_DEST ($(du -h "$LAKES_DEST" | cut -f1))"
