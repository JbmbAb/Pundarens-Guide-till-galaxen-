# 10 – Beslutslogg

Baslinje v0.1 · 2026-11-20

Varje tidigare förslag får ett utfall: `ACCEPT` (gäller), `REVISE` (gäller i ändrad form), `REJECT` (gäller inte) eller `NOT_VERIFIED` (kan inte avgöras utan bevis). Källorna (S-xx) och konflikterna (K-xx) finns i [01-inventering.md](01-inventering.md). Ett beslut här ersätter inte Jimmys eget. Där det står "Jimmy" i kolumnen *Kräver* måste han bekräfta.

## 1. Produkt och innehåll

| Id | Förslag | Källa | Utfall | Skäl | Kräver |
|---|---|---|---|---|---|
| B-01 | Illogical Engine som appens kärna | PRD 3, S-00 §2 | ACCEPT | Oföränderlig produktidentitet enligt startprompten | – |
| B-02 | Formeln Observation → Paradox → Praktisk verklighet → Eventuell urspårning | PRD 2, S-00 §2 | ACCEPT | Startprompten | – |
| B-03 | "Yoda on a Bad Day" och Inverted Grammar | Stitch-PRD, PRD 2 | REJECT | Byggplanen ändring 3: lånad röst, omvänd ordföljd slits fort. Ingår inte i startpromptens åtta mekanismer. | – |
| B-04 | Anti-navigering: "Ge aldrig direkta order", anti-direktiv | Stitch-PRD, PRD 4.4 | REJECT i fordon, REVISE till fots | Byggplanen ändring 1. Beskedet först, tramset efteråt (02, N-1, N-2). | – |
| B-05 | Stress-mikropauser ("sucken" före uppdateringen) | Stitch-PRD, PRD 4.4 | REJECT | Fördröjer beskedet. Startprompten §4 och byggplanen ändring 2. Sucken får komma efteråt. | – |
| B-06 | PRD:ns exempelrepliker med "Sväng inte höger…" och "Om 200 meter ska du inte svänga vänster…" | PRD 4.5 | REJECT i fordon | Riktningsord i svansen kan höras som ett besked (06, R-03) | – |
| B-07 | Säkerhetsregeln i PRD 4.6 (rutt och pilar alltid korrekta, tyst fönster, ärlig GPS-osäkerhet) | PRD 4.6 | ACCEPT | Stämmer med dioden och SafetyGate. Preciseras i 06. | – |
| B-08 | Rikard-faktorn | PRD 4.7 | REVISE | Behålls som Rikardprincipen (M5) och Rikard-störning med verkligt GPS-värde. Rikard omnämns men hörs aldrig. Verklig person: samtycke före spridning (M-13). | Jimmy (samtycke) |
| B-09 | Tre kartlägen Praktisk, Poetisk, Osannolik | Stitch-PRD, PRD 4.1 | REVISE | Tre MapLibre-stilfiler över samma data (byggplanen). Sanningsremsan påverkas aldrig. | – |
| B-10 | Probability Drive: reglage för kaos, Rikard och sarkasm | PRD 7 | REVISE | Reglagen styr bara Illogical Engine. Namnet "Marvin Mode" byts (B-12). | – |
| B-11 | Don't Panic som visdomsgenerator | PRD 6 | REVISE | Långt tryck blir nödkortet (byggplanen). Visdomen är en svans. Live-AI tillåten här enligt byggplanen, men bara lokalt (B-31). | – |
| B-12 | Lånade namn: Heart of Gold, Milliways, Vogon, Marvin, Vogon Poetry | Stitch-skärmar, PRD | REVISE | Arbetsnamn privat. Byts innan något delas (byggplanen "Lånat gods"). | Jimmy (nya namn) |
| B-13 | Appens namn "Pundarens Guide till Galaxen" | Alla | REVISE | Arbetsnamn. Ligger nära originalets titel. Välj eget före spridning. | Jimmy |
| B-14 | Vibe Protect | Tidigare briefer, PRD 7.1 | REJECT i kärnan | Byggplanen: "Aldrig i kärnan. Egen app, om alls." | – |
| B-15 | Intergalactic Slang som sekundärt läge | PRD 5.6 | REVISE (parkerat) | Inte i v1. Dokumenteras som slutmål. | – |
| B-16 | Handdukssensorn, Towel Tracker | Tidigare briefer | REVISE | Blir avfärdskontrollen "Towel Secured" (etapp 5) | – |
| B-17 | Två designsystem (Cosmic Drift och The Sentient Archive) och flera skärmvarianter | Stitch | REVISE | The Sentient Archive med skärmvarianterna som har rubriken Handdukssensorn, enligt F-02 (2026-11-20). Precisering i G0-beslutsbladet avsnitt 2 (NOT_VERIFIED på enheten). Sanningsremsa läggs till. PR1-20 kan prövas. | Beslutat (F-02) |

