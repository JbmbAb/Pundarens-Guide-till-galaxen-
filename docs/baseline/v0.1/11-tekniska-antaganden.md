# 11 – Tekniska antaganden, risker och öppna frågor

Baslinje v0.1 · 2026-11-20

## 1. Antaganden som måste verifieras

Alla antaganden har status NOT_VERIFIED. Ett antagande blir ACCEPT först när grinden i kolumnen har ett protokoll som bevisar det.

| Id | Antagande | Varför det spelar roll | Hur det verifieras | Grind | Status |
|---|---|---|---|---|---|
| A-01 | valhalla-mobiles OSRM-svar innehåller allt Ferrostar behöver: steg, manövrer, röst- och bannerinstruktioner, geometri med rätt precision | Utan det fungerar inte kedjan | Prototyp i flygplansläge (08, avsnitt 3), logga hela svaret | G2 | NOT_VERIFIED |
| A-02 | Ferrostars `CustomRouteProvider` kan anropa valhalla-mobile synkront eller via coroutine utan nät | Bärande för offline | Samma prototyp | G2 | NOT_VERIFIED |
| A-03 | Ferrostars avvikelsedetektering och omräkning fungerar mot en lokal ruttkälla | Startprompten §7.2 | Simulerad körning som avviker minst tre gånger | G2 | NOT_VERIFIED |
| A-04 | valhalla-mobile och Ferrostar fungerar med samma AGP-, Kotlin- och NDK-versioner som resten av appen | Byggbarhet | Kompilera prototypen med låsta versioner | G2 | NOT_VERIFIED |
| A-05 | Valhalla-rutor byggda i verkstaden med samma Valhalla-version som valhalla-mobile (badge 3.9.2) läses av biblioteket | Paketformatet | Bygg och läs i prototypen | G2 | NOT_VERIFIED |
| A-06 | MapLibre Android visar en lokal PMTiles-fil i `filesDir` utan nät, med tre stilfiler | Startprompten §7.3 | Målenhet i flygplansläge, nätverksloggning | G3 | NOT_VERIFIED |
| A-07 | Ferrostars MapLibre-UI kan kombineras med vår egen sanningsremsa och svansyta utan att täcka manöverinformation | N-4, N-6 | Skärmdumpar och UI-test | G3 | NOT_VERIFIED |
| A-08 | Det finns en svensk talmotor som inte kommer från Google och som är begriplig nog för besked | K-16. Jimmy vill inte ha Google. | Lyssningstest på målenheten av Samsungs svenska talmotor (`com.samsung.SMT.lang_sv_se_f00`, uppgift ur G0-beslutsbladet avsnitt 5, NOT_VERIFIED på enheten) och av Piper-röst via sherpa-onnx (08, T-22, T-23) | G4 | NOT_VERIFIED |
| A-09 | `TextToSpeech` och Ferrostars `AndroidTtsObserver` kan delas i två kanaler där besked avbryter svans inom 100 ms | R-01 i 06 | Mätning på målenhet | G4 | NOT_VERIFIED |
| A-10 | Offline-paketet för testområdet ryms på telefonen och laddas tillräckligt fort | Startprompten §7.5 | Mät filstorlekar, installationstid, ruttid, kallstart och batteri | G5 | NOT_VERIFIED |
| A-11 | Atomär rename fungerar på appens interna lagring och klarar avbrott | 04, 3.3 | Avbrottstest: döda processen i varje steg | G5 | NOT_VERIFIED |
| A-12 | `AndroidSystemLocationProvider` (utan Google Play-tjänster) ger tillräcklig noggrannhet och uppdateringstakt | Ingen Google (K-17) | Fälttest jämfört med verkligt spår | G5 | NOT_VERIFIED |
| A-13 | SQLite-triggers kan skapas och överlever migreringar i vald databasteknik (Room eller SQLDelight) | Skrivskyddet för original | Migreringstest | G0 | NOT_VERIFIED |
| A-14 | Gradle-beroendegrafen kan testas automatiskt i CI (t.ex. modules-graph-assert eller eget task) | 03, G-6 | Bygg ett förbjudet beroende och se att CI fallerar | G1 | NOT_VERIFIED |
| A-15 | En Konsist-liknande arkitekturkontroll kan hindra sanningstyper i `TailCandidate` | 03, G-7 | Negativt test | G1 | NOT_VERIFIED |
| A-16 | `sources_to_targets` räcker för osannolikhetsdriften och mötespunkten, eftersom isokroner saknas | 08, T-06 | Prototyp i etapp 5 | G6 | NOT_VERIFIED |
| A-17 | libsodium (t.ex. via Lazysodium) fungerar på målplattformen för XChaCha20-Poly1305 | 07 | Prototyp och granskning | G7 | NOT_VERIFIED |
| A-18 | Androids bakgrundsbegränsningar tillåter att avsändaren stoppar sändning vid sluttid även i bakgrunden | Byggplanens grind för Convoy | Test med appen i bakgrunden och i dvala | G7 | NOT_VERIFIED |
| A-19 | En lokal språkmodell på telefonen ger användbar frågebokföring på svenska, eller så finns en LAN-maskin | Startprompten §9 | Prototyp med inklistrade testsamtal (påhittade, inte verkliga) | G8 | NOT_VERIFIED |
| A-20 | Lantmäteriets produkter som behövs är CC0 och kan kombineras med OSM (ODbL) i samma paket med korrekt upphovsangivelse | Licenser i manifestet | Kontroll i Geotorget och licenstexter | G5 | NOT_VERIFIED |

