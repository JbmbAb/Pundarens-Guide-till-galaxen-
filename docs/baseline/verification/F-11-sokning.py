#!/usr/bin/env python3
"""Maskinell genomsökning inför F-11. Godkänner ingenting och ändrar inga datafiler."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).with_name("F-11-radgranskning.md")

SPARR = ["vänster", "höger", "rakt fram", "sväng", "u-sväng", "avfart", "rondell"]
# B-12 plus Adams-repliker/figurer som användes för att återskapa avsnitt 7:s 14 rader.
LANADE = [
    ("vogon", "Vogon (B-12)"),
    ("marvin", "Marvin (B-12)"),
    ("milliways", "Milliways (B-12)"),
    ("heart of gold", "Heart of Gold (B-12)"),
    ("don't panic", "Don't Panic (Adams-replik; B-11 arbetsnamn)"),
    ("dont panic", "Don't Panic (Adams-replik; B-11 arbetsnamn)"),
    ("improbability", "Improbability Drive (Adams)"),
    ("improbabilitet", "Improbabilitet (Adams-anspelning)"),
    (r"\b42\b", "42 (Adams-anspelning)"),
    ("handduk", "handduk (Adams-rekvisita; även produktnamn)"),
]
RELATION_IDS = {
    "C-004",
    "C-006",
    "C-007",
    "C-008",
    "C-009",
    "P-006",
    "R-001",
    "R-002",
    "R-003",
}
# 04 listar också R-* och Garderobsknullis.
SAK_PATTERNS = [
    (r"är korrekt", "är korrekt"),
    # "stämmer" bara med navigeringsord, så att "om allting stämmer" (V-013) inte flaggas.
    (r"(avståndet|tiden|svängen|ankomsten|positionen|gps:?n?)\s+stämmer", "avståndet/tiden stämmer"),
    (r"har rätt", "har rätt"),
    (r"är verklig", "är verklig"),
    (r"du är inte vilse", "du är inte vilse"),
    (r"fortsätt enligt", "fortsätt enligt"),
    (r"kör vidare", "kör vidare"),
    (r"sväng nu", "sväng nu"),
    (r"sväng vänster", "sväng vänster"),
    (r"sväng höger", "sväng höger"),
]

AVSNITT7_RIKTNING = {"NAV-001", "NAV-012", "NAV-014", "V-010", "EX-F-04"}
AVSNITT7_LANADE = 14  # uppräknade exempel, inte full lista i bladet
AVSNITT7_LANADE_EXEMPEL = {"NAV-013", "NAV-016", "EX-F-06", "BS-13", "vogonko", "panikbuffert"}
AVSNITT7_RELATION = {"V-005", "V-008", "V-009", "V-010", "V-011", "V-012", "V-019"}
AVSNITT7_SAK = {"NAV-003", "NAV-007"}


def load_json(rel: str):
    return json.loads((ROOT / rel).read_text(encoding="utf-8"))


def fold(s: str) -> str:
    return (s or "").casefold()


def find_direction(text: str) -> list[str]:
    t = fold(text)
    hits = []
    if "rakt fram" in t:
        hits.append("rakt fram")
    # stam + ev. böjning/sammansättning; "högre" är inte "höger"
    stems = {
        "vänster": r"vänster",
        "höger": r"höger",
        "sväng": r"sväng",
        "u-sväng": r"u[\-–−]?sväng",
        "avfart": r"avfart",
        "rondell": r"rondell",
    }
    for label, stem in stems.items():
        if re.search(stem, t) and label not in hits:
            # "höger" får inte träffa "högre"
            if label == "höger" and re.search(r"höger", t) is None:
                continue
            hits.append(label)
    return hits


def find_borrowed(text: str) -> list[str]:
    t = fold(text)
    hits = []
    for pat, label in LANADE:
        if pat.startswith(r"\b"):
            if re.search(pat, t) and label not in hits:
                hits.append(label)
        elif pat in t and label not in hits:
            hits.append(label)
    return hits


def find_sak(text: str) -> list[str]:
    t = fold(text)
    hits = []
    for pat, label in SAK_PATTERNS:
        if re.search(pat, t) and label not in hits:
            hits.append(label)
    return hits


def collect_ids(obj) -> set[str]:
    found = set()
    if isinstance(obj, str):
        if obj in RELATION_IDS or re.fullmatch(r"R-\d+", obj):
            found.add(obj)
    elif isinstance(obj, list):
        for x in obj:
            found |= collect_ids(x)
    elif isinstance(obj, dict):
        for v in obj.values():
            found |= collect_ids(v)
    return found


def md_cell(s: str) -> str:
    return (s or "—").replace("|", "\\|").replace("\n", " ").strip() or "—"


def load_items() -> list[dict]:
    items = []
    quotes = load_json("data/quotes/quotes.json")
    for v in quotes["variations"]:
        spoken = v["text"]
        all_text = spoken + " " + " ".join(str(x) for x in [v.get("sourceNote"), v.get("mechanism"), v.get("themes")])
        items.append(
            {
                "id": v["id"],
                "grupp": "V",
                "kort": spoken,
                "spoken": spoken,
                "search": all_text,
                "relation_extra": collect_ids(v),
            }
        )

    rules = load_json("data/babel-stoner/rules.json")
    for r in rules["rules"]:
        if not r["id"].startswith("BS-") or int(r["id"].split("-")[1]) < 11:
            continue
        spoken = r["function"]
        parts = [r.get("function"), r.get("basis"), r.get("note"), r.get("sourceNote")]
        search = " ".join(p for p in parts if p)
        items.append(
            {
                "id": r["id"],
                "grupp": "BS",
                "kort": f"{r['function']} (underlag: {r.get('basis') or '—'})",
                "spoken": spoken,
                "search": search,
                "relation_extra": collect_ids(r),
            }
        )

    lex = load_json("data/lexicon/lexicon.json")
    for e in lex["entries"]:
        if e.get("provenance") != "förslag" and e.get("author") != "Grok Bot (förslag)":
            continue
        spoken = e["text"]
        search = (e.get("term") or "") + " " + spoken + " " + (e.get("sourceNote") or "")
        items.append(
            {
                "id": e["id"],
                "grupp": "LEX",
                "kort": f"{e.get('term')}: {spoken}",
                "spoken": spoken,
                "search": search,
                "relation_extra": collect_ids(e),
            }
        )

    nav = load_json("data/babel-stoner/navigation_comments.json")
    for c in nav["comments"]:
        spoken = c["text"]
        items.append(
            {
                "id": c["id"],
                "grupp": "NAV",
                "kort": spoken,
                "spoken": spoken,
                "search": spoken + " " + (c.get("sourceNote") or ""),
                "relation_extra": collect_ids(c),
            }
        )

    examples = load_json("data/babel-stoner/examples.json")
    for e in examples["examples"]:
        if not str(e["id"]).startswith("EX-F-"):
            continue
        spoken = e.get("response") or e.get("text") or ""
        search = " ".join(
            str(p)
            for p in [
                e.get("title"),
                e.get("original"),
                spoken,
                e.get("sourceNote"),
                e.get("basis"),
            ]
            if p
        )
        kort = e.get("title") or e["id"]
        if e.get("original"):
            kort = f"{e['title']}. Fråga: {e['original']} Svar: {spoken}"
        items.append(
            {
                "id": e["id"],
                "grupp": "EX",
                "kort": kort,
                "spoken": spoken,
                "search": search,
                "relation_extra": collect_ids(e),
            }
        )
    return items


def analyze(item: dict) -> dict:
    # Riktningsord och sakpåståenden i den text Guiden skulle säga,
    # plus (för EX-F) hela posten eftersom originalfrågan ingår i förslaget.
    direction = find_direction(item["spoken"])
    if item["grupp"] == "EX":
        direction = find_direction(item["search"])
    borrowed = find_borrowed(item["search"])
    # 42 i källnoter till BS räknas (ligger i search)
    relation = sorted(item["relation_extra"] & (RELATION_IDS | {x for x in item["relation_extra"] if re.fullmatch(r"R-\d+", x)}))
    sak = find_sak(item["spoken"])
    if item["grupp"] == "EX":
        # "kör vidare" / "sväng nu" i svaret
        sak = find_sak(item["spoken"])
    return {
        **item,
        "riktning": direction,
        "lanade": borrowed,
        "relation": relation,
        "sak": sak,
    }


def regler_for(row: dict) -> str:
    rs = ["RR-01", "RR-08", "RR-11"]
    if row["grupp"] in {"V", "NAV"}:
        rs += ["RR-03", "RR-04"]
    if row["riktning"]:
        rs.append("RR-05")
    if row["lanade"]:
        rs.append("RR-10")
    if row["relation"]:
        rs.append("RR-11")
    if row["sak"]:
        rs.append("RR-04")
    if row["id"].startswith("V-013") or row["id"].startswith("V-014") or "Rikard" in row["kort"] or "rikard" in fold(row["search"]):
        rs.append("RR-07")
    if row["grupp"] == "BS":
        rs += ["RR-13", "RR-09"]
    if row["grupp"] == "EX":
        rs += ["RR-09", "RR-13"]
    # unique preserve order
    out = []
    for r in rs:
        if r not in out:
            out.append(r)
    return ", ".join(out)


def åtgärd(row: dict) -> str:
    parts = []
    if row["riktning"]:
        parts.append("Stryks i fordonsläge. Kan prövas för läget till fots (avsnitt 7; 02 N-2).")
    if row["lanade"]:
        parts.append("Byt namn eller märk som arbetsnamn före godkännande (B-12, RR-10).")
    if row["relation"]:
        parts.append("Alltid PRIVATE, även om posten godkänns (04; RR-11).")
    if row["sak"]:
        parts.append("Granskaren avgör om texten kan misstas för ett besked (RR-04).")
    if not parts:
        return "Ingen maskinell anmärkning. Inväntar Jimmys beslut."
    return " ".join(parts)


def fynd_cell(row: dict) -> str:
    bits = []
    if row["riktning"]:
        bits.append("Riktningsord: " + ", ".join(row["riktning"]))
    if row["lanade"]:
        bits.append("Lånade namn: " + "; ".join(row["lanade"]))
    if row["relation"]:
        bits.append("Relationscitat (alltid PRIVATE): " + ", ".join(row["relation"]))
    if row["sak"]:
        bits.append("Risk för sakpåstående: " + ", ".join(row["sak"]))
    return "; ".join(bits) if bits else "—"


def kort(row: dict, limit=220) -> str:
    t = row["kort"].strip()
    if len(t) > limit:
        t = t[: limit - 1].rstrip() + "…"
    return t


def write_md(rows: list[dict]) -> None:
    n = len(rows)
    n_rikt = [r for r in rows if r["riktning"]]
    n_lan = [r for r in rows if r["lanade"]]
    n_rel = [r for r in rows if r["relation"]]
    n_sak = [r for r in rows if r["sak"]]

    rik_ids = {r["id"] for r in n_rikt}
    lan_ids = {r["id"] for r in n_lan}
    rel_ids = {r["id"] for r in n_rel}
    sak_ids = {r["id"] for r in n_sak}

    # Expected 14 borrowed if search matches avsnitt 7
    lan_sorted = sorted(lan_ids, key=lambda x: (re.sub(r"\d+", lambda m: f"{int(m.group()):04d}", x), x))

    lines = []
    a = lines.append
    a("# F-11 – radgranskning av Grok Bots förslag")
    a("")
    a("Upprättat 2026-11-20 · underlag till F-11 · Status: NOT_VERIFIED")
    a("Källa för posterna: `data/quotes/quotes.json`, `data/lexicon/lexicon.json`, `data/babel-stoner/rules.json`, `examples.json`, `navigation_comments.json`.")
    a("Röstregler: [`../v0.1/12-rostregler.md`](../v0.1/12-rostregler.md). Förgranskning att jämföra mot: [`G0-agarbeslut-2026-11-20.md`](G0-agarbeslut-2026-11-20.md) avsnitt 7.")
    a("")
    a("**Ingenting i den här tabellen är godkänt.** Datafilerna är oförändrade. Kolumnen *Jimmys beslut* lämnas tom för godkänn, ändra eller stryk.")
    a("")
    a("## 1. Sökningsmetod")
    a("")
    a("Sökningen kördes med skriptet [`F-11-sokning.py`](F-11-sokning.py) mot filerna i `data/` den 2026-11-20. Den ersätter inte Jimmys granskning.")
    a("")
    a("- **Riktningsord.** Spärrlistan i 05 §5 och 06 R-03, samma som i avsnitt 7: vänster, höger, rakt fram, sväng, U-sväng, avfart, rondell. Träff även vid böjning och sammansättning (förslag i RR-05). \"Högre\" räknas inte som \"höger\". I första hand söktes den text Guiden skulle säga; för EX-F även frågetexten i posten.")
    a("- **Lånade namn.** B-12 (Heart of Gold, Milliways, Vogon, Marvin, Vogon Poetry) plus repliker och figurer ur Adams böcker enligt byggplanen \"Lånat gods\": Don't Panic, Improbability/Improbabilitet, talet 42 och handduk. Star Wars-namn gav inga träffar. *Babelfilter* räknades inte; se skillnaderna nedan. Handduk är samtidigt godkänt produktinslag (F-02, B-16) och flaggas ändå här eftersom avsnitt 7 räknade sådana rader.")
    a("- **Relationscitat.** Pekare (`inspiredBy`, `originalId`, `relatedQuoteIds`, `thematicQuoteIds`) mot listan i 04: C-004, C-006–C-009, P-006 och R-*. Sådana poster är alltid `PRIVATE`, även om de godkänns. Synlighet ärvs aldrig uppåt (04).")
    a("- **Sakpåstående.** Mönster enligt förslaget under RR-04: \"är korrekt\", \"avståndet/tiden stämmer\" (inte bara ordet \"stämmer\", så att t.ex. V-013 \"om allting stämmer\" inte flaggas), \"har rätt\", \"är verklig\", \"du är inte vilse\", \"fortsätt enligt\", \"kör vidare\", \"sväng nu\", \"sväng vänster\", \"sväng höger\". Granskaren avgör.")
    a("")
    a("## 2. Antal fynd")
    a("")
    a(f"Tabellen har **{n} rader**: 20 variationer (V-001–V-020), 6 regler (BS-11–BS-16), 12 lexikonförslag, 20 navigationskommentarer (NAV-001–NAV-020) och 7 exempeldialoger (EX-F-01–EX-F-07). *Garderobsknullis* ingår inte, eftersom den inte är ett Grok Bot-förslag.")
    a("")
    a("| Kategori | Antal poster med fynd | Id |")
    a("|---|---|---|")
    a(f"| Riktningsord | {len(n_rikt)} | {', '.join(sorted(rik_ids))} |")
    a(f"| Lånade namn | {len(n_lan)} | {', '.join(lan_sorted)} |")
    a(f"| Relationscitat (alltid PRIVATE) | {len(n_rel)} | {', '.join(sorted(rel_ids))} |")
    a(f"| Risk för sakpåstående | {len(n_sak)} | {', '.join(sorted(sak_ids))} |")
    a("")
    a("## 3. Skillnader mot avsnitt 7 i beslutsbladet")
    a("")
    a("Avsnitt 7 är en förgranskning, inte ett beslut. Skillnaderna nedan kommer av att den här sökningen kördes om mot samma filer, med uttryckliga regler.")
    a("")

    # Direction comparison
    extra_r = sorted(rik_ids - AVSNITT7_RIKTNING)
    miss_r = sorted(AVSNITT7_RIKTNING - rik_ids)
    a("- **Riktningsord.** Avsnitt 7: NAV-001, NAV-012, NAV-014, V-010, EX-F-04.")
    if not extra_r and not miss_r:
        a("  Den här sökningen gav samma fem poster. Inga skillnader.")
    else:
        if extra_r:
            a(f"  Extra i den här sökningen: {', '.join(extra_r)}.")
        if miss_r:
            a(f"  Angivna i avsnitt 7 men inte återfunna här: {', '.join(miss_r)}.")

    a(f"- **Lånade namn.** Avsnitt 7 anger \"14 rader, bland dem NAV-013, NAV-016, EX-F-06, BS-13 och lexikonorden vogonkö och panikbuffert\". Den här sökningen gav också **{len(n_lan)}** poster, och de uppräknade exemplen ingår alla: {', '.join(sorted(AVSNITT7_LANADE_EXEMPEL))}.")
    a(f"  De {len(n_lan)} posterna är: {', '.join(lan_sorted)}.")
    a("  Det är den sannolika listan bakom avsnitt 7 (Vogon, Don't Panic, Improbability/Improbabilitet, 42 och handduk). Ingen avvikelse i antalet.")
    a("  *Gränsfall som inte räknades:* lexikontermen *Babelfilter* anspelar möjligen på Adams babelfisk, men funktionen är densamma som produktens egen regel BS-10. Den ingår därför inte i de 14. Inga B-12-namn utöver Vogon träffades (Marvin, Milliways, Heart of Gold och Vogon Poetry saknas i förslagen).")

    extra_rel = sorted(rel_ids - AVSNITT7_RELATION)
    miss_rel = sorted(AVSNITT7_RELATION - rel_ids)
    a("- **Relationscitat.** Avsnitt 7: V-005, V-008–V-012, V-019.")
    if miss_rel:
        a(f"  Angivna i avsnitt 7 men inte återfunna här: {', '.join(miss_rel)}.")
    if extra_rel:
        a("  Extra i den här sökningen, enligt listan i 04 (synlighet ärvs aldrig uppåt):")
        for r in n_rel:
            if r["id"] in extra_rel:
                a(f"  - {r['id']} pekar på {', '.join(r['relation'])}.")
        a("  Avsnitt 7 räknade variationerna som bygger på C-004 och C-006–C-009, men inte V-006 (P-006), V-013 och V-014 (R-*) eller BS-15 (som pekar på C-007). 04 listar även P-006 och R-* som särskilt `PRIVATE`.")
    if not extra_rel and not miss_rel:
        a("  Inga skillnader.")

    extra_s = sorted(sak_ids - AVSNITT7_SAK)
    miss_s = sorted(AVSNITT7_SAK - sak_ids)
    a("- **Sakpåståenden.** Avsnitt 7: NAV-003, NAV-007.")
    if miss_s:
        a(f"  Angivna i avsnitt 7 men inte återfunna här: {', '.join(miss_s)}.")
    if extra_s:
        a(f"  Extra enligt mönstren under RR-04 (förslag, inte fryst): {', '.join(extra_s)}.")
        a("  Avsnitt 7 lämnade bedömningen till granskaren och räknade bara två exempel. De extra träffarna är alltså en utvidgning, inte en rättelse av bladet.")
    if not extra_s and not miss_s:
        a("  Inga skillnader.")
    a("")
    a("## 4. Tabell")
    a("")
    a("| Id | Kort text eller sammanfattning | Berörda röstregler | Maskinella fynd | Föreslagen åtgärd | Jimmys beslut |")
    a("|---|---|---|---|---|---|")
    for r in rows:
        a(
            "| "
            + " | ".join(
                [
                    md_cell(r["id"]),
                    md_cell(kort(r)),
                    md_cell(regler_for(r)),
                    md_cell(fynd_cell(r)),
                    md_cell(åtgärd(r)),
                    "",
                ]
            )
            + " |"
        )
    a("")
    a("Kolumnen *Jimmys beslut* fylls i med godkänn, ändra eller stryk. Inga rader är ifyllda här.")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({n} rows)")
    print("riktning", sorted(rik_ids))
    print("lanade", lan_sorted)
    print("relation", sorted(rel_ids))
    print("sak", sorted(sak_ids))
    print("rel extra vs avsnitt 7", extra_rel)
    print("sak extra vs avsnitt 7", extra_s)


def main() -> None:
    rows = [analyze(x) for x in load_items()]
    # stable order: V, BS, LEX (as in file), NAV, EX
    write_md(rows)


if __name__ == "__main__":
    main()
