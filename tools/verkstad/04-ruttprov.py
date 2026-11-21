#!/usr/bin/env python3
"""Ruttprov i verkstaden: Valhalla (pyvalhalla) mot det byggda tar-utdraget, OSRM-format med
röst- och bannerinstruktioner på svenska, som valhalla-mobile ska få på målenheten.
Körs gärna utan nätverk: unshare -rn python 04-ruttprov.py <valhalla.json> <utkatalog>"""
import json, sys, os, socket
from valhalla import Actor

PUNKTER = {  # koordinater ur OSM-utdraget (place-noder), se etapp2-protokollet
    "Orsa": (61.1201175, 14.6222536),
    "Grönklitt": (61.2077132, 14.5377551),
    "Mora": (61.0086787, 14.5442519),
}
PROV = [("Orsa", "Grönklitt", "auto"), ("Orsa", "Mora", "auto"),
        ("Orsa", "Grönklitt", "pedestrian"), ("Orsa", "Mora", "pedestrian"),
        ("Mora", "Orsa", "auto")]

def natverk():
    try:
        socket.create_connection(("1.1.1.1", 443), timeout=2).close(); return "nätverk finns"
    except OSError as e:
        return f"inget nätverk ({e.__class__.__name__})"

def main(conf, ut):
    os.makedirs(ut, exist_ok=True)
    actor = Actor(conf)
    print("NÄTVERK:", natverk())
    rader = []
    for a, b, prof in PROV:
        req = {"locations": [{"lat": PUNKTER[a][0], "lon": PUNKTER[a][1]},
                             {"lat": PUNKTER[b][0], "lon": PUNKTER[b][1]}],
               "costing": prof, "format": "osrm", "language": "sv-SE",
               "voice_instructions": True, "banner_instructions": True,
               "units": "kilometers", "directions_options": {"language": "sv-SE", "units": "kilometers"}}
        namn = f"{a}-{b}-{prof}".lower().replace("ö", "o").replace("å", "a")
        try:
            svar = json.loads(actor.route(json.dumps(req)))
        except Exception as e:
            print(f"{namn}: FEL {e}"); rader.append((namn, "FEL", str(e))); continue
        json.dump(req, open(f"{ut}/{namn}.fraga.json", "w"), ensure_ascii=False, indent=1)
        json.dump(svar, open(f"{ut}/{namn}.osrm.json", "w"), ensure_ascii=False)
        r = svar["routes"][0]; steg = [s for l in r["legs"] for s in l["steps"]]
        rost = sum(len(s.get("voiceInstructions", [])) for s in steg)
        banner = sum(len(s.get("bannerInstructions", [])) for s in steg)
        exempel = next((v["announcement"] for s in steg for v in s.get("voiceInstructions", [])), "")
        print(f"{namn}: code={svar['code']} avstånd={r['distance']/1000:.2f} km tid={r['duration']/60:.1f} min "
              f"steg={len(steg)} röst={rost} banner={banner} exempel=\"{exempel}\"")
    return 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