## 2. Arkivet och Babel-Stoner

| Id | Förslag | Källa | Utfall | Skäl | Kräver |
|---|---|---|---|---|---|
| B-18 | Proveniensnivåerna original, bearbetad, redaktionell, konstruerad, variation/förslag | S-22 | REVISE | Ersätts av fyra ursprungsklasser, separat verifieringsstatus, godkännande och synlighet (04, 1.3). Informationen bevaras. | – |
| B-19 | Byggplanens tre klasser ORIGINAL, BEARBETAD, GENERERAD | S-01 | REVISE | Startprompten kräver fyra klasser. Mappningen är entydig: ORIGINAL → `ORIGINAL_VERBATIM`, BEARBETAD → `APPROVED_EDIT`, GENERERAD → `SYNTHETIC_SATIRE`, och reglerna får `DERIVED_RULE`. | – |
| B-20 | Synlighet per rad: privat, sällskap, öppen | S-01 | ACCEPT | Blir `PRIVATE`, `COMPANY`, `OPEN`. Allt är `PRIVATE` vid import. | – |
| B-21 | Tolv händelsetyper (byggplanen) | S-01 | REVISE | 05 föreslår fjorton, med `EmergencyCleared` och `TimeOfDayChanged` som tillägg att pröva i etapp 1 | – |
| B-22 | Grok Bots variationer V-001–V-020 | S-23 | NOT_VERIFIED | `SYNTHETIC_SATIRE`, `PENDING`. Underlag för radgranskning i `docs/baseline/verification/F-11-radgranskning.md`. Används inte förrän Jimmy godkänt per rad. Inga rader är godkända. | Jimmy (F-11) |
| B-23 | Grok Bots regler BS-11–BS-16 | S-23 | NOT_VERIFIED | `DERIVED_RULE`, `PENDING`. Startprompten begränsar Satirläget till BS-01–BS-10 och godkända formuleringar. Ingår i F-11-underlaget. Inga rader är godkända. | Jimmy (F-11) |
| B-24 | Grok Bots lexikonförslag (12 st) | S-23 | NOT_VERIFIED | `PENDING`. "vogonko" är lånat gods (B-12). Ingår i F-11-underlaget. Inga rader är godkända. | Jimmy (F-11) |
| B-25 | Grok Bots navigationskommentarer NAV-001–NAV-020 | S-23 | REVISE | Måste prövas mot N-1, N-2 och 06 R-03. Rader med riktningsord stryks i fordonsläge. Underlag i F-11-tabellen. Inga rader är godkända. | Jimmy (F-11) |
| B-26 | Grok Bots exempeldialoger EX-F-01–EX-F-07 | S-23 | NOT_VERIFIED | `PENDING`. Ingår i F-11-underlaget. Inga rader är godkända. | Jimmy (F-11) |
| B-27 | Babel-Stoner med fem personligheter som huvudläge och omvänt läge som sekundärt | PRD 5.3, 5.5 | REVISE | Två separata funktioner: Satirläge (spelet med fem motståndare) och Analysläge (frågebokföring) (byggplanen och startprompten §9) | – |
| B-28 | Grok Bots koppling mellan de fem personligheterna och mekanismerna | PRD 5.3 | NOT_VERIFIED | Inte prövad av Jimmy (PRD 13.6) | Jimmy |
| B-29 | Satiriska mönster PAT-01–PAT-05 och `EX-01-FORMULERING` ur verkliga samtal | S-15, S-11, S-12 | REVISE | Verkliga utdrag stannar i arkivet (byggplanen). Visas inte i appen innan Jimmy granskat (K-15). | Jimmy |
| B-30 | Hänvisning till 112 och 1177 vid hot i omvänt läge | PRD 5.5, PR #1 | ACCEPT | Stämmer med startprompten. Formuleras så att appen aldrig framstår som en ersättning för 112. | – |
| B-31 | LLM för Illogical Engine (PRD 13.2) | PRD | REVISE | Regelstyrd radbank i navigatorn. Lokal modell bara i Analysläget och encyklopedin, på telefonen eller uttrycklig LAN-tjänst. Aldrig moln som standard och aldrig i manövervägen. | – |
| B-32 | Källsammanställningen läggs i repot | Jimmy ("lägg in allt"), S-24 | ACCEPT | Kopierad oförändrad till `docs/baseline/source/` för spårbarhet. Repot är privat. | – |

