# Etapp 5–7 – protokoll: skämt som bär last, Convoy-protokollet och Babel-Stoner med encyklopedin

| Uppgift | Värde |
|---|---|
| Datum | 2026-11-21 (Europe/Stockholm), 13.10–14.40. |
| Gren och commit | `etapp4/sanningskarna`, fortsätter efter `bdd1fdf`. Etapp 5: `61ec14d`. Etapp 6: `9157be0`. Etapp 7 och det här protokollet: samma incheckning som filen. Inget är pushat. |
| Motsvarar | Byggplanens steg 5, 6 och 7 och [09](../v0.1/09-genomforandeplan.md) etapp 5–7, grindarna **G6, G7 och G8**. |
| Ägarens uppdrag | "slutför enligt byggplan" (2026-11-21). |
| Utförare | Claude (Anthropic, modell Fable 5.2). **Ingen oberoende granskning (F-07) är gjord.** |
| Målenhet | **Inget i det här protokollet är provat på målenheten.** Plattan (SM-X900) tog inte emot adb-anslutningen från kl. 13.25, så den nya appen är varken installerad eller körd där. |
| Byggmiljö | Som etapp 4: Windows 11, JDK 21, Gradle 9.2.1, AGP 9.0.1, Kotlin 2.2.10. |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot och bara för den miljö som står i kolumnen. **Ingen grind blir `ACCEPT` genom det här protokollet.** Byggaren har skrivit både koden och protokollet. Allt nedan som rör skärmen är bara kompilerat och lintat.

## 1. Vad som byggdes

### Etapp 5: varje skämt ska bära last

| På ytan | Under (det som byggdes) | Var |
|---|---|---|
| Handduken | Avfärdskontrollens fem punkter visas i ruttförslaget med sina sanna värden: batteri (nivå och laddning), kartpaketets täckning, sparad hempunkt, dagsljus vid ankomst, aktuell position. "Okänd" när ett värde saknas. | `DepartureCheck`, `TowelCheck` i `JourneyPanels.kt` |
| Sannolikhet för snacks | Matställen inom vald omväg (5–30 min), med restid dit, omväg och öppet/stängt/okänt vid ankomst. Restiderna räknas av ruttmotorn på enheten: en matris från positionen till alla kandidater och målet, och en från alla kandidater till målet. Väljs ett stopp under en tur erbjuds det ursprungliga målet igen efteråt. | `SnackFinder`, `SnackDialog`, `ValhallaRouteProvider.travelSecondsTo` |
| Osannolikhetsdriften | Ett lottat mål som ruttmotorn verkligen når inom vald restid. Högst 40 kandidater ur offlinepaketets platser, restider ur restidsmatrisen, lottning bland dem som nås på halva till hela tiden. Fröet loggas. | `ImprobabilityDrive`, `ImprobabilityDialog` |
| Hemväg | Rutt till den sparade hempunkten. Dessutom **eget spår**: positionerna enheten faktiskt har haft, ritade som prickad linje, med längd och vägen tillbaka längs spåret. Spåret sparas i appens interna lagring och överlever omstart. | `TrackRecorder`, `TrackStore`, `HomeDialog` |
| Fel fråga | Vänstersvängar, högersvängar, rondeller och steg kvar, räknade ur ruttmotorns steg varje sekund. | `WrongQuestion`, `NavTick`, `GuidanceSnapshot` |
| Perspektivvyn | Omvägens (snacks) respektive den återstående restidens andel av universums ålder, uträknad på riktigt. | `Perspective` |
| Expeditionsprotokollet | Ett protokoll per tur. Sanningsrader kan bara uppstå ur sanningshändelser; Guidens repliker förs in som satir med arkivets rad-id. Sparas som textfil efter varje rad. | `ExpeditionProtocol`, `ProtocolKeeper`, `ProtocolDialog` |
| Redaktionens strykning | Fältanteckningen kortad till två av sina egna ord. Originalet ligger kvar. | `EditorsCut` (i etapp 7:s modul) |
| Nödkortet | Byggt i etapp 4. Konvojens senaste lägen saknas (etapp 6). | – |
| Rikard-störning | Byggt i etapp 4 (sanningsremsans positionskvalitet). | – |
| Osynlighetslagret | **Inte byggt.** Offlinepaketet har inga sådana platser (E5-D7). | – |

Reglagen (tidigare knappen "Drift") heter nu **Reglage**. Osannolikhetsdriften är det lottade målet, som i byggplanen.

