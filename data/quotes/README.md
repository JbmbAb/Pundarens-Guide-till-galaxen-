> **STYRANDE BESLUT:** [Gällande inriktning](../../docs/GALLANDE-INRIKTNING.md) har företräde framför äldre planer och designförslag.

# Citatarkivet

Grunden för Illogical Engine. Används av Don't Panic, Cosmic Navigator, Babel-Stoner och citatvyerna. Se avsnitt 5 i den gällande inriktningen och, för historiken, [avsnitt 8 i den arkiverade PRD:n](../../docs/archive/PRD-fore-2026-11-21.md#8-citatarkivet-som-grund).

`chatt-2026-11-21.json` är ett källutkast med återgivna formuleringar från projektchatten. Det läses inte av appen, och inget i det får ges proveniens `original` utan kontroll mot källan.

Källan är sammanställningen *Citatarkiv och samtalslogik för Babel-Stoner* (20 november 2026). Det är en sammanställning från återfunna arkivdokument och tidigare samtalskontext, inte en fullständig inventering av samtliga meddelanden, ljudtranskriptioner och dokument.

Närliggande filer:

- `data/quotes/quotes.json` – citat, proveniens, teman, värdepar och variationer/förslag
- `data/lexicon/lexicon.json` – uppslagsdefinitionen *Garderobsknullis* samt föreslagna galaxtermer
- `data/babel-stoner/rules.json` – regelbiblioteket BS-01–BS-10, förslag BS-11 och uppåt, samt samtalsmönster från Del II
- `data/babel-stoner/examples.json` – nyskrivna exempeldialoger (även förslag)
- `data/babel-stoner/navigation_comments.json` – föreslagna anti-navigeringskommentarer

## Regeln: original ändras aldrig, nya variationer märks

1. **Original bevaras ordagrant.** Ett originalcitat skrivs aldrig om, kortas, rättas eller översätts i sin lagrade form. Stavning, mellanslag och ev. stavfel (t.ex. "arlset", "maninte", "dåförlorar", "huvet", "pissa") behålls exakt. Ett original ändras bara om källan, det personliga arkivet, ändras.
2. **Nya variationer märks som sådana.** Allt som motorn eller en människa skapar utifrån ett original lagras separat i listan `variations`, pekar på sitt original via `originalId` och visas i appen tydligt märkt som variation.
3. **Inga påhittade original.** Bara formuleringar från det personliga arkivet med proveniens `original` får behandlas som personliga originalcitat.
4. **Normalisering är inte original.** P-001 får ha ett separat fält `normalized`. Det är tydligt märkt som inte original (`isOriginal: false`).
5. Inversioner från motsägelsemotorn (t.ex. "Stabilitet = Galenskap") är alltid variationer.

## Proveniensnivåer

Varje post i `quotes` har fältet `provenance`:

| Nivå | Betydelse | I denna inläggning |
|---|---|---|
| `original` | Dokumenterad formulering som ska bevaras utan omskrivning. Dokumenterad formulering innebär inte automatiskt verifierat upphov mot en råexport. | P-001–P-007; C-001, C-002a, C-002b, C-003, C-004, C-005, C-006, C-007; R-001–R-003; N-002 |
| `bearbetad` | Gemensamt bearbetad, godkänd textformulering – inte ett säkert självständigt originalcitat. | C-008, C-009 (meddelandetext 17 november 2026) |
| `redaktionell` | Redaktionell sammanfattning av ett original, inte ett citat i sig. | RED-P-007 (sammanfattning av P-007 i Del II §4) |
| `konstruerad` | Dialog eller exempel nyskrivet för appen. Inte ett återgivet verkligt samtal. | Ligger i `data/babel-stoner/examples.json` och `navigation_comments.json`, inte bland originalen |
| `variation` / `förslag` | Ny text skapad utifrån arkivet eller för appens ton. Får aldrig tillskrivas användaren och får aldrig ändra ett original. | Variationer V-001–V-020 i `quotes.json` (`variations[]`); lexikonförslag och BS-11 och uppåt märks `förslag` |

Flera P-poster är enligt sammanställningen återgivna från tidigare chattindex snarare än kontrollerade mot en råexport. R-posterna kommer från bokprojektets arkivunderlag. C-007 sparades uttryckligen den 18 november 2026 och fanns redan i den här filen som `orig-0001`; det id:t behålls i fältet `legacyId`.

Observationerna i Del II (t.ex. Den omöjliga rättelsen, Självbildens försvar) är **hypoteser och samtalsmönster** för satirisk dialog. De är inte fastställda psykologiska fakta om någon person. Se `data/babel-stoner/rules.json`.

