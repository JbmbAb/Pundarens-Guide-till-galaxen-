# 04 – Datamodell: arkiv, expeditioner och offline-paket

Baslinje v0.1 · 2026-11-20 · Status: REVISE (kandidat). Inga befintliga datafiler ändras i v0.1. Mappningen nedan tillämpas när arkivet importeras till databasen i etapp 2.

## 1. Arkivet

### 1.1 Fyra ursprungsklasser (startprompten §5)

| Klass | Betydelse | Skrivbar | Får användas i appen |
|---|---|---|---|
| `ORIGINAL_VERBATIM` | Ordagrant original | **Nej**, skyddas av triggers | Ja, ordagrant, med synlighetsregler |
| `APPROVED_EDIT` | Bearbetning som uttryckligen godkänts | Bara genom ny revision | Ja, märkt som bearbetning |
| `DERIVED_RULE` | Härledd regel, inklusive BS-01–BS-10 | Bara genom ny revision | Ja om `approval = APPROVED` |
| `SYNTHETIC_SATIRE` | Nyskriven eller maskingenererad text | Ja | Bara om `approval = APPROVED` |

### 1.2 Verifieringsstatus (separat fält)

Startprompten kräver att verifierat original, återgiven minnesuppgift, godkänd bearbetning och syntetiskt material hålls isär, och att overifierat ursprung anges. Ursprungsklassen säger *vad* texten är. Verifieringsstatusen säger *hur säkert* vi vet det.

| `verification` | Betydelse |
|---|---|
| `VERIFIED_SOURCE` | Kontrollerad mot råkälla (export, dokument eller inspelning) med angiven referens |
| `OWNER_CONFIRMED` | Jimmy har uttryckligen bekräftat formuleringen, med datum |
| `DOCUMENTED_UNVERIFIED` | Finns i en daterad arkivsammanställning men är inte kontrollerad mot råkällan |
| `RECALLED` | Återgiven minnesuppgift eller hämtad ur tidigare chattindex |
| `UNKNOWN` | Ursprunget kan inte fastställas |

Ingen post får i dag `VERIFIED_SOURCE`, eftersom råkällorna saknas (M-04, M-05, M-06).

### 1.3 Mappning från dagens proveniensvärden

| Nuvarande värde (repot) | Poster | Ny klass | Verifiering | Godkännande | Anmärkning |
|---|---|---|---|---|---|
| `original` | P-001–P-007 | `ORIGINAL_VERBATIM` | `RECALLED` | – | quotes README: "återgivna från tidigare chattindex snarare än kontrollerade mot en råexport" |
| `original` | C-001, C-002a, C-002b, C-003, C-004, C-005, C-006, N-002 | `ORIGINAL_VERBATIM` | `DOCUMENTED_UNVERIFIED` | – | Registrerade i arkivdokumentet (M-04) |
| `original` | C-007 | `ORIGINAL_VERBATIM` | `OWNER_CONFIRMED` (2026-11-18) | – | Uttryckligen sparad av Jimmy. `legacyId` `orig-0001`. |
| `original` | R-001–R-003 | `ORIGINAL_VERBATIM` | `DOCUMENTED_UNVERIFIED` | – | Ur bokprojektets arkivunderlag (M-05) |
| (inget fält) | Lexikon "Garderobsknullis" | `ORIGINAL_VERBATIM` | `DOCUMENTED_UNVERIFIED` | – | "Senaste uttryckligen fastställda definitionen" 2026-11-20 |
| `normalized` i P-001 | P-001 normaliserad | `SYNTHETIC_SATIRE` (undertyp `normalisering`) | `UNKNOWN` | `PENDING` | Inte original. Ifylld av Grok Bot ur arkivets ledtråd. |
| `bearbetad` | C-008, C-009 | `APPROVED_EDIT` | `DOCUMENTED_UNVERIFIED` | `APPROVED` enligt sammanställningen | Vem som godkände och när ska bekräftas av Jimmy (F-09) |
| `redaktionell` | RED-P-007 | `SYNTHETIC_SATIRE` (undertyp `redaktionell_sammanfattning`) | `UNKNOWN` | `PENDING` | Maskinskriven sammanfattning. Blir `APPROVED_EDIT` bara om Jimmy godkänner den. |
| (inget fält) | BS-01–BS-10 | `DERIVED_RULE` | `DOCUMENTED_UNVERIFIED` | `APPROVED` | Ur sammanställningen |
| `förslag` | BS-11–BS-16 | `DERIVED_RULE` | – | `PENDING` | Grok Bot. Används inte i Satirläget. |
| `hypotes` | PAT-01–PAT-05 | `DERIVED_RULE` (undertyp `mönsterhypotes`) | `UNKNOWN` | `PENDING` | Inte fastställda psykologiska fakta. Visas inte i appen före granskning (K-15). |
| `konstruerad` | EX-01, EX-03, EX-05, EX-ILLOGICAL-01 | `SYNTHETIC_SATIRE` | – | `PENDING` | Ur sammanställningens Del II och III |
| `konstruerad` | EX-01-FORMULERING | `SYNTHETIC_SATIRE` (flagga `återger_verklig_kommunikation`) | `UNKNOWN` | `BLOCKED` | Får inte visas förrän Jimmy granskat (K-15) |
| `konstruerad` | EX-F-01–EX-F-07, NAV-001–NAV-020 | `SYNTHETIC_SATIRE` | – | `PENDING` | Grok Bot |
| `variation` | V-001–V-020 | `SYNTHETIC_SATIRE` | – | `PENDING` | Grok Bot. `approved: false` i dag. |
| `förslag` | 12 lexikonförslag | `SYNTHETIC_SATIRE` | – | `PENDING` | Grok Bot. "vogonko" bryter mot regeln om lånade namn (K-11). |

