import json
import sys

DECIMALS = 4


def round_coords(node):
    if isinstance(node, list):
        if node and isinstance(node[0], (int, float)):
            return [round(v, DECIMALS) for v in node]
        return [round_coords(child) for child in node]
    return node


def main(src_path: str, dst_path: str) -> None:
    with open(src_path) as f:
        data = json.load(f)

    out_features = []
    for feature in data["features"]:
        geom = feature["geometry"]
        geom["coordinates"] = round_coords(geom["coordinates"])
        out_features.append({"type": "Feature", "geometry": geom})

    out = {"type": "FeatureCollection", "features": out_features}
    with open(dst_path, "w") as f:
        json.dump(out, f, separators=(",", ":"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
