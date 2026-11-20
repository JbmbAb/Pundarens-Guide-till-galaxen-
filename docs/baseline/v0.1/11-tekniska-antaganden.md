# 11 – Tekniska antaganden, risker och öppna frågor

Baslinje v0.1 · 2026-11-20

## 1. Antaganden som måste verifieras

Alla antaganden har status NOT_VERIFIED. Ett antagande blir ACCEPT först när grinden i kolumnen har ett protokoll som bevisar det.

| Id | Antagande | Varför det spelar roll | Hur det verifieras | Grind | Status |
|---|---|---|---|---|---|
| A-01 | valhalla-mobiles OSRM-svar innehåller allt Ferrostar behöver: steg, manövrer, röst- och bannerinstruktioner, geometri med rätt precision | Utan det fungerar inte kedjan | Prototyp i flygplansläge (08, avsnitt 3), logga hela svaret | G2 | NOT_VERIFIED |
| A-02 | Ferrostars `CustomRouteProvider` kan anropa valhalla-mobile synkront eller via coroutine utan nät | Bärande för offline | Samma prototyp | G2 | NOT_VERIFIED |
| A-03 | Ferrostars avvikelsedetektering och omräkning fungerar mot en lokal ruttkälla | Startprompten §7.2 | Simulerad körning som avviker minst tre gånger | G2 | NOT_VERIFIED |
| A-04 | valhalla-mobile och Ferrostar fungerar med samma AGP-, Kotlin- och NDK-versioner som resten av appen | Byggbarhet | Kompilera prototypen med låsta versioner | G2 | NOT_VERIFIED |
| A-05 | Valhalla-rutor byggda i verkstaden med samma Valhalla-version som valhalla-mobile (badge 3.9.2) läses av biblioteket | Paketformatet | Bygg och läs i prototypen | G2 | NOT_VERIFIED |
| A-06 | MapLibre Android visar en lokal PMTiles-fil i `filesDir` utan nät, med tre stilfiler | Startprompten §7.3 | Måltelefon i flygplansläge, nätverksloggning | G3 | NOT_VERIFIED |
| A-07 | Ferrostars MapLibre-UI kan kombineras med vår egen sanningsremsa och svansyta utan att täcka manöverinformation | N-4, N-6 | Skärmdumpar och UI-test | G3 | NOT_VERIFIED |
| A-08 | Det finns en svensk talmotor som inte kommer från Google och som är begriplig nog för besked | K-16. Jimmy vill inte ha Google. | Lyssningstest av t.ex. Piper-röst via sherpa-onnx (08, T-22, T-23) mot Googles motor | G4 | NOT_VERIFIED |
| A-09 | `TextToSpeech` och Ferrostars `AndroidTtsObserver` kan delas i två kanaler där besked avbryter svans inom 100 ms | R-01 i 06 | Mätning på måltelefon | G4 | NOT_VERIFIED |
| A-10 | Offline-paketet för testområdet ryms på telefonen och laddas tillräckligt fort | Startprompten §7.5 | Mät filstorlekar, installationstid, ruttid, kallstart och batteri | G5 | NOT_VERIFIED |
| A-11 | Atomär rename fungerar på appens interna lagring och klarar avbrott | 04, 3.3 | Avbrottstest: döda processen i varje steg | G5 | NOT_VERIFIED |
| A-12 | `AndroidSystemLocationProvider` (utan Google Play-tjänster) ger tillräcklig noggrannhet och uppdateringstakt | Ingen Google (K-17) | Fälttest jämfört med verkligt spår | G5 | NOT_VERIFIED |
| A-13 | SQLite-triggers kan skapas och överlever migreringar i vald databasteknik (Room eller SQLDelight) | Skrivskyddet för original | Migreringstest | G0 | NOT_VERIFIED |
| A-14 | Gradle-beroendegrafen kan testas automatiskt i CI (t.ex. modules-graph-assert eller eget task) | 03, G-6 | Bygg ett förbjudet beroende och se att CI fallerar | G1 | NOT_VERIFIED |
| A-15 | En Konsist-liknande arkitekturkontroll kan hindra sanningstyper i `TailCandidate` | 03, G-7 | Negativt test | G1 | NOT_VERIFIED |
| A-16 | `sources_to_targets` räcker för osannolikhetsdriften och mötespunkten, eftersom isokroner saknas | 08, T-06 | Prototyp i etapp 5 | G6 | NOT_VERIFIED |
| A-17 | libsodium (t.ex. via Lazysodium) fungerar på målplattformen för XChaCha20-Poly1305 | 07 | Prototyp och granskning | G7 | NOT_VERIFIED |
| A-18 | Androids bakgrundsbegränsningar tillåter att avsändaren stoppar sändning vid sluttid även i bakgrunden | Byggplanens grind för Convoy | Test med appen i bakgrunden och i dvala | G7 | NOT_VERIFIED |
| A-19 | En lokal språkmodell på telefonen ger användbar frågebokföring på svenska, eller så finns en LAN-maskin | Startprompten §9 | Prototyp med inklistrade testsamtal (påhittade, inte verkliga) | G8 | NOT_VERIFIED |
| A-20 | Lantmäteriets produkter som behövs är CC0 och kan kombineras med OSM (ODbL) i samma paket med korrekt upphovsangivelse | Licenser i manifestet | Kontroll i Geotorget och licenstexter | G5 | NOT_VERIFIED |

