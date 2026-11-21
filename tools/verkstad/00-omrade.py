#!/usr/bin/env python3
"""Bygger provområdets polygon ur OSM:s kommungränser (admin_level 7) i Sverigeutdraget.
Ägarbeslut F-03 (2026-11-21 06.15): Orsa kommun och grannkommunerna Mora, Rättvik, Ljusdal,
Härjedalen och Älvdalen. Unionen buffras med BUFFERT grader och förenklas lätt.
Utdata: omrade/<OMRADE>.geojson (osmium extract -p) och bbox på stdout."""
import json, os, subprocess, sys
from shapely.geometry import shape, mapping
from shapely.ops import unary_union

KOMMUNER = ["Orsa kommun", "Mora kommun", "Rättviks kommun", "Ljusdals kommun", "Härjedalens kommun", "Älvdalens kommun"]
BUFFERT = float(os.environ.get("BUFFERT", "0.02"))  # cirka 1–2 km
UT = os.environ.get("UT", "/workspace/verkstad/ut")
OSM = os.environ.get("OSM_KALLA", "/workspace/verkstad/osm/sweden-latest.osm.pbf")
OMRADE = os.environ.get("OMRADE", "f03-sex-kommuner")
HAR = os.path.dirname(os.path.abspath(__file__))

def main():
    os.makedirs(UT, exist_ok=True)
    a7 = os.path.join(UT, "admin7.osm.pbf"); gj = os.path.join(UT, "admin7.geojson")
    subprocess.run(["osmium", "tags-filter", "-O", "-o", a7, OSM, "r/admin_level=7"], check=True)
    subprocess.run(["osmium", "export", "-O", "-f", "geojson", "-o", gj, a7], check=True, stderr=subprocess.DEVNULL)
    fs = {}
    for f in json.load(open(gj))["features"]:
        n = f["properties"].get("name")
        if n in KOMMUNER and f["geometry"]["type"] in ("Polygon", "MultiPolygon"):
            fs[n] = shape(f["geometry"])
    saknas = [k for k in KOMMUNER if k not in fs]
    if saknas:
        print("MISSING kommungränser:", saknas); return 1
    u = unary_union(list(fs.values())).buffer(BUFFERT).simplify(0.002)
    feat = {"type": "Feature", "properties": {"namn": OMRADE, "kommuner": KOMMUNER, "buffert_grader": BUFFERT,
            "kalla": "OSM admin_level=7 ur " + os.path.basename(OSM) + " (ODbL)"}, "geometry": mapping(u)}
    p = os.path.join(HAR, "omrade", OMRADE + ".geojson")
    json.dump({"type": "FeatureCollection", "features": [feat]}, open(p, "w"), ensure_ascii=False)
    b = u.bounds
    for k in KOMMUNER: print(f"{k}: bbox {[round(x, 3) for x in fs[k].bounds]}")
    print(f"POLYGON {p}")
    print(f"BBOX {b[0]:.3f},{b[1]:.3f},{b[2]:.3f},{b[3]:.3f}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
