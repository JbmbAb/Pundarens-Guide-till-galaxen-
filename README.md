# 🌀 Pundarens Guide till Galaxen

> **GÄLLANDE, ÖVERORDNAD INRIKTNING:**
> [docs/GALLANDE-INRIKTNING.md](docs/GALLANDE-INRIKTNING.md)
> Detta dokument har företräde framför äldre planer, briefer och motstridiga förslag.

**Inte en navigator med skämt.** En absurd galaktisk följeslagare som av misstag råkar vara användbar.

Appen ska göra användaren glad.

## Börja här

- [Gällande produktinriktning](docs/GALLANDE-INRIKTNING.md)
- [Satirisk kanon och de tio budorden](docs/SATIRISK-KANON.md)
- [Aktuell PRD](docs/PRD.md)
- [Dokumentationsrevision och ersatta beslut](docs/DOKUMENTATIONSREVISION.md)
- [Stitch-design](docs/design/README.md)
- [Citatarkivet](data/quotes/README.md)
- [Teknisk baslinje](docs/baseline/v0.1/) och [protokoll](docs/baseline/verification/)

## Appens hjärta

**Illogical Engine** förenar:

- Don't Panic.
- Babel-Stoner.
- Rikard-faktorn.
- Citatarkivet.
- Cosmic Navigator.
- Probability Drive.
- Handdukssensorn.
- Vibe Protect.

Den visuella riktningen hämtas från Handdukssensorn och Cosmic HUD.

## Faktiskt tekniskt läge

**Status 2026-11-21:** appen finns och kör på målenheten (Samsung Galaxy Tab S8 Ultra) med karta, ruttning och navigering offline, svensk röst och Guiden bakom SafetyGate.

- **Utvecklingsgren:** `etapp4/sanningskarna` (inte sammanfogad till `main`).
- **Version:** `0.6-etapp6`.
- Etapp 5–7 (skämt som bär last, Convoy, Babel-Stoner och encyklopedin) är byggda och testade på dator men inte provade på målenheten.
- Det mesta i den gällande inriktningen är inte byggt än: startsidan kring Don't Panic, antinavigeringen, Rikards störningar, kartlägena och resten av det absurda lagret. Det bygget väntar på ägarens ord.

Appen använder Kotlin, Jetpack Compose, MapLibre, Valhalla Mobile, Ferrostar Core och lokala kart- och ruttningspaket. Ingen Google Maps SDK och inget beroende av Google Play Services ingår.

## Bygga och köra

```
./gradlew clean check assembleDebug      # alla tester, modulgraf, lint och APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Offlinepaketet (karta, ruttgraf, platser) checkas inte in. Det byggs i verkstaden (`tools/verkstad/`) och läggs i appens mapp `inkommande/` efter första start, se etapp 4-protokollet avsnitt 3.

| Modul | Vad |
|---|---|
| `truth/api`, `truth/core`, `truth/device` | Sanningslagret: händelser, kärnan (ren Kotlin) och Android-adaptrarna (valhalla-mobile, Ferrostar, GNSS) |
| `engine/api`, `engine`, `archive/*` | Illogical Engine och arkivet med ursprungsklasser |
| `safetygate`, `conductor` | SafetyGate och dirigenten som bara släpper fram Guiden när det är tillåtet |
| `voice` | En talmotor, två kanaler: besked klipper alltid svansen |
| `app` | Skärmen: sanningsremsan, kartan, svansytan, nödkortet, Convoy-rutan |
| `babel`, `encyclopedia` | Babel-Stoners satirläge och encyklopedin |
| `convoy/api`, `convoy/http`, `relay/` | Convoy: krypterad positionsdelning, transporten och det lilla reläet |
| `verification/architecture` | Tester som håller isär sanning och trams |
| `prototyp/offline-kedja` | Etapp 2-prototypen, kvar som historik |

## Viktig säkerhetsprincip

Illogical Engine får vara fullständigt absurd. Navigeringsinformation får det inte.

Rutt, position, svängar, avstånd och varningar hanteras av ett separat sanningslager.

Inga skämt får ändra, dölja eller försena säkerhetskritisk information.

## Data och upphov

Kartlagren i `data/geojson/` kommer från OpenStreetMap och är **© OpenStreetMap contributors**, tillgängliga under [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). Appen måste visa upphovet i kartan och på om-sidan. Detaljer och Overpass-frågor finns i [`data/geojson/stockholms-lan/README.md`](data/geojson/stockholms-lan/README.md).

Designen i `docs/design/` är exporterad från Google Stitch.

Citaten i `data/quotes/` är personligt material med särskilda proveniensregler.

Historiska dokument är referensmaterial, inte gällande utvecklingsbeslut.

## Licens

Licensen är inte bestämd än. Tills en licens har valts gäller inga öppna rättigheter för koden eller citatarkivet. OSM-datan omfattas av ODbL oavsett.
