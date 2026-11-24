> **TILLÄGG 2026-11-22:** [Samlad projektkontext](SAMLAD-PROJEKTKONTEXT.md) samlar tilläggen från den 21–22 november 2026.

# Dokumentationsrevision – 2026-11-21

**Gällande källa:** [Gällande inriktning](GALLANDE-INRIKTNING.md)

## Granskade källor

- README.md.
- Tidigare docs/PRD.md.
- docs/design/README.md.
- Stitch-brieferna och designsystemen.
- data/quotes/README.md.
- Den faktiska Android-koden i
  prototyp/offline-kedja/.
- Projektchatten med den fastställda
  humor- och produktinriktningen.

## Ersatta beslut

### D-01: Projektet saknar Android-kod
**Status:** ERSATT.

Android-prototypen finns och byggs redan.
Gamla statusuppgifter om enbart planering
är inaktuella.

### D-02: Google Maps är karttekniken
**Status:** ERSATT.

Gällande teknik är MapLibre, Valhalla Mobile
och Ferrostar Core.

Google-tjänster är inte del av målarkitekturen.

### D-03: Anti-navigation undviker direkta order
**Status:** ERSATT FÖR VERKLIG NAVIGERING.

Sanningslagret måste alltid lämna korrekt
och begriplig instruktion i rätt tid.

Absurdismen är ett separat kommentatorslager.

### D-04: Mikropaus före navigeringsuppdatering
**Status:** ERSATT.

Ingen komisk eller lugnande paus får
försena en navigeringsinstruktion.

### D-05: Karta först, personlighet sedan
**Status:** ERSATT.

Illogical Engine, Don't Panic och citatarkivet
är centrum för produktupplevelsen.

Kartan är en viktig stödfunktion.

### D-06: Babel-Stoner som enbart slangöversättare
**Status:** ERSATT.

Samtalssatir och möjliga logiska mönster
är huvudfunktionen. Slangläget finns kvar.

### D-07: Gamla Stitch-skärmar är gällande kod
**Status:** ALDRIG BESLUTAT.

Stitch är designreferens, inte en verifierad
färdig Android-implementation.

### D-08: Framtida PostGIS/API som grundkrav
**Status:** UPPSKJUTET.

Offlineförmågan är överordnad.
Eventuell fjärrtjänst får vara frivillig
och måste ha ett offlinealternativ.

### D-09: Ursprungliga citat får förbättras
**Status:** FÖRBJUDET.

Original är oföränderliga.
Bearbetningar och variationer separeras.

### D-10: Rikard är endast en felsymbol
**Status:** UTVIDGAT.

Rikard-faktorn bygger också på en dokumenterad
familjeanknuten berättartradition enligt
upphovspersonens återgivning.

Den fiktiva Rikard-figuren har inget krav på
verklig skuld eller närvaro.

### D-11: Produktens huvudsakliga känsla
**Status:** FASTSTÄLLT.

Appen ska göra användaren glad.
Den ska vara en rolig följeslagare som råkar
vara praktiskt användbar.

### D-12: Känslig berättelsekontext
**Status:** FASTSTÄLLT.

Privata personuppgifter, handlingar och
familjeutredningar publiceras inte som
produktdokumentation.

Mönstren kan användas som inspiration.
Originalcitat hanteras med proveniens.

## Vad som bevaras

- Historiska PRD:er som referens.
- Stitch-skärmar och designsystem.
- Existerande citatarkiv.
- Befintlig offlinekart- och ruttningskod.
- Sanningslager och säkerhetskontroller.
- OSM-attribution och licenskrav.

## Beslutshierarki

Se avsnitt 13 i GALLANDE-INRIKTNING.md.

Historiska dokument bevaras men kan
inte upphäva nyare styrande beslut.