`approval`: `APPROVED`, `PENDING`, `REJECTED`, `BLOCKED`. Bara Jimmy kan sätta `APPROVED`.

### 1.4 Synlighet

| `visibility` | Betydelse | Standard för |
|---|---|---|
| `PRIVATE` | Visas och läses upp bara för ägaren, och bara när ingen konvoj är aktiv och ljudet inte går till en delad högtalare (se 06, regel R-12) | **Alla poster vid import.** Särskilt relationscitat (C-004, C-006–C-009, P-006, R-*, Garderobsknullis) |
| `COMPANY` | Får läsas upp i sällskap eller konvoj | Bara efter Jimmys uttryckliga val per post |
| `OPEN` | Får visas för vem som helst | Bara efter Jimmys uttryckliga val per post |

Synlighet ärvs aldrig uppåt. En variation av ett `PRIVATE`-original är också `PRIVATE`.

### 1.5 Tabeller (SQLite)

```sql
CREATE TABLE archive_entry (
  id              TEXT PRIMARY KEY,          -- stabilt, t.ex. 'P-001', 'BS-03', 'V-012'
  origin_class    TEXT NOT NULL CHECK (origin_class IN
                    ('ORIGINAL_VERBATIM','APPROVED_EDIT','DERIVED_RULE','SYNTHETIC_SATIRE')),
  subtype         TEXT,                      -- 'normalisering', 'mönsterhypotes' m.fl.
  kind            TEXT NOT NULL,             -- 'quote','rule','lexicon','example','nav_line','tail_line'
  text            TEXT NOT NULL,
  text_sha256     TEXT NOT NULL,             -- räknas vid import, kontrolleras vid varje start
  language        TEXT NOT NULL DEFAULT 'sv',
  verification    TEXT NOT NULL CHECK (verification IN
                    ('VERIFIED_SOURCE','OWNER_CONFIRMED','DOCUMENTED_UNVERIFIED','RECALLED','UNKNOWN')),
  approval        TEXT NOT NULL CHECK (approval IN ('APPROVED','PENDING','REJECTED','BLOCKED')),
  visibility      TEXT NOT NULL DEFAULT 'PRIVATE' CHECK (visibility IN ('PRIVATE','COMPANY','OPEN')),
  author          TEXT NOT NULL,             -- 'Jimmy Bruce', 'Grok Bot (förslag)', 'okänd'
  source_ref      TEXT,                      -- t.ex. 'docs/baseline/source/Citatarkiv…txt#P-001'
  source_note     TEXT,
  date_known      TEXT,                      -- ISO-datum om känt, annars NULL
  derived_from    TEXT REFERENCES archive_entry(id),
  legacy_id       TEXT,
  imported_at     TEXT NOT NULL,
  revision        INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE archive_revision (              -- endast tillägg
  entry_id     TEXT NOT NULL REFERENCES archive_entry(id),
  revision     INTEGER NOT NULL,
  changed_at   TEXT NOT NULL,
  changed_by   TEXT NOT NULL,
  field        TEXT NOT NULL,
  old_value    TEXT,
  new_value    TEXT,
  reason       TEXT NOT NULL,
  PRIMARY KEY (entry_id, revision, field)
);
```