## 3. Teknik

| Id | Förslag | Källa | Utfall | Skäl | Kräver |
|---|---|---|---|---|---|
| B-40 | Google Maps SDK och maps-compose | S-19, PR #1 | REJECT | Jimmy vill inte ha Google (S-20). Byggplanen: MapLibre. | – |
| B-41 | MapLibre Native för kartvisning | S-21, S-01, S-00 | ACCEPT som kandidat | PMTiles-stöd dokumenterat (08, T-01). Låses först i G3. | – |
| B-42 | GraphHopper som ruttmotor | S-21, PRD 13.3 | REJECT | Startprompten §7. Offline-stöd för Android inte officiellt (08, T-19). | – |
| B-43 | Valhalla på egen server | S-21 | REJECT för v1 | Telefonen ska navigera utan server. Parkerat tills något visar sig omöjligt på telefonen. | – |
| B-44 | valhalla-mobile på enheten | S-01, S-00 | ACCEPT som kandidat | 08, T-05–T-08. Låses i G2. | – |
| B-45 | Ferrostar för navigationslogik | S-01, S-00 | ACCEPT som kandidat | 08, T-09–T-17. Beta-status är en risk (11, R-01). Låses i G2. | – |
| B-46 | Androids talsyntes | S-00 | NOT_VERIFIED | "Ingen Google" gäller också talmotorn (F-06, dokumenterat). Motor väljs i G4. Kandidater: Samsungs svenska talmotor och Piper via sherpa-onnx. | – |
| B-47 | Google Directions/Routes API | PRD 13.3 | REJECT | Google, kräver nät och kostar | – |
| B-48 | GeoJSON i appen först | S-18 | REVISE | Var rätt som första steg. Ersätts av platser i offline-paketet (04, 3). GeoJSON-filerna i `data/` behålls som källdata. | – |
| B-49 | PostGIS lokalt senare, bakom API med bbox-frågor (`RemotePlacesRepository`) | S-18, PRD 9.2–9.3 | REVISE | PostGIS behålls som verkstad hemma. Telefonen kontaktar den aldrig (startprompten §6). `RemotePlacesRepository` REJECT. | – |
| B-50 | PostGIS, Martin eller pg_tileserv som tile-server | S-21 | REJECT för telefonen | Ersätts av PMTiles byggda i verkstaden | – |
| B-51 | Lantmäteriets data som lager, OSM eller NVDB som vägnät | S-21 | REVISE | Sammanslagning OSM och NVDB parkerad tills en verklig felrutt beror på vägdata (byggplanen). Lantmäteriets data i verkstaden, omräknad från SWEREF 99 TM. | – |
| B-52 | KSP och Moshi för JSON | PRD 10, PR #1 | ACCEPT | Inget hinder. Används för import av arkivfiler. | – |
| B-53 | Room som lokal cache | PRD 10 | REVISE | SQLite krävs för arkivet med triggers. Room eller SQLDelight väljs i G0. | – |
| B-54 | Retrofit för API | PRD 10 | REJECT för v1 | Inget API i v1 | – |
| B-55 | Coil för bilder | PRD 10 | NOT_VERIFIED | Inget behov identifierat i v1 | – |
| B-56 | Convoy med delad nyckel i QR och tidsgräns hos avsändaren | S-01 | REVISE | Kombineras med startpromptens servertillstånd och utgångstid (07) | – |
| B-57 | LoRa och Meshtastic | Underlaget enligt byggplanen | REJECT för v1 (parkerat) | Väcks när två turer har misslyckats på grund av täckning | – |
| B-58 | Ruttning på skoterleder | Tidigare briefer | REJECT för v1 | Inget verifierat underlag och inga tillträdesregler (startprompten §7) | – |
| B-59 | Google Play-tjänsternas Fused Location | Ferrostar-tillval | REJECT | Ingen Google. `AndroidSystemLocationProvider` används. | – |
| B-60 | Tink för kryptering | – (övervägt här) | REJECT som förstahandsval | Google-projekt. libsodium föreslås (03). | Jimmy kan ändra |

