#!/usr/bin/env bash
# Kör hela verkstadskedjan och provens skrivbordsdel. Utdata: $UT och en logg på stdout.
#   bash tools/verkstad/kor-alla.sh | tee logg.txt
source "$(dirname "$0")/gemensamt.sh"
echo "# Kört $(date '+%Y-%m-%d %H:%M:%S %Z') på commit $(git -C "$HAR" rev-parse HEAD)"
steg() { echo; echo "=== $1"; }
steg "01 hämta OSM (Geofabrik, MD5)";       bash "$HAR/01-hamta-osm.sh"
steg "00 områdespolygon ur kommungränserna"; [ -z "$POLYGON" ] || "$VENV/bin/python" "$HAR/00-omrade.py"
steg "02 klipp provområdet ($OMRADE, $BBOX)";        bash "$HAR/02-klipp-omrade.sh"
steg "03 Valhalla-rutor";                    bash "$HAR/03-valhalla-rutor.sh"
steg "05 PMTiles";                           bash "$HAR/05-pmtiles.sh"
steg "06 paket och manifest";                OMRADE="$OMRADE" BBOX="$BBOX" POLYGON="$POLYGON" "$VENV/bin/python" "$HAR/06-paket-manifest.py" | head -1
steg "04 ruttprov utan nätverk (unshare -rn)"
(cd "$UT" && unshare -rn "$VENV/bin/python" "$HAR/04-ruttprov.py" valhalla/valhalla.json ruttprov)
steg "Ferrostar-prov utan nätverk (OSRM-parser, simulering, avvikelse)"
( cd "$HAR/ferrostar-prov" && CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-/workspace/verkstad/cargo-target}" "${CARGO:-$HOME/.cargo/bin/cargo}" build --release -q )
(cd "$UT/ruttprov" && unshare -rn "${CARGO_TARGET_DIR:-/workspace/verkstad/cargo-target}/release/ferrostar-prov" *.osrm.json)
