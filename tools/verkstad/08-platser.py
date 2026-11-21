#!/usr/bin/env python3
"""Bygger platser.geojson ur offlinepaketets egen kartfil och packar ett nytt paket som innehåller den.

Platserna (orter och namngivna matställen, butiker, drivmedel, toaletter och rastplatser) läses ur
lagren `place` och `poi` i karta.pmtiles på högsta zoomnivån. De kommer alltså ur exakt samma
OSM-ögonblick som kartan och ruttgrafen i paketet, och inget hämtas från nätet.

Kartfilen bär inte OSM-taggen opening_hours. Platserna får därför ingen öppettid här, och appen
visar "Okänd" (byggplanen: "Okänd" när öppettider saknas). Öppettider kräver ett verkstadssteg mot
rå OSM-data och är inte byggt.

Bara Pythons standardbibliotek. PMTiles v3 och Mapbox Vector Tiles avkodas här.

    python3 tools/verkstad/08-platser.py <paketkatalog> [<utkatalog>]

<paketkatalog> innehåller manifest.json, karta.pmtiles och valhalla_tiles.tar. Det nya paketet
skrivs till <utkatalog>/<version>.p<N>/ (standard: bredvid det gamla) med ett manifest där
platser.geojson har storlek och SHA-256 som de andra filerna.
"""
import datetime
import gzip
import hashlib
import json
import math
import os
import shutil
import struct
import sys

SETTLEMENT = {"city", "town", "village", "hamlet", "suburb", "quarter", "neighbourhood", "isolated_dwelling", "locality"}
FOOD_CLASS = {"restaurant", "fast_food", "cafe", "bar", "beer", "ice_cream"}
FOOD_SUB = {"restaurant", "fast_food", "cafe", "pub", "bar", "biergarten", "food_court", "ice_cream"}
SHOP_CLASS = {"grocery", "bakery", "butcher"}
SHOP_SUB = {"supermarket", "convenience", "kiosk", "bakery", "deli", "greengrocer", "butcher", "farm"}
REST_SUB = {"picnic_site", "shelter", "rest_area", "picnic_table", "wilderness_hut"}
# Hållplatser är för många och för lika till namnet för att vara användbara som mål i sökningen.
SKIPPED_SUB = {"bus_stop", "tram_stop", "platform"}


def varint(buf, pos):
    result = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def decompress(data, kind):
    if kind == 2:
        return gzip.decompress(data)
    if kind in (0, 1):
        return data
    raise SystemExit(f"komprimering {kind} stöds inte (bara ingen eller gzip)")


def read_directory(data):
    n, pos = varint(data, 0)
    ids, runs, lengths, offsets = [], [], [], []
    last = 0
    for _ in range(n):
        d, pos = varint(data, pos)
        last += d
        ids.append(last)
    for _ in range(n):
        v, pos = varint(data, pos)
        runs.append(v)
    for _ in range(n):
        v, pos = varint(data, pos)
        lengths.append(v)
    for i in range(n):
        v, pos = varint(data, pos)
        offsets.append(offsets[i - 1] + lengths[i - 1] if v == 0 and i > 0 else v - 1)
    return list(zip(ids, runs, lengths, offsets))