### 1.6 Skrivskydd på databasnivå

```sql
-- 1. Ett original får aldrig ändras i text, klass, författare eller källa.
CREATE TRIGGER original_immutable
BEFORE UPDATE ON archive_entry
WHEN OLD.origin_class = 'ORIGINAL_VERBATIM'
 AND (NEW.text IS NOT OLD.text OR NEW.text_sha256 IS NOT OLD.text_sha256
      OR NEW.origin_class IS NOT OLD.origin_class OR NEW.author IS NOT OLD.author
      OR NEW.source_ref IS NOT OLD.source_ref OR NEW.id IS NOT OLD.id)
BEGIN
  SELECT RAISE(ABORT, 'ORIGINAL_VERBATIM är skrivskyddad');
END;

-- 2. Ett original får aldrig raderas.
CREATE TRIGGER original_undeletable
BEFORE DELETE ON archive_entry
WHEN OLD.origin_class = 'ORIGINAL_VERBATIM'
BEGIN
  SELECT RAISE(ABORT, 'ORIGINAL_VERBATIM kan inte raderas');
END;

-- 3. Ingen post får uppgraderas till original efter import.
CREATE TRIGGER no_promotion_to_original
BEFORE UPDATE OF origin_class ON archive_entry
WHEN NEW.origin_class = 'ORIGINAL_VERBATIM' AND OLD.origin_class <> 'ORIGINAL_VERBATIM'
BEGIN
  SELECT RAISE(ABORT, 'Endast import från källa kan skapa ORIGINAL_VERBATIM');
END;

-- 4. Revisionshistoriken är bara tillägg.
CREATE TRIGGER revision_append_only_u BEFORE UPDATE ON archive_revision
BEGIN SELECT RAISE(ABORT, 'archive_revision är bara tillägg'); END;
CREATE TRIGGER revision_append_only_d BEFORE DELETE ON archive_revision
BEGIN SELECT RAISE(ABORT, 'archive_revision är bara tillägg'); END;

-- 5. Varje tillåten ändring loggas automatiskt.
CREATE TRIGGER log_visibility_change
AFTER UPDATE OF visibility ON archive_entry
BEGIN
  INSERT INTO archive_revision VALUES
    (NEW.id, OLD.revision + 1, strftime('%Y-%m-%dT%H:%M:%fZ','now'), 'app', 'visibility',
     OLD.visibility, NEW.visibility, 'användarval');
  UPDATE archive_entry SET revision = OLD.revision + 1 WHERE id = NEW.id;
END;
```

Om källan till ett original ändras (startprompten: originalet ändras bara om källan ändras) skapas en **ny** post med nytt id-suffix (t.ex. `P-001.r2`) och `derived_from` som pekar på den gamla. Den gamla ligger kvar.

Röktest 2026-11-20: SQL-koden i det här dokumentet kördes i Pythons sqlite3 (SQLite 3.46.1) på arbetsdatorn. Update och delete av ett original, uppgradering till original samt update och delete i `archive_revision` stoppades, och synlighetsändringen loggades. Det är **inte** ett test på Android och inte ett test av Room eller SQLDelight, så A-13 är fortfarande NOT_VERIFIED.