## 2. Risker

| Id | Risk | Följd | Åtgärd |
|---|---|---|---|
| R-01 | Ferrostar är beta och lovar ingen API-stabilitet (08, T-10) | Uppgraderingar kan kräva omskrivning | Lås version i G2. Kapsla Ferrostar i `:truth:core`. Om kedjan faller: minsta ändring först (egen parser eller översättning), byte av bibliotek sist. |
| R-02 | valhalla-mobile är ett litet projekt (84 stjärnor 2026-11-20) | Underhållsrisk | Lås version, följ releaser, behåll möjligheten att bygga själv (MIT) |
| R-03 | Svensk talsyntes utan Google kan låta dåligt | Rösten är kärnan | G4 tidigt och parallellt |
| R-04 | Rösten är inte rolig | Byggplanen: "är den inte rolig spelar resten ingen roll" | Etapp 2 före kartan. Jimmy granskar radbanken rad för rad. |
| R-05 | Verkliga personer i arkivet (Rikard, Bonnie) | Integritet och juridik | Allt `PRIVATE`. Ingen spridning före samtycke. Verkliga utdrag inte i appen. |
| R-06 | Ingen oberoende granskare (M-14) | Säkerhetsgrindar kan inte bli ACCEPT | Jimmy utser en granskare |
| R-07 | `:app` ser alla moduler | Kan visa en svans som om den vore ett besked | SafetyGate renderar svansar bara i svansytan, plus granskning |

## Frågor som bara Jimmy kan besvara

| Id | Fråga | Behövs för |
|---|---|---|
| F-01 | Godkänner du baslinjen v0.1 som underlag, eller vill du ändra något? | Etapp 0 |
| F-02 | Vilket designsystem (Cosmic Drift eller The Sentient Archive) och vilken variant av varje skärm? Byggplanen föreslår varianten med rubriken Handdukssensorn. | G0 |
| F-03 | Vilket geografiskt testområde ("hemområdet")? Är det Stockholms län, eller ett mindre område? | G2, G5 |
| F-04 | Vilken telefon är måltelefon (modell, Android-version, ledigt lagringsutrymme)? | G3–G5 |
| F-05 | Vilka färdsätt gäller i v1: till fots och som passagerare, eller också bil, cykel, skoter eller ATV? | G5 |
| F-06 | Gäller "ingen Google" också talsyntesmotorn på telefonen (Googles Speech Recognition & Synthesis)? Och biblioteket Tink? | G4, G7 |
| F-07 | Vem är oberoende granskare för säkerhetsgrindarna? | Alla grindar |
| F-08 | Har Rikard tillfrågats, och får hans namn och R-001–R-003 användas i appen? Vill han läsa in dementierna? | Etapp 2, spridning |
| F-09 | Bekräftar du att C-008 och C-009 är godkända bearbetningar, och när du godkände dem? | Arkivimport |
| F-10 | Ska RED-P-007 och den normaliserade P-001 godkännas som `APPROVED_EDIT` eller stå kvar som syntetiska? | Arkivimport |
| F-11 | Vilka av Grok Bots förslag (V-001–V-020, BS-11–BS-16, lexikon, NAV-001–NAV-020, EX-F-01–EX-F-07) godkänner du eller stryker du? | Etapp 1 |
| F-12 | Får `EX-01-FORMULERING` och PAT-01–PAT-05 användas i appen, eller ska de bara finnas i arkivet? | Etapp 7 |
| F-13 | Finns det personliga projektarkivet, och hur ska jag få åtkomst för att arkivera baslinjen där? Finns `Citat_och_paradoxer_falsifiering.md`, bokunderlaget och "bilagan på 912 rader" att tillgå? | M-01, M-02, M-04, M-05 |
| F-14 | Vad är "Mimer"? | M-07 |
| F-15 | Var ska Convoy-servern köras (hemdatorn, en egen VPS)? | Etapp 6 |
| F-16 | Ska PR #1 stängas eller lämnas öppen som referens? | Beslutslogg PR1 |
| F-17 | Vilken licens ska kod och arkiv ha? Repot har ingen LICENSE-fil. | Spridning |
