#!/usr/bin/env bash
# Bygger Valhalla-rutor för provområdet och packar dem som en tar-fil (tile_extract) för valhalla-mobile.
source "$(dirname "$0")/gemensamt.sh"
[ -x "$VENV/bin/python" ] || python3 -m venv "$VENV"
"$VENV/bin/pip" -q install "pyvalhalla==$PYVALHALLA_VERSION"
# pyvalhallas omslag (python -m valhalla <kommando>) hittar inte programmen i 3.9.1; de anropas direkt.
BIN="$("$VENV/bin/python" -m valhalla print_bin_path)"
V="$UT/valhalla"; rm -rf "$V"; mkdir -p "$V/tiles"
"$VENV/bin/python" - "$V" <<'PY'
import json, sys
from valhalla import get_config
v = sys.argv[1]
# get_config kräver att tar-filen finns; en tom platshållare skapas och skrivs över av utdraget.
open(f"{v}/valhalla_tiles.tar", "wb").close()
c = get_config(tile_extract=f"{v}/valhalla_tiles.tar", tile_dir=f"{v}/tiles")
c["mjolnir"]["admin"] = f"{v}/admins.sqlite"
c["mjolnir"]["concurrency"] = 4
json.dump(c, open(f"{v}/valhalla.json", "w"), indent=2)
PY
"$BIN/valhalla_build_admins" -c "$V/valhalla.json" "$UT/$OMRADE.osm.pbf" > "$V/admins.log" 2>&1
"$BIN/valhalla_build_tiles" -c "$V/valhalla.json" "$UT/$OMRADE.osm.pbf" > "$V/tiles.log" 2>&1
# tar-utdraget byggs med Valhallas eget skript (samma format som tile_extract läser).
"$VENV/bin/python" -m valhalla.valhalla_build_extract -c "$V/valhalla.json" -v --overwrite > "$V/extract.log" 2>&1 \
  || (echo "build_extract misslyckades, faller tillbaka på vanlig tar" && cd "$V/tiles" && tar -cf "$V/valhalla_tiles.tar" $(find . -name '*.gph' | sort))
ls -l "$V/valhalla_tiles.tar"; find "$V/tiles" -name '*.gph' | wc -l
