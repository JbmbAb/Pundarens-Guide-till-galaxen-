# Etapp 2 – protokoll: offlinekedjan valhalla-mobile → Ferrostar → MapLibre (PMTiles)

| Uppgift | Värde |
|---|---|
| Datum | 2026-11-21, natten (Europe/Stockholm, UTC+1). Verkstadskedjan kördes 01.17, Android-bygget 01.47, emulatorprovet 01.25–01.45. |
| Gren och commit | `etapp2/offline-karta`. Verkstad och emulator-APK byggda på `93af263`/`79d2b96`, slutligt Android-bygge och APK-kontroll på `79d2b96`. Exakt commit står överst i varje fil i [`etapp2-testutdata/`](etapp2-testutdata/). |
| Motsvarar | Byggplanens **etapp 3** i [09](../v0.1/09-genomforandeplan.md) och grindarna **G2** och **G3** i [08](../v0.1/08-teknikverifieringsplan.md#2-grindar). Projektets löpande numrering kallar det etapp 2 (etapp 1 var Illogical Engine, PR #5). |
| Ägarbeslut | "Klart" (2026-11-21 00.42, efter att PR #5 slogs ihop): gå vidare med nästa etapp. |
| Utförare | Grok Bot. Ingen oberoende granskning (F-07) är gjord. |
| Målenhet | Samsung Galaxy Tab S8 Ultra SM-X900. **Inte provad.** Allt nedan är verkstad (Debian 13, x86_64) och Android-emulator. |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot, annars `NOT_VERIFIED`, `REVISE` eller `REJECT`. `ACCEPT` nedan gäller den miljö som står i kolumnen. **G2 och G3 som grindar är `NOT_VERIFIED`**, eftersom kriterierna i 08 kräver målenheten i flygplansläge.

## 1. Vad som byggdes

| Del | Var | Kort |
|---|---|---|
| Verkstadsskript | [`tools/verkstad/`](../../../tools/verkstad/) | `01` hämtar Geofabriks Sverigeutdrag och kontrollerar MD5. `02` klipper provområdet med osmium. `03` bygger Valhalla-rutor och tar-utdrag. `05` bygger `karta.pmtiles` med tilemaker. `06` packar paketet och skriver manifest med SHA-256. `04` gör ruttprov utan nätverk. `07` kontrollerar APK:n. `kor-alla.sh` kör allt. Stora filer hamnar i `/workspace/verkstad/ut` och checkas inte in. |
| Ferrostar-prov | [`tools/verkstad/ferrostar-prov/`](../../../tools/verkstad/ferrostar-prov/) | Rust, `ferrostar = "=0.59.0"` (samma kärna som Android-artefakten). Läser Valhallas OSRM-svar med Ferrostars `OsrmResponseParser`, kör `NavigationController` med simulerad position och gör tre avvikelseförsök per rutt. |
| Prototyp | [`prototyp/offline-kedja/`](../../../prototyp/offline-kedja/) | Kastbar Android-app (08 §3), eget Gradle-bygge skilt från rotprojektet. `PaketInstallerare` (atomär installation), `ValhallaRuttkalla` (Ferrostars `CustomRouteProvider` mot valhalla-mobile), `ProvPositionskalla` (simulerad position med avvikelseförsök), `MainActivity` (MapLibre med `pmtiles://file://`, sanningsremsa, logg). Ingen Illogical Engine, ingen talsyntes. |
| Beroendespärr | `prototyp/offline-kedja/app/build.gradle.kts`, uppgiften `verifieraIngenGoogle` | Löser `debugRuntimeClasspath` och `releaseRuntimeClasspath`. Stoppar bygget vid `com.google.android.gms`, `com.google.firebase`, `com.google.crypto.tink`, Google Maps-grupperna, `com.google.android.play`, `com.google.mlkit` m.fl. Alla andra `com.google.*` måste stå i en tillåtelselista med motivering. Körs före varje bygge (`preBuild`) och i `check`. |

### Provområde (F-03 är öppen)

Gränsrektangel `14.20,60.90,15.20,61.65` (WGS84): Orsa kommun (OSM-gräns 14.304–15.093 E, 61.050–61.595 N) med marginal så att Mora centrum och Grönklitt ryms. Grannkommuner enligt OSM-gränserna i Sverigeutdraget (avstånd < 0,001°): **Härjedalen, Ljusdal, Mora, Rättvik**. Älvdalen gränsar inte till Orsa. De fyra grannarna ligger bara delvis i rektangeln. Hela F-03-området (cirka 12,0–16,6 E, 60,7–62,8 N) är inte byggt.

## 2. Låsta versioner och licenser

| Del | Version | Licens | Källa kontrollerad 2026-11-21 |
|---|---|---|---|
| valhalla-mobile | 0.6.6 (Valhalla 3.9.2, undermodul `a919bb6` = taggen 3.9.2) | MIT | Maven Central, GitHub-taggen 0.6.6 |
| valhalla-models, valhalla-models-config | 0.6.0 | MIT | Maven Central (krävs av 0.6.6) |
| Ferrostar core (Android) | 0.59.0 | BSD-3 | Maven Central, taggen 0.59.0 |
| Ferrostar (Rust, verkstadsprovet) | 0.59.0 | BSD-3 | crates.io |
| MapLibre Native Android | 13.7.1 (`org.maplibre.gl:android-sdk`) | BSD-2 | Maven Central |
| Valhalla i verkstaden | pyvalhalla 3.9.1 | MIT | PyPI (3.9.2 finns inte där, se E2-D1) |
| tilemaker | 3.0.0 (Debian-paket), konfiguration och Lua-skript från taggen v3.0.0 | FTWPL | GitHub |
| osmium-tool | 1.18.0 | GPL-3 (verktyg, följer inte med appen) | Debian |
| OSM-data | Geofabrik `sweden-latest.osm.pbf`, replikering 2026-11-19T21:22:20Z, MD5 `5a43dbaadcf307f8d5da7404972da054` | ODbL 1.0 | Geofabrik |
| Bygg | Gradle 9.2.1 (SHA-256 kontrollerad), AGP 9.0.1, Kotlin 2.3.20, compileSdk 36, minSdk 26, JDK 17.0.21.1 | – | – |

## 3. Kommandon

```
bash tools/verkstad/kor-alla.sh                     # 01–06, ruttprov och Ferrostar-prov (de två sista under unshare -rn)
cd prototyp/offline-kedja
./gradlew --console=plain clean :app:verifieraIngenGoogle :app:testDebugUnitTest :app:assembleDebug \
    -PpaketKatalog=/workspace/verkstad/ut/paket/orsa-prov-20261121-0117
bash ../../tools/verkstad/07-kontrollera-apk.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug -Pabi=x86_64 -PpaketKatalog=…   # bara för emulatorn
./gradlew --no-daemon clean check                    # i roten: de 96 testerna från etapp 1
```

## 4. Utfall per kriterium

| Id | Kriterium | Bevis | Miljö | Status |
|---|---|---|---|---|
| E2-K01 | OSM-källan är spårbar | MD5 stämmer mot Geofabrik, replikeringstid i manifestet ([`verkstad-kedja.txt`](etapp2-testutdata/verkstad-kedja.txt), [`paket-manifest.json`](etapp2-testutdata/paket-manifest.json)) | verkstad | ACCEPT |
| E2-K02 | Valhalla-rutor för provområdet | 30 rutor, `valhalla_tiles.tar` 4 526 080 byte med `index.bin` (Valhallas `valhalla_build_extract`) | verkstad | ACCEPT |
| E2-K03 | Ruttning utan nätverk, bil (passagerare) och gång | `04-ruttprov.py` under `unshare -rn` ("inget nätverk"): Orsa→Grönklitt bil 15,73 km/16,0 min, Orsa→Mora bil 16,07 km/15,2 min, Mora→Orsa bil 16,34 km, Orsa→Grönklitt gång 12,42 km/146 min, Orsa→Mora gång 15,60 km/184 min. Alla `code=Ok` med svenska röst- och bannerinstruktioner. Svaren ligger i [`ruttprov/`](etapp2-testutdata/ruttprov/). | verkstad | ACCEPT |
| E2-K04 | Ferrostars OSRM-parser läser Valhallas svar (T-12) | Ferrostar-provet läser alla fem svaren: 17–31 steg, talinstruktioner och bildinstruktioner följer med. I appen: `createOsrmResponseParser(6u)` på valhalla-mobiles svar ([`emulator-logcat-etapp2.txt`](etapp2-testutdata/emulator-logcat-etapp2.txt)). | verkstad + emulator | ACCEPT |
| E2-K05 | Navigering med simulerad position: stegbyte, talinstruktioner, framme | Verkstad: alla fem rutterna når `Complete`, alla steg byts, 25/25, 28/28, 28/28, 36/36 och 63/64 talinstruktioner utlöses. Emulator: Orsa→Grönklitt navigerades, stegbyten och sex talinstruktioner loggades innan provet stoppades. | verkstad + emulator | ACCEPT (verkstad). Emulator: ACCEPT för stegbyte och tal; framkomst inte körd i emulatorn. |
| E2-K06 | Avvikelse, minst tre försök, och omräkning utan nätverk (T-13) | Verkstad: 3/3 försök upptäckta på alla fem rutterna (250 m vinkelrätt ut), tillbaka på rutten efter två positioner. Emulator i flygplansläge: 3/3 försök upptäckta (`CompletelyOffRoute`, 249,7 m), varje gång följt av `OMRÄKNING` och en ny rutt från valhalla-mobile (1,3–3,5 s per rutt under TCG). Omräkningen görs av `FerrostarCore` i Kotlin via vår `CustomRouteProvider`. | verkstad + emulator | ACCEPT i emulatorn. Målenhet: NOT_VERIFIED. |
| E2-K07 | `karta.pmtiles` för provområdet | tilemaker 3.0.0: zoom 0–14, 4 507 rutor, 11,6 MB, OpenMapTiles-schema, gränser 14,2–15,2 E / 60,9–61,65 N. Lager kontrollerade med pmtiles-läsaren (z14 i Orsa centrum: vägar, byggnader, vatten, markanvändning). | verkstad | ACCEPT |
| E2-K08 | MapLibre visar den lokala PMTiles-filen (T-01, T-02) | Emulatorn i flygplansläge (`airplane_mode_on=1`, `ping: Network is unreachable`): kartan över Orsa ritas från `pmtiles://file://…/karta.pmtiles` med den mörka stilen, rutten och positionen ritas ovanpå, sanningsremsan överst ([`emulator-start.png`](etapp2-testutdata/emulator-start.png), [`emulator-navigering.png`](etapp2-testutdata/emulator-navigering.png), [`emulator-avvikelse.png`](etapp2-testutdata/emulator-avvikelse.png)). | emulator (swiftshader) | ACCEPT i emulatorn. Målenhet: NOT_VERIFIED. |
| E2-K09 | Inga Google-beroenden i appen | `verifieraIngenGoogle` grön, 170 rader i [`beroenden.txt`](etapp2-testutdata/beroenden.txt). Negativt prov: play-services-location, firebase-analytics, tink-android, play-services-maps och Ferrostars `google-play-services`-artefakt stoppar alla bygget, `EXIT=1` ([`negativt-prov-beroenden.txt`](etapp2-testutdata/negativt-prov-beroenden.txt)). APK:ns dex-kod har 0 klasser under `com/google/android/gms`, `com/google/firebase`, `com/google/crypto/tink`, Maps och Play ([`apk-kontroll-arm64.txt`](etapp2-testutdata/apk-kontroll-arm64.txt)). Emulatorns system saknar Google-paket. | verkstad | ACCEPT för spärren. Två öppna Google-bibliotek finns kvar, se E2-D3: **REVISE**, ägarbeslut. |
| E2-K10 | Inget nätverk vid körning | APK:n saknar `android.permission.INTERNET` (aapt2). Appen loggar "INTERNET-behörighet: saknas". MapLibre körs med `setConnected(false)`. Ferrostars HTTP-klient är utbytt mot en som alltid vägrar och loggar; den anropades aldrig (0 rader `FEL` i loggen). | verkstad + emulator | ACCEPT. Nätverksmätning på målenheten: NOT_VERIFIED. |
| E2-K11 | Manifest med SHA-256 och atomär installation | `PaketInstallerareTest`, 8 tester gröna: lyckad installation, fel SHA-256 och fel storlek avvisas utan att det aktiva paketet rörs, avbrott mitt i kopieringen, kvarlämnade `tmp-*` städas, pekaren byts atomärt, föregående version sparas för återställning, farliga filnamn avvisas, trasig pekare ger inget paket. Emulatorn: paketet installerades atomärt ur APK:ns tillgångar på 12,2 s (TCG). | JVM + emulator | ACCEPT för logiken. Strömavbrott och prestanda på målenheten: NOT_VERIFIED (G5). |
| E2-K12 | Envägsarkitekturen hålls | Prototypen innehåller ingen Illogical Engine och ingen `:babel` (`ArkitekturTest`, 5 tester). Navigeringen ligger i sanningslagret (`sanning.*`). Rotprojektets 96 tester är fortfarande gröna ([`rotprojekt-clean-check.txt`](etapp2-testutdata/rotprojekt-clean-check.txt)). | verkstad | ACCEPT |
| E2-K13 | APK för målenheten | `offlinekedja-etapp2-arm64-debug.apk`, 64 480 736 byte, SHA-256 `e670e33b…8b2b`, `native-code: arm64-v8a`, minSdk 26, targetSdk 36. Debug-signerad. | verkstad | ACCEPT som bygge. Körning på målenheten: NOT_VERIFIED. |
| G2 | Kedjan offline på målenheten (08) | Allt utom målenheten är visat ovan. Talinstruktionerna utlöses men uttalas inte (ingen talmotor, G4). Ingen skärm- eller ljudinspelning från målenheten. | – | **NOT_VERIFIED** |
| G3 | MapLibre med lokalt kartpaket på målenheten (08) | Visat i emulatorn. Kriteriet kräver `karta.pmtiles` i `filesDir` (uppfyllt), **tre stilfiler** (bara en finns) och nätverksmätning på målenheten. | – | **NOT_VERIFIED**; stilarna: REVISE |

Enhetstester i prototypen: 14 av 14 gröna ([`junit-xml/`](etapp2-testutdata/junit-xml/)). Androids instrumenterade tester finns inte.

## 5. Avvikelser

| Id | Avvikelse | Skäl | Beslut av |
|---|---|---|---|
| E2-D1 | Valhalla-rutorna byggs med Valhalla 3.9.1 (pyvalhalla) medan appen läser med 3.9.2 | 3.9.2 finns inte på PyPI; en äldre skrivare mot en nyare läsare är den säkra riktningen. Emulatorn läste rutorna utan fel. | Granskare |
| E2-D2 | Provområdet är en rektangel runt Orsa, inte Orsa med alla grannkommuner | F-03 är öppen. Hela området skulle bli cirka 250 × 230 km. | Jimmy (F-03) |
| E2-D3 | Två öppna Google-bibliotek står i tillåtelselistan: `com.google.code.gson:gson` 2.10.1 (krävs av MapLibres GeoJSON-modul) och `com.google.guava:listenablefuture` 1.0 (tom markörartefakt som androidx kräver) | Varken nätverk, konto eller tjänst. Att ta bort dem kräver att MapLibres GeoJSON-del och androidx byts ut. | **Jimmy** (ingen Google över huvud taget, eller bara inga Google-tjänster?) |
| E2-D4 | En stilfil (mörk), inga textetiketter, inga glyfer eller sprites | Glyfer måste ligga lokalt; utan textlager hämtar MapLibre inget. G3 kräver tre stilfiler. | Etapp 4 |
| E2-D5 | Ingen talsyntes; talinstruktionerna loggas | G4 och val av talmotor (F-06, ingen Google) är en egen grind | – |
| E2-D6 | `ACCESS_NETWORK_STATE` ligger kvar i manifestet | Utan den kraschar MapLibres `ConnectivityReceiver` med `SecurityException` (sett i emulatorn). Behörigheten ger bara läsning av nätstatus. | Granskare |
| E2-D7 | Ferrostar core lägger till `ACCESS_FINE_LOCATION` och `ACCESS_COARSE_LOCATION` i manifestet. Prototypen ber aldrig om dem och använder bara simulerad position. | Behövs för verkliga turer (G5). | – |
| E2-D8 | Emulatorn körs med programvaru-CPU (TCG), inte KVM | KVM i verkstaden kraschar vid start av en virtuell CPU (`kvm_spurious_fault` i kärnloggen). Tider från emulatorn (Valhalla-start 162 s, rutt 1,3–5,6 s) är därför inte representativa. | – |
| E2-D9 | Prototypen ligger i `prototyp/offline-kedja/` på grenen `etapp2/offline-karta`, inte på en egen gren `spike/offline-chain` (08 §3) | Ägaren slår ihop via PR; koden är märkt kastbar och byggs separat från rotprojektet. | Jimmy |
| E2-D10 | `desugar_jdk_libs` (`com.android.tools`) läggs till | Ferrostar core 0.59.0 kräver core library desugaring. Byggverktyg från AOSP, inte en Google-tjänst. | – |
| E2-D11 | tilemaker ger inte byte-identisk utdata mellan körningar (11 589 244 mot 11 591 336 byte) | Trådning. Manifestets SHA-256 gäller den fil som faktiskt packas. | – |
| E2-D12 | Debug-signerad APK med osymboliserade Valhalla-bibliotek (`libvalhalla-wrapper.so` 133 MB okomprimerat) | Prototyp. Storleken på en releaseversion är inte mätt. | G5 |

## 6. Iakttagelser

- **Valhallas svenska talbesked har fel som påverkar sanningsrösten.** "Kör söderut  Hantverkargatan" (ordet "på" saknas, dubbelt mellanslag), "Därefter Sväng" (stor bokstav mitt i meningen), vägnummer läses upp ("Badstugatan, 70.04", "Kaplansgatan, W 1000") och decimaler skrivs med punkt ("1.5 kilometer"). Det är Valhallas språkfil för sv-SE, inte vår kod. Måste rättas eller filtreras i `:truth:core` innan G4.
- **Avvikelsen syns en position för sent.** I både verkstaden och emulatorn gav den första avvikande positionen `NoDeviation` och först den andra `Deviation`. Vid en position per sekund betyder det cirka en sekunds fördröjning före omräkning.
- **En falsk avvikelse vid start.** När en ny tur startades direkt efter en annan såg Ferrostar den förra turens sista position, rapporterade 486 m avvikelse och räknade om rutten. Appen bör nollställa positionen innan en ny tur startar.
- **Upprepade besked efter omräkning.** Efter varje omräkning sades "Fortsätt på E 45 i 11 kilometer" igen (tre gånger under avvikelseprovet). Det strider mot andan i röstreglerna om upprepning och bör hanteras när rösten kopplas in.
- En av 64 talinstruktioner på gångrutten Orsa→Mora utlöstes inte i verkstadens simulering (63/64). Inte utrett.
- Systemgränssnittet i emulatorn svarade inte under en period ("System UI isn't responding"), vilket beror på TCG. Appen fortsatte att köra.

## 7. Vad ägaren behöver göra på målenheten (G2 och G3)

1. Aktivera utvecklaralternativ och USB-felsökning på Tab S8 Ultra och anslut den till datorn.
2. Installera: `adb install offlinekedja-etapp2-arm64-debug.apk` (filen finns i verkstaden, `/workspace/verkstad/apk/`, och kan skickas eller laddas upp på begäran; den ligger inte i git).
3. Slå på **flygplansläge** och kontrollera att wifi också är av.
4. Starta "Offlinekedja (etapp 2)". Vänta på "Offlinepaket … Välj en provtur."
5. Tryck **Orsa → Grönklitt (passagerare)** och låt den gå till "Framme". Tryck sedan **Orsa → Mora (till fots)** och **Avvikelseprov ×3**.
6. Spela in skärmen (Samsungs skärminspelare) under minst en tur.
7. Hämta loggen: `adb pull /sdcard/Android/data/se.pundaren.guide.prototyp.offlinekedja/files/` (innehåller `etapp2-logg.txt` och råa ruttsvar). Skicka loggen och inspelningen, så skrivs G2- och G3-protokollen.
8. Notera Android-version och One UI-version (F-04).

## 8. Inte verifierat

Allt på målenheten SM-X900: installation, kartvisning, ruttid, kallstart, minne och batteri. Nätverkstrafik mätt på enheten. Talsyntes och avbrott (G4). Tre stilfiler och textetiketter (G3). Verkliga GPS-positioner, GNSS på målenheten (F-04) och tio verkliga turer (G5). Atomär installation vid strömavbrott. Hela F-03-området. Oberoende granskning (F-07).
