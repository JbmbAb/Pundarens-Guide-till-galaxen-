#!/usr/bin/env bash
# Hämtar Geofabriks Sverigeutdrag och kontrollerar MD5 mot Geofabriks egen summa.
source "$(dirname "$0")/gemensamt.sh"
D="$(dirname "$OSM_KALLA")"; mkdir -p "$D"; cd "$D"
curl -sSL -o sweden-latest.osm.pbf.md5 https://download.geofabrik.de/europe/sweden-latest.osm.pbf.md5
if ! md5sum -c --status sweden-latest.osm.pbf.md5 2>/dev/null; then
  curl -sSL -D headers.txt -o sweden-latest.osm.pbf.tmp https://download.geofabrik.de/europe/sweden-latest.osm.pbf
  mv sweden-latest.osm.pbf.tmp sweden-latest.osm.pbf
fi
md5sum -c sweden-latest.osm.pbf.md5
osmium fileinfo -e -g header.option.osmosis_replication_timestamp sweden-latest.osm.pbf || true