### Variationer och förslag

`variation` och `förslag` är samma nivå i praktiken: ny text som inte kommer från det personliga arkivet.

- **Variationer** ligger i listan `variations`, inte i `quotes`. De har `provenance: "variation"`, `author: "Grok Bot (förslag)"` och `inspiredBy` (citat-id som de utgår från). `originalId` pekar på ett befintligt original. De ska visas i appen som variation/förslag, aldrig som användarens citat.
- **Lexikonförslag** och **nya regler** (BS-11 och uppåt) märks med `provenance: "förslag"` och samma författarfält.
- **Anti-navigeringskommentarer** är konstruerade. De får vara fel ute eller absurda som kommentar, men aldrig ge en manöver som motsäger den verkliga rutten.
- Inga förslag ställer diagnoser eller tillskriver verkliga personer motiv.

## Fält i `quotes.json`

**Rot**

| Fält | Beskrivning |
|---|---|
| `schemaVersion` | Nu `2` (en gemensam `quotes`-lista med proveniens i stället för bara `originals`) |
| `compilation` | Metadata om källsammanställningen |
| `valuePairs` | Motsatspar som motorn kan spela ut, t.ex. frihet ↔ trygghet och kaos ↔ kontroll |
| `quotes` | Alla citat och redaktionella sammanfattningar |
| `variations` | Föreslagna variationer, strikt åtskilda från `quotes` |

**`quotes[]`**

| Fält | Typ | Beskrivning |
|---|---|---|
| `id` | string | Stabilt id, t.ex. `P-001`, `C-007`, `RED-P-007` |
| `title` | string eller null | Titel från arkivet, om en sådan finns |
| `text` | string | Ordagrann text |
| `category` | `"paradox"` \| `"tillit"` \| `"rikard"` \| `"ovrigt"` | Innehållskategori |
| `provenance` | se tabellen ovan | Proveniensnivå |
| `language` | string | t.ex. `sv` |
| `themes` | string[] | Teman. För C-007 behålls de tidigare temana `tillit` och `löften` |
| `valuePairs` | string[] | Id:n från `valuePairs`, om citatet tydligt hör till ett par |
| `sourceNote` | string | Anteckning om källa och säkerhet |
| `source` | string, valfritt | Behålls från tidigare post för C-007 (`personligt citatarkiv`) |
| `legacyId` | string, valfritt | Tidigare id; C-007 har `orig-0001` |
| `normalized` | objekt, valfritt | Bara P-001; inte originalstavning |
| `summarizesQuoteId` | string, valfritt | För redaktionella sammanfattningar |

**`variations[]`**

| Fält | Typ | Beskrivning |
|---|---|---|
| `id` | string | Stabilt id, t.ex. `V-001` |
| `type` | `"variation"` | Alltid `variation` |
| `provenance` | `"variation"` | Alltid `variation` för den här listan |
| `author` | string | För Grok Bots förslag: `Grok Bot (förslag)` |
| `inspiredBy` | string[] | Citat-id som variationen utgår från |
| `originalId` | string | Måste peka på ett befintligt original (`provenance: original`) |
| `text` | string | Variationens text |
| `language` | string | t.ex. `sv` |
| `themes` | string[] | Teman |
| `generatedBy` | `"rule"` \| `"llm"` \| `"human"` \| `"förslag"` | Hur den skapades |
| `mechanism` | string | t.ex. `inversion`, `paradox`, `ansvarsforskjutning` |
| `approved` | boolean | Om variationen har godkänts för visning. Förslag börjar som `false` |
| `status` | string, valfritt | t.ex. `förslag` |
| `sourceNote` | string | Anteckning om att texten inte är ett personligt original |

## Nuvarande innehåll

- 19 original, 2 bearbetade formuleringar (C-008, C-009), 1 redaktionell sammanfattning (RED-P-007).
- C-007 är sammanslaget med den tidigare posten `orig-0001`; text och teman är oförändrade.
- 20 variationer (V-001–V-020) i `variations`, alla märkta `Grok Bot (förslag)` och `approved: false`.
- *Garderobsknullis* ligger i lexikonfilen som huvudversion, daterad 20 november 2026, plus 12 föreslagna galaxtermer.
- Konstruerade dialoger, däribland exemplet "Du lovade att ringa mig igår" och sju senare förslag, ligger i `data/babel-stoner/examples.json`.
- Regler BS-01–BS-10 är arkivets grundbibliotek; BS-11–BS-16 är förslag.
- 20 anti-navigeringskommentarer i `data/babel-stoner/navigation_comments.json`.
