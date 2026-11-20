# G0 – grindbeslut 2026-11-20

| Fält | Värde |
|---|---|
| Grind | G0, "Konceptet är fryst" (08, avsnitt 2) |
| Beslutsfattare | Jimmy Bruce, ägare |
| Tidpunkt | 2026-11-20 kl. 22.32 (Europe/Stockholm, UTC+1) |
| Gäller commit | `main` @ `81afd559abdfdddbdd8540ff7adf844e3db0b0d2` (sammanslagning av PR #3) |
| Status | **ACCEPT genom ägarbeslut med accepterad risk** |
| Oberoende granskning | **Inte gjord.** |

## 1. Beslutet ordagrant

> Stäng G0 nu och börja bygga, jag tar risken utan oberoende granskning

Beslutet gäller före ägarens tidigare instruktion att G0 ska förbli öppen tills GPT-6 har granskat underlaget (F-07, [G0-instruktion-2026-11-20.md](G0-instruktion-2026-11-20.md), avsnittet "Gränser" och "Oberoende granskning").

## 2. Vad statusen betyder

- G0 står som ACCEPT för att ägaren har beslutat det, inte för att kriterierna i 08 är bevisade. Det är en accepterad risk, inte ett verifierat utfall.
- **Den oberoende granskningen (F-07, G-10 i 03, M-14) har inte gjorts för G0.** Inget i G0-underlaget är prövat av någon annan än byggaren.
- Beslutet gäller bara G0. F-07 gäller fortfarande för senare grindar om ägaren inte beslutar annat. Övriga grindar G1–G8 förblir `NOT_VERIFIED`.
- Beslutet godkänner inga rader i F-11 och ändrar ingen post i `data/`.

## 3. Punkter som var öppna när G0 stängdes, och hur bygget hanterar dem tills vidare

Källor: [G0-agarbeslut-2026-11-20.md](G0-agarbeslut-2026-11-20.md) avsnitt 4 och [G0-instruktion-2026-11-20.md](G0-instruktion-2026-11-20.md) avsnittet "Kvar för G0".

| Punkt | Läge vid beslutet | Hantering i bygget tills vidare |
|---|---|---|
| F-11, radgranskning | Underlag klart ([F-11-radgranskning.md](F-11-radgranskning.md)). **Inga förslag är godkända.** | Motorn får bara använda poster med ursprungsklass `ORIGINAL_VERBATIM` eller `APPROVED_EDIT` samt deterministiska regler (BS-01–BS-10, `DERIVED_RULE` med `APPROVED`). Grok Bots förslag (`approved: false`: V-001–V-020, NAV-001–NAV-020, EX-F-01–EX-F-07, BS-11–BS-16, lexikonförslagen) importeras som `PENDING` och filtreras bort. Ett automatiskt test bevisar det. Ingen ny tramstext skrivs av byggaren. |
| B-12, lånade namn | Inga nya namn valda. | Alla lånade namn räknas som arbetsnamn. Rader som innehåller dem kommer inte in i motorns radbank (test). Inget delas eller publiceras. |
| B-13, appens namn | Inget nytt namn valt. | "Pundarens Guide till Galaxen" är arbetsnamn. Repot förblir privat. Ingen publicering. |
| 05, frysning av händelselistan | Inte fryst. | De fjorton typerna i 05 byggs som kandidat, `schemaVersion = 1`, märkta `KANDIDAT_OFRYST` i koden. Ändringar får bara vara tillägg tills listan fryses. |
| 06, kandidatvärden | Inte frysta. | Värdena markerade * i 06 ligger i en konfigurerbar `SafetyGateConfig` med startvärdena ur 06, märkta som ofrysta. Testerna läser värdena ur konfigurationen. |
| A-13, Room eller SQLDelight | Inte valt. Triggerprototyp saknas. | Lagringen skjuts upp bakom ett gränssnitt (`ArchiveStore` i `:archive:api`). Skrivskyddet bevisas på modellnivå och genom att 04:s SQL-triggers körs mot vanlig SQLite i test. Att triggerna överlever en migrering i Room eller SQLDelight förblir `NOT_VERIFIED` tills valet görs. |
| Gränsvärden för G5 | Inte satta. | Inget i nästa etapp beror på dem. G5 kan inte bli ACCEPT förrän gränserna är satta. |
| F-03, paketgränsen | Orsa kommun och angränsande kommuner. Grannkommunerna inte bestämda. | Ingen karta, inget paket och ingen ruttning i nästa etapp. Påverkar G2 och G5. |
| F-04, verifiering på enheten | Android-version och GNSS på SM-X900 inte kontrollerade. | Ingen enhetsberoende kod i nästa etapp (rena Kotlin-moduler). Kontrolleras på enheten före G2. |

Övrigt som fortfarande gäller oförändrat: F-08 (Rikards samtycke; till dess 11 R-05 och RR-07: Rikard hörs aldrig, R-001–R-003 läses inte upp), F-13 (bilagan på 912 rader och `Citat_och_paradoxer_falsifiering.md` är MISSING, inget rekonstrueras), F-15 och F-17.

## 4. Följd

Byggandet får börja med den isolerade motorn och SafetyGate i rena Kotlin-moduler. Navigering, karta och ruttning ingår inte. Den stående frågan gäller fortfarande: *kan tramslagret på något sätt påverka ett besked?*
