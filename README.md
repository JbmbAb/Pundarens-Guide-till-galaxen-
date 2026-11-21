# 🌀 Pundarens Guide till galaxen

*Pundarens vän till galaxen · Stoner's Friend to the Galaxy*

En ologisk men oumbärlig digital följeslagare för Android, inspirerad av *Liftarens guide till galaxen*. Den är en karta, en filosof och en guide till nattmat i ett.

> Appen ska inte vara en förnuftig assistent som försöker vara rolig. Den ska vara en absurd, inkonsekvent, självmedvetet inkompetent galaktisk följeslagare som av misstag råkar vara användbar.

**Status 2026-11-21:** appen finns och kör på målenheten (Samsung Galaxy Tab S8 Ultra) med karta, ruttning och navigering offline, svensk röst och Guiden bakom SafetyGate. Ingen grind efter G0 är godkänd: det som återstår är verkliga turer, lyssning och oberoende granskning. Läget står i protokollen under [`docs/baseline/verification/`](docs/baseline/verification/), senast [etapp 4](docs/baseline/verification/etapp4-protokoll.md).

Det som gäller för bygget är baslinjen i [`docs/baseline/v0.1/`](docs/baseline/v0.1/), inte resten av den här sidan. Avsnitten nedan är den ursprungliga beskrivningen och är delvis ersatta: Google Maps används inte (MapLibre med lokal kartfil), och anti-navigering i fordon är struken.

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
| `app` | Skärmen: sanningsremsan, kartan, svansytan, nödkortet |
| `babel` | Babel-Stoners satirläge |
| `verification/architecture` | Tester som håller isär sanning och trams |
| `prototyp/offline-kedja` | Etapp 2-prototypen, kvar som historik |

## Grundprincip: Illogical Engine

Illogical är själva grundprincipen, inte en humoristisk effekt ovanpå en vanlig app. Personligheten bygger på **Inverted Grammar + Paradox + Mundane Reality**:

**Observation → Paradox → Verklighet → Eventuell total urspårning**

En gemensam motor, Illogical Engine, används av navigatorn, translatorn, citatarkivet och Don't Panic. Den tar in verkliga händelser och frågor, egna citat och paradoxer, logiska motsägelser, Rikard-faktorn och slump, och ger ett absurt, situationsanpassat resultat. Fakta (rutt, platser, avstånd) passerar aldrig motorns kreativa del.

Läs hela specifikationen i [`docs/PRD.md`](docs/PRD.md).

## Funktioner

- **Cosmic Navigator (GIS):** karta med toaletter, rastplatser och munchie stations i tre lägen: Practical, Poetic och Improbability. **Anti-Navigation** ger korrekt rutt och korrekta visuella instruktioner, men en oberäknelig kommentator: *"Om 400 meter inträffar en korsning. Rikard har förnekat all kännedom om den."*
- **Rikard-faktorn:** källan till alla systemfel, felsvängar och korta glitch-effekter (300–500 ms).
- **Babel-Stoner:** en satirisk samtalsmotor där logiken aldrig får vinna, med mekanismerna ansvarsförskjutning, ämnesbyte, motsägelse, självbildsförsvar, cirkulär argumentation och omvänd skuld. Den har fem personligheter (Ansvarsförskjutaren, Den eviga cirkeln, Verklighetsrevisorn, Den sårade hjälten, Kosmisk totalförvirring), en galaktisk analys av varje svar och ett omvänt läge som pekar ut möjliga felslut, motsägelser och obesvarade frågor i ett verkligt samtal. *Babel-Stoner är satir, inte ett diagnostiskt verktyg.* Den ursprungliga slang-översättningen finns kvar som ett sekundärt läge.
- **"Don't Panic" Wisdom Generator:** en pulserande knapp med nonsensartade och märkligt djupa råd, byggda på det personliga citatarkivet.
- **Infinite Improbability Theme Engine (Probability Drive):** Material 3 med dynamisk färg och rörelse, och reglage för kaos, Rikard-frekvens, glitchnivå och anti-navigeringens sarkasmnivå.
- **Citatarkivet:** personliga livscitat och värdepar (frihet ↔ trygghet, kaos ↔ kontroll). Originalen bevaras ordagrant, och variationer lagras separat och märks som variationer.
- **Handdukssensorn och Vibe Protect** från tidigare briefer.

## Teknisk stack (planerad)

- **Kotlin**
- **Jetpack Compose + Material 3** (edge-to-edge, dynamisk färg, animationer)
- **Google Maps SDK for Android / maps-compose** (+ android-maps-utils för GeoJSON)
- **Kotlin Coroutines**
- **KSP + Moshi** (parsning av GeoJSON och citatarkiv)
- **Coil** (bilder)

Kartdata ligger först som GeoJSON i appen, bakom ett `PlacesRepository`-interface. PostGIS med ett API framför kommer senare utan att resten av appen behöver ändras.

## Repots struktur

```
.
├── README.md
├── docs/
│   ├── PRD.md                      # reviderad PRD: Illogical Engine som kärna
│   └── design/
│       ├── README.md
│       ├── screens/                # Stitch-skärmar (code.html + screen.png)
│       ├── design-systems/         # Cosmic Drift och The Sentient Archive (DESIGN.md)
│       └── original-briefs/        # de tre ursprungliga briefarna/PRD:erna från Stitch
└── data/
    ├── geojson/stockholms-lan/     # toaletter, rastplatser, munchie stations (OSM)
    └── quotes/                     # citatarkivets schema och fastställda original
```

## Data och upphov

Kartlagren i `data/geojson/` kommer från OpenStreetMap och är **© OpenStreetMap contributors**, tillgängliga under [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). Appen måste visa upphovet i kartan och på om-sidan. Detaljer och Overpass-frågor finns i [`data/geojson/stockholms-lan/README.md`](data/geojson/stockholms-lan/README.md).

Designen i `docs/design/` är exporterad från Google Stitch.

Citaten i `data/quotes/` är personligt material.

## Licens

Licensen är inte bestämd än. Tills en licens har valts gäller inga öppna rättigheter för koden eller citatarkivet. OSM-datan omfattas av ODbL oavsett.

## Ursprunglig beskrivning

Stoner's Guide to the Galaxy is a digital companion inspired by The Hitchhiker's Guide to the Galaxy. It is designed to be completely illogical yet absolutely indispensable, providing cosmic guidance for the modern wanderer with a vibrant, energetic Material 3 aesthetic. Features • "Don't Panic" Wisdom Generator: A central, pulsating
