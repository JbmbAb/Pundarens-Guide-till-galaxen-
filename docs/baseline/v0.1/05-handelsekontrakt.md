# 05 – Händelsekontrakt för Illogical Engine

Baslinje v0.1 · 2026-11-20 · Status: REVISE (kandidat)

## 1. Princip

Hard Truth Core publicerar typade, oföränderliga händelser. Illogical Engine prenumererar och svarar med `TailCandidate` (03, 4.2). Svaret går till SafetyGate, aldrig tillbaka till Hard Truth Core. Bussen är **enkelriktad**: `:engine` har bara `Flow<TruthEvent>` och ingen publiceringsmetod (03, G-3).

## 2. Gemensamt kuvert

```kotlin
sealed interface TruthEvent {
    val eventId: String              // ULID, unikt och sorterbart
    val monotonicMillis: Long        // SystemClock.elapsedRealtime()
    val wallClockUtc: Instant        // bara för loggning och visning
    val expeditionId: String?
    val schemaVersion: Int           // börjar på 1
}
```

Regler för kuvertet:

- Händelser är oföränderliga `data class` med bara `val`.
- Ordningen bestäms av `monotonicMillis`. Väggklockan används aldrig för att avgöra aktualitet.
- En ny `schemaVersion` får lägga till fält men inte ändra betydelsen av befintliga.
- Händelser innehåller värden, aldrig referenser till föränderliga objekt eller callbacks.

## 3. Händelsetyper

Byggplanen anger tolv händelsetyper för version 1. Listan nedan är kandidaten. Den fryses i etapp 1.

| # | Typ | Fält (utöver kuvertet) | Prioritet i SafetyGate | Exempel på användning i tramslagret |
|---|---|---|---|---|
| 1 | `RouteStarted` | `destinationName`, `distanceMeters`, `etaSeconds`, `travelMode` | Normal | Byråkratisk inledning med exakt avstånd |
| 2 | `ManeuverUpcoming` | `maneuverId`, `type`, `modifier`, `distanceMeters`, `secondsToManeuver`, `streetName?` | **Kritisk** | Svans bara om tid räcker (06, R-02) |
| 3 | `ManeuverCompleted` | `maneuverId` | Normal | "Det gick. Rikard är förvånad." |
| 4 | `OffRoute` | `deviationMeters`, `secondsOffRoute` | **Kritisk** | Ingen svans förrän ny rutt finns (06, R-05) |
| 5 | `Rerouted` | `newDistanceMeters`, `newEtaSeconds`, `reason` | Hög | Svans tidigast efter första nya beskedet |
| 6 | `Stationary` | `durationSeconds` | Låg | Kosmisk proportionalitet |
| 7 | `Arrived` | `destinationName`, `totalSeconds`, `totalMeters` | Normal | Ankomstreplik |
| 8 | `PositionQualityChanged` | `accuracyMeters`, `fixAgeSeconds`, `quality` (`GOOD`, `DEGRADED`, `LOST`) | Hög vid `LOST` | Rikard-störning, men osäkerheten visas sakligt först |
| 9 | `BatteryLow` | `percent`, `estimatedMinutesLeft?` | Hög | Ingen svans under 10 % |
| 10 | `ConvoyGap` | `peerAlias`, `distanceMeters`, `peerFixAgeSeconds` | Hög | Bara saklig visning om åldern överstiger gränsen |
| 11 | `ConvoyContactLost` | `peerAlias`, `lastSeenAgeSeconds` | **Kritisk** | Ingen svans |
| 12 | `EmergencyActivated` | `source` (`USER`, `PEER`) | **Nödläge** | All satir undertrycks |
| 13 | `EmergencyCleared` | – | Normal | Ingen svans de första 60 sekunderna (kandidatvärde) |
| 14 | `TimeOfDayChanged` | `phase` (`DAY`, `DUSK`, `NIGHT`) | Låg | Tid på dygnet |

13 och 14 är tillägg utöver byggplanens tolv och ska prövas i etapp 1 (beslut B-21).

## 4. Vad tramslagret får och inte får

| Får | Får inte |
|---|---|
| Läsa alla fält i händelserna | Publicera händelser |
| Använda exakta värden i svansen (precisionsprovet) | Ange riktning, avstånd eller gata som om det vore ett besked |
| Komma ihåg egna presenterade yttranden och förneka dem | Förneka eller omtolka ett **besked** |
| Skylla på Rikard, universum eller Guiden | Skylla på användaren |
| Föreslå en svans med livslängd | Bestämma när svansen spelas (det gör SafetyGate) |

## 5. Svarskontrakt

`IllogicalEngine.onEvent(event, context)` är en ren funktion av händelsen, motorns minne och en seed. Samma indata och samma seed ger samma utdata. Det gör motorn testbar med en händelsesimulator i etapp 2.

Varje `TailCandidate` måste uppfylla följande, och det kontrolleras automatiskt:

1. `triggeredBy` är eventId från en verklig händelse.
2. `validUntilMonotonicMillis` är högst 10 s efter händelsen (kandidatvärde).
3. Texten innehåller inga riktningsord (vänster, höger, rakt fram, sväng, U-sväng, avfart, rondell) i fordonsläge, utom som citat av det aktuella beskedet med samma riktning. Kontrollen är en ordlistebaserad spärr i `:engine` *och* en oberoende kontroll i SafetyGate (06, R-03).
4. `origin` och `lineId` pekar på en arkivpost med `approval = APPROVED`.
5. `visibility` är densamma som för radens källpost eller striktare.

## 6. Versionering och test

- Kontraktet ligger i `:truth:api` och `:engine:api` med egna tester av serialisering och jämlikhet.
- Händelsesimulatorn i etapp 2 genererar sekvenser av alla typer, inklusive kantfall: manöver om 2 s, omräkning mitt i en svans, nödläge mitt i en svans, positionen förlorad under en manöver.
- Byggplanens grind för etapp 2: hundra simulerade händelser utan att en svans motsäger ett besked, skyller på användaren eller upprepas inom tio repliker. Status: NOT_VERIFIED.
