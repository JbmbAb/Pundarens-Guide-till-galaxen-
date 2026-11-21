# Etapp 2 – protokoll: offlinekedjan valhalla-mobile → Ferrostar → MapLibre (PMTiles)

| Uppgift | Värde |
|---|---|
| Datum | 2026-11-21 (Europe/Stockholm, UTC+1). Första omgången (Orsa-rektangeln): verkstad 01.17, Android-bygge 01.47, emulator 01.25–01.45. **Andra omgången (F-03-området, efter ägarbeslutet 06.15):** verkstad 06.17–06.19, Android-bygge 06.20, rotprojektet 06.22, emulator 06.24–06.48. |
| Gren och commit | `etapp2/offline-karta` (PR #6). Andra omgången: verkstad på `1877525`, Android-bygge på `1877525` + ändringarna i `2812a60` (committade direkt efter bygget), rotprojektet på `2812a60`. Exakt commit står överst i varje fil i [`etapp2-testutdata/`](etapp2-testutdata/). |
| Motsvarar | Byggplanens **etapp 3** i [09](../v0.1/09-genomforandeplan.md) och grindarna **G2** och **G3** i [08](../v0.1/08-teknikverifieringsplan.md#2-grindar). Projektets löpande numrering kallar det etapp 2 (etapp 1 var Illogical Engine, PR #5). |
| Ägarbeslut | "Klart" (2026-11-21 00.42, efter att PR #5 slogs ihop): gå vidare med nästa etapp. 2026-11-21 06.15: förbudet gäller Googles tjänster (E2-D3) och testområdet är Orsa, Mora, Rättvik, Ljusdal, Härjedalen och Älvdalen (F-03), se [10](../v0.1/10-beslutslogg.md) avsnitt 9. |
| Utförare | Grok Bot. Ingen oberoende granskning (F-07) är gjord. |
| Målenhet | Samsung Galaxy Tab S8 Ultra SM-X900, One UI 8.0, Android 16. **Provad av ägaren 2026-11-21 med första omgångens APK** (ägariakttagelse utan logg, avsnitt 9). Allt annat nedan är verkstad (Debian 13, x86_64) och Android-emulator. |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot, annars `NOT_VERIFIED`, `REVISE` eller `REJECT`. `ACCEPT` nedan gäller den miljö som står i kolumnen. **G2 och G3 som grindar är `NOT_VERIFIED`.** Ägarens provkörning på målenheten i flygplansläge (avsnitt 9) är en ägariakttagelse, en lägre bevisgrad än logg eller inspelning, och den gäller bara första omgångens APK.

## 1. Vad som byggdes

| Del | Var | Kort |
|---|---|---|
| Verkstadsskript | [`tools/verkstad/`](../../../tools/verkstad/) | `01` hämtar Geofabriks Sverigeutdrag och kontrollerar MD5. `00` bygger områdespolygonen ur kommungränserna. `02` klipper provområdet med osmium (`-p` polygon, `complete_ways`). `03` bygger Valhalla-rutor och tar-utdrag. `05` bygger `karta.pmtiles` med tilemaker. `06` packar paketet och skriver manifest med SHA-256. `04` gör ruttprov utan nätverk. `07` kontrollerar APK:n. `kor-alla.sh` kör allt. Stora filer hamnar i `/workspace/verkstad/ut` och checkas inte in. |
| Ferrostar-prov | [`tools/verkstad/ferrostar-prov/`](../../../tools/verkstad/ferrostar-prov/) | Rust, `ferrostar = "=0.59.0"` (samma kärna som Android-artefakten). Läser Valhallas OSRM-svar med Ferrostars `OsrmResponseParser`, kör `NavigationController` med simulerad position och gör tre avvikelseförsök per rutt. |
| Prototyp | [`prototyp/offline-kedja/`](../../../prototyp/offline-kedja/) | Kastbar Android-app (08 §3), eget Gradle-bygge skilt från rotprojektet. `PaketInstallerare` (atomär installation), `ValhallaRuttkalla` (Ferrostars `CustomRouteProvider` mot valhalla-mobile), `ProvPositionskalla` (simulerad position med avvikelseförsök), `MainActivity` (MapLibre med `pmtiles://file://`, sanningsremsa, logg). Ingen Illogical Engine, ingen talsyntes. |
| Beroendespärr | `prototyp/offline-kedja/app/build.gradle.kts`, uppgiften `verifieraIngenGoogle` | Löser `debugRuntimeClasspath` och `releaseRuntimeClasspath`. Stoppar bygget vid `com.google.android.gms`, `com.google.firebase`, `com.google.crypto.tink`, Google Maps-grupperna, `com.google.android.play`, `com.google.mlkit` m.fl. Alla andra `com.google.*` måste stå i en tillåtelselista med motivering. Körs före varje bygge (`preBuild`) och i `check`. |

### Provområde (F-03, ägarbeslut 2026-11-21 06.15)

Unionen av OSM-gränserna (admin_level 7) för **Orsa, Mora, Rättvik, Ljusdal, Härjedalen och Älvdalen** i Sverigeutdraget, buffrad 0,02° (cirka 1–2 km) och förenklad 0,002°: [`tools/verkstad/omrade/f03-sex-kommuner.geojson`](../../../tools/verkstad/omrade/f03-sex-kommuner.geojson), 9 922 byte, SHA-256 `ae8c237a…5c07` (står i manifestet). Gränsrektangel `12.036,60.645,16.652,62.785`, cirka 250 × 240 km. Osmium klipper vägar och övrig data efter polygonen; tilemaker får bara rektangeln, så kartrutor utanför polygonen är tomma. Älvdalen gränsar inte till Orsa i OSM men ingår enligt beslutet.

Första omgången byggdes på rektangeln `14.20,60.90,15.20,61.65` runt Orsa (E2-D2, nu stängd). Den APK:n (`e670e33b…8b2b`) är den som ligger på ägarens Google Drive.

| Paket `f03-sex-kommuner-20261121-0619` | Storlek | SHA-256 |
|---|---|---|
| `valhalla_tiles.tar` (143 rutor) | 19 947 520 byte | `5312a8d8…da6c` |
| `karta.pmtiles` (zoom 0–14, 38 480 rutor) | 77 023 128 byte | `cac97988…7253` |
| `manifest.json` | 1 341 byte | – |

Som jämförelse: Orsa-rektangeln gav 4 526 080 byte Valhalla och 11 591 336 byte PMTiles.

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
| OSM-data | Geofabrik `sweden-latest.osm.pbf`. Andra omgången: replikering 2026-11-20T21:21:21Z (22.21 svensk tid), MD5 `955c818ced0a5b5362d8746579f7c738`. Första omgången: 2026-11-19T21:22:20Z, MD5 `5a43dbaa…2054`. | ODbL 1.0 | Geofabrik |
| Bygg | Gradle 9.2.1 (SHA-256 kontrollerad), AGP 9.0.1, Kotlin 2.3.20, compileSdk 36, minSdk 26, JDK 17.0.21.1 | – | – |

## 3. Kommandon

```
bash tools/verkstad/kor-alla.sh                     # 01, 00, 02, 03, 05, 06, ruttprov och Ferrostar-prov (de två sista under unshare -rn)
# Gamla rektangeln: OMRADE=orsa-prov POLYGON= BBOX=14.20,60.90,15.20,61.65 bash tools/verkstad/kor-alla.sh
cd prototyp/offline-kedja
./gradlew --console=plain clean :app:verifieraIngenGoogle :app:testDebugUnitTest :app:assembleDebug \
    -PpaketKatalog=/workspace/verkstad/ut/paket/f03-sex-kommuner-20261121-0619
bash ../../tools/verkstad/07-kontrollera-apk.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug -Pabi=x86_64 -PpaketKatalog=…   # bara för emulatorn
./gradlew --no-daemon clean check                    # i roten: de 96 testerna från etapp 1
```

## 4. Utfall per kriterium

| Id | Kriterium | Bevis | Miljö | Status |
|---|---|---|---|---|
| E2-K01 | OSM-källan är spårbar | MD5 stämmer mot Geofabrik, replikeringstid i manifestet ([`verkstad-kedja.txt`](etapp2-testutdata/verkstad-kedja.txt), [`paket-manifest.json`](etapp2-testutdata/paket-manifest.json)) | verkstad | ACCEPT |
| E2-K02 | Valhalla-rutor för provområdet | F-03-området: 143 rutor, `valhalla_tiles.tar` 19 947 520 byte med `index.bin` (Valhallas `valhalla_build_extract`). Byggtid cirka 1 min. | verkstad | ACCEPT |
| E2-K03 | Ruttning utan nätverk, bil (passagerare) och gång | `04-ruttprov.py` under `unshare -rn` ("inget nätverk"), 11 rutter, alla `code=Ok` med svenska röst- och bannerinstruktioner. De fem gamla oförändrade (svaren byte-identiska med första omgången): Orsa→Grönklitt bil 15,73 km/16,0 min, Orsa→Mora bil 16,07 km/15,2 min, Mora→Orsa bil 16,34 km/15,5 min, Orsa→Grönklitt gång 12,42 km/146 min, Orsa→Mora gång 15,60 km/184 min. Nya, en per grannkommun: Orsa→Älvdalen bil 45,50 km/40,2 min, Orsa→Rättvik bil 48,38 km/37,9 min, Orsa→Ljusdal bil 144,54 km/111,6 min, Orsa→Sveg (Härjedalen) bil 124,10 km/85,1 min, Älvdalen→Idre bil 112,04 km/79,5 min, Orsa→Älvdalen gång 40,92 km/482 min. Mora täcks av de gamla rutterna. Målpunkterna är `place=town`/`village`-noder ur OSM. Svaren ligger i [`ruttprov/`](etapp2-testutdata/ruttprov/). | verkstad | ACCEPT |
| E2-K04 | Ferrostars OSRM-parser läser Valhallas svar (T-12) | Ferrostar-provet läser alla elva svaren: 5–35 steg, talinstruktioner och bildinstruktioner följer med. I appen: `createOsrmResponseParser(6u)` på valhalla-mobiles svar ([`emulator-logcat-etapp2.txt`](etapp2-testutdata/emulator-logcat-etapp2.txt)). | verkstad + emulator | ACCEPT |
| E2-K05 | Navigering med simulerad position: stegbyte, talinstruktioner, framme | Verkstad: alla elva rutterna når `Complete` och alla steg byts (upp till 15 444 positioner på Orsa→Ljusdal). Talinstruktioner utlösta: 25/25, 28/28, 28/28, 36/36, 63/64 (Orsa→Mora gång), 27/27, 32/32, 30/30, 17/17, 6/6 och 82/84 (Orsa→Älvdalen gång). Emulator, F-03-paketet: Orsa→Sveg 124,10 km (samma längd som i verkstaden) navigerades med stegbyten och fyra talinstruktioner innan provet stoppades. | verkstad + emulator | ACCEPT (verkstad). Emulator: ACCEPT för stegbyte och tal; framkomst inte körd i emulatorn. |
| E2-K06 | Avvikelse, minst tre försök, och omräkning utan nätverk (T-13) | Verkstad: 3/3 försök upptäckta på alla elva rutterna (250 m vinkelrätt ut), tillbaka på rutten efter två positioner. Emulator i flygplansläge med F-03-paketet: 3/3 försök upptäckta (`CompletelyOffRoute`, 249,7 m), varje gång följt av `OMRÄKNING` och en ny rutt från valhalla-mobile (1,0–1,5 s per rutt under TCG). Omräkningen görs av `FerrostarCore` i Kotlin via vår `CustomRouteProvider`. | verkstad + emulator | ACCEPT i emulatorn. Målenhet: NOT_VERIFIED. |
| E2-K07 | `karta.pmtiles` för provområdet | tilemaker 3.0.0: zoom 0–14, 38 480 rutor, 77 023 128 byte, OpenMapTiles-schema, gränser 12,036–16,652 E / 60,645–62,785 N. Byggtid cirka 1 min. | verkstad | ACCEPT |
| E2-K08 | MapLibre visar den lokala PMTiles-filen (T-01, T-02) | Emulatorn i flygplansläge (`airplane_mode_on=1`, `ping: Network is unreachable`): kartan över Orsa ritas från `pmtiles://file://…/karta.pmtiles` med den mörka stilen ([`emulator-start.png`](etapp2-testutdata/emulator-start.png)). Under Orsa→Sveg ritas kartan och rutten norr om Orsa ([`emulator-navigering-sveg.png`](etapp2-testutdata/emulator-navigering-sveg.png)), under avvikelseprovet söder om Orsa ([`emulator-avvikelse.png`](etapp2-testutdata/emulator-avvikelse.png)). [`emulator-navigering.png`](etapp2-testutdata/emulator-navigering.png) är från första omgången. | emulator (swiftshader) | ACCEPT i emulatorn. Målenhet: NOT_VERIFIED. |
| E2-K09 | Inga Google-tjänster i appen | `verifieraIngenGoogle` grön, 170 rader i [`beroenden.txt`](etapp2-testutdata/beroenden.txt) (beroendena är oförändrade sedan första omgången). Negativt prov: play-services-location, firebase-analytics, tink-android, play-services-maps och Ferrostars `google-play-services`-artefakt stoppar alla bygget, `EXIT=1` ([`negativt-prov-beroenden.txt`](etapp2-testutdata/negativt-prov-beroenden.txt), första omgången). APK:ns dex-kod har 0 klasser under `com/google/android/gms`, `com/google/firebase`, `com/google/crypto/tink`, Maps och Play ([`apk-kontroll-arm64.txt`](etapp2-testutdata/apk-kontroll-arm64.txt)). Emulatorns system saknar Google-paket. `gson` och `listenablefuture` finns kvar enligt ägarbeslutet (E2-D3). | verkstad | ACCEPT |
| E2-K10 | Inget nätverk vid körning | APK:n saknar `android.permission.INTERNET` (aapt2). Appen loggar "INTERNET-behörighet: saknas". MapLibre körs med `setConnected(false)`. Ferrostars HTTP-klient är utbytt mot en som alltid vägrar och loggar; den anropades aldrig (0 rader `FEL` i loggen). | verkstad + emulator | ACCEPT. Nätverksmätning på målenheten: NOT_VERIFIED. |
| E2-K11 | Manifest med SHA-256 och atomär installation | `PaketInstallerareTest`, 8 tester gröna: lyckad installation, fel SHA-256 och fel storlek avvisas utan att det aktiva paketet rörs, avbrott mitt i kopieringen, kvarlämnade `tmp-*` städas, pekaren byts atomärt, föregående version sparas för återställning, farliga filnamn avvisas, trasig pekare ger inget paket. Emulatorn: F-03-paketet (cirka 95 MB) installerades atomärt ur APK:ns tillgångar på 33,6 s (TCG). | JVM + emulator | ACCEPT för logiken. Strömavbrott och prestanda på målenheten: NOT_VERIFIED (G5). |
| E2-K12 | Envägsarkitekturen hålls | Prototypen innehåller ingen Illogical Engine och ingen `:babel` (`ArkitekturTest`, 5 tester). Navigeringen ligger i sanningslagret (`sanning.*`). Rotprojektets 96 tester är fortfarande gröna ([`rotprojekt-clean-check.txt`](etapp2-testutdata/rotprojekt-clean-check.txt)). | verkstad | ACCEPT |
| E2-K13 | APK för målenheten | `offlinekedja-etapp2-arm64-debug.apk` med F-03-paketet: 137 276 668 byte, SHA-256 `631ca10a97072459dff62988aef78620b658b5db6d8ae23289c28181d199ef2b`, `native-code: arm64-v8a`, minSdk 26, targetSdk 36, debug-signerad ([`apk-sha256.txt`](etapp2-testutdata/apk-sha256.txt)). Emulatorns x86_64-variant: 137 929 180 byte, `b71d1ed8…99a7`. | verkstad | ACCEPT som bygge. Körning på målenheten: NOT_VERIFIED. |
| G2 | Kedjan offline på målenheten (08) | Verkstad och emulator: visat ovan. Målenheten, första omgångens APK (`e670e33b…`) i flygplansläge: ägaren körde alla tre turerna, Grönklitt nådde "Framme" och avvikelseprovet "verkade fungera" (avsnitt 9). Ingen logg, ingen inspelning, inga tider. Talinstruktionerna utlöses men uttalas inte (ingen talmotor, G4); att de utlöstes på målenheten är inte observerat. F-03-APK:n (`631ca10a…`) är inte provad på målenheten. | målenhet (ägariakttagelse) | **NOT_VERIFIED.** Ägariakttagelsen stöder rutt, navigering och framkomst offline på målenheten (gamla APK:n). Loggbevis för avvikelse och omräkning, talinstruktioner och tider saknas. |
| G3 | MapLibre med lokalt kartpaket på målenheten (08) | Visat i emulatorn. På målenheten körde ägaren turerna i flygplansläge med kartan i appen (gamla APK:n); att kartan ritades är underförstått i "verkade fungera" men inte uttryckligen bekräftat eller fotograferat. Kriteriet kräver `karta.pmtiles` i `filesDir` (uppfyllt i koden), **tre stilfiler** (bara en finns), sanningsremsan otäckt och nätverksmätning på målenheten (inte gjord; flygplansläge är inte en mätning). | målenhet (ägariakttagelse) + emulator | **NOT_VERIFIED**; stilarna: REVISE |

Enhetstester i prototypen: 14 av 14 gröna även i andra omgången ([`junit-xml/`](etapp2-testutdata/junit-xml/)). Androids instrumenterade tester finns inte.

## 5. Avvikelser

| Id | Avvikelse | Skäl | Beslut av |
|---|---|---|---|
| E2-D1 | Valhalla-rutorna byggs med Valhalla 3.9.1 (pyvalhalla) medan appen läser med 3.9.2 | 3.9.2 finns inte på PyPI; en äldre skrivare mot en nyare läsare är den säkra riktningen. Emulatorn läste rutorna utan fel. | Granskare |
| E2-D2 | ~~Provområdet är en rektangel runt Orsa~~ **Stängd.** Ersatt av F-03-området (sex kommuner) | Ägarbeslut 2026-11-21 06.15. | Jimmy (F-03) |
| E2-D3 | Två öppna Google-bibliotek står i tillåtelselistan: `com.google.code.gson:gson` 2.10.1 (krävs av MapLibres GeoJSON-modul) och `com.google.guava:listenablefuture` 1.0 (tom markörartefakt som androidx kräver) | Varken nätverk, konto eller tjänst. **ACCEPT genom ägarbeslut 2026-11-21 06.15:** "Gson och listenablefuture får vara kvar, förbudet gäller Googles tjänster". Nya rader i listan kräver nytt ägarbeslut. | Jimmy |
| E2-D4 | En stilfil (mörk), inga textetiketter, inga glyfer eller sprites | Glyfer måste ligga lokalt; utan textlager hämtar MapLibre inget. G3 kräver tre stilfiler. | Etapp 4 |
| E2-D5 | Ingen talsyntes; talinstruktionerna loggas | G4 och val av talmotor (F-06, ingen Google) är en egen grind | – |
| E2-D6 | `ACCESS_NETWORK_STATE` ligger kvar i manifestet | Utan den kraschar MapLibres `ConnectivityReceiver` med `SecurityException` (sett i emulatorn). Behörigheten ger bara läsning av nätstatus. | Granskare |
| E2-D7 | Ferrostar core lägger till `ACCESS_FINE_LOCATION` och `ACCESS_COARSE_LOCATION` i manifestet. Prototypen ber aldrig om dem och använder bara simulerad position. | Behövs för verkliga turer (G5). | – |
| E2-D8 | Emulatorn körs med programvaru-CPU (TCG), inte KVM | KVM i verkstaden kraschar vid start av en virtuell CPU (`kvm_spurious_fault` i kärnloggen). Tider från emulatorn (Valhalla-start 162 s, rutt 1,3–5,6 s) är därför inte representativa. | – |
| E2-D9 | Prototypen ligger i `prototyp/offline-kedja/` på grenen `etapp2/offline-karta`, inte på en egen gren `spike/offline-chain` (08 §3) | Ägaren slår ihop via PR; koden är märkt kastbar och byggs separat från rotprojektet. | Jimmy |
| E2-D10 | `desugar_jdk_libs` (`com.android.tools`) läggs till | Ferrostar core 0.59.0 kräver core library desugaring. Byggverktyg från AOSP, inte en Google-tjänst. | – |
| E2-D11 | tilemaker ger inte byte-identisk utdata mellan körningar (11 589 244 mot 11 591 336 byte) | Trådning. Manifestets SHA-256 gäller den fil som faktiskt packas. | – |
| E2-D12 | Debug-signerad APK med osymboliserade Valhalla-bibliotek (`libvalhalla-wrapper.so` 133 MB okomprimerat) | Prototyp. Storleken på en releaseversion är inte mätt. | G5 |
| E2-D13 | APK:n med F-03-området är 137 MB (första var 64 MB); paketet ligger i APK:ns tillgångar och kopieras till `filesDir` vid första start, så det tar dubbel plats på enheten | Prototyp. Separat nedladdning eller sidladdning av paketet hör till G5. | G5 |
| E2-D14 | PMTiles byggs för gränsrektangeln, inte polygonen | tilemaker tar bara en rektangel; data utanför polygonen är redan bortklippt av osmium, så rutorna där är tomma. | – |

## 6. Iakttagelser

- **Valhallas svenska talbesked har fel som påverkar sanningsrösten.** "Kör söderut  Hantverkargatan" (ordet "på" saknas, dubbelt mellanslag), "Därefter Sväng" (stor bokstav mitt i meningen), vägnummer läses upp ("Badstugatan, 70.04", "Kaplansgatan, W 1000") och decimaler skrivs med punkt ("1.5 kilometer"). Det är Valhallas språkfil för sv-SE, inte vår kod. Måste rättas eller filtreras i `:truth:core` innan G4.
- **Avvikelsen syns en position för sent.** I både verkstaden och emulatorn gav den första avvikande positionen `NoDeviation` och först den andra `Deviation`. Vid en position per sekund betyder det cirka en sekunds fördröjning före omräkning.
- **En falsk avvikelse vid start.** När en ny tur startades direkt efter en annan såg Ferrostar den förra turens sista position, rapporterade 486 m avvikelse och räknade om rutten. Appen bör nollställa positionen innan en ny tur startar.
- **Upprepade besked efter omräkning.** Efter varje omräkning sades "Fortsätt på E 45 i 11 kilometer" igen (tre gånger under avvikelseprovet). Det strider mot andan i röstreglerna om upprepning och bör hanteras när rösten kopplas in.
- En av 64 talinstruktioner på gångrutten Orsa→Mora utlöstes inte i verkstadens simulering (63/64). Inte utrett.
- Systemgränssnittet i emulatorn svarade inte under en period ("System UI isn't responding", i andra omgången även "Quickstep isn't responding"), vilket beror på TCG. Appen fortsatte att köra.

**Andra omgången (F-03-området):**

- De fem gamla ruttsvaren blev byte-identiska trots nyare OSM-data och större område.
- Den falska avvikelsen vid start syns igen: avvikelseprovet startades direkt efter Orsa→Sveg och Ferrostar rapporterade 976,7 m avvikelse och räknade om.
- Efter avvikelseförsök 1 kom en extra avvikelse (`OffStepOnRoute`, 194 m) och en extra omräkning när positionen återvände till rutten. Inte utrett.
- Gångrutten Orsa→Älvdalen utlöste 82 av 84 talinstruktioner i verkstaden (jämför 63/64 Orsa→Mora). Inte utrett.
- Långa landsvägsrutter har få steg: Älvdalen→Idre 112 km har 5 steg och 6 talbesked. Första beskedet läser vägnumret: "Kör norrut på Dalgatan, 70."
- Paketinstallationen i emulatorn tog 33,6 s för cirka 95 MB (12,2 s för 16 MB i första omgången). Valhallas start tog 150 s (162 s förra gången), alltså inte märkbart längre med fler rutor. Båda under TCG.
- Kameran ramas in mot hela rutten när en tur startar, men knappraden och loggen täcker kartans nedre del, så Orsa→Sveg syns bara delvis.
- Emulatorns tidszon är UTC; tiderna i `etapp2-logg.txt` och logcat är UTC (06.42 svensk tid = 05.42 i loggen).

## 7. Vad som återstår på målenheten (G2 och G3)

1. Installera F-03-APK:n från den privata förhandsreleasen [`etapp2-prov-641cf5c`](https://github.com/JbmbAb/Pundarens-Guide-till-galaxen-/releases/tag/etapp2-prov-641cf5c) (`offlinekedja-etapp2-sex-kommuner-arm64-debug.apk`, 137 MB, SHA-256 `631ca10a…ef2b`). Ladda ner innan flygplansläget slås på.
2. Kör turerna i flygplansläge med wifi av, även **Orsa → Sveg (passagerare)**. Tryck **Stopp** mellan turerna.
3. Hämta loggen när plattan kan kopplas till en dator: `adb pull /sdcard/Android/data/se.pundaren.guide.prototyp.offlinekedja/files/` (`etapp2-logg.txt` och råa ruttsvar). Med loggen kan avvikelse, omräkning, talinstruktioner och tider bedömas på målenheten. En skärminspelning är frivillig.
4. GNSS på målenheten (F-04) provas med verkliga turer i G5.

## 8. Inte verifierat

På målenheten SM-X900: allt för F-03-APK:n. För första omgångens APK finns bara ägarens iakttagelse (avsnitt 9); loggbevis för kartvisning, avvikelse och omräkning, talinstruktioner, ruttid, kallstart, minne och batteri saknas. Nätverkstrafik mätt på enheten. Talsyntes och avbrott (G4). Tre stilfiler och textetiketter (G3). Verkliga GPS-positioner, GNSS på målenheten (F-04) och tio verkliga turer (G5). Atomär installation vid strömavbrott. Framkomst på de långa rutterna i emulatorn (bara verkstaden). Ruttning till Ljusdal, Rättvik och Älvdalen i appen (bara verkstaden). Oberoende granskning (F-07).

## 9. Målenheten: ägarens provkörning och enhetsfakta (2026-11-21)

### Enhetsfakta (F-04)

Avlästa ur ägarens skärmbild av Inställningar › Om surfplattan › Programvaruinformation, tagen 2026-11-21 kl. 08.38 och skickad i chatten. Skärmbilden ligger i [`etapp2-testutdata/malenhet-programvaruinformation-2026-11-21-0838.jpg`](etapp2-testutdata/malenhet-programvaruinformation-2026-11-21-0838.jpg). Den är granskad och visar bara enhetsuppgifter, inga privata uppgifter.

| Uppgift | Värde |
|---|---|
| Modell | SM-X900 (Galaxy Tab S8 Ultra, Wi-Fi) |
| One UI | 8.0 |
| Android | 16 |
| Säkerhetsuppdatering | 5 oktober 2026 |
| Basband/bygge | BP2A.250605.031.A3.X900XXUBEZJ3 |
| Kärna | 5.10.252-android12-9 |
| Knox | 3.12 |
| CSC | SAOMC_SM-X900_OXM_EUX_16_0002 EUX |

Enheten har Samsungs standardfirmware, och där finns "Google Play-systemuppdatering" (1 oktober 2026) och en post för Google-kontot i inställningarna. Appen använder ingen av dem: den saknar INTERNET-behörighet och har inga Google-tjänster (E2-K09, E2-K10). GNSS på enheten är fortfarande inte verifierad.

### Ägarens provkörning

| Uppgift | Värde |
|---|---|
| APK | Första omgångens, Orsa-rektangeln, SHA-256 `e670e33b2c77c1d3e2f9866a5a35a471c296c443c889f007c1064380c5538b2b`, från ägarens Google Drive |
| Läge | Flygplansläge |
| Turer | Orsa → Grönklitt (passagerare), Orsa → Mora (till fots), Avvikelseprov ×3 |
| Ägarens ord | "kört alla tre verkade fungera"; på frågan om Grönklitt nådde "Framme": "ja" (chatten 2026-11-21 08.36–08.40) |
| Bevisgrad | **Ägariakttagelse.** Ingen skärminspelning, ingen logg hämtad, inga tider. |

| G2/G3-kriterium | Läge på målenheten |
|---|---|
| Installation och start offline | Ägariakttagelse (gamla APK:n) |
| Rutt räknas i flygplansläge och navigering med simulerad position, framkomst | Ägariakttagelse: Grönklitt nådde "Framme" |
| Avvikelse minst 3 försök och omräkning utan nät | Ägariakttagelse ("verkade fungera"); antal upptäckta försök inte bekräftat. NOT_VERIFIED som loggbevis |
| Talinstruktioner utlöses | NOT_VERIFIED på målenheten (ingen logg; inget uttalas förrän G4) |
| Kartan ritas från `karta.pmtiles` | Underförstått, inte uttryckligen bekräftat. NOT_VERIFIED |
| Tre stilfiler | REVISE (bara en finns, E2-D4) |
| Sanningsremsan otäckt | NOT_VERIFIED på målenheten |
| Ingen nätverkstrafik (mätt) | NOT_VERIFIED (flygplansläge, ingen mätning) |
| Tider: paketinstallation, Valhalla-start, ruttid | NOT_VERIFIED |
| F-03-APK:n (`631ca10a…`) | Inte provad på målenheten |

### Leverans av F-03-APK:n

På ägarens begäran ("Lägg APK:n som en privat release i GitHub-repot") ligger F-03-APK:n som privat förhandsrelease: <https://github.com/JbmbAb/Pundarens-Guide-till-galaxen-/releases/tag/etapp2-prov-641cf5c>, fil `offlinekedja-etapp2-sex-kommuner-arm64-debug.apk`, SHA-256 `631ca10a97072459dff62988aef78620b658b5db6d8ae23289c28181d199ef2b`. Den gick inte att lägga på Google Drive, eftersom uppladdningen dit är begränsad till 100 MB och filen är 137 MB. Den gamla APK:n på Drive är ersatt för fortsatta prov.
