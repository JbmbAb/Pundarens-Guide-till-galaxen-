# 03 – Systemarkitektur och modulkontrakt

Baslinje v0.1 · 2026-11-20 · Status för hela dokumentet: REVISE (kandidat, inte granskad)

## 1. Grundregel: dioden

Information går bara åt ett håll: **Hard Truth Core → typad händelsebuss → Illogical Engine**. Illogical Engine kan aldrig ändra rutt, manöver, koordinater, nödläge, positionsdelning eller säkerhetsbesked. Regeln upprätthålls av **Gradle-modulgränser och Kotlin-synlighet**, inte av instruktioner till en språkmodell. En granskare ska kunna kontrollera regeln på en minut genom att läsa beroendegrafen (byggplanen).

## 2. Moduler

```
                         ┌──────────────────────────────┐
                         │ :app  (kompositionsrot, UI)  │
                         └──┬───────┬────────┬───────┬──┘
                            │       │        │       │
              ┌─────────────▼─┐  ┌──▼─────┐ ┌▼─────┐ ┌▼───────────────┐
              │ :truth:core   │  │:safety │ │:voice│ │ :convoy:wss    │
              │ (Android)     │  │ gate   │ │      │ │ (transport)    │
              └──┬────────────┘  └─┬──┬───┘ └─┬────┘ └──┬─────────────┘
                 │                 │  │       │         │
                 │       ┌─────────┘  │       │         │
                 ▼       ▼            ▼       ▼         ▼
              ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
              │ :truth:api   │◄──│ :engine:api  │   │ :convoy:api  │
              │ (ren Kotlin) │   │ (ren Kotlin) │   │ (ren Kotlin) │
              └──────────────┘   └──────▲───────┘   └──────────────┘
                     ▲                  │
                     │           ┌──────┴───────┐    ┌──────────────┐
                     └───────────│ :engine      │───►│ :archive:api │
                                 │ (ren Kotlin) │    └──────▲───────┘
                                 └──────────────┘           │
                                 ┌──────────────┐    ┌──────┴───────┐
                                 │ :babel       │───►│ :archive     │
                                 │ :llm:api     │    │ (SQLite)     │
                                 └──────────────┘    └──────────────┘
```

Pilen betyder "beror på vid kompilering".

| Modul | Typ | Ansvar | Får bero på | Får **inte** bero på |
|---|---|---|---|---|
| `:truth:api` | Ren Kotlin (JVM) | Oföränderliga sanningstyper och händelser (`TruthEvent`). Läsgränssnittet `TruthEventStream`. | Kotlin stdlib, kotlinx-coroutines-core | Allt annat |
| `:truth:core` | Android-bibliotek | Position, offline-paket, valhalla-mobile, Ferrostar, ruttstate, nödkort. Publicerar händelser. | `:truth:api`, MapLibre, valhalla-mobile, Ferrostar | `:engine`, `:engine:api`, `:babel`, `:archive`, `:safetygate`, `:llm:api` |
| `:engine:api` | Ren Kotlin | Typen `TailCandidate` och gränssnittet `IllogicalEngine` | `:truth:api` (bara läsning av händelsetyper) | `:truth:core` och allt med Android |
| `:engine` | Ren Kotlin | Illogical Engine: radbank, drag, Rikardgenerator, motsägelseminne | `:engine:api`, `:truth:api`, `:archive:api` | `:truth:core`, `:safetygate`, `:voice`, `:convoy:*`, Android, nätverk |
| `:archive:api` | Ren Kotlin | Läsgränssnitt för arkivet, ursprungsklasser, synlighet | – | – |
| `:archive` | Android-bibliotek | SQLite med skrivskydd via triggers (04) | `:archive:api` | `:truth:*`, `:engine` |
| `:safetygate` | Ren Kotlin | Deterministisk presentationsregel (06) | `:truth:api`, `:engine:api` | `:truth:core`, `:engine`, Android |
| `:voice` | Android-bibliotek | Talkö med prioritet, avbrott och ljudfokus | `:truth:api`, `:engine:api` | `:engine`, `:truth:core` |
| `:convoy:api` | Ren Kotlin | Gränssnittet `ConvoyTransport` och konvojtyper (07) | `:truth:api` | Karta, `:engine` |
| `:convoy:wss` | Android-bibliotek | Självhostad HTTPS/WebSocket-transport | `:convoy:api` | `:engine`, `:truth:core` |
| `:babel` | Ren Kotlin | Babel-Stoner: Satirläge och Analysläge | `:archive:api`, `:llm:api` | `:truth:*`, `:convoy:*`, `:safetygate` |
| `:llm:api` | Ren Kotlin | Gränssnitt mot lokal språkmodell eller LAN-tjänst | – | Nätverksbibliotek. Implementationen ligger i `:app` och är avstängd som standard. |
| `:app` | Android-app | Kompositionsrot, Compose-UI, sanningsremsa, kartstilar | Allt ovan | – |

