#!/usr/bin/env python3
"""Bygger appens tre kartstilar (G3: tre stilfiler över samma data) till app/src/main/assets/stil/.

Praktisk, Poetisk och Osannolik har exakt samma lager över samma källa (OpenMapTiles-schemat ur
tilemaker); bara färgerna skiljer. Ingen stil har textlager, glyfer eller sprite, så MapLibre hämtar
ingenting. Turkost (#4af8e3) används aldrig i kartan: det är sanningsfärgen och finns bara i
sanningsremsan och på nödkortet (F-02). Rutten och positionen ritas av appen ovanpå stilen, lika i
alla tre lägena. Platsnamn ritas av appen som bilder, se MapController.

Körs från repots rot:  python3 tools/verkstad/09-kartstilar.py
"""
import json
import os

UT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "stil")

PALETTER = {
    "praktisk": {
        "namn": "Praktisk", "bakgrund": "#0e0e0e",
        "wood": "#14201a", "grass": "#161d16", "wetland": "#141c1f", "farmland": "#1a1a14", "mark": "#151515",
        "anvandning": "#1b1b1e", "park": "#14201a", "vatten": "#0d2233", "byggnad": "#232327", "byggnadskant": "#2c2c31",
        "grans": "#3a3346", "stig": "#4a4a50", "liten": "#6a6a72", "mellan": "#8f8f98", "stor": "#b4b4be", "rals": "#4a4a52",
    },
    "poetisk": {
        "namn": "Poetisk", "bakgrund": "#0e0b14",
        "wood": "#171228", "grass": "#1a1530", "wetland": "#141a2e", "farmland": "#1c1626", "mark": "#130f1c",
        "anvandning": "#1d1830", "park": "#191433", "vatten": "#1a1140", "byggnad": "#2a2140", "byggnadskant": "#35294f",
        "grans": "#5b3f7a", "stig": "#4d3d66", "liten": "#6d5a8c", "mellan": "#9179b8", "stor": "#c5a8ee", "rals": "#4a3b63",
    },
    "osannolik": {
        "namn": "Osannolik", "bakgrund": "#120a0e",
        "wood": "#2a1020", "grass": "#24141c", "wetland": "#1c1830", "farmland": "#2a1a12", "mark": "#1a0e14",
        "anvandning": "#2c1424", "park": "#301228", "vatten": "#3b2a05", "byggnad": "#3a1c30", "byggnadskant": "#4c2540",
        "grans": "#8a5a1e", "stig": "#6e3a4c", "liten": "#a04f66", "mellan": "#d76a84", "stor": "#ff8ba0", "rals": "#6a4a2a",
    },
}


def bredd(faktor):
    return ["interpolate", ["exponential", 1.5], ["zoom"], 8, round(0.3 * faktor, 2), 14, round(1.0 * faktor, 2), 18, round(6.0 * faktor, 2)]


def vag(ident, farg, klasser, faktor, minzoom=None):
    lager = {
        "id": ident, "type": "line", "source": "karta", "source-layer": "transportation",
        "layout": {"line-cap": "round", "line-join": "round"},
        "paint": {"line-color": farg, "line-width": bredd(faktor)},
        "filter": ["in", ["get", "class"], ["literal", klasser]],
    }
    if minzoom is not None:
        lager["minzoom"] = minzoom
    return lager


def stil(p):
    return {
        "version": 8,
        "name": "Pundarens Guide – " + p["namn"],
        "metadata": {"not": "Byggd av tools/verkstad/09-kartstilar.py. Inga textlager, glyfer eller sprite."},
        "sources": {"karta": {"type": "vector", "url": "{{KARTA}}", "attribution": "© OpenStreetMap contributors"}},
        "layers": [
            {"id": "bakgrund", "type": "background", "paint": {"background-color": p["bakgrund"]}},
            {"id": "marktacke", "type": "fill", "source": "karta", "source-layer": "landcover", "paint": {
                "fill-color": ["match", ["get", "class"], "wood", p["wood"], "grass", p["grass"], "wetland", p["wetland"], "farmland", p["farmland"], p["mark"]],
                "fill-opacity": 0.9}},
            {"id": "markanvandning", "type": "fill", "source": "karta", "source-layer": "landuse", "paint": {"fill-color": p["anvandning"], "fill-opacity": 0.8}},
            {"id": "park", "type": "fill", "source": "karta", "source-layer": "park", "paint": {"fill-color": p["park"], "fill-opacity": 0.6}},
            {"id": "vatten", "type": "fill", "source": "karta", "source-layer": "water", "paint": {"fill-color": p["vatten"]}},
            {"id": "vattendrag", "type": "line", "source": "karta", "source-layer": "waterway", "paint": {"line-color": p["vatten"], "line-width": 1.2}},
            {"id": "byggnader", "type": "fill", "source": "karta", "source-layer": "building", "minzoom": 13,
             "paint": {"fill-color": p["byggnad"], "fill-outline-color": p["byggnadskant"]}},
            {"id": "granser", "type": "line", "source": "karta", "source-layer": "boundary", "filter": ["<=", ["get", "admin_level"], 7],
             "paint": {"line-color": p["grans"], "line-dasharray": [3, 2], "line-width": 1}},
            vag("vag-stig", p["stig"], ["path", "track"], 0.8, 12),
            vag("vag-liten", p["liten"], ["service", "minor"], 1.4, 10),
            vag("vag-mellan", p["mellan"], ["tertiary", "secondary"], 2.2),
            vag("vag-stor", p["stor"], ["primary", "trunk", "motorway"], 3.0),
            {"id": "jarnvag", "type": "line", "source": "karta", "source-layer": "transportation", "filter": ["==", ["get", "class"], "rail"],
             "paint": {"line-color": p["rals"], "line-width": 1, "line-dasharray": [2, 2]}},
        ],
    }


def main():
    os.makedirs(UT, exist_ok=True)
    for namn, palett in PALETTER.items():
        text = json.dumps(stil(palett), ensure_ascii=False, indent=1)
        assert "4af8e3" not in text.lower(), "turkost får inte finnas i kartan (F-02)"
        assert "http" not in text and '"glyphs"' not in text and '"sprite"' not in text
        with open(os.path.join(UT, namn + ".json"), "w", encoding="utf-8", newline="\n") as f:
            f.write(text + "\n")
        print("skrev", namn + ".json", len(text), "tecken")


if __name__ == "__main__":
    main()
