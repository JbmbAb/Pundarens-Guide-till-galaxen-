# Etapp 3 – protokoll: svensk navigeringsröst (Samsungs talmotor, offline)

| Uppgift | Värde |
|---|---|
| Datum | 2026-11-21 (Europe/Stockholm, UTC+1). Bygge 09.23, emulatorprov 09.16–09.29. |
| Gren | `etapp3/svenskt-tal`, utgår från `etapp2/offline-karta` (`161f760`). PR #6 är orörd. |
| APK byggd på | `7b10de24b2d5c5db3015a045225ffebf23e7484a` |
| Underlag | Ägarens egen fil `SvenskTalObserver.kt`, inklistrad i chatten 2026-11-21 09.02 (Jimmy Bruce). |
| Grind | **G4** (tal och avbrott) i [08](../v0.1/08-teknikverifieringsplan.md). |
| Utförare | Grok Bot. Ingen oberoende granskning (F-07). |

Statusreglerna gäller: `ACCEPT` bara med bevis i repot. **G4 är `NOT_VERIFIED`**: ingen uppläsning är hörd på målenheten.

## 1. Vad som byggdes

| Del | Var | Kort |
|---|---|---|
| Navigeringsröst | `prototyp/offline-kedja/app/src/main/java/se/pundaren/guide/prototyp/ui/SvenskTalObserver.kt` | Ägarens kod: Ferrostars `SpokenInstructionObserver` mot Samsungs talmotor `com.samsung.SMT`, svensk röst som inte kräver nät, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, takt 0,95. Ersätter prototypens observatör som bara loggade. Skapas i `onCreate`, `close()` i `onDestroy`. |
| Tvätt av talbesked | `…/sanning/tal/SvenskTalTvatt.kt` | Ren Kotlin i sanningslagret. Körs före `speak()`. Regler i avsnitt 3. |
| Upprepningsspärr | `…/sanning/tal/UpprepningsSparr.kt` | Ren Kotlin. Stoppar ett identiskt besked direkt efter en omräkning. |
| Arkitektur | `ArkitekturTest` | Två nya tester: `sanning.tal` importerar inget från Android, UI, Guiden eller Illogical Engine; manifestet frågar bara efter `TTS_SERVICE`. Navigeringsrösten är sanningslagrets röst och skild från Guidens röst. |

Loggraderna går till `etapp2-logg.txt` (hämtas med `adb pull`) och logcat-taggen `Etapp2`: `TAL REDO: <röst>, offline`, `TAL FEL: …`, `TAL EJ REDO: <besked>`, `TAL RÅTT: <Valhallas text>` (när tvätten ändrat något), `TAL #n: <uppläst text>`, `TAL FOKUS nekat`, `TAL UPPREPNING stoppad efter omräkning: …`.

## 2. Ändringar i ägarens kod

Ägarens fil är incheckad ordagrant först (`f67d401`). Varje ändring är en egen commit och märkt `// Ändring N` i koden.

| Nr | Commit | Ändring | Skäl |
|---|---|---|---|
| 1 | `06fb3c3` | `tal.currentEngine != "com.samsung.SMT"` → `tal.engines.none { it.name == "com.samsung.SMT" }` | Kompilerar inte: `TextToSpeech` har inget publikt `currentEngine` (det är dolt API). Kvarstående risk: finns Samsungs motor men bindningen misslyckas kan Android falla tillbaka på en annan motor; röstfiltret (svenska, inte nät) gäller ändå. |
| 2 | `4d6183e` | `<queries>` med `android.intent.action.TTS_SERVICE` i manifestet | Android 11+ döljer andra appars tjänster; utan raden hittar appen ingen talmotor (targetSdk 36). |
| 3a | `e6139ed` | `shutdown()` och `motor = null` när starten misslyckas (ingen motor, ingen svensk offlineröst) | Motorn läckte. |
| 3b | `7b7325a` | Kortvarigt ljudfokus med duckning (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, navigationsvägledning) före varje `speak()`, släpps i `onDone`/`onError`/`onStop` via `UtteranceProgressListener` och vid stopp, tystnad och `close()`. Ägarens ljudattribut återanvänds. | Annan ljuduppspelning sänks medan beskedet läses. Nekas fokus läses beskedet ändå och `TAL FOKUS nekat` loggas (ägarbeslut 2026-11-20 23.30: allt ska höras). |
| 3c | `10231c5` | `SvenskTalTvatt.tvatta()` före `speak()`; råtexten loggas som `TAL RÅTT` | Valhallas svenska fel (etapp2-protokollet §6). |
| 3d | `e135a7f` | `UpprepningsSparr`; `MainActivity` anropar `omrakning()` vid `isCalculatingNewRoute` och `nyTur()` när en tur startar | "Fortsätt på E 45 i 11 kilometer" upprepades efter varje omräkning. |
| – | `26a8e69` | Inkoppling i `MainActivity` | Uppgift 2. |
| 4 | `7b10de2` | Initieringslyssnaren postas alltid till huvudtråden (`huvudtrad.post`) i stället för `paHuvudtrad` | Fel hittat i emulatorn: utan installerad motor anropar Android lyssnaren synkront inifrån konstruktorn. Då var `motor` fortfarande `null`, `initiera()` gick ut tyst, `TAL FEL` loggades aldrig och motorn frigjordes inte ([`emulator-logcat-fore-andring4.txt`](etapp3-testutdata/emulator-logcat-fore-andring4.txt)). |

