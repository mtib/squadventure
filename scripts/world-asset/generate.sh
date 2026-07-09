#!/usr/bin/env bash
set -euo pipefail

# Regenerates app/src/main/assets/world/world_lowpoly.geojson from Natural Earth's 110m land
# dataset. See scripts/world-asset/README.md for provenance and license.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEST="$REPO_ROOT/app/src/main/assets/world/world_lowpoly.geojson"
SOURCE_URL="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

curl -sSL -o "$WORKDIR/ne_110m_land.geojson" "$SOURCE_URL"
python3 "$SCRIPT_DIR/round_geojson.py" "$WORKDIR/ne_110m_land.geojson" "$DEST"

echo "Wrote $DEST ($(du -h "$DEST" | cut -f1))"
