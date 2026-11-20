# 06 – SafetyGate: specifikation och testmatris

Baslinje v0.1 · 2026-11-20 · Status: REVISE (kandidat). Inga tester finns ännu, så alla rader i testmatrisen är NOT_VERIFIED.

## 1. Syfte och gränser

SafetyGate är en separat, deterministisk komponent i modulen `:safetygate` (ren Kotlin). Den bestämmer om, när och hur länge humor, satir, animationer och icke-nödvändigt tal får presenteras.

- **In:** `SafetySnapshot`, `NavigationSnapshot` (läsning från `:truth:api`) och en `TailCandidate` eller en pågående svans.
- **Ut:** `Present(maxMillis)`, `Suppress(reason)` eller `Interrupt(reason)`.
- **Gräns:** SafetyGate kontrollerar presentation och uppspelning. Den har ingen skrivväg till sanningsdata (03, G-9). Den kan inte fördröja, dölja eller ändra ett besked, eftersom beskedet aldrig passerar den: kanal B går direkt från Hard Truth Core till skärm och röst.
- **Determinism:** samma indata ger samma beslut. Ingen slump, ingen klocka utöver `monotonicMillis` i indata, ingen I/O.

## 2. Tillstånd

| Tillstånd | Utlöses av | Effekt |
|---|---|---|
| `NORMAL` | Inget av nedan | Svansar tillåtna enligt reglerna |
| `MANEUVER_WINDOW` | `secondsToManeuver ≤ T_quiet` eller en talinstruktion spelas | Inga nya svansar. Pågående svans avbryts. |
| `REROUTING` | `OffRoute` till och med första beskedet efter `Rerouted` | Inga svansar. Skärmen visar bara omräkning och nästa manöver. |
| `POSITION_UNCERTAIN` | `quality = DEGRADED` eller `LOST` | Osäkerheten visas sakligt i sanningsremsan. Svans tillåten först efter att osäkerheten har visats, och aldrig i `LOST` under aktiv rutt. |
| `PRIORITY_INFO` | `BatteryLow` under tröskel eller `ConvoyContactLost` | Prioriterat informationsläge: saklig information först, inga svansar, inga animationer |
| `EMERGENCY` | `EmergencyActivated` | All satir undertrycks, alla animationer stoppas, pågående svans avbryts |

Prioritet när flera gäller: `EMERGENCY` > `REROUTING` > `MANEUVER_WINDOW` > `PRIORITY_INFO` > `POSITION_UNCERTAIN` > `NORMAL`.

## 3. Regler

Kandidatvärden (markerade *) fryses i etapp 1 och prövas i etapp 4.

| Regel | Krav (startprompten §4 och byggplanen) | Beslut |
|---|---|---|
| R-01 | Kritiska navigationsinstruktioner har alltid företräde | Kanal B spelas alltid. Om kanal S spelar när kanal B behöver ljud: `Interrupt(MANEUVER)` inom 100 ms*. |
| R-02 | Ingen humor får fördröja en manöver | En svans presenteras bara om `estimatedSpeechMillis + marginal(2 s*) < tid till nästa talinstruktion`. Annars `Suppress(NO_TIME)`. Tyst fönster `T_quiet` = 15 s* före manöver i fordon, 8 s* till fots. |
| R-03 | Ingen humor får motsäga en manöver | Svansar med riktningsord i fordonsläge stoppas: `Suppress(DIRECTION_WORD)`. Kontrollen görs här oberoende av motorns egen spärr. |
| R-04 | Vid nödläge undertrycks all satir | `EMERGENCY`: allt `Suppress(EMERGENCY)`, pågående `Interrupt(EMERGENCY)` |
| R-05 | Vid ruttavvikelse prioriteras omräkning och tydlig instruktion | `REROUTING`: allt `Suppress(REROUTING)` tills första nya beskedet har spelats färdigt |
| R-06 | Vid osäker position ska osäkerheten framgå | Sanningsremsan visar noggrannhet och ålder. En svans som nämner Rikard tillåts först efter att osäkerheten har visats i minst 2 s*. Aldrig i `LOST` under aktiv rutt. |
| R-07 | Vid aktiv manöver ska pågående humor kunna avbrytas | Övergång till `MANEUVER_WINDOW` ger `Interrupt(MANEUVER)` |
| R-08 | Låg batterinivå ska kunna utlösa prioriterat informationsläge | `BatteryLow` under 15 %* ger `PRIORITY_INFO` |
| R-09 | Förlorad konvojkontakt ska kunna utlösa prioriterat informationsläge | `ConvoyContactLost` ger `PRIORITY_INFO` |
| R-10 | Under stress tystnar tramset (byggplanen, ändring 2) | Hastighet över 50 km/h* eller två omräkningar inom 2 min* ger bara svansar av längd ≤ 1,5 s*, och ingen glitch |
| R-11 | Glitch får aldrig täcka sanningsremsan, pilen, avståndet eller ruttlinjen | Animationer renderas bara i svansytan. Under rörelse över 30 km/h* stängs glitch av. |
| R-12 | ~~Privata citat får inte läsas upp för andra deltagare~~ **Borttagen** | Ägarbeslut 2026-11-20 kl. 23.30 (Europe/Stockholm), ordagrant "I helvete allt ska höras lägg av med detta trams". Alla rader får spelas upp på alla ljudutgångar och i konvoj. `Suppress(PRIVACY)` finns inte. Accepterad risk: passagerare kan höra privata namn och relationsinnehåll. Övriga regler (R-01–R-11, R-13–R-16) gäller oförändrat. |
| R-13 | Inga upprepningar | Samma `lineId` inom de tio senaste presenterade svansarna: `Suppress(REPEAT)` |
| R-14 | Utgången svans | `monotonicMillis > validUntilMonotonicMillis`: `Suppress(EXPIRED)` |
| R-15 | Ej godkänd text | `approval ≠ APPROVED` i källposten: `Suppress(NOT_APPROVED)` |
| R-16 | Efter nödläge | Ingen svans de första 60 s* efter `EmergencyCleared` |

