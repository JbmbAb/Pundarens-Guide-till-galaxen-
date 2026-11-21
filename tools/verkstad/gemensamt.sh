# Gemensamma värden för verkstadens skript (etapp 2, offlinekedjan).
# Hemverkstad: körs på Linux, aldrig på målenheten. Stora filer hamnar i $UT och checkas inte in.
set -euo pipefail
HAR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UT="${UT:-/workspace/verkstad/ut}"
OSM_KALLA="${OSM_KALLA:-/workspace/verkstad/osm/sweden-latest.osm.pbf}"
VENV="${VENV:-/workspace/verkstad/venv}"
# Provområde enligt ägarbeslut F-03 (2026-11-21 06.15): Orsa kommun och grannkommunerna Mora,
# Rättvik, Ljusdal, Härjedalen och Älvdalen. Polygonen byggs av 00-omrade.py ur OSM:s kommungränser.
# Den tidigare rektangeln runt Orsa kan fortfarande köras: OMRADE=orsa-prov POLYGON= BBOX=14.20,60.90,15.20,61.65
OMRADE="${OMRADE:-f03-sex-kommuner}"
POLYGON="${POLYGON-$HAR/omrade/$OMRADE.geojson}"
# Gränsrektangel (väst,syd,öst,nord) för tilemaker och manifestet; tas ur polygonen om den finns.
if [ -z "${BBOX:-}" ] && [ -n "$POLYGON" ] && [ -f "$POLYGON" ]; then
  BBOX="$(python3 -c 'import json,sys
c=[]
def w(x):
    if isinstance(x[0],(int,float)): c.append(x)
    else: [w(y) for y in x]
for f in json.load(open(sys.argv[1]))["features"]: w(f["geometry"]["coordinates"])
print("%.3f,%.3f,%.3f,%.3f"%(min(p[0] for p in c),min(p[1] for p in c),max(p[0] for p in c),max(p[1] for p in c)))' "$POLYGON")"
fi
BBOX="${BBOX:-14.20,60.90,15.20,61.65}"
# Valhalla-versionen ska ligga så nära valhalla-mobile 0.6.6 (Valhalla 3.9.2) som möjligt.
# 3.9.2 finns inte på PyPI; 3.9.1 används (avvikelse E2-D1).
PYVALHALLA_VERSION="${PYVALHALLA_VERSION:-3.9.1}"
mkdir -p "$UT"
