# 08 – Teknikverifieringsplan för Android

Baslinje v0.1 · 2026-11-20

G0 är `ACCEPT` genom ägarbeslut med accepterad risk 2026-11-20 kl. 22.32 (Europe/Stockholm), utan oberoende granskning, se [G0-grindbeslut-2026-11-20.md](../verification/G0-grindbeslut-2026-11-20.md). Alla andra grindar har status `NOT_VERIFIED` tills ett verifieringsprotokoll finns.

## 1. Kontrollerade bibliotekfakta

Fakta nedan kontrollerades mot källorna den **2026-11-20** (Europe/Stockholm). Att något står i dokumentationen bevisar inte att det fungerar i vår kedja. Därför har varje rad två statusar: *Källa* säger om påståendet finns i källan, och *I vår kedja* säger om vi har bevisat det själva.

| Id | Påstående | Källa | Källa kontrollerad | I vår kedja |
|---|---|---|---|---|
| T-01 | MapLibre Android läser PMTiles som tile-källa från och med version 11.7.0, med prefixet `pmtiles://` | [MapLibre Android Examples, PMTiles](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/) | ACCEPT | NOT_VERIFIED |
| T-02 | Lokala filer läses med `pmtiles://file://<absolut sökväg>`, och filer i `assets` med `pmtiles://asset://…` | Samma | ACCEPT | NOT_VERIFIED |
| T-03 | "PMTiles sources do not support offline pack downloads or caching." Vi behöver inte det, eftersom hela filen ligger lokalt. | Samma | ACCEPT | NOT_VERIFIED |
| T-04 | MapLibre Native har aktiva Android-releaser, t.ex. `android-v13.7.1` (2026-10-15) och `android-v13.5.4` (2026-11-09) | [GitHub releases, maplibre/maplibre-native](https://github.com/maplibre/maplibre-native/releases) | ACCEPT | Version inte vald (NOT_VERIFIED) |
| T-05 | valhalla-mobile bygger Valhalla som delat bibliotek för Android och exponerar `route`, `trace_route`, `trace_attributes`, `height` och `sources_to_targets`, alla mot rutor på enheten | [Rallista/valhalla-mobile README](https://github.com/Rallista/valhalla-mobile) | ACCEPT | NOT_VERIFIED |
| T-06 | valhalla-mobile stöder **inte** `isochrone`, `optimized_route`, `locate` eller `expansion` | Samma | ACCEPT | – (följd: osannolikhetsdriften byggs på `sources_to_targets`) |
| T-07 | valhallas `route` kan svara i OSRM-format, som avkodas på Android. PBF stöds inte. | Samma | ACCEPT | NOT_VERIFIED |
| T-08 | valhalla-mobile har MIT-licens. Senaste release är 0.6.6 (2026-11-16). README:s exempel visar 0.6.1 och badge Valhalla 3.9.2. | GitHub API och README | ACCEPT | Version inte vald (K-18) |
| T-09 | Ferrostar har en BSD-licens (3-klausul enligt `LICENSE.txt`). Senaste release 0.59.0 (2026-10-22). | [stadiamaps/ferrostar](https://github.com/stadiamaps/ferrostar), `LICENSE.txt` | ACCEPT | – |
| T-10 | Ferrostar märks själv som **beta** och ger inga API-stabilitetsgarantier | [Ferrostar-guiden](https://stadiamaps.github.io/ferrostar/print.html) | ACCEPT | Risk R-01 i 11 |
| T-11 | Ferrostar har två sorters ruttkällor: `RouteAdapter` (request generator och response parser, för HTTP) och `CustomRouteProvider` (för t.ex. lokal ruttning) | Samma | ACCEPT | NOT_VERIFIED |
| T-12 | Ferrostars OSRM-parser kan läsa Valhallas OSRM-kompatibla svar ("with a single RouteResponseParser … we can parse responses from a Valhalla server or an OSRM server") | Samma | ACCEPT | NOT_VERIFIED. Att valhalla-mobiles OSRM-utdata innehåller allt Ferrostar behöver (t.ex. röst- och bannerinstruktioner) är **inte** kontrollerat. |
| T-13 | Ferrostar Android upptäcker avvikelse (`RouteDeviationTracking.StaticThreshold`) och räknar om som standard när användaren är utanför rutten. Omräkning sker på plattformsnivå (Kotlin och Swift), inte i Rust-kärnan. | Samma | ACCEPT | NOT_VERIFIED offline |
| T-14 | Ferrostar har `AndroidTtsObserver`, som använder Androids inbyggda talsyntes | Samma | ACCEPT | NOT_VERIFIED |
| T-15 | Ferrostar har `AndroidSystemLocationProvider` (AOSP `LocationManager`) och, som tillval, `FusedLocationProvider` via Google Play-tjänster i artefakten `google-play-services` | Samma | ACCEPT | Vi väljer AOSP (K-17) |
| T-16 | Ferrostar har `SimulatedLocationProvider` för simulerad körning | Samma | ACCEPT | NOT_VERIFIED |
| T-17 | Android-artefakter: `com.stadiamaps.ferrostar:core`, `ui-maplibre`, `ui-compose` | Samma | ACCEPT | NOT_VERIFIED att de löses från Maven Central med vår AGP- och Kotlin-version |
| T-18 | Ett färdigt recept för valhalla-mobile → Ferrostar har **inte** hittats. GitHubs kodsökning gav inga träffar med vår token, vilket inte bevisar att det saknas. | Egen sökning 2026-11-20 | NOT_VERIFIED | NOT_VERIFIED |
| T-19 | GraphHoppers offline-stöd för Android släpptes efter version 1.0. Det kan fås att fungera (användare nämner 4.0 och 5.3) men stöds inte officiellt. | [GraphHopper-forumet](https://discuss.graphhopper.com/t/offlne-routing-on-android/9176), inlägg 2025-02-07 | ACCEPT | – (stöder REJECT av GraphHopper) |
| T-20 | Androids `TextToSpeech` väljer språk med `setLanguage(Locale)` och `isLanguageAvailable`. Appar som riktar sig mot Android 11 eller senare ska deklarera `TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE` under `queries` i manifestet. | [Android Developers, TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) | ACCEPT | NOT_VERIFIED |
| T-21 | Googles motor "Speech Recognition & Synthesis" har svenska (Sverige) | [Wikipedia](https://en.wikipedia.org/wiki/Google_Text-to-Speech), [Google TalkBack-hjälp](https://support.google.com/accessibility/android/answer/11101402) | ACCEPT | **Krockar med "ingen Google" (K-16)** |
| T-22 | Det finns öppna svenska Piper-röster: `sv_SE/alma`, `sv_SE/lisa` och `sv_SE/nst` | [rhasspy/piper-voices på Hugging Face](https://huggingface.co/rhasspy/piper-voices/tree/main/sv/sv_SE) | ACCEPT | NOT_VERIFIED att de går att köra som Android-talmotor |
| T-23 | sherpa-onnx (Apache-2.0, aktivt 2026-11-20) är en kandidat för att köra sådana röster på Android | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Bara licens och aktivitet kontrollerade | NOT_VERIFIED att den fungerar som systemets talmotor |
| T-24 | Lantmäteriets öppna data ligger under CC0. Topografi 50 innehåller bland annat vandringsled, vinterled och höjdkurvor i SWEREF 99 TM. | Byggplanens källor ([Lantmäteriet](https://lantmateriet.se/en/geodata/our-products/open-data)) | Inte omkontrollerat 2026-11-20 | NOT_VERIFIED (M-15) |

## 2. Grindar

Varje grind har ett verifieringsprotokoll i `docs/baseline/verification/Gx-<datum>.md` med datum, commit-hash, enhet och Android-version, biblioteksversioner, kommandon, mätvärden, avvikelser, granskare och utfall (`ACCEPT`, `REVISE` eller `REJECT`). Utan protokoll är status `NOT_VERIFIED`.

| Grind | Etapp | Vad bevisas | Acceptanskriterier | Stopp vid fel | Status |
|---|---|---|---|---|---|
| **G0** | 1 | Konceptet är fryst | Designsystem och skärmvariant valda (F-02). Lånade namn har ersättare eller är märkta som arbetsnamn. Röstreglerna ryms på en sida och en AI-kodare kan följa dem utan att fråga. Händelselistan (05) är fryst. Prototyp av arkivdatabasen visar att triggers i 04 stoppar update och delete av original, också efter en migrering. | Ja | **ACCEPT genom ägarbeslut med accepterad risk** (2026-11-20 22.32, Europe/Stockholm). Kriterierna är inte bevisade och den oberoende granskningen är inte gjord. Öppna punkter och hur de hanteras: [G0-grindbeslut](../verification/G0-grindbeslut-2026-11-20.md). |
| **G1** | 2 | Illogical Engine fungerar isolerat | Ren Kotlin-modul utan Android och nätverk. Hundra simulerade händelser utan att en svans motsäger ett besked, skyller på användaren eller upprepas inom tio repliker. Motsägelseminnet förnekar bara presenterade yttranden. Bara `APPROVED` rader används. Beroendegrafstest finns och är grönt. | Ja | NOT_VERIFIED |
| **G2** | 3 | Kedjan valhalla-mobile → Ferrostar fungerar offline (startprompten §7.1 och §7.2) | I flygplansläge räknar valhalla-mobile en rutt i testområdet i OSRM-format. Ferrostar läser den via `CustomRouteProvider` och navigerar med `SimulatedLocationProvider`. Manöverbyte, avvikelse (minst 3 försök) och omräkning sker utan nät. Talinstruktioner utlöses. | **Ja. Faller det: stanna, redovisa och föreslå minsta ändring (11, R-01).** | NOT_VERIFIED |
| **G3** | 3 | MapLibre visar lokalt kartpaket på målenheten (§7.3) | `karta.pmtiles` i `filesDir` visas med de tre stilfilerna i flygplansläge. Sanningsremsan ritas ovanpå och täcks aldrig. Ingen nätverkstrafik under testet. | Ja | NOT_VERIFIED |
| **G4** | 2–3 (parallellt) | Svensk talsyntes fungerar med rätt prioritet (§7.4) | Talmotorn är vald (F-06; ingen Google). Kandidater: Samsungs svenska talmotor (`com.samsung.SMT.lang_sv_se_f00`, uppgift ur G0-beslutsbladet avsnitt 5, NOT_VERIFIED på enheten) och öppna Piper-röster via sherpa-onnx (T-22, T-23). Svenska besked uttalas begripligt för 20 testmeningar med gatunamn och siffror (lyssningstest av Jimmy). Beskedet avbryter en pågående svans inom 100 ms*. Fungerar i flygplansläge. | Ja | NOT_VERIFIED |
| **G5** | 4 | Sanningskärnan i testområdet, med lagring och prestanda (§7.5) | Paketets storlek, installationstid, ruttid, kallstart och batteriförbrukning är mätta och godtagbara (gränser sätts i G0). Atomär installation och återställning testade med avbrott (04, 3.3). Tio verkliga turer till fots och som passagerare i flygplansläge: beskedet kommer alltid först och inget trams hörs de sista sekunderna före en manöver (SG-24). Oberoende granskning av dioden. | Ja | NOT_VERIFIED |
| **G6** | 5 | Skämt som bär last | Varje funktion (handduken, snacks, hemväg, nödkortet, osannolikhetsdriften, protokollet) visar sitt underliggande sanna värde och klarar Sirius-provet. "Okänd" visas när data saknas. | Ja | NOT_VERIFIED |
| **G7** | 6 | Convoy för två | Se 07, avsnitt 6 | Ja | NOT_VERIFIED |
| **G8** | 7 | Babel-Stoner och encyklopedin | Satirläget använder bara BS-01–BS-10 och godkända rader. Analysläget pekar varje fynd på ett radnummer, ställer inga diagnoser och märker de tre resultatklasserna. Inget lämnar telefonen utan uttrycklig LAN-konfiguration (nätverkstest). Encyklopedin innehåller bara fältanteckningar. | Ja | NOT_VERIFIED |

\* Kandidatvärde som fryses i G0.

## 3. Prototypen för G2 och G3 (förslag till upplägg)

En separat, kastbar app i en egen gren (`spike/offline-chain`), inte i PR #1:s kodbas.

1. I verkstaden: ta ut OSM för testområdet, bygg Valhalla-rutor med den Valhalla-version som valhalla-mobile använder, och bygg en PMTiles-fil (t.ex. med Planetiler).
2. Kopiera båda till telefonens `filesDir`.
3. Anropa valhalla-mobile med `route` och OSRM-format, och logga hela svaret.
4. Mata svaret till Ferrostar via `CustomRouteProvider` och starta simulerad navigering.
5. Kör i flygplansläge. Spela in skärm och ljud.
6. Skriv protokollet G2 och G3.

Om valhalla-mobiles OSRM-svar saknar fält som Ferrostar kräver är minsta ändring att skriva en egen `RouteResponseParser` eller en översättning i `:truth:core`, inte att byta ruttmotor. Det beslutet fattas i protokollet.

## 4. Det som inte kan verifieras här

- Kvaliteten på svensk talsyntes i Jimmys telefon.
- Storlek på kartfil och ruttrutor för testområdet, eftersom området inte är bestämt (M-11).
- Täckning och ledstatus där Jimmy kör (M-12).
- Juridik kring namn och verkliga personer.
