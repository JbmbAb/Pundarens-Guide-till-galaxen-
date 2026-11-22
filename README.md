# 🌀 Pundarens Guide till Galaxen

> **GÄLLANDE, ÖVERORDNAD INRIKTNING:**  
> [docs/GALLANDE-INRIKTNING.md](docs/GALLANDE-INRIKTNING.md)  
> Detta dokument har företräde framför äldre
> planer, briefer och motstridiga förslag.

**Inte en navigator med skämt.**  
En absurd galaktisk följeslagare som av misstag
råkar vara användbar.

Appen ska göra användaren glad.

## Börja här

- [Gällande produktinriktning](docs/GALLANDE-INRIKTNING.md)
- [Satirisk kanon och de tio budorden](docs/SATIRISK-KANON.md)
- [Aktuell PRD](docs/PRD.md)
- [Dokumentationsrevision och ersatta beslut](docs/DOKUMENTATIONSREVISION.md)
- [Stitch-design](docs/design/README.md)
- [Citatarkivet](data/quotes/README.md)

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

Den visuella riktningen hämtas från
Handdukssensorn och Cosmic HUD.

## Faktiskt tekniskt läge

Det finns en byggbar Android-prototyp
under `prototyp/offline-kedja/`.

**Utvecklingsgren:** `etapp2/offline-karta`  
**Verifierad prototypversion:** `0.1-etapp2`

Nuvarande offlinekedja använder:

- Kotlin.
- MapLibre.
- Valhalla Mobile.
- Ferrostar Core.
- Lokala kart- och ruttningspaket.

Ingen Google Maps SDK eller beroende av
Google Play Services ingår i målarkitekturen.

Den nuvarande prototypen är ett teknikprov.
Den färdiga Guidens startsida och personlighet
ska utvecklas med den styrande produktinriktningen
som referens.

## Viktig säkerhetsprincip

Illogical Engine får vara fullständigt absurd.
Navigeringsinformation får det inte.

Rutt, position, svängar, avstånd och varningar
hanteras av ett separat sanningslager.

Inga skämt får ändra, dölja eller försena
säkerhetskritisk information.

## Ursprung och upphov

Designreferenser kommer från Google Stitch.
Personligt citatmaterial har särskilda
proveniensregler.

Kartdata från OpenStreetMap:
© OpenStreetMap contributors, ODbL.

Se respektive datakatalog för detaljer.

Historiska dokument är referensmaterial,
inte gällande utvecklingsbeslut.