## 3. Hur gränserna upprätthålls

| Nr | Mekanism | Vad den hindrar | Status |
|---|---|---|---|
| G-1 | `:engine` och `:babel` har inget kompileringsberoende på `:truth:core`. De kan inte ens namnge klasserna som ändrar rutt eller nödläge. | Direkta anrop från tramslagret till sanningslagret | REVISE (design) |
| G-2 | Allt muterande i `:truth:core` är `internal`. Det publika API:t är bara `TruthEventStream` (läsning) och `TruthStateReader` (läsning), plus kommandon som bara `:app` får använda: välj mål, starta och avbryt navigering. | Att någon utanför modulen skriver sanningsdata | REVISE |
| G-3 | `TruthEventStream` exponerar bara `Flow<TruthEvent>`. Publiceringen (`MutableSharedFlow`) är `internal` i `:truth:core`. | Att tramslagret injicerar falska händelser | REVISE |
| G-4 | `TailCandidate` innehåller bara text, id, ursprungsklass, synlighet, prioritetsklass och livslängd. Den har inga fält av sanningstyp och inga callbacks. | Att en svans bär med sig ett ändrat besked | REVISE |
| G-5 | `:truth:core` har inget publikt API som tar emot `String`, `TailCandidate` eller något från `:engine:api`. | Att `:app` av misstag kopplar svansar in i sanningen | REVISE |
| G-6 | Automatiskt test av beroendegrafen i CI. Kandidater: Gradle-plugin [`com.jraska.module.graph.assertion`](https://github.com/jraska/modules-graph-assert) eller ett eget Gradle-task som läser `configurations.*.dependencies`. Bygget fallerar om en förbjuden kant finns. | Att en ny utvecklare lägger till ett förbjudet beroende | NOT_VERIFIED (verktyget är inte provat) |
| G-7 | Arkitekturtest med [Konsist](https://docs.konsist.lemonappdev.com/) eller motsvarande: inga publika `var` i `:truth:api`, inga sanningstyper i `:engine:api`s utdata, inga nätverksimporter i `:engine` och `:babel`. | Glidning över tid | NOT_VERIFIED |
| G-8 | `:engine` och `:babel` är rena JVM-moduler utan `INTERNET`-behörighet och utan nätverksbibliotek. Behörigheten finns bara i `:app`-manifestet och används av `:convoy:wss` och en uttrycklig LAN-adapter för `:llm:api`. | Att tramslagret läcker data eller hämtar text i manövervägen | REVISE |
| G-9 | SafetyGate läser sanningstillstånd men har ingen skrivväg tillbaka. Den returnerar bara `PresentationDecision`. | Att SafetyGate skriver om sanningsdata | REVISE |
| G-10 | Oberoende granskning av G-1–G-9 i varje fas, med den stående frågan: *kan tramslagret på något sätt påverka ett besked?* | Mänskliga misstag som verktygen inte ser | NOT_VERIFIED (granskare saknas, M-14) |

Kvarstående risk: `:app` ser alla moduler och kan i teorin skriva kod som tolkar en svans och agerar på den. G-5 gör det omöjligt att mata in en svans i sanningskärnan, men inte omöjligt att visa en svans som om den vore ett besked. Det fångas av SafetyGate (svansar renderas bara i svansytan, se 06) och av granskning.

## 4. Kontrakt

Typerna nedan är kontraktsskisser. De blir kod först i etapp 2 och 4.

### 4.1 `:truth:api`

```kotlin
// Alla typer är oföränderliga. Inga publika konstruktörer behövs utanför :truth:core,
// men även om tramslagret skapar en egen instans finns ingen väg att skicka in den.
interface TruthEventStream {
    val events: Flow<TruthEvent>          // se 05-handelsekontrakt.md
}

interface TruthStateReader {
    val navigation: StateFlow<NavigationSnapshot>
    val position: StateFlow<PositionSnapshot>
    val safety: StateFlow<SafetySnapshot>   // nödläge, batteri, konvojkontakt
}
```

### 4.2 `:engine:api`

```kotlin
interface IllogicalEngine {
    /** Ren funktion av händelsen, motorns eget minne och en seed. Ingen I/O. */
    fun onEvent(event: TruthEvent, context: EngineContext): List<TailCandidate>
}

data class TailCandidate(
    val id: String,                   // stabilt, t.ex. "TAIL-<radId>-<seq>"
    val text: String,
    val lineId: String,               // radbankens id
    val origin: OriginClass,          // 04-datamodell
    val visibility: Visibility,
    val kind: TailKind,               // SPOKEN, HUD_TEXT, ANIMATION
    val estimatedSpeechMillis: Int,
    val triggeredBy: String,          // eventId från bussen
    val validUntilMonotonicMillis: Long,
    val contradicts: String? = null,  // id på egen tidigare svans som förnekas
)
```

### 4.3 `:safetygate`

```kotlin
fun decide(state: SafetySnapshot, nav: NavigationSnapshot, candidate: TailCandidate?): PresentationDecision

sealed interface PresentationDecision {
    data class Present(val candidate: TailCandidate, val maxMillis: Int) : PresentationDecision
    data class Suppress(val reason: SuppressReason) : PresentationDecision
    data class Interrupt(val reason: SuppressReason) : PresentationDecision  // avbryt pågående svans
}
```

### 4.4 `:voice`

Två kanaler med strikt prioritet. **Kanal B (besked)** ägs av Hard Truth Core via Ferrostars talinstruktioner och kan inte blockeras av något. **Kanal S (svans)** får bara spela det SafetyGate returnerar som `Present`, och avbryts direkt när kanal B behöver ljud.

### 4.5 `:convoy:api`

Se 07. `ConvoyTransport` vet inget om kartan och inget om Illogical Engine.

### 4.6 `:llm:api`

```kotlin
interface LocalLanguageModel {
    val location: ModelLocation       // ON_DEVICE eller LAN(explicitHost)
    suspend fun interpret(request: AnalysisRequest): ModelInterpretation
}
```

Ingen implementation som pratar med ett externt moln finns i standardbygget. En LAN-adress måste anges uttryckligen av användaren.

## 5. Teknikval (kandidater, låses i G2–G4)

| Behov | Kandidat | Status |
|---|---|---|
| Kartvisning | MapLibre Native Android med lokal PMTiles | NOT_VERIFIED på måltelefon (G3) |
| Ruttning | valhalla-mobile, Valhalla-rutor på enheten | NOT_VERIFIED (G2) |
| Navigationslogik | Ferrostar (core, ui-maplibre, ui-compose) | NOT_VERIFIED ihop med valhalla-mobile (G2) |
| Plats | Ferrostars `AndroidSystemLocationProvider` (AOSP `LocationManager`), inte Fused Location | NOT_VERIFIED |
| Röst | Androids `TextToSpeech`-API via Ferrostars `AndroidTtsObserver`. Motorn är öppen (F-06). | NOT_VERIFIED (G4) |
| Arkivdatabas | SQLite via Room eller SQLDelight. Triggers krävs. | NOT_VERIFIED (G0) |
| Kryptering, Convoy | Granskat standardbibliotek. Förstahandsval libsodium (via t.ex. Lazysodium för Android). Tink är ett Google-projekt och väljs bort med hänvisning till S-20, om inte Jimmy säger annat. TLS via plattformens standardstack. | NOT_VERIFIED (G7) |
| Verkstad hemma | PostGIS, ogr2ogr, Planetiler eller tippecanoe för PMTiles, valhalla_build_tiles | NOT_VERIFIED |

Detaljer och källor: [08-teknikverifieringsplan.md](08-teknikverifieringsplan.md).

## 6. Verkstad och telefon

```
Hemdatorn (verkstad)                           Telefonen
┌──────────────────────────────┐               ┌───────────────────────────────┐
│ PostGIS: OSM, Lantmäteriet,  │  bygger       │ Offline-paket (04):           │
│ NVDB, egna platser           │ ────────────► │  karta.pmtiles                │
│ valhalla_build_tiles         │  (fil, USB    │  valhalla-rutor               │
│ PMTiles-bygge                │   eller LAN)  │  platser.geojson / sqlite     │
│ Manifest + kontrollsummor    │               │  manifest.json                │
└──────────────────────────────┘               └───────────────────────────────┘
        Telefonen kontaktar aldrig PostGIS för att navigera.
```
