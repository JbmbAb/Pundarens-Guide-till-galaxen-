# Etapp 1 – verifieringsprotokoll

| Fält | Värde |
|---|---|
| Datum | 2026-11-20, testkörningen startade 22.48.36 (Europe/Stockholm, UTC+1) |
| Kod-commit som provades | `baf40b4a5dd629961187d533b6eea15c06df131f` (gren `etapp1/illogical-engine`) |
| Bas | `main` @ `c0fb94b` (G0-grindbeslutet, PR #4) |
| Byggare | Grok Bot |
| Granskare | **Ingen.** Oberoende granskning är inte gjord. |
| Miljö | Linux (byggmaskinen), Temurin JDK 17.0.21.1 (`/opt/jdk17`), Gradle 8.11.1 via wrapper, Kotlin 2.0.21 |
| Testutdata | [`etapp1-testutdata/`](etapp1-testutdata/) |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot, annars `NOT_VERIFIED` eller `REVISE`. Inget i det här protokollet gör en grind till ACCEPT. **G1 förblir `NOT_VERIFIED`** tills en oberoende granskning är gjord och ägaren har fattat grindbeslutet.

## 1. Omfattning och numrering

Uppdraget kallar det här "etapp 1". I [09](../v0.1/09-genomforandeplan.md) är etapp 1 frysningen inför G0. Det som byggts här är innehållet i 09:s **etapp 2** ("Illogical Engine isolerat", grind G1), plus den del av 09:s etapp 1 som är kod: prototypen av arkivdatabasens triggers (A-13, delvis). Namnet "etapp 1" behålls i filnamnen eftersom uppdraget anger dem.

Byggt, bara rena Kotlin-moduler (JVM, ingen Android):

| Modul | Innehåll |
|---|---|
| `:truth:api` | De fjorton händelsetyperna i 05 som oföränderliga `data class`, prioritet per typ, `TruthEventStream` och `TruthStateReader` (bara läsning), ögonblicksbilder för SafetyGate. Märkt `KANDIDAT_OFRYST`. |
| `:archive:api` | Fyra ursprungsklasser, verifieringsstatus, godkännande, synlighet, mekanismerna M1–M8, `ArchiveReader` och `ArchiveStore` (skrivregler enligt triggerna i 04). |
| `:archive:core` | Import av `data/` till arkivposter enligt 04 §1.3 och F-09/F-10, `InMemoryArchiveStore` med skrivskydd på modellnivå, SQL-schemat ur 04 som resurs. |
| `:engine:api` | `TailCandidate`, `IllogicalEngine`, `EngineContext`, `EngineMemory`. |
| `:engine` | Radbank, textspärrar (röstreglerna), deterministisk `DefaultIllogicalEngine`, motsägelseminne, kontraktskontroll enligt 05 §5. |
| `:safetygate` | Deterministisk SafetyGate enligt 06 med konfigurerbara, ofrysta kandidatvärden och en egen, oberoende riktningsordskontroll. |
| `:babel` | Babel-Stoners Satirläge, deterministiskt. |
| `:verification:architecture` | Arkitekturtester, röstreglerna som tester och ett genomgående test händelser → motor → SafetyGate. |

Inte byggt (utanför etappen eller uppskjutet): `:truth:core`, `:voice`, `:convoy:*`, `:llm:api`, `:app`, Android-modulen `:archive`, Analysläget, karta, ruttning och talsyntes.

## 2. Kommandon

```bash
export JAVA_HOME=/opt/jdk17
./gradlew --no-daemon --console=plain clean check          # bygge, alla tester och verifyModuleGraph
./gradlew --no-daemon --console=plain verifyModuleGraph    # G-6, med en tillfällig förbjuden kant (negativt prov)
```

Utdata: [`gradle-clean-check.txt`](etapp1-testutdata/gradle-clean-check.txt) (`BUILD SUCCESSFUL`, `EXIT=0`), [`sammanfattning.txt`](etapp1-testutdata/sammanfattning.txt), [`junit-xml/`](etapp1-testutdata/junit-xml/) (tio testsviter), [`module-graph.txt`](etapp1-testutdata/module-graph.txt) och [`negativt-prov-G6.txt`](etapp1-testutdata/negativt-prov-G6.txt) (`BUILD FAILED`, `EXIT=1`, som avsett).

**Utfall: 87 tester, 0 fel, 0 avbrott, 0 överhoppade.**

| Testsvit | Tester |
|---|---|
| `archive.core.ArchiveImportTest` | 10 |
| `archive.core.ArchiveStoreTest` | 7 |
| `archive.core.SqliteTriggerTest` | 6 |
| `babel.SatireModeTest` | 5 |
| `engine.IllogicalEngineTest` | 8 |
| `engine.LineBankTest` | 4 |
| `safetygate.SafetyGateTest` | 26 |
| `verification.ArchitectureTest` | 10 |
| `verification.EndToEndDiodeTest` | 1 |
| `verification.VoiceRulesTest` | 10 |

## 3. Utfall per kriterium

| Id | Kriterium | Bevis | Status |
|---|---|---|---|
| K-01 | Gradle-projektet bygger med rena Kotlin-moduler utan Android | `gradle-clean-check.txt`; `module-graph.txt` visar bara `org.jetbrains*` och egna moduler i `runtimeClasspath` | ACCEPT |
| K-02 | Händelsekontraktet: fjorton typer, oföränderliga, ingen publiceringsväg i `:truth:api` (G-3) | `ArchitectureTest`: "händelserna är oföränderliga värden", "G-3 sanningsgränssnitten är bara läsning" | ACCEPT. Frysning av listan: NOT_VERIFIED (ofryst enligt G0-grindbeslutet). |
| K-03 | G-6: beroendegrafen kontrolleras automatiskt och bygget fallerar vid förbjuden kant | `verifyModuleGraph` i `check`; `negativt-prov-G6.txt` (`:engine -> :safetygate` stoppades) | ACCEPT |
| K-04 | G-1 och G-8: motorn, Babel och SafetyGate importerar inte sanningskärnan, konvoj, Android, Google eller nätverk | `ArchitectureTest` G-1, G-8 | ACCEPT |
| K-05 | Motorn gör ingen I/O och använder ingen klocka eller oseedad slump | `ArchitectureTest` "motorn gör ingen I-O" | ACCEPT |
| K-06 | G-4: `TailCandidate` bär inga sanningstyper och inga callbacks | `ArchitectureTest` G-4 | ACCEPT |
| K-07 | G-5: `:truth:core` tar inte emot tramstyper | `:truth:core` finns inte än | NOT_VERIFIED |
| K-08 | G-7 med Konsist | Konsist används inte. Delar av G-7 täcks av egna käll- och reflektionstester (K-02, K-04, K-06). | NOT_VERIFIED (verktyget) |
| K-09 | G-9 och SG-23: SafetyGate returnerar bara beslut och beror inte på `:truth:core` eller `:engine` | `ArchitectureTest` G-9/SG-23; `module-graph.txt` | ACCEPT |
| K-10 | Dynamiskt: sanningstillståndet ändras inte av tramslagret, och inget släpps fram i EMERGENCY, REROUTING eller MANEUVER_WINDOW | `EndToEndDiodeTest` (200 händelser; sanningsmodellen är en teststub) | ACCEPT för stubben. Mot riktig `:truth:core`: NOT_VERIFIED. |
| K-11 | SafetyGate deterministisk, kandidatvärdena konfigurerbara och märkta ofrysta | `SafetyGateConfig` (`STATUS = KANDIDAT_OFRYST`); SG-21 | ACCEPT |
| K-12 | Ursprungsklasser och import enligt 04 §1.3, F-09 och F-10; originalen byte för byte; alla poster `PRIVATE` | `ArchiveImportTest` (10 tester) | ACCEPT. Se avvikelse D-3. |
| K-13 | Skrivskydd för original på modellnivå | `ArchiveStoreTest` (7 tester) | ACCEPT |
| K-14 | Triggerna i 04 stoppar update/delete av original, uppgradering till original och ändring av historiken, och loggar synlighet | `SqliteTriggerTest` mot SQLite 3.41.2 via `org.xerial:sqlite-jdbc` (bara i test) | ACCEPT för vanlig SQLite. Room eller SQLDelight och Android: NOT_VERIFIED (A-13). |
| K-15 | Migreringsrisken: en ombyggnad av tabellen tappar triggerna | `SqliteTriggerTest` "ombyggnad av tabellen tappar triggerna och upptäcks" visar att triggerna försvinner, att kontrollen mot `sqlite_master` upptäcker det och att skyddet är tillbaka när triggerna återskapas | ACCEPT som bevis för risken och kontrollen. Att vald teknik återskapar dem: NOT_VERIFIED. |
| K-16 | Motorn använder bara `ORIGINAL_VERBATIM` och `APPROVED_EDIT` med `APPROVED`, aldrig Grok Bots förslag | `LineBankTest`: radbanken har 20 poster (C-001, C-002a, C-002b, C-003–C-009, N-002, P-001, P-001-N, P-002–P-007, RED-P-007). 89 utesluts: 75 ej godkända (alla 65 Grok Bot-poster), 10 fel ursprungsklass, 3 Rikard-utdrag, 1 lexikon. | ACCEPT |
| K-17 | Determinism: samma händelse, minne och seed ger samma svar | `IllogicalEngineTest` (1 000 upprepningar) | ACCEPT |
| K-18 | Svarskontraktet i 05 §5 punkt 1–5 för varje svans | `IllogicalEngineTest` "varje svans uppfyller svarskontraktet" (300 händelser) | ACCEPT |
| K-19 | Motsägelseminnet förnekar bara presenterade yttranden | `IllogicalEngineTest` med testfixturer | ACCEPT på fixturer. Med dagens data finns inga godkända förnekelserader, så funktionen är vilande. |
| K-20 | G1:s hundra-händelsekriterium på motornivå: ingen svans motsäger ett besked, skyller på användaren eller upprepas inom tio | `IllogicalEngineTest` "G1-kriteriet": 100 händelser, 57 svansar, 0 brott | ACCEPT som test. **G1 som grind: NOT_VERIFIED** (ingen oberoende granskning). |
| K-21 | Babel-Stoners Satirläge deterministiskt, bara BS-01–BS-10, märkt som satir | `SatireModeTest` (5 tester) | ACCEPT. Satiriska repliker skrivs inte av byggaren och väntar på F-11. |
| K-22 | Inget Google-beroende någonstans | `verifyModuleGraph` (lösta artefakter); `ArchitectureTest` textsökning i bygg- och källfiler | ACCEPT |
| K-23 | Oberoende granskning (F-07, G-10) | – | NOT_VERIFIED |

### 3.1 SafetyGate, testmatrisen i 06

| Test | Testnamn | Status |
|---|---|---|
| SG-01 | `SG-01 normalt läge ger Present` | ACCEPT |
| SG-02 | `SG-02 manöver om 10 s i fordon ger NO_TIME` (+ `SG-02b`) | ACCEPT. Se D-5. |
| SG-03 | `SG-03 pågående svans avbryts …` | ACCEPT för beslutet. Avbrott inom 100 ms: NOT_VERIFIED (kräver `:voice` på enheten). |
| SG-04, SG-05 | riktningsord fordon/till fots (+ `SG-05b`) | ACCEPT |
| SG-06, SG-07 | nödläge, 1 000 slumpade kandidater | ACCEPT |
| SG-08, SG-09 | omräkning | ACCEPT |
| SG-10 | förlorad position | ACCEPT för beslutet. "Sanningsremsan visar Position osäker": NOT_VERIFIED (UI finns inte). |
| SG-11–SG-20 | osäkerhet, batteri, konvoj, stress, glitch, integritet, upprepning, utgång, ej godkänd | ACCEPT |
| R-16 | 60 s efter nödläge | ACCEPT |
| SG-21 | 10 000 upprepningar | ACCEPT |
| SG-22 | 50 000 slumpade lägen, 26 703 i EMERGENCY/REROUTING/MANEUVER_WINDOW, 0 `Present` där | ACCEPT |
| SG-23 | se K-09 | ACCEPT |
| SG-24 | fälttest i flygplansläge | NOT_VERIFIED |

### 3.2 Röstreglerna (12) som tester

| Regel | Test | Status |
|---|---|---|
| RR-01 | varje rad i radbanken har minst en mekanism | ACCEPT. Klassningen av citat som M1 är byggarens (D-12). |
| RR-02 | ton, ingen omvänd ordföljd | NOT_VERIFIED (kräver stickprov av Jimmy) |
| RR-03 | SG-01–SG-03, SG-22, K-10 | ACCEPT för logiken. SG-24: NOT_VERIFIED. |
| RR-04 | mönsterspärr i radbanken | ACCEPT för spärren. Mänsklig bedömning (F-11): NOT_VERIFIED. |
| RR-05 | spärrlista med böjningar, "högre" undantas, både motor och SafetyGate | ACCEPT. Undantaget "citat av aktuellt besked" är inte byggt; spärren är strängare. |
| RR-06 | spärr mot skuld på användaren | ACCEPT (mönsterlistan är ett förslag) |
| RR-07 | R-001–R-003 utesluts; ingen post har Rikard som upphov | ACCEPT |
| RR-08 | förslag och variationer kan inte vara original | ACCEPT |
| RR-09 | Satirläget märker svaret "Satir" | ACCEPT. Analysläget finns inte: NOT_VERIFIED. |
| RR-10 | lånade namn hålls utanför radbanken | ACCEPT. Nya namn (B-12, B-13): öppna. |
| RR-11 | bara APPROVED; relationscitat PRIVATE; SG-16, SG-17, SG-20 | ACCEPT |
| RR-12 | sanning skild från satir på skärm och i ljud | NOT_VERIFIED (UI och G4) |
| RR-13 | inga BS-regler i navigatorns radbank | ACCEPT |

## 4. Avvikelser från baslinjen

| Id | Avvikelse | Skäl | Kräver |
|---|---|---|---|
| D-1 | `:engine:api` beror på `:archive:api` (03 säger bara `:truth:api`) | `TailCandidate` innehåller ursprungsklass och synlighet. `:archive:api` har inga beroenden. Minsta ändring. | Granskare |
| D-2 | `TailCandidate` har fälten `approval`, `mechanisms` och `mentionsRikard` utöver 03 §4.2 | SafetyGate ska kunna pröva R-06 och R-15 utan att läsa arkivet | Granskare |
| D-3 | `ORIGINAL_VERBATIM` får `approval = APPROVED` vid import (04 anger "–") | Originalen är arkivets egna ord. Utan det skulle motorn inte ha några rader. Alla är `PRIVATE`. | **Jimmy** |
| D-4 | SafetyGates signatur är `decide(GateInput, TailCandidate)` och `checkOngoing(GateInput, TailCandidate)` i stället för `decide(state, nav, candidate?)`. `PositionSnapshot` ingår. Avbrott sker också i REROUTING, PRIORITY_INFO och vid förlorad position under aktiv rutt. | 06 säger att inga svansar får finnas i de lägena. Strängare än 06. | Granskare |
| D-5 | NO_TIME prövas före tyst fönster | Annars ger SG-02 skälet MANEUVER i stället för NO_TIME. Beslutet blir `Suppress` i båda fallen. | Granskare |
| D-6 | Nytt kandidatvärde: fordonsreglerna gäller från 10 km/h även till fots | F-05 säger "fordonsfart" utan värde | Jimmy (frysning av 06) |
| D-7 | `:archive` byggs inte som Android-bibliotek. Lagringen ligger bakom `ArchiveStore`; `:archive:core` är ren Kotlin. | A-13 (Room eller SQLDelight) är öppen | Jimmy (A-13) |
| D-8 | R-001–R-003 utesluts helt ur radbanken. Relationscitaten finns med men är `PRIVATE`. | RR-07 och R-12 | Jimmy |
| D-9 | Etappnumreringen, se avsnitt 1 | Uppdraget och 09 numrerar olika | – |
| D-10 | JDK 17, Gradle 8.11.1, Kotlin 2.0.21 (PR #1 hade JDK 21, Gradle 9.6, Kotlin 2.4.21) | Fanns på byggmaskinen. Versionerna låses först med Android-delarna. | – |
| D-11 | Taltid uppskattas som 600 ms + 380 ms per ord | Byggarens antagande, mäts i G4 | – |
| D-12 | Citat i kategorierna paradox, tillit och övrigt klassas som M1; BS-regler M6 (BS-02, BS-06 även M3; BS-09 även M5) | 02 §3 anger mekanismer men inte per citat | Jimmy |
| D-13 | Konsist används inte | Egna käll- och reflektionstester räckte för etappen | Granskare |

## 5. Återanvänt från PR #1 (`cursor/android-milestone-1-7e26`)

Ingen kod är kopierad rakt av. Idéer som tagits om enligt beslutsloggen:

- `SessionMemory` → oföränderligt `EngineMemory` som anroparen håller (PR1-07/09). Seed sätts alltid av anroparen; `System.nanoTime()` används inte.
- JSON-läsningen (PR1-12) → `ArchiveImporter` med kotlinx-serialization (i stället för Moshi och KSP), fyra ursprungsklasser, verifiering, godkännande och synlighet.
- Testet `parsesRealArchiveWithoutAlteringOriginals` (PR1-13) → `ArchiveImportTest`.
- PR1-11 (förslag användes utan godkännande) är rättat: förslag kan inte nå radbanken.
- Babel-analysen (PR1-18, PR1-19) hör till etapp 7 och är inte återanvänd.

## 6. Iakttagelser

- I det genomgående testet släpptes 2 av 160 föreslagna svansar fram (158 stoppades). Det beror på att den syntetiska sekvensen ofta ligger i manöverfönster, omräkning eller nödläge. Det är inte ett kvalitetsmått; tröskelvärdena prövas i G5.
- Radbanken har bara 20 rader, alla privata. Med bilhögtalare, Bluetooth eller okänd ljudutgång stoppas alla (R-12). Fler rader kräver F-11.
- Gradle varnar för API som tas bort i Gradle 9 (`dependencyProject` i `verifyModuleGraph`). Det påverkar inte utfallet nu.

## 7. Inte verifierat

Talsyntes och avbrott inom 100 ms (G4), allt på målenheten SM-X900 (F-04), triggerna i Room eller SQLDelight och efter migrering (A-13), `:truth:core` och G-5, Konsist (G-7), sanningsremsan (UI), Analysläget, SG-24 och oberoende granskning av allt ovan.
