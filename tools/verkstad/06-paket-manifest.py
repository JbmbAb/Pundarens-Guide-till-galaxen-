#!/usr/bin/env python3
"""Packar offlinepaketet: kopierar karta.pmtiles och valhalla_tiles.tar till paket/<version>/ och
skriver manifest.json med SHA-256 och storlek för varje fil. Appen installerar paketet atomärt
(se prototyp/offline-kedja, PaketInstallerare) och vägrar om en kontrollsumma inte stämmer."""
import hashlib, json, os, shutil, subprocess, sys, datetime

UT = os.environ.get("UT", "/workspace/verkstad/ut")
OMRADE = os.environ["OMRADE"]
BBOX = os.environ["BBOX"]
POLYGON = os.environ.get("POLYGON", "")
OSM = os.environ.get("OSM_KALLA", "/workspace/verkstad/osm/sweden-latest.osm.pbf")

def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""): h.update(b)
    return h.hexdigest()

def ut(cmd):
    try: return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout.strip()
    except Exception as e: return f"MISSING ({e.__class__.__name__})"

def main():
    nu = datetime.datetime.now().astimezone()
    version = f"{OMRADE}-{nu:%Y%m%d-%H%M}"
    d = os.path.join(UT, "paket", version); os.makedirs(d, exist_ok=True)
    filer = [(os.path.join(UT, "karta", "karta.pmtiles"), "karta.pmtiles", "karta"),
             (os.path.join(UT, "valhalla", "valhalla_tiles.tar"), "valhalla_tiles.tar", "ruttning")]
    poster = []
    for kalla, namn, roll in filer:
        shutil.copyfile(kalla, os.path.join(d, namn))
        p = os.path.join(d, namn)
        poster.append({"namn": namn, "roll": roll, "storlek": os.path.getsize(p), "sha256": sha256(p)})
    md5 = open(OSM + ".md5").read().split()[0] if os.path.exists(OSM + ".md5") else "MISSING"
    manifest = {
        "format": "pundarens-offlinepaket/1",
        "version": version,
        "omrade": {"namn": OMRADE, "bbox_wgs84": [float(x) for x in BBOX.split(",")],
                   "polygon": os.path.basename(POLYGON) if POLYGON else None,
                   "polygon_sha256": sha256(POLYGON) if POLYGON and os.path.exists(POLYGON) else None,
                   "not": ("Ägarbeslut F-03 2026-11-21 06.15: Orsa, Mora, Rättvik, Ljusdal, Härjedalen och Älvdalen (OSM admin_level 7, buffert 0,02°)"
                           if POLYGON else "Rektangel runt Orsa (äldre provområde)")},
        "skapad": nu.isoformat(timespec="seconds"),
        "kallor": {"osm": {"fil": os.path.basename(OSM), "geofabrik_md5": md5,
                           "replikering": ut(["osmium", "fileinfo", "-g", "header.option.osmosis_replication_timestamp", OSM]),
                           "licens": "ODbL 1.0, © OpenStreetMap contributors"}},
        "verktyg": {"osmium": ut(["osmium", "--version"]).splitlines()[0] if True else "",
                    "tilemaker": ut(["tilemaker", "--help"]).splitlines()[0],
                    "valhalla": "pyvalhalla " + ut([os.environ.get("VENV", "/workspace/verkstad/venv") + "/bin/python", "-c", "import valhalla;print(valhalla.__version__)"])},
        "valhalla_mobile_forvantad": "0.6.6 (Valhalla 3.9.2)",
        "filer": poster,
    }
    json.dump(manifest, open(os.path.join(d, "manifest.json"), "w"), ensure_ascii=False, indent=2)
    print(os.path.join(d, "manifest.json")); print(json.dumps(manifest, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    sys.exit(main())