<a id="pr-1"></a>
## 4. PR #1, "Android-milstolpe 1" (gren `cursor/android-milestone-1-7e26`, `eacb014`)

PR #1 byggdes efter `docs/PRD.md` innan byggplanen och startprompten fanns. Den följer därför beslut som nu är ersatta. Granskat genom diff mot `main` (41 filer, +4 102 rader) den 2026-11-20. PR:ns egen uppgift att `./gradlew test` och `./gradlew assembleDebug` är gröna har **inte** upprepats i den här granskningen och är NOT_VERIFIED här.

| Id | Komponent | Filer | Utfall | Skäl |
|---|---|---|---|---|
| PR1-01 | Google Maps (maps-compose, `MAPS_API_KEY`, manifest-platshållare, `local.defaults.properties`) | `app/build.gradle.kts`, `libs.versions.toml`, `MapScreen.kt`, `AndroidManifest.xml` | **REJECT** | B-40 |
| PR1-02 | Gradle-uppsättning: AGP 9.5, Kotlin 2.4, versionskatalog, wrapper, `minSdk` 26 | `settings.gradle.kts`, `gradle/` | REVISE | Bra utgångspunkt. Modulstrukturen måste följa 03 (`:truth:*`, `:engine:api`, `:safetygate` med flera), och beroendegrafstest ska in. |
| PR1-03 | `:engine` som ren Kotlin-modul utan Android | `engine/build.gradle.kts` | ACCEPT (principen) | Precis den isolering 03 kräver |
| PR1-04 | Sanningstyperna (`FactPayload`, `TurnDirection`, `Place`, `BoundingBox`) ligger i `:engine` | `engine/…/model/Models.kt` | **REJECT** (placeringen) | Sanningstyper ska ägas av `:truth:api`. I PR #1 äger tramslagret dem, vilket vänder dioden. |
| PR1-05 | `EngineOutput` bär med sig `facts` tillbaka ut | `Models.kt`, `IllogicalEngine.kt` | **REJECT** | Sanning passerar tramslagret. Strider mot N-4 och G-4. `TailCandidate` får inte innehålla sanningstyper. |
| PR1-06 | Motorn formulerar själva manöverbeskedet ("Sväng $word om $distance meter.") | `commentary/CommentaryGenerator.kt` (`maneuver`, `instructionLabel`, `realitySentence`) | **REJECT** | Beskedet ska formuleras i Hard Truth Core (Ferrostars instruktioner), aldrig i Illogical Engine |
| PR1-07 | `creativeIsSafe` och testet `commentaryKeepsFactsAndAgreesWithTheManeuver` | `CommentaryGenerator.kt`, `EngineTest.kt` | REVISE | Bra idé: kontroll av att svansen inte motsäger manövern. Flyttas och skärps till R-03 i SafetyGate och 05 §5. |
| PR1-08 | Motsägelseminne (`SessionMemory`) och förnekelse vid högt kaos | `CommentaryGenerator.kt`, test `highChaosCanDeny…` | REVISE | Rätt riktning (M2). Ska bara förneka *presenterade* yttranden och vara spårbart med id (04, 1.8). |
| PR1-09 | Slump med `System.nanoTime()` som standard-seed | `IllogicalEngine.kt` | REVISE | Motorn ska vara deterministisk givet seed (05 §5). Seed sätts alltid av anroparen. |
| PR1-10 | Ett gränssnitt för navigation, Babel, Don't Panic och observation | `IllogicalEngine.kt` | REVISE | Delas: `IllogicalEngine.onEvent` för bussen, `:babel` separat |
| PR1-11 | Navigationskommentarer och variationer används utan godkännande. Variationer slås ihop med citaten (`storedQuotes + variations`). | `ArchiveLoader.kt`, `CommentaryGenerator.kt` | **REJECT** (beteendet) | Strider mot A-4 och R-15. Bara `APPROVED` får användas. |
| PR1-12 | `Provenance` med sex värden och JSON-läsning från assets | `ArchiveLoader.kt`, `JsonDtos.kt`, `Models.kt` | REVISE | Läsningen kan återanvändas som import. Modellen byts mot fyra klasser, verifiering, godkännande och synlighet i SQLite med triggers (04). |
| PR1-13 | Originalcitat visas ordagrant, genererat märks | `ArchiveScreen.kt`, test `parsesRealArchiveWithoutAlteringOriginals` | ACCEPT (principen) | Stämmer med A-1. Synlighet saknas och läggs till. |
| PR1-14 | `PlacesRepository` och `BundledGeoJsonPlacesRepository` | `engine/…/places/Places.kt`, `PlacesTest.kt` | REVISE | Gränssnittet är bra, men platser är sanningsdata och ska ligga i `:truth:core` och läsa från offline-paketet. Inte i `:engine`. |
| PR1-15 | Gradle kopierar `data/` till assets i bygget | `app/build.gradle.kts` | REVISE | En källa för data är rätt. Ersätts av offline-paketet för geodata och av import till databasen för arkivet. |
| PR1-16 | Kartskärm med tre lägen, glitch som inte ligger på pilen | `MapScreen.kt`, `Components.kt` | REVISE | Principen ACCEPT (N-6). Lägena blir MapLibre-stilar. Manövrerna är simulerade knappar, inte ruttning, och får inte se ut som verklig navigering. |
| PR1-17 | `GuideViewModel` blandar sanning, avstånd från kamerans mitt och motoranrop | `GuideViewModel.kt` | **REJECT** | Ingen modulgräns. Avstånd ska komma från Hard Truth Core, och svansar ska gå via SafetyGate. |
| PR1-18 | Babel-Stoner med fem lägen och galaktisk analys | `babel/Babel.kt`, `BabelScreen.kt` | REVISE | Delas i Satirläge och Analysläge. Analysen blandar i dag satir (`rikardVerdict`, satiriska etiketter) med sakfynd. Det strider mot B-4 i 02. |
| PR1-19 | Omvänd läsning med längdgräns, inga diagnoser, hänvisning till 112 och 1177 | `Babel.kt`, test `reverseAnalysisFlagsPatternsWithoutDiagnosing` | REVISE | Bra grund för Analysläget. Varje fynd måste få radnummer, och de tre resultatklasserna måste märkas. |
| PR1-20 | Tema: mörkt, Manrope och Space Grotesk, färger ur Stitch | `Theme.kt`, `res/font/` | NOT_VERIFIED | Beror på valet av designsystem (F-02). Typsnittens licensfiler finns inte i PR:n. |
| PR1-21 | Bottennavigering på svenska (Hem, Karta, Babel, Arkiv, Drift) | `GuideScaffold.kt` | REVISE | Behålls i sak. Ordningen följer v1:s omfattning. |
| PR1-22 | SafetyGate | – | REJECT (saknas) | Finns inte i PR #1 |
| PR1-23 | Tyst fönster före sväng, dämpad glitch i hög fart | – | REJECT (saknas) | Listat som kvar i PR:n. Täcks av 06. |
| PR1-24 | Tester `EngineTest` (8 tester) och `PlacesTest` | `engine/src/test/` | REVISE | Återanvänds som utgångspunkt där semantiken stämmer med 05 och 06 |
| PR1-25 | README-ändringar i PR:n | `README.md` | REJECT | Beskriver Google Maps-stacken |

