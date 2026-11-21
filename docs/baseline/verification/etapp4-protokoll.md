# Etapp 4 – protokoll: sanningskärnan, appen och rösten på målenheten

| Uppgift | Värde |
|---|---|
| Datum | 2026-11-21 (Europe/Stockholm). Bygge och prov på målenheten 11.40–12.55. |
| Gren och commit | `etapp4/sanningskarna`, utgår från `etapp3/svenskt-tal` (`47df98c`, PR #7). Koden som provades på målenheten är `d6579c8`; testutdata nedan är från en ren byggnad på samma commit. |
| Motsvarar | Byggplanens **etapp 4** ("Sanningslagret, ett område, ensam") och [09](../v0.1/09-genomforandeplan.md) etapp 4, grind **G5**. Dessutom nya bevis för **G2, G3 och G4** på målenheten. Projektets löpande numrering: etapp 4. |
| Ägarens uppdrag | "slutför enligt byggplan" (2026-11-21). |
| Utförare | Claude (Anthropic, modell Fable 5.2). **Ingen oberoende granskning (F-07) är gjord.** |
| Målenhet | Samsung Galaxy Tab S8 Ultra SM-X900, One UI 8.0, Android 16 (API 36), ansluten med trådlös adb. Enheten låg på nät under proven (annars går adb inte); appen saknar INTERNET-behörighet. |
| Byggmiljö | Windows 11, Android Studios JDK 21.0.10, Gradle 9.2.1, Android Gradle Plugin 9.0.1, Kotlin 2.2.10, compileSdk 36, minSdk 26. |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot och bara för den miljö som står i kolumnen. **Ingen grind blir `ACCEPT` genom det här protokollet.** Grindbeslut fattas av ägaren efter oberoende granskning. Byggaren har skrivit både koden och protokollet.

## 1. Vad som byggdes

| Modul | Typ | Innehåll |
|---|---|---|
| `:truth:core` | Ren Kotlin | `TruthKernel` gör navigeringens tillstånd till de fjorton händelserna i 05 och till ögonblicksbilderna för SafetyGate och skärmen. Ingen klocka, ingen I/O. All inmatning är märkt `@TruthInput`. Flyttat från prototypen med oförändrad logik: `SvenskTalTvatt`, `UpprepningsSparr`, `PaketInstallerare`. Nytt: `InstructionAnnouncer` (kanal B), `ManeuverText` ("Vänster om 300 m"), nödkortet med WGS84 och SWEREF 99 TM, solens läge, platsindex. Etapp 5-logik: avfärdskontroll, osannolikhetsdrift, snacks, expeditionsprotokoll, perspektivvy. |
| `:truth:device` | Android | `OfflineNavigator`: offlinepaketet, valhalla-mobile, Ferrostar (`CustomRouteProvider`), GNSS via `LocationManager` med bara `GPS_PROVIDER`, batteri, provtur med simulerad position. Matar kärnan. |
| `:voice` | Android | `GuideVoice`: en talmotor, två kanaler. Besked (kanal B) klipper alltid svansen (kanal S). Två olika svenska röster när enheten har flera. |
| `:conductor` | Ren Kotlin | `TailConductor`: händelse → Illogical Engine → SafetyGate → scenen. Läser bara `TruthStateReader`. |
| `:app` | Android (Compose) | Kompositionsroten och skärmen: sanningsremsan, kartan (MapLibre, PMTiles, tre stilfiler), svansytan, målsökning, provtur, nödkortet, reglagen. |
| `tools/verkstad/08-platser.py` | Python | Bygger `platser.geojson` ur paketets egen `karta.pmtiles` och packar ett nytt paket med manifest. |
| `tools/verkstad/09-kartstilar.py` | Python | Bygger de tre kartstilarna ur en lagerdefinition. |
| Rotbygget | Gradle | Ett bygge för rena moduler och Android. `verifyModuleEdges` kör per modul och har fått Google-spärren från `verifieraIngenGoogle`. |

Etapp 2-prototypen (`prototyp/offline-kedja`) ligger kvar orörd som historik, utom en rättning av ett test som föll på Windows.

## 2. Offlinepaketet

`f03-sex-kommuner-20261121-0619.p1`: samma `karta.pmtiles` (77 023 128 byte, `cac97988…7253`) och `valhalla_tiles.tar` (19 947 520 byte, `5312a8d8…da6c`) som i etapp 2, plus `platser.geojson` (984 806 byte, SHA-256 `2ed2bacb…0213`) med 5 247 platser: 3 274 orter, 283 matställen, 90 butiker, 48 drivmedel, 202 rastplatser, 10 toaletter och 1 340 övriga. Kart- och ruttfilen hämtades byte för byte ur prototypappen på målenheten och stämmer mot manifestets kontrollsummor. Platserna är lästa ur kartfilens lager `place` och `poi` på zoom 14, alltså ur samma OSM-ögonblick. **Öppettider finns inte i kartfilen.**

## 3. Kommandon

```
./gradlew --console=plain clean check assembleDebug        # alla tester, modulgraf, lint och APK
python tools/verkstad/08-platser.py <paketkatalog>         # platser.geojson och nytt paket
python tools/verkstad/09-kartstilar.py                     # de tre kartstilarna
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push <paket>/* /sdcard/Android/data/se.pundaren.guide/files/inkommande/   # efter första start
adb pull /sdcard/Android/data/se.pundaren.guide/files/guide-logg.txt
```

På den här datorn krävs `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` och `ANDROID_HOME`.

## 4. Utfall per kriterium

Bevisen ligger i [`etapp4-testutdata/`](etapp4-testutdata/). Målenhetens logg är [`malenhet-guide-logg-2026-11-21.txt`](etapp4-testutdata/malenhet-guide-logg-2026-11-21.txt) (647 rader, tider i svensk tid). Den sista provturen, 13.02–13.09 med taltid räknad per tecken, ligger i [`malenhet-guide-logg-kalibrerad-2026-11-21.txt`](etapp4-testutdata/malenhet-guide-logg-kalibrerad-2026-11-21.txt) (161 rader).

| Id | Kriterium | Bevis | Miljö | Status |
|---|---|---|---|---|
| E4-K01 | Allt bygger och alla tester är gröna | [`gradle-clean-check.txt`](etapp4-testutdata/gradle-clean-check.txt): `BUILD SUCCESSFUL`, 196 tester i 23 testsviter, 0 fel, 0 överhoppade. [`junit-xml/`](etapp4-testutdata/junit-xml/). | dator | ACCEPT |
| E4-K02 | Modulgränserna hålls (G-6) | `verifyModuleGraph`: 0 överträdelser i 13 moduler ([`module-graph.txt`](etapp4-testutdata/module-graph.txt)). Under arbetet stoppade kontrollen bygget en gång på riktigt (Androids egen testkant), vilket visar att den körs före bygget. | dator | ACCEPT |
| E4-K03 | G-1, G-2, G-5 för de nya modulerna | `TruthBoundaryTest` (8 tester): all inmatning i kärnan är märkt `@TruthInput`; bara `:truth:device` och testkod använder märkningen; inmatningen tar inga tramstyper och text bara som målets namn; sanningslagret importerar inget ur tramslagret; rösten känner varken motorn, SafetyGate eller kärnan. | dator | ACCEPT. G-5 bokstavligt: se E4-D4. |
| E4-K04 | Dioden mot den riktiga kärnan (K-10, tidigare bara mot stubbe) | `KernelDiodeTest`: samma tur genom `TruthKernel` med och utan tramslagret. 27 händelser och 904 ögonblicksbilder är identiska; 11 svansar presenterades med tramslagret inkopplat. | dator | ACCEPT som test |
| E4-K05 | Dirigenten släpper aldrig fram trams i förbjudna lägen | `TailConductorTest` (13 tester), bland dem 2 000 slumpade händelser med riktig motor och riktigt arkiv: 271 svansar presenterade, 0 i EMERGENCY, REROUTING eller MANEUVER_WINDOW, 0 riktningsord i fordonsläge, ingen rad upprepad inom tio. | dator | ACCEPT |
| E4-K06 | Kärnans händelser och ögonblicksbilder | `TruthKernelTest` (20 tester): alla händelsetyper utom konvojens, omräkningsläget till första beskedet spelats (R-05), positionskvalitet med ärlig ålder, stillastående, batteri, nödläge, dygnsfas, determinism. | dator | ACCEPT |
| E4-K07 | Nödkortets koordinater | `GeoTest`: SWEREF 99 TM stämmer med fyra av Lantmäteriets kontrollpunkter på millimetern; grader och decimalminuter; soltider inom fyra minuter från almanackan. `EmergencyCardTest`: kortet säger alltid att det inte ersätter 112 och säger rakt ut när position saknas. | dator | ACCEPT |
| E4-K08 | Paketet installeras atomärt på målenheten | Logg 12.03.11: `PAKET … installerat atomärt på 448 ms` (97 MB, tre filer), därefter `inkommande/ tömd`. `PaketInstallerareTest` (8 tester). | målenhet | ACCEPT. Avbrott vid strömbortfall: NOT_VERIFIED. |
| E4-K09 | Ruttning på enheten | valhalla-mobile startar på 1,4–1,9 s (åtta starter). Rutt Orsa–Grönklitt: 12,42 km till fots och 15,73 km som passagerare, samma värden som verkstadens ruttprov. Ruttid 8–13 ms, första rutten efter start cirka 100 ms. | målenhet | ACCEPT |
| E4-K10 | Navigering till framkomst | Provtur ×8, 12.12.28–12.15.36: 17 steg, 25 besked utlösta, 24 `ManeuverUpcoming`, 17 `ManeuverCompleted`, `Arrived` (15 708 m) och `FRAMME (Complete)`. Simulerad position. | målenhet | ACCEPT med simulerad position. Verklig position: NOT_VERIFIED. |
| E4-K11 | Avvikelse, minst tre försök, och omräkning på enheten | Provtur 12.25.10–12.26.46: tre av tre försök upptäckta (250, 250 och 249 m), varje gång `OMRÄKNING pågår` och ny rutt från valhalla-mobile på 8–13 ms. 3 `OffRoute`, 5 `Rerouted`. Inga svansar under omräkningen. | målenhet | ACCEPT |
| E4-K12 | Tvätten av talbeskeden på enheten | 18 av 25 besked ändrades, t.ex. `Fortsätt på W 1000 i 2 kilometer.` → `Fortsätt på väg 1000 i 2 kilometer.` (`TAL RÅTT` följt av `TAL #n`). Sanningsremsan visar samma tvättade text. | målenhet | ACCEPT |
| E4-K13 | Kartan ritas ur den lokala PMTiles-filen med tre stilar | Logg: `KARTA stil Praktisk/Poetisk/Osannolik laddad, källa pmtiles://file://…/karta.pmtiles` (12.29.08–12.29.20). Skärmbilder granskade av byggaren: vägar, vatten, ortnamn och rutt syns; sanningsremsan ligger ovanför kartan och täcks inte. Stilarna har samma lager, inga nätadresser och ingen sanningsfärg (`TruthBoundaryTest`). | målenhet | ACCEPT som logg. Skärmbilderna är inte incheckade (de visar enhetens statusfält). |
| E4-K14 | Inga nätverksbehörigheter | [`apk-kontroll.txt`](etapp4-testutdata/apk-kontroll.txt): `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_NETWORK_STATE`. Ingen `INTERNET`. APK:n ur den rena byggnaden: 59 125 428 byte, SHA-256 `8f3a5156…239c`. APK:n som provades på målenheten byggdes ur samma källkod strax före incheckningen: 59 255 000 byte, SHA-256 `27255984…38f2` (felsökningsbyggen blir inte byte-identiska). | dator (aapt2) | ACCEPT. Mätning av nätverkstrafik på enheten: NOT_VERIFIED. |
| E4-K15 | Rösten läser upp på svenska, på enheten | Logg 12.37.23: `TAL REDO: besked sv-se-x-afp-local, Guiden sv-se-x-lfs-local, motor com.google.android.tts, utan nät`. 21 besked började läsas 44–164 ms efter begäran (median 64 ms; första efter start 481 ms) och tog 2,5–6,4 s. | målenhet | ACCEPT som logg. **Ingen har lyssnat.** Begriplighet (20 testmeningar): NOT_VERIFIED. |
| E4-K16 | Beskedet avbryter en pågående svans inom 100 ms (R-01, SG-03) | Två mätningar av talmotorns kvitto på att svansen tystnat. 12.53.10: `SVANS TYST: 50 ms efter beskedets begäran`, beskedet började efter 110 ms. 13.03.06: `SVANS TYST: 130 ms`, beskedet började efter 553 ms (första uppläsningen efter appstart; även 481 ms kl. 12.37). Övriga 16 besked på den senare provturen började 43–106 ms efter begäran. | målenhet | **REVISE.** En av två mätningar ligger över kandidatvärdet 100 ms, och första beskedet efter start dröjer cirka en halv sekund. Fler mätningar och lyssning krävs. Se också E4-K21. |
| E4-K17 | Sanningen först, tramset efteråt, på enheten | Provtur i verklig fart (45 km/h), 12.18.29 och 12.38.02: en svans som just börjat avbröts när första beskedet kom (`SVANS AVBRUTEN (MANEUVER)`); svansar väntade under manöverfönstren (`SVANS VÄNTAR`) och ströks när tiden inte räckte (`EXPIRED`); 13 svansar presenterades under dagen, alla på raksträckor eller i vila. | målenhet (simulerad position) | ACCEPT som logg. SG-24 (tio verkliga turer med ljudinspelning): NOT_VERIFIED. |
| E4-K18 | Nödläget | Långt tryck på Don't Panic: `EmergencyActivated(source=USER)` 12.29.23, nödkortet visades i sanningsfärgen med "Ring 112 vid nödläge. Det här kortet ersätter inte 112.", `EmergencyCleared` 12.30.07. | målenhet | ACCEPT som logg och skärmbild |
| E4-K19 | Positionskvalitet visas ärligt | Efter en avslutad provtur: `PositionQualityChanged … DEGRADED` efter 6–7 s utan position och `LOST` efter 20 s; remsan visade "Ingen aktuell position: senaste ±5 m, 1 min gammal". | målenhet | ACCEPT |
| E4-K20 | GNSS på målenheten (F-04) | Enheten har GNSS-hårdvara (`android.hardware.location.gps`, Qualcomm). Appen lyssnar på `GPS_PROVIDER`. Ingen position togs emot under proven; plattan stod inomhus. | målenhet | **NOT_VERIFIED** |
| E4-K21 | En svans kan aldrig stoppa eller fördröja ett besked (R-01) | **Fel funnet i loggen 13.03.06** ([`malenhet-guide-logg-kalibrerad-2026-11-21.txt`](etapp4-testutdata/malenhet-guide-logg-kalibrerad-2026-11-21.txt), rad 10–20): när beskedet klippte svansen C-009 kördes svansens slutanrop inifrån `speakInstruction`, innan beskedet hade märkts som pågående. Dirigenten lämnade då in nästa svans (V-020) till talmotorn i samma ögonblick. Beskedet tömde kön direkt efteråt, så V-020 hördes aldrig, och dirigenten avbröt den efter 50 ms. Men svansens bokföring stod kvar, och dess tidsgräns anropar `stop()` på talmotorn, vilket hade klippt beskedet om dirigenten inte hunnit före. **Rättat i koden efter provet:** beskedet märks som pågående först av allt, `speakTail` vägrar medan ett besked finns, svansens tidsgräns stoppar aldrig motorn under ett besked, och slutanropet postas i stället för att köras inifrån. | målenhet (felet), dator (rättningen) | **REVISE.** Rättningen är byggd men varken installerad eller provad på målenheten: plattan tog inte emot adb-anslutningen kl. 13.25. |

## 5. Rösten på målenheten (G4)

Det här är etappens viktigaste fynd och gäller också PR #7.

1. **Samsungs talmotor är en privat motor på målenheten.** När appen ber om `com.samsung.SMT` kontrollerar Samsungs motor (3.5.02.46) appens paketnamn mot en egen lista och avvisar det: `[check_allowed_package] se.pundaren.guide is not allowed. [-4]` och `is not allowed to bind to private engine`. Det gäller också när ägaren har valt Samsungs motor som önskad motor i systemets inställningar. Bevis: [`malenhet-logcat-talmotor-2026-11-21.txt`](etapp4-testutdata/malenhet-logcat-talmotor-2026-11-21.txt).
2. **Android byter då tyst till Googles motor.** Ramverket väljer `getHighestRankedPublicEngineName = com.google.android.tts` och rapporterar en lyckad anslutning. Koden i PR #7 kontrollerar bara att Samsungs motor är *installerad* och hade därför läst upp med Googles röst och loggat `TAL REDO … offline`. Det är samma fel som den automatiska granskningen pekade på i PR #7. På målenheten inträffar det varje gång.
3. **Ägarbeslut 2026-11-21:** förbudet gäller Googles API:er, inte Google i övrigt; enhetens talmotor får användas ([10](../v0.1/10-beslutslogg.md), avsnitt 10). Appen ber därför om systemets motor, loggar vilken motor och vilka röster den fick och väljer bara röster som läses upp på enheten (`…-local`).
4. **Två röster.** Enheten har fem lokala svenska röster. Beskeden läses av en och Guiden av en annan, så att det hörs vem som talar (RR-12). Rösterna kan bytas och provlyssnas under Drift.

G4 som grind: talmotorn är vald genom ägarbeslut, uppläsning och avbrott är visade i logg. Lyssningstestet med 20 testmeningar, uppläsning i flygplansläge och avbrott av andra appar är inte gjorda. **G4 förblir `NOT_VERIFIED`.**

## 6. Avvikelser

| Id | Avvikelse | Skäl | Kräver |
|---|---|---|---|
| E4-D1 | `:truth:core` är ren Kotlin och Android-adaptrarna ligger i en ny modul `:truth:device` (03 beskriver `:truth:core` som Android-bibliotek) | Logiken kan testas på JVM och omfattas av samma arkitekturtester som övriga rena moduler. | Granskare |
| E4-D2 | Ny ren modul `:conductor` för sammankopplingen händelse → motor → SafetyGate (03 lägger den i `:app`) | Samma skäl. `:app` blir tunn. | Granskare |
| E4-D3 | G-2 ("allt muterande är `internal`") upprätthålls med märkningen `@TruthInput` (kräver uttryckligt medgivande i koden) och ett test, eftersom `internal` inte når över modulgränsen mellan `:truth:core` och `:truth:device` | Följd av E4-D1. | Granskare |
| E4-D4 | Kärnans inmatning tar emot en textsträng: målets namn (`routeStarted`). G-5 säger bokstavligt att ingen `String` tas emot. | Ett mål har ett namn, och det visas i remsan ("Framme: Grönklitt"). Namnet kommer ur platsindexet eller är "Punkt på kartan". `:app` kan i teorin skicka annan text (R-07). | Granskare |
| E4-D5 | `SafetyGate.checkOngoing` avbryter inte längre en pågående svans bara för att livslängden (R-14) går ut | På målenheten klipptes en svans efter 6,4 av cirka 8 sekunder. 06 kräver `Suppress(EXPIRED)` för nya svansar; avbrottet av pågående var ett tillägg i etapp 1. Säkerhetsavbrotten (manöver, nödläge, omräkning, prioriterat läge, förlorad position) är oförändrade och testade. | **Jimmy** (ändring i SafetyGate) |
| E4-D6 | Rösten använder systemets talmotor i stället för uttryckligen Samsungs | Avsnitt 5. Ägarbeslut. | Beslutat |
| E4-D7 | Typsnitten Space Grotesk och Manrope (F-02) följer inte med; systemets typsnitt används | Licensfilerna finns inte i repot (PR1-20), och inget hämtades från nätet. | Jimmy |
| E4-D8 | Ingen förgrundstjänst: navigeringen kräver att appen ligger framme med skärmen tänd (skärmen hålls tänd under en rutt) | Byggplanen: telefonen i fäste, röst först. Navigering med släckt skärm är inte byggd. | G5 |
| E4-D9 | Platserna saknar öppettider, och osynlighetslagret (pumpstationer, reningsverk, ställverk) saknar data | Kartfilen bär inte de taggarna. Kräver ett verkstadssteg mot rå OSM-data. | Etapp 5 |
| E4-D10 | Lint-kontrollerna `NotificationPermission`, `ChromeOsAbiSupport` och `UseKtx` är avstängda i `:app` | Den första gäller Ferrostars förgrundstjänst, som aldrig startas. Motiveringarna står i `app/build.gradle.kts`. | Granskare |
| E4-D11 | Rotbygget använder Kotlin 2.2.10 (den version Android Gradle Plugin 9.0.1 själv använder) och kräver ingen JDK 17-verktygskedja | Etapp 2-protokollets "Kotlin 2.3.20" är versionen på standardbiblioteket som beroendena drar in, inte kompilatorn. | – |
| E4-D12 | Provtur (simulerad position) finns i appen som en uttalad funktion | Behövs för att pröva appen utan att röra sig. Remsan visar "PROVTUR (simulerad position)", och den simulerade positionen glöms när turen kvitteras. | – |
| E4-D13 | Appen säkerhetskopieras inte och följer inte med vid enhetsbyte (`data_extraction_rules.xml`) | Privat app; hemposition och loggar stannar på enheten. | – |
| E4-D14 | Uppskattad taltid räknas per tecken (800 ms + 65 ms per tecken) i stället för per ord (D-11) | Mätt på målenheten: 110 tecken tog 6,3 s och 97 tecken 5,9 s. Med den gamla uppskattningen klipptes två svansar av sin egen tidsgräns (12.43.13 och 12.44.07 i loggen). Provat på målenheten 13.03–13.09 (provtur i 45 km/h): fem av fem svansar lästes färdigt (`SVANS KLAR`), ingen klipptes av tidsgränsen. Marginalen är liten: uppläsningarna tog 9 685 av 10 485 ms, 5 905 av 6 260, 7 112 av 7 560, 6 870 av 8 665 och 4 580 av 5 350 ms, alltså 79–94 % av uppskattningen. En långsammare röst kan fortfarande klippas. | Granskare |

## 7. Iakttagelser

- **Repot är publikt på GitHub.** Baslinjen utgår från att det är privat. Se [11](../v0.1/11-tekniska-antaganden.md), R-08. Ägarbeslut krävs.
- **Guiden är nästan tyst i fordon.** Över 50 km/h släpper SafetyGate bara fram svansar på högst 1,5 s (R-10), och så korta rader finns inte i arkivet. I tätort ligger manövrerna så tätt att de flesta svansar hinner gå ut (`EXPIRED`) under de tysta fönstren (15 s före manöver). På provturen Orsa–Grönklitt i 45 km/h presenterades två till fyra svansar på fem minuter. Värdena är kandidatvärden (06) och bör prövas av ägaren i G5.
- **Efter en avvikelse räknas rutten ofta om två gånger.** När den simulerade positionen hoppar tillbaka till den gamla rutten avviker den från den nya. Det förklarar den "extra omräkningen" i etapp 2-protokollet.
- **Den falska avvikelsen vid start** (etapp 2-protokollet §6) är borta: provturen sätter startpositionen innan navigeringen börjar.
- **En mapp som skapas med `adb shell mkdir` kan inte läsas av appen** (annan ägare). Appen skapar därför `inkommande/` själv vid start; paketet läggs dit efteråt.
- **Under 15 % batteri tystnar Guiden även när enheten laddar** (R-08 i 06 ser bara nivån). Sett på målenheten vid 12–14 %.
- **Första svansen efter start kom innan rutten börjat** och avbröts direkt av första beskedet. Det är rätt beteende men låter hackigt; kan dämpas genom att inte föreslå svansar de första sekunderna efter `RouteStarted`. Samma ögonblick avslöjade felet i E4-K21.
- **Guiden hinner sällan.** På den sista provturen (6 min 46 s, 17 besked) noterades väntan på tid (`NO_TIME`) 30 gånger och väntan på manöver 6 gånger, 5 svansar ströks som för gamla och 5 lästes upp i sin helhet.
- Enheten stod i stående läge under proven. Layouten fungerar, men F-04 säger liggande surfplatta först.
- Byggarens prov på målenheten gjordes med adb-styrda tryck och textinmatning. Ingen människa har ännu använt appen.

## 8. Grindläge

| Grind | Läge efter etappen | Status |
|---|---|---|
| G1 | Oförändrat. Motorn och SafetyGate har nu också körts mot den riktiga kärnan (E4-K04, E4-K05). | NOT_VERIFIED (ingen oberoende granskning) |
| G2 | Kedjan valhalla-mobile → Ferrostar är visad på målenheten med logg: rutt, navigering till framkomst, tre avvikelser med omräkning, utlösta och upplästa talinstruktioner (E4-K09–K11, K15). Dessutom prototypens logg från ägarens körningar 06.17–09.12 ([`../verification/etapp2-testutdata/malenhet-etapp2-logg-2026-11-21.txt`](etapp2-testutdata/malenhet-etapp2-logg-2026-11-21.txt)). Kvar: körning i flygplansläge med logg (ägaren har kört prototypen så), oberoende granskning. | NOT_VERIFIED |
| G3 | Kartan ur `filesDir` med tre stilfiler och sanningsremsan otäckt är visad på målenheten (E4-K13). Kvar: mätning av nätverkstrafik, ägarens bedömning av F-02 på skärmen, oberoende granskning. | NOT_VERIFIED |
| G4 | Avsnitt 5. | NOT_VERIFIED |
| G5 | Appen finns och kör på målenheten med simulerad position. Kvar: tio verkliga turer till fots och som passagerare i flygplansläge med ljudinspelning (SG-24), GNSS (E4-K20), mätning av kallstart, minne och batteri, avbrottstest av paketinstallationen, gränsvärden, oberoende granskning av dioden. | NOT_VERIFIED |

## 9. Vad ägaren gör

1. **Bestäm om repot ska vara privat** (R-08).
2. **Lyssna.** Starta appen, öppna Drift och tryck Prov vid Besked och vid Guiden. Byt röst tills beskedsrösten och Guidens röst går att skilja åt. Säg vilka som gäller.
3. **Kör en provtur** (Vart? → välj ort → Provtur) och lyssna på besked och svansar. Bedöm om Guiden pratar för lite (iakttagelsen om R-10 ovan).
4. **Gå ut med plattan** och kontrollera att remsan går från "Söker position …" till "GPS ±… m" (F-04, E4-K20). Spara Hem under Drift.
5. **Tio verkliga turer** i flygplansläge, till fots och som passagerare, med ljudinspelning (SG-24). Hämta loggen efteråt med `adb pull`.
6. **Bestäm om E4-D5** (ändringen i SafetyGate) ska gälla.

## 10. Inte verifierat

Allt som kräver en människa eller verklig rörelse: lyssning, begriplighet, verkliga turer, GNSS, flygplansläge med logg, nätverksmätning på enheten, kallstart, minne och batteri, strömavbrott under paketinstallation, liggande läge, andra appars ljud. Etapp 5-logiken är testad på JVM men inte inkopplad i appen. Convoy (etapp 6) och Babel-Stoners spel, analysläge och encyklopedi (etapp 7) är inte byggda. Oberoende granskning (F-07) saknas för allt.