## 2. Risker

| Id | Risk | Följd | Åtgärd |
|---|---|---|---|
| R-01 | Ferrostar är beta och lovar ingen API-stabilitet (08, T-10) | Uppgraderingar kan kräva omskrivning | Lås version i G2. Kapsla Ferrostar i `:truth:core`. Om kedjan faller: minsta ändring först (egen parser eller översättning), byte av bibliotek sist. |
| R-02 | valhalla-mobile är ett litet projekt (84 stjärnor 2026-11-20) | Underhållsrisk | Lås version, följ releaser, behåll möjligheten att bygga själv (MIT) |
| R-03 | Svensk talsyntes utan Google kan låta dåligt | Rösten är kärnan | G4 tidigt och parallellt |
| R-04 | Rösten är inte rolig | Byggplanen: "är den inte rolig spelar resten ingen roll" | Etapp 2 före kartan. Jimmy granskar radbanken rad för rad. |
| R-05 | Verkliga personer i arkivet (Rikard, Bonnie) | Integritet och juridik | Allt märks `PRIVATE`. Ingen spridning före samtycke. Verkliga utdrag inte i appen (R-001–R-003 utanför radbanken, D-8). Repot förblir privat på GitHub. **Ägarbeslut 2026-11-20 kl. 23.30 (Europe/Stockholm), ordagrant "I helvete allt ska höras lägg av med detta trams":** märkningen stoppar inte längre uppspelning; allt får höras på alla ljudutgångar. Accepterad risk: passagerare kan höra privata namn och relationsinnehåll. |
| R-06 | Oberoende granskare saknades (M-14) | Säkerhetsgrindar kan inte bli ACCEPT utan granskning enligt F-07 | Jimmy har utsett GPT-6, oberoende från byggaren, med kraven i F-07. Granskning och protokoll saknas fortfarande. |
| R-07 | `:app` ser alla moduler | Kan visa en svans som om den vore ett besked | SafetyGate renderar svansar bara i svansytan, plus granskning |

## 3. Frågor till Jimmy – läge efter ägarbeslutet 2026-11-20

Svaren kommer från [`../verification/G0-agarbeslut-2026-11-20.md`](../verification/G0-agarbeslut-2026-11-20.md) och [`../verification/G0-instruktion-2026-11-20.md`](../verification/G0-instruktion-2026-11-20.md). Sakliga avvikelser står i [`../verification/G0-agarbeslut-svar.md`](../verification/G0-agarbeslut-svar.md). Inget här gör en grind till ACCEPT. G0 är öppen (NOT_VERIFIED).

Markeringar som i beslutsbladet: **BESLUTAT**, **DOKUMENTERAT**, **ÖPPEN**.

