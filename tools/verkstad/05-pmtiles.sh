#!/usr/bin/env bash
# Bygger kartfilen karta.pmtiles (vektorrutor, OpenMapTiles-schema, zoom 0–14) med tilemaker 3.0.0.
# Lagren som kräver shapefiler (hav, Natural Earth) är borttagna i config-orsa.json: området ligger
# i inlandet och sjöarna kommer ur OSM. process-openmaptiles.lua är tilemakers egen, oförändrad (v3.0.0).
source "$(dirname "$0")/gemensamt.sh"
K="$UT/karta"; rm -rf "$K"; mkdir -p "$K"
cd "$HAR/tilemaker"
tilemaker --input "$UT/$OMRADE.osm.pbf" --output "$K/karta.pmtiles" \
  --config config-orsa.json --process process-openmaptiles.lua \
  --bbox "$BBOX" --threads 4 > "$K/tilemaker.log" 2>&1
ls -l "$K/karta.pmtiles"