### Etapp 6: Convoy för två, bara protokollets logik

Ny ren modul `:convoy:api` utan beroenden på resten av appen: session (128 bitars namn, 256 bitars nyckel, sluttid högst 24 h), inbjudan som QR-text (giltig 10 min), samtycke, verifieringskod, förseglade kuvert, avsändarens tidsgräns och återkallelse, mottagarens kontroller, ärlig ålder i fyra klasser och mötespunkt ur bådas restider. **Ingen transport, ingen server, ingen skärm och ingen nätverksbehörighet.** Appen beror inte på modulen än.

### Etapp 7: Babel-Stoner och encyklopedin

| Del | Innehåll |
|---|---|
| Spelet (`BabelGame`) | Fem motståndare (namnen ur ägarens PRD 5.3). Varje replik är en godkänd arkivrad som är knuten till greppet; spelaren väljer bland fyra godkända regler. Rätt grepp ger tillbaka frågan; poängen är antalet repliker. Deterministiskt. |
| Frågebokföringen (`QuestionLedger`) | Inklistrat samtal → varje fråga med radnummer, nästa replik från någon annan, gemensamma innehållsord, om frågan ställdes igen, och repliker utan gemensamma ord med de tre föregående. Samma regler för alla talare. Ren funktion: inget sparas. |
| Allvar före satir (`SeriousnessCheck`) | Vid tecken på hot eller våld i frågan börjar inget spel; appen hänvisar till 112. |
| Encyklopedin (`:encyclopedia`) | Egna fältanteckningar, bara tillägg, med tid och plats när enheten har en verklig position. Arkivets godkända lexikon visas skrivskyddat med id och klass. |
| Skärmen | Två nya paneler, **Babel** och **Encyklopedi**, som bara nås när ingen tur pågår. |

## 2. Kommandon

```
./gradlew --console=plain clean check assembleDebug
./gradlew :convoy:api:check :babel:check :encyclopedia:check :truth:core:test
```

## 3. Utfall per kriterium

Bevisen ligger i [`etapp5-7-testutdata/`](etapp5-7-testutdata/).