**Rekommendation (utförd som F-16):** slå inte ihop PR #1. Stäng den med hänvisning till den här beslutsloggen, och behåll grenen `cursor/android-milestone-1-7e26` som referens. PR1-03, 07, 08, 12, 13, 14, 18, 19 och 24 kan återanvändas i etapp 2 efter omarbetning.

PR #1 stängdes utan sammanslagning den 2026-11-20 kl. 19:40 UTC+1 (Europe/Stockholm), verifierat via GitHub API. Grenen finns kvar på `eacb014`. Beslutsbladet avsnitt 3 säger att åtgärden inte är utförd; det är en saklig avvikelse, se [`../verification/G0-agarbeslut-svar.md`](../verification/G0-agarbeslut-svar.md).

<a id="g0-agarbeslut"></a>
## 5. Ägarbeslut 2026-11-20 (G0)

Källa: [`../verification/G0-agarbeslut-2026-11-20.md`](../verification/G0-agarbeslut-2026-11-20.md) (inlagt byte för byte) och instruktionen [`../verification/G0-instruktion-2026-11-20.md`](../verification/G0-instruktion-2026-11-20.md). Svar på sakfel: [`../verification/G0-agarbeslut-svar.md`](../verification/G0-agarbeslut-svar.md). Röstregler: [`12-rostregler.md`](12-rostregler.md). F-11-underlag: [`../verification/F-11-radgranskning.md`](../verification/F-11-radgranskning.md).