def zxy(tile_id):
    acc = 0
    z = 0
    while True:
        count = 1 << (2 * z)
        if acc + count > tile_id:
            break
        acc += count
        z += 1
    t = tile_id - acc
    n = 1 << z
    x = y = 0
    s = 1
    while s < n:
        rx = 1 & (t // 2)
        ry = 1 & (t ^ rx)
        if ry == 0:
            if rx == 1:
                x = s - 1 - x
                y = s - 1 - y
            x, y = y, x
        x += s * rx
        y += s * ry
        t //= 4
        s *= 2
    return z, x, y


def fields(buf):
    """Protobuf: ger (fältnummer, trådtyp, värde) för varje fält i ett meddelande."""
    pos = 0
    end = len(buf)
    while pos < end:
        key, pos = varint(buf, pos)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = varint(buf, pos)
        elif wire == 2:
            length, pos = varint(buf, pos)
            value = buf[pos:pos + length]
            pos += length
        elif wire == 5:
            value = buf[pos:pos + 4]
            pos += 4
        elif wire == 1:
            value = buf[pos:pos + 8]
            pos += 8
        else:
            raise ValueError(f"trådtyp {wire}")
        yield number, wire, value


def packed(buf):
    out = []
    pos = 0
    while pos < len(buf):
        v, pos = varint(buf, pos)
        out.append(v)
    return out


def value(buf):
    for number, wire, v in fields(buf):
        if number == 1:
            return bytes(v).decode("utf-8", "replace")
        if number == 2:
            return struct.unpack("<f", bytes(v))[0]
        if number == 3:
            return struct.unpack("<d", bytes(v))[0]
        if number in (4, 5):
            return v
        if number == 6:
            return (v >> 1) ^ -(v & 1)
        if number == 7:
            return bool(v)
    return None


def points(tile, wanted):
    """Ger (lager, egenskaper, px, py, extent, id) för punktobjekt i de önskade lagren."""
    for number, _, layer in fields(tile):
        if number != 3:
            continue
        name = None
        keys, values, feats = [], [], []
        extent = 4096
        for n2, _, v in fields(layer):
            if n2 == 1:
                name = bytes(v).decode("utf-8")
            elif n2 == 2:
                feats.append(v)
            elif n2 == 3:
                keys.append(bytes(v).decode("utf-8"))
            elif n2 == 4:
                values.append(v)
            elif n2 == 5:
                extent = v
        if name not in wanted:
            continue
        decoded = {}
        for f in feats:
            fid, tags, geom_type, geom = None, [], 0, []
            for n3, _, v in fields(f):
                if n3 == 1:
                    fid = v
                elif n3 == 2:
                    tags = packed(v)
                elif n3 == 3:
                    geom_type = v
                elif n3 == 4:
                    geom = packed(v)
            if geom_type != 1 or len(geom) < 3 or geom[0] != 9:
                continue
            props = {}
            for i in range(0, len(tags) - 1, 2):
                k, vi = tags[i], tags[i + 1]
                if vi not in decoded:
                    decoded[vi] = value(values[vi])
                props[keys[k]] = decoded[vi]
            px = (geom[1] >> 1) ^ -(geom[1] & 1)
            py = (geom[2] >> 1) ^ -(geom[2] & 1)
            yield name, props, px, py, extent, fid


def lonlat(z, x, y, px, py, extent):
    n = 1 << z
    wx = (x + px / extent) / n
    wy = (y + py / extent) / n
    return wx * 360.0 - 180.0, math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * wy))))


def classify(layer, props):
    cls = str(props.get("class") or "")
    sub = str(props.get("subclass") or "")
    if layer == "place":
        return ("SETTLEMENT", cls) if cls in SETTLEMENT else (None, None)
    if cls in FOOD_CLASS or sub in FOOD_SUB:
        return "FOOD", sub or cls
    if cls in SHOP_CLASS or sub in SHOP_SUB:
        return "SHOP", sub or cls
    if cls == "fuel" or sub == "fuel":
        return "FUEL", "fuel"
    if cls == "toilets" or sub == "toilets":
        return "TOILET", "toilets"
    if sub in REST_SUB or cls in REST_SUB:
        return "REST_AREA", sub or cls
    return "OTHER", sub or cls


