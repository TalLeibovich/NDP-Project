# main.py
import json

from pipeline import optimize_for_courier


class SimpleDistanceProvider:
    # קורא מטריצה מוכנה מהקובץ ושולף ממנה תת-מטריצה לפי points (מתעלם מהנקודות)
    def __init__(self, matrix):
        self.matrix = matrix

    def distance_matrix_km(self, points):
        n = len(points)
        return [row[:n] for row in self.matrix[:n]]


def read_matrix(path: str):
    m = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [x.strip() for x in line.replace(",", " ").split()]
            m.append([float(1e9) if x.upper() == "INF" else float(x) for x in parts])
    return m


def read_constraints(path: str):
    d = {}
    
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            k, v = [x.strip() for x in line.split("=", 1)]
            d[k] = v

    return {
        "start": {"lat": float(d["start_lat"]), "lon": float(d["start_lon"])},
        "constraints": {
            "max_distance_km": float(d.get("max_distance_km", 1e18)),
            "max_weight": float(d.get("max_weight", 1e18)),
            "max_volume": float(d.get("max_volume", 1e18)),
            "max_stops": int(d.get("max_stops", 20)),
        },
    }


def read_packages(path: str):
    pkgs = []
    with open(path, "r", encoding="utf-8") as f:
        header = [h.strip() for h in f.readline().strip().split("|")]
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [x.strip() for x in line.split("|")]
            p = dict(zip(header, parts))
            pkgs.append(
                {
                    "id": p["id"],
                    "lat": float(p["lat"]),
                    "lon": float(p["lon"]),
                    "weight": float(p["weight"]),
                    "volume": float(p["volume"]),
                    "profit": float(p.get("profit", 0) or 0),
                    "deadline": p.get("deadline") or None,
                }
            )
    return pkgs


if __name__ == "__main__":
    dist = read_matrix("dist_matrix.txt")
    req = read_constraints("constraints.txt")
    packages = read_packages("packages.txt")

    provider = SimpleDistanceProvider(dist)
    out = optimize_for_courier(req, packages, provider)

    print(json.dumps(out, indent=2, ensure_ascii=False))
