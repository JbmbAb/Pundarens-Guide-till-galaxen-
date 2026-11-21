# Gemensamma värden för verkstadens skript (etapp 2, offlinekedjan).
# Hemverkstad: körs på Linux, aldrig på målenheten. Stora filer hamnar i $UT och checkas inte in.
set -euo pipefail
HAR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UT="${UT:-/workspace/verkstad/ut}"
OSM_KALLA="${OSM_KALLA:-/workspace/verkstad/osm/sweden-latest.osm.pbf}"
VENV="${VENV:-/workspace/verkstad/venv}"
# Provområde: Orsa kommun med marginal så att Mora centrum och Grönklitt ryms.
# F-03 (exakta grannkommuner) är öppen. Gränsrektangel: väst,syd,öst,nord (WGS84).
BBOX="${BBOX:-14.20,60.90,15.20,61.65}"
OMRADE="${OMRADE:-orsa-prov}"
# Valhalla-versionen ska ligga så nära valhalla-mobile 0.6.6 (Valhalla 3.9.2) som möjligt.
# 3.9.2 finns inte på PyPI; 3.9.1 används (avvikelse E2-D1).
PYVALHALLA_VERSION="${PYVALHALLA_VERSION:-3.9.1}"
mkdir -p "$UT"