| Id | Kriterium | Bevis | Miljö | Status |
|---|---|---|---|---|
| E5-K01 | Allt bygger och alla tester är gröna | [`gradle-clean-check.txt`](etapp5-7-testutdata/gradle-clean-check.txt): `BUILD SUCCESSFUL`, 249 tester i 28 testsviter, 0 fel, 0 överhoppade (etapp 4: 196 i 23). [`junit-xml/`](etapp5-7-testutdata/junit-xml/). | dator | ACCEPT |
| E5-K02 | Modulgränserna hålls | `verifyModuleGraph`: 0 överträdelser i 15 moduler ([`module-graph.txt`](etapp5-7-testutdata/module-graph.txt)). `:convoy:api` har inga projektberoenden; `:engine` och `:babel` ser inte Convoy (07 T-11). | dator | ACCEPT |
| E5-K03 | Dioden är orörd | Arkitekturtesterna (31) är gröna. `TruthBoundaryTest` stoppade bygget när kärnan fick en ny publik läsare (`power`); testet härleder nu vad som är läsning ur de tre läsgränssnitten, så allt annat publikt i kärnan måste vara märkt `@TruthInput`. Etapp 5-panelerna läser bara `TruthDisplayReader` och `TruthStateReader`. | dator | ACCEPT som test |
| E5-K04 | De sanna värdena under skämten | `LoadBearingJokesTest` (11 tester): handduken, lottat mål, öppettider, snacks, protokollet, fel fråga, perspektivet. `TruthKernelTest` (22): bland annat antal svängar och batteriets laddning. | dator | ACCEPT som test |
| E5-K05 | Eget spår och vägen tillbaka | `TrackRecorderTest` (10 tester): minsta avstånd, osäkra positioner, hopp i positionen, längd, utglesning som behåller början, vägen tillbaka från närmaste spårpunkt. | dator | ACCEPT som test. På enheten med verklig GNSS: NOT_VERIFIED. |
| E5-K06 | Etapp 5 i appen | Kompilerat, lintat och byggt till APK. | dator | **NOT_VERIFIED på målenheten.** Okänt bland annat: hur lång tid restidsmatrisen tar för 25–40 mål, hur panelerna ser ut, om knappraden ryms. |
| E5-K07 | Inga nätverksbehörigheter | [`apk-kontroll.txt`](etapp5-7-testutdata/apk-kontroll.txt): fortfarande ingen `INTERNET`. APK 59 272 275 byte, SHA-256 `1d60ddc8…1508`. | dator (aapt2) | ACCEPT |
| E6-K01 | Sändningen upphör vid sluttiden, och den andra telefonen visar "senast sedd" med rätt ålder (G7) | `ConvoyProtocolTest`: A försöker sända var femte sekund i 80 minuter mot en sluttid efter 60. 708 kuvert förseglas före sluttiden, 241 försök vägras efter den. B visar "senast sedd 11.59" tio minuter senare och ingen prick efter en kvart. | dator, två parter i samma process | ACCEPT som test. **På två telefoner med appen i bakgrunden: NOT_VERIFIED.** |
| E6-K02 | Mellanhanden ser bara krypterad data av fast storlek (T-05, T-10) | Samma test: alla positionskuvert är 52 byte chiffertext, latituden står inte i klartext, ny nonce varje gång. | dator | ACCEPT som test |
| E6-K03 | Ändring, fel nyckel och återspelning upptäcks (T-02, T-06) | Sju sorters ändring av ett kuvert avvisas; fel nyckel avvisas; samma kuvert två gånger och ett äldre kuvert efter ett nyare avvisas. | dator | ACCEPT som test |
| E6-K04 | Återkallelse (07, 3.4) | "Sluta dela" ger ett sista kuvert, stoppar all sändning och raderar nyckeln; det som utger sig för att vara sänt efteråt avvisas. | dator | ACCEPT som test |
| E6-K05 | Ärlig ålder (T-07) | Fyra åldersklasser vid gränserna 30 s, 2 min och 15 min; åldern står alltid med; vid olika klockor visas den högre åldern; en position som var gammal vid ankomst visas aldrig som aktuell. | dator | ACCEPT som test |
| E6-K06 | Inbjudan, samtycke, verifieringskod (T-01) | Inbjudan går runt som text och gäller 10 min; den som går med kan välja tidigare men aldrig senare sluttid; koden är lika på båda telefonerna och annan för ett obehörigt id. | dator | ACCEPT som test |
| E6-K07 | Krypteringsdelen granskad av någon som inte byggt den (G7) | – | – | **NOT_VERIFIED** |
| E7-K01 | Spelet använder bara godkända regler och rader (G8) | `BabelGameTest` (7 tester): varje replik är en godkänd arkivrad som inte återger en verklig person; alternativen är godkända regler; deterministiskt; rätt grepp återställer frågan ordagrant. Testet skriver ut läget: **6 av 16 grepp är spelbara** (BS-03, 04, 05, 06, 07, 15). | dator | ACCEPT som test. Spelvärdet: se iakttagelserna. |
| E7-K02 | Frågebokföringen pekar varje fynd på ett radnummer och ställer inga diagnoser (G8) | `QuestionLedgerTest` (8 tester) på påhittade samtal: radnummer på varje fynd, samma regler när talarna byter plats, resultatklassen utskriven, inget ordförråd för motiv eller diagnoser. | dator | ACCEPT som test för klass ett. Klass två (språkmodell): inte byggd. |
| E7-K03 | Encyklopedin innehåller bara fältanteckningar (G8) | `FieldNotesTest` (4 tester): anteckningen sparas ordagrant och kan inte ändras; strykningen består av anteckningens egna ord; lexikonet visar bara godkända poster. | dator | ACCEPT som test |
| E7-K04 | Inget lämnar telefonen (G8) | `:babel` och `:encyclopedia` är rena moduler utan nätverksbibliotek (modulgrafen), och appen saknar `INTERNET` (E5-K07). | dator | ACCEPT. Nätverkstest på enheten: NOT_VERIFIED. |
| E7-K05 | Etapp 7 i appen | Kompilerat, lintat och byggt till APK. | dator | **NOT_VERIFIED på målenheten.** |

## 4. Avvikelser