Allt annat i ägarens kod är oförändrat: motorvalet, röstvalet (svenska, `!isNetworkConnectionRequired`, `SE` först), takt och tonhöjd, `QUEUE_FLUSH`, tystnad, trådhantering och `close()`.

## 3. Tvättregler (`SvenskTalTvatt`)

Riktningsord, avståndsvärden och gatunamn ändras aldrig utöver mellanslag och versaler. Testat mot alla 241 unika besked i etapp2-ruttproven; 166 ändras ([`talvatt-fore-efter.txt`](etapp3-testutdata/talvatt-fore-efter.txt)).

| Regel | Före | Efter |
|---|---|---|
| Dubbla mellanslag; startbeskedet saknar "på" (bara mönstret Kör/Gå/Cykla + väderstreck + dubbelt mellanslag) | `Kör söderut  Hantverkargatan.` | `Kör söderut på Hantverkargatan.` |
| Liten bokstav mitt i meningen, bara instruktionsord i en vitlista efter "Därefter" eller "Om N meter," | `Därefter Sväng vänster in på Slaggatan.` / `Om 500 meter, Sväng höger …` | `Därefter sväng vänster in på Slaggatan.` / `Om 500 meter, sväng höger …` |
| Decimalkomma före längdenhet | `Fortsätt i 1.5 kilometer.` | `Fortsätt i 1,5 kilometer.` |
| Europaväg | `Fortsätt på E 45 i 11 kilometer.` | `Fortsätt på E45 i 11 kilometer.` |
| Vägnummer efter gatunamn tas bort | `… in på Badstugatan, 70.04.` / `… in på Kaplansgatan, W 1000.` / `Kör norrut på Dalgatan, 70.` | `… in på Badstugatan.` / `… in på Kaplansgatan.` / `Kör norrut på Dalgatan.` |
| Ensamt vägnummer efter "på", "mot" eller "ta" blir "väg N" som på skylten | `Fortsätt på W 1000 i 2 kilometer.` / `… in på 83, 84.` / `Fortsätt på E 45, 70 i 1.5 kilometer.` | `Fortsätt på väg 1000 i 2 kilometer.` / `… in på väg 83, väg 84.` / `Fortsätt på E45, väg 70 i 1,5 kilometer.` |
| Ensamt delnummer med punkt kan inte sägas säkert och tas bort med "in på" | `Sväng höger in på 70.04.` | `Sväng höger.` (finns inte i ruttproven, bara i test) |

Var vägnumren kommer ifrån: Valhalla hänger på OSM-taggen `ref` efter gatunamnet. `W 1000` är länsväg 1000 i Dalarna (länsbokstav W); `70.04` är Trafikverkets delnummer för en gren av riksväg 70 (`ref=70.04` på Badstugatan i Mora, 11 vägsträckor i utdraget). Skyltarna visar bara numret, därför "väg 1000". Gatunamnet står kvar, så inget hittas på.

## 4. Utfall

