import json
import sys

# Coordinate precision. 5 decimals ~= 1 m, plenty for a world basemap; fidelity at 10m comes from
# vertex density, not decimal places.
DECIMALS = 5


def round_coords(node):
    if isinstance(node, list):
        if node and isinstance(node[0], (int, float)):
            return [round(v, DECIMALS) for v in node]
        return [round_coords(child) for child in node]
    return node


def main(dst_path: str, src_paths: list[str]) -> None:
    """Merge one or more GeoJSON files into a single rounded, minified FeatureCollection.

    Only geometry is kept (properties are dropped) and geometry `type` is emitted before
    `coordinates` so the on-device streaming parser can read type-first.
    """
    out_features = []
    for src_path in src_paths:
        with open(src_path) as f:
            data = json.load(f)
        for feature in data["features"]:
            geom = feature.get("geometry")
            if not geom:
                continue
            out_features.append({
                "type": "Feature",
                "geometry": {
                    "type": geom["type"],
                    "coordinates": round_coords(geom["coordinates"]),
                },
            })

    out = {"type": "FeatureCollection", "features": out_features}
    with open(dst_path, "w") as f:
        json.dump(out, f, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