def extract(pmtiles_path):
    with open(pmtiles_path, "rb") as f:
        header = f.read(127)
        if header[:7] != b"PMTiles" or header[7] != 3:
            raise SystemExit("inte en PMTiles v3-fil")
        (root_off, root_len, _meta_off, _meta_len, leaf_off, _leaf_len, data_off, _data_len, _n1, _n2, _n3) = struct.unpack("<11Q", header[8:96])
        internal, tile_comp = header[97], header[98]
        max_zoom = header[101]

        def directory(offset, length):
            f.seek(offset)
            return read_directory(decompress(f.read(length), internal))

        entries = []
        stack = [directory(root_off, root_len)]
        while stack:
            for tile_id, run, length, offset in stack.pop():
                if run == 0:
                    stack.append(directory(leaf_off + offset, length))
                else:
                    entries.append((tile_id, run, length, offset))
        found = {}
        tiles = 0
        cache = {}
        for tile_id, run, length, offset in entries:
            for r in range(run):
                z, x, y = zxy(tile_id + r)
                if z != max_zoom:
                    continue
                if offset not in cache:
                    f.seek(data_off + offset)
                    cache = {offset: decompress(f.read(length), tile_comp)}
                tiles += 1
                for layer, props, px, py, extent, _fid in points(cache[offset], {"place", "poi"}):
                    if not (0 <= px < extent and 0 <= py < extent):
                        continue  # ligger i grannrutans buffert och kommer med där
                    # tilemakers OpenMapTiles-profil lägger namnet i name:latin.
                    name = str(props.get("name") or props.get("name:latin") or props.get("name:nonlatin") or "").strip()
                    if not name:
                        continue
                    kind, sub = classify(layer, props)
                    if kind is None or sub in SKIPPED_SUB or (kind == "OTHER" and not sub):
                        continue  # okänt slag utan underklass säger inget om vad platsen är
                    lon, lat = lonlat(z, x, y, px, py, extent)
                    key = hashlib.sha1(f"{kind}|{name}|{lat:.5f}|{lon:.5f}".encode("utf-8")).hexdigest()[:12]
                    found["plats/" + key] = (name, kind, sub, round(lon, 6), round(lat, 6))
    return found, tiles, max_zoom


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    source = os.path.abspath(sys.argv[1])
    manifest = json.load(open(os.path.join(source, "manifest.json"), encoding="utf-8"))
    found, tiles, max_zoom = extract(os.path.join(source, "karta.pmtiles"))
    features = [
        {"type": "Feature", "geometry": {"type": "Point", "coordinates": [lon, lat]},
         "properties": {"id": pid, "name": name, "kind": kind, "subKind": sub}}
        for pid, (name, kind, sub, lon, lat) in sorted(found.items())
    ]
    counts = {}
    for f in features:
        counts[f["properties"]["kind"]] = counts.get(f["properties"]["kind"], 0) + 1

    base = manifest["version"].split(".p")[0]
    out_root = os.path.abspath(sys.argv[2]) if len(sys.argv) > 2 else os.path.dirname(source)
    n = 1
    while os.path.exists(os.path.join(out_root, f"{base}.p{n}")):
        n += 1
    version = f"{base}.p{n}"
    target = os.path.join(out_root, version)
    os.makedirs(target)
    for entry in manifest["filer"]:
        if entry["namn"] != "platser.geojson":
            shutil.copyfile(os.path.join(source, entry["namn"]), os.path.join(target, entry["namn"]))
    places_path = os.path.join(target, "platser.geojson")
    with open(places_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"type": "FeatureCollection", "features": features}, f, ensure_ascii=False, separators=(",", ":"))
    for entry in manifest["filer"]:
        if entry["namn"] != "platser.geojson" and sha256(os.path.join(target, entry["namn"])) != entry["sha256"]:
            raise SystemExit(f"{entry['namn']}: SHA-256 stämmer inte med manifestet; avbryter")
    manifest["version"] = version
    manifest["utgar_fran"] = base
    manifest["paketerad_om"] = datetime.datetime.now().astimezone().isoformat(timespec="seconds")
    manifest["platser"] = {
        "kalla": f"karta.pmtiles i samma paket, lagren place och poi, zoom {max_zoom} ({tiles} rutor)",
        "skript": "tools/verkstad/08-platser.py",
        "antal": len(features),
        "per_slag": dict(sorted(counts.items())),
        "oppettider": "saknas (kartfilen bär inte opening_hours); appen visar Okänd",
        "licens": "ODbL 1.0, © OpenStreetMap contributors",
    }
    manifest["filer"] = [e for e in manifest["filer"] if e["namn"] != "platser.geojson"] + [
        {"namn": "platser.geojson", "roll": "platser", "storlek": os.path.getsize(places_path), "sha256": sha256(places_path)},
    ]
    with open(os.path.join(target, "manifest.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(target)
    print(json.dumps(manifest["platser"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