| Id | Kriterium | Bevis | Miljö | Status |
|---|---|---|---|---|
| E3-K01 | Ägarens kod kompilerar mot Ferrostar 0.59.0 | Gränssnittet ur `core-0.59.0.aar` (`javap`): `onSpokenInstructionTrigger(SpokenInstruction)`, `stopAndClearQueue()`, `setMuted(Boolean)`, `getMuteState(): StateFlow<Boolean>`. Ägarens signaturer och `muteState`-typ stämmer. Enda kompileringsfelet var `currentEngine` (ändring 1). | verkstad | ACCEPT |
| E3-K02 | Enhetstester | 31 av 31 gröna: `SvenskTalTvattTest` 9, `UpprepningsSparrTest` 6, `ArkitekturTest` 7, `PaketInstallerareTest` 8, `RuttFragaTest` 1 ([`junit-xml/`](etapp3-testutdata/junit-xml/)). | JVM | ACCEPT |
| E3-K03 | Inga Google-tjänster, ingen INTERNET-behörighet | `verifieraIngenGoogle` grön (samma två undantag enligt ägarbeslut E2-D3). APK-kontrollen: ingen INTERNET, 0 förbjudna Google-klasser i dex ([`apk-kontroll-arm64.txt`](etapp3-testutdata/apk-kontroll-arm64.txt)). Samsungs talmotor är en systemapp på enheten och inget beroende i appen. | verkstad | ACCEPT |
| E3-K04 | Envägsarkitekturen | `sanning.tal` utan Android/UI/Guide/motor (`ArkitekturTest`). Rotprojektets 96 tester gröna på `b4d67b5` ([`rotprojekt-clean-check.txt`](etapp3-testutdata/rotprojekt-clean-check.txt)); ändring 4 rör bara prototypen. | verkstad | ACCEPT |
| E3-K05 | Felvägen utan Samsungs motor kraschar inte och navigeringen fortsätter | Emulatorn (Android 15, ingen talmotor alls, flygplansläge): `TAL FEL: Samsungs talmotor kunde inte startas` 0,8 s efter start, därefter rutt, navigering, 3/3 avvikelser med omräkning; varje besked loggas som `TAL EJ REDO: <tvättat besked>`; 0 `FATAL EXCEPTION` ([`emulator-logcat-etapp3.txt`](etapp3-testutdata/emulator-logcat-etapp3.txt), [`emulator-tal-fel.png`](etapp3-testutdata/emulator-tal-fel.png)). | emulator | ACCEPT |
| E3-K06 | Tvätten i appen | I emulatorn: `TAL RÅTT: Kör söderut  Hantverkargatan. Därefter Sväng vänster …` → `Kör söderut på Hantverkargatan. Därefter sväng vänster …`; `E 45, Moravägen` → `E45, Moravägen`. | emulator | ACCEPT |
| E3-K07 | Upprepning efter omräkning stoppas | I emulatorn efter avvikelseförsök 2: `TAL UPPREPNING stoppad efter omräkning: Fortsätt på E45 i 11 kilometer.` | JVM + emulator | ACCEPT |
| E3-K08 | Uppläsning med svensk offlineröst, ljudfokus och duckning | Kräver Samsungs motor; finns inte i emulatorn. | målenhet | **NOT_VERIFIED** |
| G4 | Tal och avbrott (08) | Ingen uppläsning hörd, inga avbrott provade. | – | **NOT_VERIFIED** |

APK för målenheten: `offlinekedja-etapp3-tal-arm64-debug.apk`, 137 288 298 byte, SHA-256 `af00a9d76f91095e3b1d9cc44b1714247d7750e4e1060937243a1d41e43c6892`, F-03-paketet (`f03-sex-kommuner-20261121-0619`) inbyggt. Privat förhandsrelease i repot (ägaren godkände GitHub-förhandsreleaser som leveransväg 2026-11-21 08.33).

## 5. Iakttagelser

- Ferrostar lämnar också `ssml` i `SpokenInstruction`; ägarens kod och tvätten använder bara `text`.
- `QUEUE_FLUSH` avbryter ett pågående besked när nästa kommer. Med "Därefter …"-besked tätt efter varandra kan slutet klippas. Ska bedömas på målenheten.
- Den falska avvikelsen vid start (etapp2 §6) och den extra `OffStepOnRoute`-avvikelsen efter försök 1 syns igen. Tvätten och spärren påverkar inte dem.
- Tiderna i appens logg är UTC (emulatorns tidszon).

## 6. Vad ägaren gör på målenheten (G4)

1. **Svensk röst offline:** Inställningar › Allmän hantering › Text till tal. Välj **Samsungs talmotor** som motor. Tryck på kugghjulet vid motorn › Installera röstdata › **Svenska** och ladda ner rösten. Gör det med nät, före flygplansläget.
2. Ladda ner APK:n från förhandsreleasen (länken i PR:en) medan plattan har nät och installera den. Den ersätter den förra prototypen.
3. Slå på flygplansläge och stäng av wifi. Starta "Offlinekedja (etapp 2)".
4. Överst i loggen på skärmen ska det stå `TAL REDO: <röstnamn>, offline`. Står det `TAL FEL: …` är rösten eller motorn inte installerad; gör om steg 1.
5. Kör **Orsa → Grönklitt (passagerare)** och lyssna: besked ska höras på svenska. Spela gärna musik samtidigt och kontrollera att musiken sänks under beskedet och kommer tillbaka efteråt.
6. Kör **Avvikelseprov ×3**. Efter omräkningarna ska "Fortsätt på E45 i 11 kilometer" inte upprepas.
7. Om plattan kan kopplas till en dator: `adb pull /sdcard/Android/data/se.pundaren.guide.prototyp.offlinekedja/files/`. Annars räcker det att berätta vad som hördes, så skrivs det som ägariakttagelse.

## 7. Inte verifierat

Uppläsning på målenheten, vilken svensk röst Samsungs motor väljer, ljudfokus och duckning, uttal av "väg 1000", "E45" och "1,5 kilometer", klippta besked vid `QUEUE_FLUSH`, avbrott (samtal, andra appar), Guidens röst (finns inte än) och oberoende granskning (F-07).
