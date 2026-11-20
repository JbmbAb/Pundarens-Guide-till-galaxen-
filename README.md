# 🌀 Pundarens Guide till galaxen

*Pundarens vän till galaxen · Stoner's Friend to the Galaxy*

En ologisk men oumbärlig digital följeslagare för Android, inspirerad av *Liftarens guide till galaxen*. Den är en karta, en filosof och en guide till nattmat i ett.

> Appen ska inte vara en förnuftig assistent som försöker vara rolig. Den ska vara en absurd, inkonsekvent, självmedvetet inkompetent galaktisk följeslagare som av misstag råkar vara användbar.

**Status:** milstolpe 1. Android-appen byggs med `./gradlew assembleDebug` och enhetstesterna körs med `./gradlew test`.

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

## Teknisk stack

- **Kotlin**
- **Jetpack Compose + Material 3** (edge-to-edge, dynamisk färg, animationer)
- **Google Maps SDK for Android / maps-compose** (+ android-maps-utils för GeoJSON)
- **Kotlin Coroutines**
- **KSP + Moshi** (parsning av GeoJSON och citatarkiv)

Coil, Room och Retrofit är inte med i milstolpe 1.

Kartdata ligger som GeoJSON i repot och paketeras in i APK:n av Gradle, bakom `PlacesRepository` / `BundledGeoJsonPlacesRepository`. PostGIS med ett API framför kan ersätta implementationen senare utan att resten av appen behöver ändras.

## Bygga och köra

Kräver JDK 21 och Android SDK med `compileSdk` 37.1. `local.properties` är gitignorerad och ska inte innehålla en incheckad nyckel.

```
sdk.dir=/sökväg/till/android-sdk
MAPS_API_KEY=din_nyckel
```

Nyckeln läses i `app/build.gradle.kts` och injiceras som `manifestPlaceholders` (`MAPS_API_KEY`) i `AndroidManifest.xml`. Saknas nyckeln används platshållaren i `local.defaults.properties`, bygget går igenom, och kartskärmen säger att nyckeln saknas. Lägg ingen riktig nyckel i git.

```
./gradlew assembleDebug
./gradlew test
```

Debug-APK: `app/build/outputs/apk/debug/app-debug.apk`.

All synlig text i appen är på svenska. Originalcitat visas ordagrant. Text som motorn skriver märks som konstruerad, variation eller förslag och presenteras inte som användarens eget citat. JSON-parsern ignorerar okända fält och accepterar proveniens `original`, `bearbetad`, `redaktionell`, `konstruerad`, `variation` och `förslag`.

## Milstolpe 1

- Gradle-projekt med Kotlin, Jetpack Compose, Material 3, versionskatalog, KSP och Moshi. Ren Kotlin-modul `:engine` och Android-modul `:app`.
- Mörkt tema från Cosmic Drift och The Sentient Archive: lila och turkos, Manrope för nytta, Space Grotesk för visdom.
- Bottennavigering, och sidomeny på bred skärm: Hem, Karta, Babel, Arkiv, Drift.
- Karta med Praktisk, Poetisk och Osannolik, glitch i osannolikhetsläget, och upphovet © OpenStreetMap contributors.
- Anti-navigering: pil, avstånd och gata kommer från fakta. Bara kommentaren är opålitlig, inklusive Rikard.
- Babel-Stoner med de fem lägena, galaktisk analys och omvänd läsning som flaggar felslut utan diagnos eller motiv.
- Citat, lexikon, regler, exempel och navigationskommentarer läses från `data/` via Gradle, utan att filerna dupliceras i källträdet.

## Kvar till milstolpe 2

- Riktig ruttmotor, tyst fönster före sväng och dämpad glitch i hög fart.
- `RemotePlacesRepository` mot PostGIS via API, bbox-frågor och tätare rastplatser.
- Klustring av kartpunkter, Room-cache och Retrofit.
- Röst, slangläget, handdukssensorn, Vibe Protect och ett tema som byter färg med driften.
- Godkännandeflöde för variationer. Ogiltiga eller inte godkända förslag visas i arkivet men används inte som visdomssändning.

## Repots struktur

```
.
├── README.md
├── settings.gradle.kts
├── app/                            # Jetpack Compose
├── engine/                         # ren Kotlin: Illogical Engine, citat, GeoJSON
├── docs/
│   ├── PRD.md
│   └── design/
└── data/
    ├── geojson/stockholms-lan/
    ├── quotes/
    ├── babel-stoner/
    └── lexicon/
```

## Data och upphov

Kartlagren i `data/geojson/` kommer från OpenStreetMap och är **© OpenStreetMap contributors**, tillgängliga under [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/). Appen måste visa upphovet i kartan och på om-sidan. Detaljer och Overpass-frågor finns i [`data/geojson/stockholms-lan/README.md`](data/geojson/stockholms-lan/README.md).

Designen i `docs/design/` är exporterad från Google Stitch.

Citaten i `data/quotes/` är personligt material.

## Licens

Licensen är inte bestämd än. Tills en licens har valts gäller inga öppna rättigheter för koden eller citatarkivet. OSM-datan omfattas av ODbL oavsett.

## Ursprunglig beskrivning

Stoner's Guide to the Galaxy is a digital companion inspired by The Hitchhiker's Guide to the Galaxy. It is designed to be completely illogical yet absolutely indispensable, providing cosmic guidance for the modern wanderer with a vibrant, energetic Material 3 aesthetic. Features • "Don't Panic" Wisdom Generator: A central, pulsating
