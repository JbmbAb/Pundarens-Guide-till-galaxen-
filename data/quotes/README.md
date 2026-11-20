# Citatarkivet

Grunden för Illogical Engine. Används av Don't Panic, Cosmic Navigator, Babel-Stoner och citatvyerna. Se [`docs/PRD.md`, avsnitt 8](../../docs/PRD.md#8-citatarkivet-som-grund).

## Regeln: original kontra variation

1. **Original (`"type": "original"`) bevaras ordagrant.** De skrivs aldrig om, kortas, rättas eller översätts. Ett original ändras bara om källan, det personliga arkivet, ändras.
2. **Variationer (`"type": "variation"`) lagras separat** i listan `variations`. De pekar alltid på sitt original via `originalId` och visas i appen tydligt märkta som variationer.
3. **Inga påhittade original.** Bara citat från det personliga arkivet får läggas i `originals`. Saknas citat används platshållare i dokumentationen, aldrig uppfunna citat.
4. Inversioner från motsägelsemotorn (t.ex. "Stabilitet = Galenskap") är alltid variationer.

## Fält

**`originals[]`**

| Fält | Typ | Beskrivning |
|---|---|---|
| `id` | string | Stabilt id, t.ex. `orig-0001` |
| `type` | `"original"` | Alltid `original` |
| `text` | string | Ordagrant citat |
| `language` | string | t.ex. `sv` |
| `themes` | string[] | Teman (hunger, flykt, natt, tillit, …). Lämna tomt om inget är uppenbart |
| `valuePairs` | string[] | Id:n från `valuePairs`, om citatet tydligt hör till ett par |
| `source` | string | Varifrån citatet kommer |

**`variations[]`**

| Fält | Typ | Beskrivning |
|---|---|---|
| `id` | string | t.ex. `var-0001` |
| `type` | `"variation"` | Alltid `variation` |
| `originalId` | string | Måste peka på ett befintligt original |
| `text` | string | Variationens text |
| `generatedBy` | `"rule"` \| `"llm"` \| `"human"` | Hur den skapades |
| `mechanism` | string | t.ex. `inversion`, `paradox`, `ansvarsforskjutning` |
| `approved` | boolean | Om variationen har godkänts för visning |

**`valuePairs[]`**: motsatspar som motorn kan spela ut mot varandra, t.ex. frihet ↔ trygghet och kaos ↔ kontroll.

## Nuvarande innehåll

- Ett fastställt original (`orig-0001`). Teman `tillit` och `löften` är satta eftersom de framgår direkt av texten, och inga värdepar är kopplade.
- `variations` är tom.
- **Resten av arkivet:** `[PLATSHÅLLARE – hämtas från det personliga citatarkivet]`