Inget här gör G0 till ACCEPT. Alla grindar förblir NOT_VERIFIED. Ingen implementationskod ändrades. `data/quotes/quotes.json` ändrades inte trots F-10 (beslutet gäller i dokumenten, datafilen uppdateras i en senare omgång).

| Id | Utfall | Kort |
|---|---|---|
| F-01 | BESLUTAT | Baslinje v0.1 godkänd som arbetsbaslinje. |
| F-02 | BESLUTAT | The Sentient Archive, varianten Handdukssensorn. Precisering i beslutsbladet avsnitt 2 (NOT_VERIFIED på enheten). Stänger B-17. |
| F-03 | BESLUTAT, precisering öppen | Orsa kommun och angränsande kommuner. Grannkommunerna ritas när paketgränsen sätts. |
| F-04 | BESLUTAT, verifiering öppen | Samsung Galaxy Tab S8 Ultra, SM-X900 (Wi-Fi). Målenhet, inte måltelefon. Android-version och GNSS mäts på enheten. Cirka 60 GB ledigt enligt Jimmy, mäts om i G5. |
| F-05 | DOKUMENTERAT | Till fots och som passagerare. Inte skoter eller ATV. |
| F-06 | DOKUMENTERAT | Ingen Google gäller också talmotor och Tink. libsodium i stället för Tink. |
| F-07 | BESLUTAT | GPT-6, oberoende från byggaren, med skärpta granskningskrav (arkitektur, kod, testbevis, egna protokoll). |
| F-08 | ÖPPEN (blockerar inte G0) | Rikards samtycke före etapp 2 och spridning. Till dess 11 R-05. |
| F-09 | DOKUMENTERAT | C-008 och C-009 är godkända bearbetningar den 17 november 2026. |
| F-10 | BESLUTAT i dokumenten | RED-P-007 och den normaliserade P-001 blir `APPROVED_EDIT`. Originalraderna orörda. Datafilen oförändrad i den här omgången. |
| F-11 | Arbetsordning beslutad, rader öppna | Radgranskning efter röstreglerna. Underlaget är klart. Inga rader godkända. |
| F-12 | DOKUMENTERAT | `EX-01-FORMULERING` och PAT-01–PAT-05 bara i arkivet. |
| F-13 | ÖPPEN | Bilagan på 912 rader och `Citat_och_paradoxer_falsifiering.md` är fortfarande MISSING. Inget rekonstrueras. |
| F-14 | DOKUMENTERAT | Mimer är Jimmys företag. Inget appberoende. Stänger M-07. |
| F-15 | ÖPPEN (blockerar inte G0) | Convoy-server i etapp 6. |
| F-16 | DOKUMENTERAT, redan utfört | PR #1 är stängd utan merge. Grenen finns kvar. Se avvikelsen i svarsfilen. |
| F-17 | ÖPPEN (blockerar inte G0) | Licens före spridning. |

<a id="g0-grindbeslut"></a>
## 6. Grindbeslut G0, 2026-11-20 kl. 22.32 (Europe/Stockholm)