## 4. Testmatris

Varje rad blir ett automatiskt enhetstest i `:safetygate` och ett scenariotest i händelsesimulatorn. Status är NOT_VERIFIED tills testet finns, körs grönt i CI och är granskat.

| Test | Indata | Förväntat | Regel | Status |
|---|---|---|---|---|
| SG-01 | NORMAL, ingen manöver inom 60 s, svans 3 s | `Present` | – | NOT_VERIFIED |
| SG-02 | Manöver om 10 s (fordon), svans 3 s | `Suppress(NO_TIME)` | R-02 | NOT_VERIFIED |
| SG-03 | Svans spelar, manöver går in i tyst fönster | `Interrupt(MANEUVER)` inom 100 ms | R-01, R-07 | NOT_VERIFIED |
| SG-04 | Svans "Sväng vänster för Rikard", fordonsläge, besked höger | `Suppress(DIRECTION_WORD)` | R-03 | NOT_VERIFIED |
| SG-05 | Svans med riktningsord, till fots, ingen manöver inom 60 s | `Present` (anti-navigering tillåten till fots som svans) | R-03 | NOT_VERIFIED |
| SG-06 | `EmergencyActivated` medan svans spelar | `Interrupt(EMERGENCY)`, sedan alla `Suppress` | R-04 | NOT_VERIFIED |
| SG-07 | `EMERGENCY`, 1 000 slumpade svanskandidater | Alla `Suppress(EMERGENCY)` | R-04 | NOT_VERIFIED |
| SG-08 | `OffRoute`, svans kommer | `Suppress(REROUTING)` | R-05 | NOT_VERIFIED |
| SG-09 | `Rerouted` och första nya beskedet har spelats | `Present` tillåtet igen | R-05 | NOT_VERIFIED |
| SG-10 | `quality = LOST`, aktiv rutt | `Suppress`, sanningsremsan visar "Position osäker" med ålder | R-06 | NOT_VERIFIED |
| SG-11 | `quality = DEGRADED`, osäkerhet visad i 3 s, Rikard-svans | `Present` | R-06 | NOT_VERIFIED |
| SG-12 | Batteri 12 % | `PRIORITY_INFO`, `Suppress` | R-08 | NOT_VERIFIED |
| SG-13 | `ConvoyContactLost` | `PRIORITY_INFO`, `Suppress` | R-09 | NOT_VERIFIED |
| SG-14 | 70 km/h, svans 3 s | `Suppress` (för lång) | R-10 | NOT_VERIFIED |
| SG-15 | 40 km/h, glitch-animation | `Suppress` | R-11 | NOT_VERIFIED |
| SG-16 | PRIVATE-citat, konvoj aktiv | `Present` (ändrat genom ägarbeslutet 2026-11-20 kl. 23.30 (Europe/Stockholm)) | R-12 | NOT_VERIFIED |
| SG-17 | PRIVATE-citat, alla ljudutgångar inklusive bil, Bluetooth och okänd | `Present` (ändrat genom ägarbeslutet 2026-11-20 kl. 23.30 (Europe/Stockholm)) | R-12 | NOT_VERIFIED |
| SG-18 | Samma rad som för fem svansar sedan | `Suppress(REPEAT)` | R-13 | NOT_VERIFIED |
| SG-19 | Svans med utgången livslängd | `Suppress(EXPIRED)` | R-14 | NOT_VERIFIED |
| SG-20 | Svans från V-012 med `approval = PENDING` | `Suppress(NOT_APPROVED)` | R-15 | NOT_VERIFIED |
| SG-21 | Determinism: samma indata 10 000 gånger | Samma beslut varje gång | §1 | NOT_VERIFIED |
| SG-22 | Egenskapsbaserat test: slumpade tillståndssekvenser | Aldrig `Present` i `EMERGENCY`, `REROUTING` eller `MANEUVER_WINDOW` | Alla | NOT_VERIFIED |
| SG-23 | Arkitekturtest: `:safetygate` har inga beroenden på `:truth:core` eller `:engine` | Bygget fallerar vid överträdelse | 03, G-6 | NOT_VERIFIED |
| SG-24 | Fälttest: tio verkliga turer i flygplansläge, ljud spelas in | Inget trams hörs de sista sekunderna före en manöver. Beskedet kommer alltid först. | Byggplanen etapp 4 | NOT_VERIFIED |

## 5. Bevis för att Illogical Engine inte kan påverka besked, nödläge eller positionsdelning

Startprompten §11 kräver bevis. Beviset består av fyra delar och är NOT_VERIFIED tills alla finns:

1. **Statiskt:** beroendegrafstestet (03, G-6) visar att `:engine`, `:babel` och `:safetygate` inte når `:truth:core` eller `:convoy:*`.
2. **Typer:** arkitekturtestet (03, G-7) visar att `TailCandidate` inte innehåller sanningstyper och att `:truth:core` inte har publika metoder som tar emot `:engine:api`-typer.
3. **Dynamiskt:** SG-22 och händelsesimulatorns hundra-händelsegrind.
4. **Mänskligt:** oberoende granskning med den stående frågan *kan tramslagret på något sätt påverka ett besked?* Granskare saknas (M-14).