| Id | Fråga | Läge | Svar | Behövs för |
|---|---|---|---|---|
| F-01 | Godkänner du baslinjen v0.1 som underlag, eller vill du ändra något? | **BESLUTAT** | Baslinje v0.1 godkänd som arbetsbaslinje. | Etapp 0 |
| F-02 | Vilket designsystem och vilken variant av varje skärm? | **BESLUTAT** | The Sentient Archive med skärmvarianterna som har rubriken Handdukssensorn. Precisering i beslutsbladet avsnitt 2 (kandidat, NOT_VERIFIED på enheten): bakgrund `#0e0e0e`, lila `#d896fe` och `#c59aff`, Space Grotesk för rubriker, Manrope för löptext; från Cosmic Drift turkos `#4af8e3` som sanningsfärg bara i sanningsremsan och på nödkortet; turkos och lila aldrig som enda skillnad mellan ytor intill varandra; sanningsremsan har raka hörn, Manrope, ingen glaseffekt, glitch, drift eller asymmetri. Turkost är sant. Lila är Guiden. B-17 stängs. PR1-20 kan prövas. | G0, G3 (A-07) |
| F-03 | Vilket geografiskt testområde? | **BESLUTAT**, med öppen precisering | Orsa kommun och angränsande kommuner. Exakt vilka grannkommuner som ingår avgörs när paketgränsen ritas. | G2, G5 |
| F-04 | Vilken enhet är målenhet (modell, Android-version, ledigt lagringsutrymme)? | **BESLUTAT**, med öppen verifiering | Samsung Galaxy Tab S8 Ultra, SM-X900 (Wi-Fi-modellen). Surfplatta på 14,6 tum, inte en telefon. Android-version och faktisk GNSS-funktion verifieras på enheten före G2. Ledigt utrymme cirka 60 GB enligt Jimmy 2026-11-20, mäts igen vid G5 (A-10). Se beslutsbladet avsnitt 5. | G2–G5 |
| F-05 | Vilka färdsätt gäller i v1? | **DOKUMENTERAT** | Till fots och som passagerare. Inte skoter eller ATV. Fordonsreglerna gäller så fort enheten rör sig i fordonsfart. Källa: 02 (etapp 4), 08 G5, B-58. | G5 |
| F-06 | Gäller "ingen Google" också talsyntesmotorn och Tink? | **DOKUMENTERAT** | Ja. libsodium i stället för Tink. Talmotor väljs i G4. Källa: S-20, B-46, B-59, B-60. | G4, G7 |
| F-07 | Vem är oberoende granskare för säkerhetsgrindarna? | **BESLUTAT** | GPT-6, oberoende från byggaren. Granskningen ska omfatta faktisk arkitektur, kod och testbevis. En annan modellbeteckning räcker inte ensam för att styrka säkerheten. Granskaren får repot, protokollen och testutdata, inte byggarens resonemang eller sammanfattningar. Påståenden utan bevis i repot får NOT_VERIFIED. Varje grind får ett eget granskningsprotokoll med commit-hash, vad som kördes och utfall per kriterium. Byggaren rättar inte granskarens protokoll; invändningar skrivs som svar bredvid. Jimmy fattar grindbeslutet. | Alla grindar (R-06, M-14) |
| F-08 | Har Rikard tillfrågats, och får hans namn och R-001–R-003 användas i appen? | **ÖPPEN** (blockerar inte G0) | Till dess gäller 11 R-05: allt `PRIVATE`, Rikard omnämns men hörs aldrig, inga verkliga utdrag i appen. Avgörs före etapp 2 och spridning. | Etapp 2, spridning |
| F-09 | Bekräftar du att C-008 och C-009 är godkända bearbetningar, och när du godkände dem? | **DOKUMENTERAT** | C-008 och C-009 är godkända bearbetningar ur den gemensamt bearbetade meddelandetexten 17 november 2026. Källa: `data/quotes/README.md`, 04 avsnitt 1.3. Hänvisningen till bilagan rad 216–222 är inte kontrollerad, eftersom bilagan är MISSING. | Arkivimport |
| F-10 | Ska RED-P-007 och den normaliserade P-001 godkännas som `APPROVED_EDIT` eller stå kvar som syntetiska? | **BESLUTAT** i dokumenten | Båda godkänns som `APPROVED_EDIT`. Arkivimporten godkänd av Jimmy 2026-11-20. Originalraderna ligger kvar orörda bredvid. *Datafilen `data/quotes/quotes.json` är inte ändrad i den här omgången.* | Arkivimport |
| F-11 | Vilka av Grok Bots förslag godkänner du eller stryker du? | **BESLUTAT** 2026-11-20 kl. 23.36 (Europe/Stockholm) | Ordagrant: "Godkänn allt är det något som inte passar går att radera senare". Alla 65 rader godkända; lånade namn som arbetsnamn (B-12); rader med riktningsord eller beskedsrisk bara till fots. Ursprungsklass och författare oförändrade. Rader kan strykas senare. Underlag: [`../verification/F-11-radgranskning.md`](../verification/F-11-radgranskning.md). | Etapp 1 |
| F-12 | Får `EX-01-FORMULERING` och PAT-01–PAT-05 användas i appen, eller ska de bara finnas i arkivet? | **DOKUMENTERAT** | Bara i arkivet. Visas inte i appen. Källa: B-29, 11 R-05. | Etapp 7 |
| F-13 | Finns det personliga projektarkivet, och hur ska baslinjen arkiveras där? Finns falsifieringsfilen, bokunderlaget och bilagan på 912 rader? | **ÖPPEN** | Beslutsbladet avsnitt 6 återger en redogörelse om Google Drive (`Personligt_Arkiv`). Det är inte verifierat i det här arbetet. `Citat_och_paradoxer_falsifiering.md` och "bilagan på 912 rader" (M-02) står kvar som MISSING i repot och får inte rekonstrueras. Arkivering av baslinjen i arkivet är inte gjord. | M-01, M-02, M-04, M-05 |
| F-14 | Vad är "Mimer"? | **DOKUMENTERAT** | Mimer är Jimmys eget företag och plattform. Appen har inget beroende dit. Stänger M-07. Inte kontrollerat mot någon annan källa. | M-07 |
| F-15 | Var ska Convoy-servern köras? | **ÖPPEN** (blockerar inte G0) | Avgörs i etapp 6. | Etapp 6 |
| F-16 | Ska PR #1 stängas eller lämnas öppen som referens? | **DOKUMENTERAT**, åtgärden redan utförd | Rekommendationen i 10: stäng utan sammanslagning och behåll grenen som referens. PR #1 stängdes utan merge den 2026-11-20 kl. 19:40 UTC+1 (Europe/Stockholm); grenen `cursor/android-milestone-1-7e26` finns kvar. Beslutsbladet avsnitt 3 säger att åtgärden inte är utförd; det är en saklig avvikelse, se svarsfilen. | Beslutslogg PR1 |
| F-17 | Vilken licens ska kod och arkiv ha? | **ÖPPEN** (blockerar inte G0) | Avgörs före spridning. Repot har ingen LICENSE-fil. | Spridning |
