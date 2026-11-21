# Dokumentationsrevision – 2026-11-21

**Gällande källa:** [Gällande inriktning](GALLANDE-INRIKTNING.md)

## Granskade källor

- `README.md`.
- Tidigare `docs/PRD.md`, nu [`archive/PRD-fore-2026-11-21.md`](archive/PRD-fore-2026-11-21.md).
- `docs/design/README.md`.
- Stitch-brieferna och designsystemen.
- `data/quotes/README.md`.
- Den faktiska Android-koden på grenen `etapp4/sanningskarna`.
- Projektchatten med den fastställda humor- och produktinriktningen.

## Ersatta beslut

### D-01: Projektet saknar Android-kod
**Status:** ERSATT.

Appen finns, byggs och kör på plattan (version `0.6-etapp6`). Gamla statusuppgifter om enbart planering är inaktuella. På `main` står den gamla uppgiften kvar tills grenen har sammanfogats.

### D-02: Google Maps är karttekniken
**Status:** ERSATT.

Gällande teknik är MapLibre, Valhalla Mobile och Ferrostar Core. Google-tjänster är inte del av målarkitekturen.

### D-03: Anti-navigation undviker direkta order
**Status:** ERSATT FÖR VERKLIG NAVIGERING.

Sanningslagret måste alltid lämna korrekt och begriplig instruktion i rätt tid. Absurdismen är ett separat kommentatorslager. Antinavigering genom uteslutning är tillåten som kommentar bredvid det raka beskedet (ägarbeslut kl. 19.41, baslinjens beslutslogg avsnitt 15).

### D-04: Mikropaus före navigeringsuppdatering
**Status:** ERSATT.

Ingen komisk eller lugnande paus får försena en navigeringsinstruktion.

### D-05: Karta först, personlighet sedan
**Status:** ERSATT.

Illogical Engine, Don't Panic och citatarkivet är centrum för produktupplevelsen. Kartan är en viktig stödfunktion.

### D-06: Babel-Stoner som enbart slangöversättare
**Status:** ERSATT.

Samtalssatir och möjliga logiska mönster är huvudfunktionen. Slangläget finns kvar.

### D-07: Gamla Stitch-skärmar är gällande kod
**Status:** ALDRIG BESLUTAT.

Stitch är designreferens, inte en verifierad färdig Android-implementation.

### D-08: Framtida PostGIS/API som grundkrav
**Status:** UPPSKJUTET.

Offlineförmågan är överordnad. Eventuell fjärrtjänst får vara frivillig och måste ha ett offlinealternativ.

### D-09: Ursprungliga citat får förbättras
**Status:** FÖRBJUDET.

Original är oföränderliga. Bearbetningar och variationer separeras.

### D-10: Rikard är endast en felsymbol
**Status:** UTVIDGAT.

Rikard-faktorn bygger också på en dokumenterad familjeanknuten berättartradition enligt upphovspersonens återgivning. Den fiktiva Rikard-figuren har inget krav på verklig skuld eller närvaro.

### D-11: Produktens huvudsakliga känsla
**Status:** FASTSTÄLLT.

Appen ska göra användaren glad. Den ska vara en rolig följeslagare som råkar vara praktiskt användbar.

### D-12: Känslig berättelsekontext
**Status:** FASTSTÄLLT.

Privata personuppgifter, handlingar och familjeutredningar publiceras inte som produktdokumentation. Mönstren kan användas som inspiration. Originalcitat hanteras med proveniens.

## Vad som bevaras

- Historiska PRD:er som referens.
- Stitch-skärmar och designsystem.
- Existerande citatarkiv.
- Befintlig offlinekart- och ruttningskod.
- Sanningslager och säkerhetskontroller.
- OSM-attribution och licenskrav.

## Avvikelser från det inlämnade underlaget

Underlaget kom som ett skript skrivet mot repots läge i etapp 2. Det här är ändrat när det fördes in, och varje punkt är byggarens tolkning:

1. **Gren och version.** Underlaget angav `etapp2/offline-karta` och `0.1-etapp2`. Verkligt läge är `etapp4/sanningskarna` och `0.6-etapp6`, med Compose, svensk röst och Convoy redan i appen. Avsnitt 9 i inriktningen är skrivet efter det.
2. **Gamla PRD:n är flyttad oförändrad.** Den fick ingen ny rubrik inuti filen, eftersom baslinjen har en kontrollsumma för den. Noteringen att den är historisk står i [`archive/README.md`](archive/README.md).
3. **README behåller bygginstruktioner och modultabell.** Underlaget hade strukit dem.
4. **Rikard-citatet.** Arkivets belagda original R-001 lyder "Allt ont kommer från dig Rikard." Chattens formulering är märkt som återgiven och ligger i utkastet `data/quotes/chatt-2026-11-21.json`.
5. **Antinavigering.** En mening i avsnitt 8 i inriktningen knyter ihop säkerhetsgränsen med ägarbeslutet kl. 19.41.
6. **Beslutshierarkin** nämner baslinjen `docs/baseline/v0.1/`, som underlaget inte kände till.
7. **Ingenting är fört till `main`.** Underlaget ville plocka över ändringen dit. Det görs först på ägarens order.

## Beslutshierarki

Se avsnitt 13 i [GALLANDE-INRIKTNING.md](GALLANDE-INRIKTNING.md). Historiska dokument bevaras men kan inte upphäva nyare styrande beslut.
