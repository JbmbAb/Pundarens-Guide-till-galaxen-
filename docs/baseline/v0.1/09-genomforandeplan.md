# 09 – Etappindelad genomförandeplan

Baslinje v0.1 · 2026-11-20

Byggplanens ordning gäller: **rösten först, kartan sedan, ingenting vidare utan grind.** Grindarna finns i [08](08-teknikverifieringsplan.md#2-grindar). Varje grind avslutas med kall granskning av någon som inte har byggt delen, och beslutet ACCEPT, REVISE eller REJECT. Den stående frågan i varje fas är: *kan tramslagret på något sätt påverka ett besked?*

Röstmodulen (etapp 2 och G4) och den tekniska navigationsprototypen (etapp 3) får göras parallellt, men ingen senare etapp räknas som godkänd innan dess beroenden har verifierats. Ingen funktion är klar för att koden är skriven.

| Etapp | Innehåll | Leverabler | Beroenden | Grind | Status |
|---|---|---|---|---|---|
| **0** | Baslinje (det här dokumentet) | `docs/baseline/v0.1/` | – | Jimmys granskning av v0.1 | REVISE (väntar på Jimmy) |
| **1** | Frys koncept, designval och röstregler | Val av designsystem och skärmvariant. Ersättare för lånade namn. Röstregler på en sida. Fryst händelselista. Godkända eller strukna rader bland Grok Bots förslag. Prototyp av arkivdatabasen med triggers. | Etapp 0 | G0 | NOT_VERIFIED |
| **2** | Illogical Engine isolerat | `:truth:api` (bara typer), `:engine:api`, `:engine`, `:archive:api`, `:archive`, `:safetygate`. Radbank med cirka 150 rader. Händelsesimulator. Talsyntes kopplad till simulatorn. Ingen karta. | G0 | G1 (och G4 parallellt) | NOT_VERIFIED |
| **3** | Bevisa offlinekedjan valhalla-mobile → Ferrostar → MapLibre | Kastbar prototyp (08, avsnitt 3). Verifieringsprotokoll G2 och G3. Låsta versioner av valhalla-mobile, Ferrostar och MapLibre. | G0 (kan starta parallellt med etapp 2) | G2, G3 | NOT_VERIFIED |
| **4** | Sanningskärnan för ett testområde | `:truth:core`, offline-paket för testområdet, sanningsremsa, rösten inkopplad via dioden och SafetyGate, nödkort utan konvoj. Tio verkliga turer. | G1, G2, G3, G4 | G5 = **version 1** | NOT_VERIFIED |
| **5** | Praktiska funktioner bakom Handduksläget och osannolikhetsdriften | Handduken, snacks, hemväg, nödkortet fullt ut, osannolikhetsdriften (restidsmatris), Perspektivvyn, Fel fråga, protokollet | G5 | G6 | NOT_VERIFIED |
| **6** | Convoy för två | `:convoy:api`, `:convoy:wss`, självhostad server, samtyckesflöde, hotmodellstester | G5 | G7 | NOT_VERIFIED |
| **7** | Babel-Stoner och Galactic Encyclopedia | `:babel` med Satirläge (spelet) och Analysläge (frågebokföring), `:llm:api` med lokal modell eller LAN, encyklopedi med fältanteckningar | G1 (motor), G5 | G8 | NOT_VERIFIED |

## Etapp 1 i detalj (nästa steg)

Etapp 1 är nästan helt beslut som bara Jimmy kan fatta. Den kräver ingen kod utom arkivdatabasens prototyp.

1. Jimmy besvarar frågorna i [11](11-tekniska-antaganden.md#frågor-som-bara-jimmy-kan-besvara).
2. Röstreglerna skrivs på en sida: ton (lugn myndighetssvenska), skuldregeln, Rikardregeln, förbjudna riktningsord i svansar och de åtta mekanismerna med ett exempel var.
3. Jimmy går igenom V-001–V-020, BS-11–BS-16, lexikonförslagen och NAV-001–NAV-020 och sätter `APPROVED` eller `REJECTED` per rad.
4. Händelselistan i 05 fryses.
5. Kandidatvärdena (markerade *) i 06 och 07 fryses eller justeras.
6. G0-protokollet skrivs och granskas.

## Vad som händer med PR #1

Se [10-beslutslogg.md](10-beslutslogg.md#pr-1). Kortfattat: PR #1 slås inte ihop. Delar av `:engine` kan återanvändas i etapp 2 efter omarbetning.