Ägarens beslut ordagrant: "Stäng G0 nu och börja bygga, jag tar risken utan oberoende granskning". Gäller `main` @ `81afd55` (PR #3 sammanslagen). Detaljer och hantering av öppna punkter: [`../verification/G0-grindbeslut-2026-11-20.md`](../verification/G0-grindbeslut-2026-11-20.md).

| Id | Utfall | Kort |
|---|---|---|
| G0 | ACCEPT genom ägarbeslut med accepterad risk | Den oberoende granskningen (F-07) är **inte gjord**. Beslutet går före ägarens tidigare instruktion att G0 skulle vara öppen tills GPT-6 granskat. Övriga grindar `NOT_VERIFIED`. |
| F-11 | Fortfarande öppen | Inga rader godkända. Motorn använder bara `ORIGINAL_VERBATIM`, `APPROVED_EDIT` och deterministiska regler (BS-01–BS-10). Grok Bots förslag med `approved: false` används aldrig. |
| B-12, B-13 | Fortfarande öppna | Lånade namn och appens namn är arbetsnamn. Rader med lånade namn hålls utanför motorns radbank. Ingen spridning. |
| 05, 06 | Kandidat, ofryst | Byggs som kandidat märkt ofryst. Kandidatvärdena i 06 är konfigurerbara startvärden. |
| A-13 | Uppskjutet | Lagring bakom gränssnitt. Triggerna i 04 prövas mot SQLite i test. Room eller SQLDelight väljs senare. |
| G5-gränser | Öppen | Måste sättas före G5. |
| F-03, F-04 | Precisering respektive verifiering öppen | Ingen karta och ingen enhetsberoende kod i nästa etapp. |

<a id="allt-ska-horas"></a>
## 7. Ägarbeslut 2026-11-20 kl. 23.30 (Europe/Stockholm): allt ska höras

Ägarens beslut ordagrant: "I helvete allt ska höras lägg av med detta trams". Svaret gällde frågan om vad `PRIVATE` innebär, alltså att rader inte spelas upp via bil, Bluetooth eller okänd ljudutgång.

| Id | Utfall | Kort |
|---|---|---|
| R-12 (06) | REJECT genom ägarbeslut med accepterad risk | Uppspelningsspärren för `PRIVATE` är borttagen ur SafetyGate. Alla rader får spelas upp på alla ljudutgångar och i konvoj. **Accepterad risk: passagerare och konvojdeltagare kan höra privata namn och relationsinnehåll.** |
| `visibility` (04) | REVISE | Fältet och markeringen `PRIVATE` finns kvar som uppgift om känsligt innehåll, men styr inte uppspelning. |
| RR-11 (12), R-05 (11) | REVISE | Uppdaterade efter beslutet. |
| Gäller inte | – | F-11 (Grok Bots förslag med `approved: false` används fortfarande inte), D-8 (R-001–R-003 står kvar som öppen fråga), skrivskyddet för original, SafetyGates säkerhetsregler (manöverfönster, nödläge, omräkning, position, batteri, konvojkontakt, stress, upprepning, godkännande) och att repot är privat på GitHub. |

Genomfört i PR #5, se [`../verification/etapp1-protokoll.md`](../verification/etapp1-protokoll.md) avsnitt 8.

<a id="godkann-allt"></a>
## 8. Ägarbeslut 2026-11-20 kl. 23.36 (Europe/Stockholm): godkänn allt

Ägarens beslut ordagrant: "Godkänn allt är det något som inte passar går att radera senare". Det gäller alla 65 raderna i F-11 och besluten D-3, D-6, D-8 och D-12 i [etapp1-protokollet](../verification/etapp1-protokoll.md).

| Id | Utfall | Kort |
|---|---|---|
| F-11 | ACCEPT genom ägarbeslut | Alla 65 rader godkända (`approved: true`, `approvedBy: "Jimmy Bruce"`, `approvedAt: "2026-11-20"`). Ursprungsklassen (`SYNTHETIC_SATIRE`/`DERIVED_RULE`) och författaren "Grok Bot (förslag)" ligger kvar; ingen blir original. Rader kan strykas senare. |
| B-12, B-24 | REVISE | De 14 raderna med lånade namn är godkända som arbetsnamn (`workingName: true`). Byts före spridning. |
| B-22, B-23, B-25, B-26 | ACCEPT genom ägarbeslut | Variationer, BS-11–BS-16, navigationskommentarer och exempeldialoger godkända. De 12 raderna med riktningsord eller beskedsrisk har `footOnly: true` och spärras i fordonsläge av motorn och SafetyGate (säkerhetsregel, oförändrad). |
| D-3 | ACCEPT | Original får `approval = APPROVED` vid import. |
| D-6 | ACCEPT | Fordonsreglerna gäller från 10 km/h även till fots (kandidatvärde, fortfarande ofryst). |
| D-8 | ACCEPT | R-001–R-003 hålls utanför motorns radbank. |
| D-12 | ACCEPT | Citaten klassas som M1 (utvidgat: navigationskommentarer M8, med Rikard även M5; exempel M6). |
| Gäller inte | – | Exemplen ur sammanställningen (EX-01, EX-03, EX-05, EX-ILLOGICAL-01) och PAT-01–PAT-05 är inte Grok Bot-förslag och förblir `PENDING`; `EX-01-FORMULERING` förblir `BLOCKED` (F-12). Originaltexter och skrivskydd oförändrade. |

Genomfört i PR #5, se [`../verification/etapp1-protokoll.md`](../verification/etapp1-protokoll.md) avsnitt 9.

