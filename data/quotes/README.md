# Citatarkivet

Grunden för Illogical Engine. Används av Don't Panic, Cosmic Navigator, Babel-Stoner och citatvyerna. Se [`docs/PRD.md`, avsnitt 8](../../docs/PRD.md#8-citatarkivet-som-grund).

Källan är sammanställningen *Citatarkiv och samtalslogik för Babel-Stoner* (20 november 2026). Det är en sammanställning från återfunna arkivdokument och tidigare samtalskontext, inte en fullständig inventering av samtliga meddelanden, ljudtranskriptioner och dokument.

Närliggande filer:

- `data/quotes/quotes.json` – citat, proveniens, teman och värdepar
- `data/lexicon/lexicon.json` – uppslagsdefinitionen *Garderobsknullis*
- `data/babel-stoner/rules.json` – regelbiblioteket BS-01–BS-10 och samtalsmönster från Del II
- `data/babel-stoner/examples.json` – nyskrivna exempeldialoger

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
| `konstruerad` | Dialog eller exempel nyskrivet för appen. Inte ett återgivet verkligt samtal. | Ligger i `data/babel-stoner/examples.json`, inte bland originalen |

Flera P-poster är enligt sammanställningen återgivna från tidigare chattindex snarare än kontrollerade mot en råexport. R-posterna kommer från bokprojektets arkivunderlag. C-007 sparades uttryckligen den 18 november 2026 och fanns redan i den här filen som `orig-0001`; det id:t behålls i fältet `legacyId`.

Observationerna i Del II (t.ex. Den omöjliga rättelsen, Självbildens försvar) är **hypoteser och samtalsmönster** för satirisk dialog. De är inte fastställda psykologiska fakta om någon person. Se `data/babel-stoner/rules.json`.

## Fält i `quotes.json`

**Rot**

| Fält | Beskrivning |
|---|---|
| `schemaVersion` | Nu `2` (en gemensam `quotes`-lista med proveniens i stället för bara `originals`) |
| `compilation` | Metadata om källsammanställningen |
| `valuePairs` | Motsatspar som motorn kan spela ut, t.ex. frihet ↔ trygghet och kaos ↔ kontroll |
| `quotes` | Alla citat och redaktionella sammanfattningar |
| `variations` | Tom tills en variation godkänns |

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

**`variations[]`** (samma modell som tidigare, fortfarande tom)

| Fält | Typ | Beskrivning |
|---|---|---|
| `id` | string | t.ex. `var-0001` |
| `type` | `"variation"` | Alltid `variation` |
| `originalId` | string | Måste peka på ett befintligt original (`provenance: original`) |
| `text` | string | Variationens text |
| `generatedBy` | `"rule"` \| `"llm"` \| `"human"` | Hur den skapades |
| `mechanism` | string | t.ex. `inversion`, `paradox`, `ansvarsforskjutning` |
| `approved` | boolean | Om variationen har godkänts för visning |

## Nuvarande innehåll

- 19 original, 2 bearbetade formuleringar (C-008, C-009), 1 redaktionell sammanfattning (RED-P-007).
- C-007 är sammanslaget med den tidigare posten `orig-0001`; text och teman är oförändrade.
- `variations` är tom.
- *Garderobsknullis* ligger i lexikonfilen som huvudversion, daterad 20 november 2026.
- Konstruerade dialoger, däribland exemplet "Du lovade att ringa mig igår", ligger i `data/babel-stoner/examples.json`.