Kända begränsningar, som ska stå i verifieringsprotokollet:

- Triggers skyddar mot oavsiktlig överskrivning i appen, inte mot någon som har rot på telefonen eller byter databasfil. Kontrollsumman `text_sha256` kontrolleras vid varje start mot importfilen, och avvikelse blockerar arkivet.
- Triggers måste överleva databasmigreringar. Ett migrationstest ska bevisa det (G0).
- Room skapar inte triggers själv. De läggs i en `RoomDatabase.Callback` eller i en migration. Alternativt SQLDelight, där triggers skrivs direkt i `.sq`-filen. NOT_VERIFIED.

### 1.7 Radbanken (Illogical Engine)

Radbanken är arkivposter av `kind = 'tail_line'` med extra fält:

| Fält | Beskrivning |
|---|---|
| `event_types` | Vilka händelsetyper raden passar (05) |
| `mechanism` | M1–M8 (02-PRD) |
| `slots` | Luckor för exakta värden, t.ex. `{distance_m}`, `{eta_min}` |
| `max_speech_ms` | Uppskattad taltid |
| `forbidden_words_check` | Resultat av kontrollen att raden inte innehåller riktningsord som kan förväxlas med ett besked |
| `contradiction_of` | Id på rad som denna förnekar |

### 1.8 Motsägelseminne

```sql
CREATE TABLE engine_utterance (
  utterance_id   TEXT PRIMARY KEY,
  expedition_id  TEXT,
  line_id        TEXT NOT NULL REFERENCES archive_entry(id),
  rendered_text  TEXT NOT NULL,
  event_id       TEXT NOT NULL,
  presented      INTEGER NOT NULL,     -- 0 om SafetyGate stoppade den
  presented_at   TEXT,
  denies         TEXT REFERENCES engine_utterance(utterance_id),
  reinterprets   TEXT REFERENCES engine_utterance(utterance_id)
);
```

Motorn får bara förneka eller omtolka yttranden som faktiskt presenterades (`presented = 1`). Det gör kontinuiteten spårbar.

## 2. Expeditioner

```sql
CREATE TABLE expedition (
  id               TEXT PRIMARY KEY,
  title            TEXT,
  started_at       TEXT NOT NULL,
  ended_at         TEXT,
  travel_mode      TEXT NOT NULL CHECK (travel_mode IN ('FOOT','PASSENGER','CAR','BICYCLE','SNOWMOBILE','ATV')),
  package_id       TEXT NOT NULL,
  package_version  TEXT NOT NULL,
  home_track_id    TEXT,               -- sparad hemväg
  convoy_session   TEXT                -- NULL om ingen konvoj
);

CREATE TABLE track_point (
  expedition_id  TEXT NOT NULL REFERENCES expedition(id),
  seq            INTEGER NOT NULL,
  lat            REAL NOT NULL,         -- WGS84 (EPSG:4326)
  lon            REAL NOT NULL,
  accuracy_m     REAL,
  altitude_m     REAL,
  fix_time_utc   TEXT NOT NULL,         -- GNSS-tid
  elapsed_rt_ns  INTEGER NOT NULL,      -- Androids monotona klocka
  PRIMARY KEY (expedition_id, seq)
);

CREATE TABLE expedition_log (           -- Expeditionsprotokollet: bara tillägg
  expedition_id  TEXT NOT NULL,
  seq            INTEGER NOT NULL,
  at_utc         TEXT NOT NULL,
  layer          TEXT NOT NULL CHECK (layer IN ('TRUTH','SATIRE')),
  text           TEXT NOT NULL,
  source_ref     TEXT,
  PRIMARY KEY (expedition_id, seq)
);

CREATE TABLE convoy_consent (            -- 07
  session_id     TEXT NOT NULL,
  participant_id TEXT NOT NULL,
  granted_at     TEXT NOT NULL,
  expires_at     TEXT NOT NULL,
  revoked_at     TEXT,
  PRIMARY KEY (session_id, participant_id, granted_at)
);
```