| Id | Avvikelse | Skäl | Kräver |
|---|---|---|---|
| E5-D1 | Det egna spåret hör inte till en expedition (04 har `home_track_id` per expedition). Det börjar vid första positionen och fortsätter tills användaren väljer "Nytt spår" eller det har gått 24 timmar utan position; då läggs det gamla undan på enheten. | Vägen tillbaka behövs också när ingen rutt är startad. | Jimmy (24 h är ett kandidatvärde) |
| E5-D2 | Expeditionsprotokollet sparas som textfil per tur i appens externa mapp `protokoll/`, inte i arkivdatabasens `expedition_log`. | Arkivdatabasen på Android finns inte än (A-13). | Granskare |
| E5-D3 | Visningskontraktet har vuxit: `TruthDisplayReader.power` (batteri och laddning) och tre räknare i `GuidanceSnapshot`. | Avfärdskontrollen och Fel fråga behöver värdena. Bara läsning. | Granskare |
| E5-D4 | Ett matstopp ersätter den pågående rutten. Det ursprungliga målet erbjuds igen när stoppet är kvitterat. | Rutt med mellanmål är inte provad i kedjan valhalla-mobile → Ferrostar. | G6 |
| E5-D5 | Fel fråga saknar höjdmeter (byggplanen nämner dem). Panelen säger "okänt". | Offlinepaketet har ingen höjddata. | Verkstaden |
| E5-D6 | En avbruten tur får raden "Protokollet avslutades. Ingen framkomst registrerades." utan att någon händelse finns i 05. | 05 har ingen händelse för avbruten rutt. Raden är fast text och tar ingenting utifrån. | Granskare |
| E5-D7 | **Osynlighetslagret är inte byggt, och snacks säger alltid "öppettider okända".** | Offlinepaketets platser är lästa ur kartfilen, som varken bär öppettider eller pumpstationer, reningsverk och ställverk (0 platser av slaget INFRASTRUCTURE, 0 med öppettider). Det kräver ett verkstadssteg mot OSM-utdraget, som inte finns på den här datorn. | Verkstaden |
| E5-D8 | Under en provtur förs ett simulerat spår, märkt som simulerat. Det sparas aldrig och försvinner när provturen kvitteras. | Så att spåret går att prova utan att röra sig. | – |
| E5-D9 | Spåret, protokollen och fältanteckningarna är verkliga uppgifter om var användaren har varit. De ligger okrypterade i appens lagring, säkerhetskopieras inte och lämnar aldrig enheten. Protokollen ligger i den externa appmappen och kan hämtas med adb. | Samma hantering som loggen i etapp 4. | Jimmy |
| E6-D1 | Kuverten förseglas med **AES-256-GCM ur plattformens JCA**, inte XChaCha20-Poly1305 i libsodium (kandidaten i 07). | Inget nytt inbyggt bibliotek läggs i appen utan ägarbeslut. Noncen är 96 bitar och slumpas per kuvert; en session sänder några tiotusental kuvert. Primitiven ligger bakom gränssnittet `Aead` och kan bytas. | **Beslutat** av ägaren 2026-11-21 kl. 14.19: "Kör på AES-256-GCM" ([beslutsloggen](../v0.1/10-beslutslogg.md), avsnitt 11). Granskningen i G7 står kvar. |
| E6-D2 | Bara `:convoy:api` är byggd. Saknas: `:convoy:wss`, servern, serverns kontroller (engångsinlösen, åtkomsttoken, radering vid sluttiden), samtyckesdialogen, QR-kod på skärmen och skanning, konvojhändelserna i kärnan (`ConvoyGap`, `ConvoyContactLost`), konvojens lägen på nödkortet, skärmlåskravet (T-08) och notisen under delning (T-09). | Var servern ska köras var ett öppet ägarbeslut (F-15), och transporten ger appen nätverksbehörighet. | **Beslutat** 2026-11-21 kl. 14.23: nätverksbehörighet ja. **Öppet:** var servern körs (F-15); en tidigare lydelse om datorn `mimer` över Tailscale var byggarens feltolkning och rättades kl. 17.40 ([beslutsloggen](../v0.1/10-beslutslogg.md), avsnitt 12). Inte byggt än. |
| E6-D3 | Löpnumret är det större av föregående plus ett och klockan i millisekunder. | Ökar strikt även efter omstart utan att något behöver sparas. Går klockan bakåt över en omstart kastas kuverten som återspelning: ingen position visas. | Granskare |
| E7-D1 | Spelet använder bara grepp som har en godkänd arkivrad knuten till sig. Tio grepp går inte att spela (BS-01, 02, 08–14, 16). Fördelningen av grepp på de fem motståndarna är ett kandidatvärde. | Byggaren skriver inga repliker. De godkända exempelreplikerna EX-F-01–07 är inte knutna till något grepp. | **Jimmy** (godkända exempel per grepp, märkta `rule:<id>`) |
| E7-D2 | Fältanteckningarna sparas i en textfil i appens interna lagring. De kan inte ändras eller tas bort i appen. | Arkivdatabasen finns inte än. "Originalet ligger kvar." | Jimmy |
| E7-D3 | Texten vid tecken på hot eller våld nämner 112 och polisens 114 14. Ordlistan som utlöser den är bred och enkel. | 02 B-7 säger "112 och stöd" utan att ange vilket stöd. | **Jimmy** (vilka stödlinjer som ska stå där) |
| E7-D4 | Analysläget har bara resultatklass ett. Om en fråga fick svar bedöms inte. `:llm:api` är inte byggd. | Ingen språkmodell finns på enheten (A-19). En mening räknas som fråga om den slutar med frågetecken; en rad som börjar med "Ord: " räknas som en talare. | G8 |
| E7-D5 | Lexikonet visar alla godkända poster, även de med synlighet PRIVATE, på ägarens egen enhet. | Synligheten styr uppläsning och delning, inte ägarens egen skärm. | Jimmy |

