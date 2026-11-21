#!/usr/bin/env bash
# Klipper ut provområdet ur Sverigeutdraget med osmium (complete_ways, så att vägar vid kanten blir hela).
source "$(dirname "$0")/gemensamt.sh"
if [ -n "$POLYGON" ] && [ -f "$POLYGON" ]; then
  echo "polygon: $POLYGON"; osmium extract --overwrite -s complete_ways -p "$POLYGON" -o "$UT/$OMRADE.osm.pbf" "$OSM_KALLA"
else
  echo "rektangel: $BBOX"; osmium extract --overwrite -s complete_ways -b "$BBOX" -o "$UT/$OMRADE.osm.pbf" "$OSM_KALLA"
fi
osmium fileinfo -e "$UT/$OMRADE.osm.pbf" | grep -E "Bounding|Number of (nodes|ways|relations)|timestamp"
