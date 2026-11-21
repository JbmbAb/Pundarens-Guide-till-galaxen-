#!/usr/bin/env bash
# Klipper ut provområdet ur Sverigeutdraget med osmium (complete_ways, så att vägar vid kanten blir hela).
source "$(dirname "$0")/gemensamt.sh"
osmium extract --overwrite -s complete_ways -b "$BBOX" -o "$UT/$OMRADE.osm.pbf" "$OSM_KALLA"
osmium fileinfo -e "$UT/$OMRADE.osm.pbf" | grep -E "Bounding|Number of (nodes|ways|relations)|timestamp"