## 5. Iakttagelser

- **Fyra av fem motståndare har ett eller två spelbara grepp.** Mot dem är spelet avgjort på förhand. Mekaniken är klar; innehållet väntar på godkända rader (E7-D1).
- **Rättningen av röstfelet (E4-K21) ligger i samma APK** och är inte heller provad på målenheten.
- **Repot är publikt** (R-08). Arkivets privata citat och lexikon ligger alltså öppet på GitHub redan. Inget i den här etappen ändrar det, men spår, protokoll och anteckningar hålls utanför repot.
- **Restidsmatrisens tid på plattan är okänd.** Lottat mål räknar upp till 40 mål och snacks upp till 2 × 26. Loggen skriver ut tiden (`DRIFT … ms`, `SNACKS … ms`) vid första provet.
- **Kartans långtryck läste av gammalt tillstånd** (rättat): hanteraren skapades vid appstart och såg därför aldrig att en tur pågick eller vilket paket som gällde.
- Skärmens knapprad har vuxit till upp till tio knappar och bryts nu över flera rader. Don't Panic-knappen ligger ovanför raden.

## 6. Grindläge

| Grind | Läge | Status |
|---|---|---|
| G6 (etapp 5) | Logiken testad på JVM och inkopplad i appen. Inte körd på målenheten. Osynlighetslagret och öppettiderna saknar data. | NOT_VERIFIED |
| G7 (etapp 6) | Protokollets logik testad på JVM, inklusive grindens scenario med två parter i samma process. Transport, server, skärm, två telefoner och granskning av krypteringsdelen saknas. | NOT_VERIFIED |
| G8 (etapp 7) | Spelet, frågebokföringen (klass ett) och encyklopedin testade på JVM och inkopplade i appen. Inte körda på målenheten. Ingen språkmodell. | NOT_VERIFIED |

## 7. Vad ägaren gör

1. **Släpp in adb igen** (ny port under Trådlös felsökning), eller installera själv: `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Därefter kan byggaren prova etapp 5–7 och röstfelets rättning på plattan.
2. **Prova själv:** Vart? → välj ort → läs handduken i förslaget → Provtur. Under turen: Fel fråga, Snacks, Protokoll, Hem. Utan tur: Lotta mål, Babel, Encyklopedi.
3. **Convoy:** beslutat 2026-11-21: AES-256-GCM (E6-D1) och nätverksbehörighet för Convoy. Kvar att bestämma: var servern körs och hur telefonerna når den (F-15, öppet; Tailscale är inte valt), och om reläet ska ligga i det här repot.
4. **Babel-Stoner:** godkänn exempelrader per grepp så att fler än sex grepp går att spela, och se över vilka grepp som hör till vilken motståndare.
5. **Stödlinjer:** bestäm vad som ska stå vid tecken på hot eller våld (E7-D3).
6. **Verkstaden:** kör ett steg mot OSM-utdraget som lägger öppettider och osynlig infrastruktur i `platser.geojson` (E5-D7).
7. Besluten från etapp 4 står kvar: repots synlighet (R-08), ändringen i SafetyGate (E4-D5), röstvalet och de tio verkliga turerna.

## 8. Inte verifierat

Allt på målenheten. Allt som kräver en människa: om skämten är roliga, om panelerna går att använda, om avfärdskontrollen hjälper. Verklig GNSS och därmed spåret. Restidsmatrisens tid. Convoy över ett nät mellan två telefoner. Språkmodell för frågebokföringen. Oberoende granskning (F-07) av allt, och särskilt av krypteringsdelen i `:convoy:api`.