Fältet `layer` gör att protokollet alltid skiljer fakta från satir. Ett `TRUTH`-inlägg skrivs bara av Hard Truth Core.

## 3. Offline-paket

### 3.1 Innehåll

Ett paket är en katalog med en manifestfil. Karta, ruttdata och platser hör ihop versionsmässigt och installeras som en enhet.

```
pundaren-pkg-<omrade>-<version>/
├── manifest.json
├── map/karta.pmtiles             # vektorrutor, PMTiles v3
├── map/styles/praktisk.json      # MapLibre-stilar som pekar på pmtiles://
├── map/styles/poetisk.json
├── map/styles/osannolik.json
├── routing/valhalla_tiles.tar    # eller katalog, enligt valhalla-mobile (G2)
├── routing/valhalla.json         # konfiguration
├── places/places.sqlite          # eller GeoJSON
└── LICENSES/                     # licenstexter och upphovsrader
```

### 3.2 Manifest (utkast)

```json
{
  "schema": "pundaren.offline-package/0.1",
  "packageId": "se-ab-test",
  "version": "2026.11.20-1",
  "createdAt": "2026-11-20T18:00:00Z",
  "coverage": {
    "name": "Testområde (ej fastställt, M-11)",
    "bbox": [0, 0, 0, 0],
    "polygonFile": "coverage.geojson"
  },
  "crs": {
    "storage": "EPSG:4326",
    "sourceDataCrs": ["EPSG:3006", "EPSG:4326"],
    "note": "Lantmäteriets data räknas om från SWEREF 99 TM i verkstaden"
  },
  "components": [
    {"role": "map",     "path": "map/karta.pmtiles",         "format": "pmtiles-v3",     "sha256": "…", "bytes": 0},
    {"role": "routing", "path": "routing/valhalla_tiles.tar", "format": "valhalla-tiles", "valhallaVersion": "…", "sha256": "…", "bytes": 0},
    {"role": "places",  "path": "places/places.sqlite",      "format": "sqlite",         "sha256": "…", "bytes": 0}
  ],
  "sources": [
    {"name": "OpenStreetMap", "license": "ODbL-1.0", "attribution": "© OpenStreetMap contributors", "extractDate": "…"},
    {"name": "Lantmäteriet",  "license": "CC0-1.0 (kontrolleras i Geotorget, M-15)", "product": "…", "date": "…"}
  ],
  "compatibility": {
    "minAppVersion": "…",
    "valhallaMobile": ">=x.y,<x.z",
    "ferrostar": ">=…",
    "maplibreAndroid": ">=11.7.0",
    "routingTileFormat": "…"
  },
  "smokeTest": {
    "from": [0, 0], "to": [0, 0], "mode": "pedestrian",
    "expectRouteMetersBetween": [0, 0]
  }
}
```

### 3.3 Atomär installation och återställning

1. Paketet packas upp i `packages/.staging/<id>-<version>/`. Det aktiva paketet rörs inte.
2. Kontrollsummor och storlek kontrolleras för varje komponent. Avvikelse leder till att staging raderas.
3. Kompatibilitet kontrolleras mot appens, valhalla-mobiles, Ferrostars och MapLibres versioner.
4. Röktest: MapLibre öppnar `karta.pmtiles`, valhalla-mobile räknar rutten i `smokeTest`, platsdatabasen öppnas.
5. Katalogen flyttas till `packages/<id>-<version>/` med en atomär rename inom samma filsystem.
6. Pekarfilen `packages/current.json` skrivs som en temporär fil och byts med atomär rename.
7. Det föregående paketet sparas tills det nya har använts i en hel session utan fel. Därefter får det tas bort.
8. Vid start: om `current.json` pekar på ett paket som saknas eller inte klarar kontrollsumman, används föregående paket och användaren får veta det.
9. Navigering pågår aldrig under ett paketbyte. Bytet sker bara när ingen rutt är aktiv.

Lagringsplats: appens interna lagring (`filesDir`). MapLibre läser filer via `pmtiles://file://` (08, T-02).
