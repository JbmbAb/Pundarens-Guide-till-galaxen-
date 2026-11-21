# 01 – Inventering av underlag

Baslinje v0.1 · 2026-11-20 · Bas-commit `main` @ `969ed14`, PR #1 @ `eacb014`

Varje källa har ett id (S-xx). Status beskriver om källan finns och kunde läsas i sin helhet, inte om innehållet är rätt. Innehållets status står i [10-beslutslogg.md](10-beslutslogg.md).

| Status | Betydelse |
|---|---|
| TILLGÄNGLIG | Finns och har lästs i sin helhet |
| DELVIS | Finns men är ofullständig eller hänvisar till något som saknas |
| MISSING | Hänvisas till men har inte funnits åtkomligt. Innehållet har **inte** rekonstruerats. |

## 1. Styrande underlag

| Id | Källa | Plats | Status | Anmärkning |
|---|---|---|---|---|
| S-00 | Jimmys startprompt, 2026-11-20 | `docs/baseline/00-startprompt.md` | TILLGÄNGLIG | Sparad ordagrant. Senast och bindande. |
| S-01 | "Pundarens Guide till Galaxen – utvärdering och byggplan.md", 2026-11-20, @Jimmy | `docs/baseline/source/` | DELVIS | Läst i sin helhet (243 rader). Den hänvisar till ett inbäddat diagram ("två lager · enkelriktat flöde") som inte finns med i md-filen, se M-03. Den bygger på "bilagan på 912 rader", se M-02. |

## 2. Projektmaterial i repot

| Id | Källa | Plats | Status | Anmärkning |
|---|---|---|---|---|
| S-02 | Reviderad PRD v0.1 | `docs/archive/PRD-fore-2026-11-21.md` (456 rader, låg i `docs/PRD.md` till 2026-11-21) | TILLGÄNGLIG | Skriven av Grok Bot 2026-11-20 och uppdaterad i `0eb7b87`. **Ersatt som utvecklingsunderlag** av den här baslinjen men oförändrad. Den tidigare versionen (`dfe7338`) är identisk med chattbilagan `596fe3c…md`. |
| S-03 | Stitch: slutlig PRD | `docs/design/original-briefs/final_project_prd_pundarens_v_n_till_galaxen.html` | TILLGÄNGLIG | Original, oförändrad |
| S-04 | Stitch: projektbrief | `docs/design/original-briefs/project_brief_pundarens_v_n_till_galaxen.html` | TILLGÄNGLIG | Original, oförändrad |
| S-05 | Stitch: uppdaterad brief | `docs/design/original-briefs/updated_project_brief_stoner_s_friend_to_the_galaxy.html` | TILLGÄNGLIG | Original, oförändrad |
| S-06 | Designsystem Cosmic Drift | `docs/design/design-systems/cosmic_drift/DESIGN.md` | TILLGÄNGLIG | Ett av två system. Valet är öppet (F-02). |
| S-07 | Designsystem The Sentient Archive | `docs/design/design-systems/sentient_archive/DESIGN.md` | TILLGÄNGLIG | Se S-06 |
| S-08 | Stitch-skärmar | `docs/design/screens/` (9 mappar) | DELVIS | Åtta har `screen.png`. `cosmic_hud_hitchhiker_s_guide_companion` har bara `code.html`. Byggplanen räknar "åtta skärmar". Ingen skärm har svängpil, gatunamn eller ankomsttid (byggplanen, ändring 4). |
| S-09 | Citatarkiv | `data/quotes/quotes.json` (schema 2) och `README.md` | TILLGÄNGLIG | 22 poster: 19 `original`, 2 `bearbetad` (C-008, C-009), 1 `redaktionell` (RED-P-007). 20 variationer V-001–V-020 (`approved: false`). |
| S-10 | Lexikon | `data/lexicon/lexicon.json` | TILLGÄNGLIG | 13 poster: "Garderobsknullis" (huvudversion från arkivet) och 12 `förslag` från Grok Bot. |
| S-11 | Babel-Stoner-regler | `data/babel-stoner/rules.json` | TILLGÄNGLIG | BS-01–BS-10 från arkivet, BS-11–BS-16 `förslag`. Mönster PAT-01–PAT-05 med status `hypotes`. |
| S-12 | Babel-Stoner-exempel | `data/babel-stoner/examples.json` | TILLGÄNGLIG | 12 exempel, alla `konstruerad`. `EX-01-FORMULERING` återger dock en formulering ur verklig kommunikation (se K-15). |
| S-13 | Navigationskommentarer | `data/babel-stoner/navigation_comments.json` | TILLGÄNGLIG | NAV-001–NAV-020, `konstruerad`, Grok Bot-förslag |
| S-14 | GeoJSON, Stockholms län | `data/geojson/stockholms-lan/` | TILLGÄNGLIG | toilets 892, rest_areas 17, munchie_stations 2 292 objekt. OSM via Overpass 2026-11-19, ODbL. Inte ett offline-paket enligt 04-datamodell. |
| S-15 | Citatarkivets källsammanställning, "Citatarkiv och samtalslogik för Babel-Stoner", 2026-11-20 | `docs/baseline/source/…(sammanställning 2026-11-20).txt` | DELVIS | Kopierad från chattbilagan i den här baslinjen. Den säger själv att den inte är "en fullständig inventering". Den hänvisar till `Citat_och_paradoxer_falsifiering.md` och till ett bokunderlag, se M-04 och M-05. |
| S-16 | PR #1, "Android-milstolpe 1" | gren `cursor/android-milestone-1-7e26` @ `eacb014` | TILLGÄNGLIG | 41 filer, +4 102 rader. Inventerad komponent för komponent i [10-beslutslogg.md](10-beslutslogg.md#pr-1). Inte sammanslagen. |
| S-17 | README.md | repots rot | TILLGÄNGLIG | Anger Google Maps som stack och "ingen Android-kod ännu". Båda är inaktuella (K-03, K-19). |

## 3. Tidigare förslag och beslut i chatten

Chattens rådata finns inte i repot (M-06). Följande är hämtat ur konversationssammanfattningen och de tidigare svaren och loggas som förslag, inte som källor med eget innehåll.

| Id | Förslag eller beslut | Datum | Vem |
|---|---|---|---|
| S-18 | Börja med GeoJSON i appen, lokal PostGIS senare | 2026-11-19 | Jimmy ("Postgis kommer senare börja med annan lösning") |
| S-19 | Google Maps SDK och maps-compose som karta | 2026-11-19 | Grok Bot, i PRD v0.1 och PR #1 |
| S-20 | Ingen Google alls | 2026-11-20 | Jimmy ("vill inte vara i närheten av google") |
| S-21 | MapLibre för kartan och GraphHopper eller Valhalla som ruttmotor på egen server, PostGIS bakom Martin eller pg_tileserv | 2026-11-20 | Grok Bot |
| S-22 | Proveniensnivåerna original, bearbetad, redaktionell, konstruerad, variation/förslag | 2026-11-20 | Grok Bot, efter Jimmys krav på åtskillnad |
| S-23 | Grok Bots förslag V-001–V-020, BS-11–BS-16, lexikonförslag och NAV-001–NAV-020 | 2026-11-20 | Grok Bot på Jimmys begäran ("Fyll på med egna förslag"). Alla `approved: false`. |
| S-24 | Repot görs privat och hela arkivet läggs in | 2026-11-20 | Jimmy. Verifierat: repot är privat (`private: true` enligt GitHub API 2026-11-20). |

## 4. MISSING

Inget av följande har rekonstruerats.

| Id | Vad saknas | Var det hänvisas | Följd |
|---|---|---|---|
| M-01 | **Det personliga projektarkivet** | Startprompten §12 | Ingen åtkomst. **Ingenting är arkiverat där.** Den enda verifierade lagringsplatsen är GitHub-repot. |
| M-02 | "Bilagan på 912 rader", byggplanens underlag | Byggplanen, avsnittet "Kontrollerat och okontrollerat" | Inte identifierad. `docs/PRD.md` har 456 rader och är alltså inte den. Byggplanens kritik av "Förslaget kräver en server med Valhalla, PostGIS och Martin, plus en annan ruttmotor för offline" kan inte spåras till sin källtext. |
| M-03 | Byggplanens inbäddade diagram "två lager · enkelriktat flöde" | Byggplanen, avsnittet "Grundlagen" | Bara platshållartexten finns i md-filen. 03-arkitektur.md bygger på byggplanens text, inte på diagrammet. |
| M-04 | `Citat_och_paradoxer_falsifiering.md` | S-15 | Råkällan för P- och C-posterna. Utan den kan ingen post få verifieringsstatus `VERIFIED_SOURCE` (se 04-datamodell). |
| M-05 | Bokprojektets arkivunderlag | S-15, R-001–R-003 | Samma följd som M-04, för R-posterna |
| M-06 | Råa chattexporter, meddelanden och ljudtranskriptioner | S-15 ("inte en fullständig inventering"), quotes README ("återgivna från tidigare chattindex") | P-posterna kan inte kontrolleras mot råexport. De klassas som återgiven minnesuppgift tills motsatsen visats. |
| M-07 | "Mimer" | Byggplanen ("Mimer gör beslut spårbara") | Dokumenterat 2026-11-20 (F-14): Jimmys eget företag och plattform. Appen har inget beroende dit. Inte kontrollerat mot någon annan källa. |
| M-08 | Röstreglerna på en sida | Byggplanen, etapp 1 | Kandidat finns i [12-rostregler.md](12-rostregler.md). Inte fryst. G0 är fortfarande NOT_VERIFIED. |
| M-09 | Radbanken med cirka 150 rader och tolv händelsetyper | Byggplanen, Illogical Engine | Finns inte. NAV-001–NAV-020 är en början men är oprövade förslag. |
| M-10 | Målenhet (modell, Android-version, lagring) | Startprompten §7 punkt 3 och 5 | Modell beslutad (F-04): Samsung Galaxy Tab S8 Ultra, SM-X900 (Wi-Fi). Android-version, GNSS och lagring verifieras på enheten. |
| M-11 | Geografiskt testområde ("hemområdet") | Byggplanen, etapp 4. Startprompten §10 punkt 4 | Orsa kommun och angränsande kommuner (F-03). Exakta grannkommuner är inte satta. |
| M-12 | Leddata med verifierad status för skoter och ATV | Byggplanen "Parkerat", startprompten §7 | Ingen leddata finns. Automatisk ledruttning är därför uteslutet. |
| M-13 | Rikards samtycke | Byggplanen "Gränser" | Krävs före spridning. Okänt om det finns. |
| M-14 | Oberoende granskare | Startprompten §11, byggplanen "kall granskning" | GPT-6 utsedd (F-07), oberoende från byggaren, med krav på arkitektur, kod och testbevis. Granskning och protokoll saknas. Utan sådant protokoll kan ingen säkerhetsgrind bli `ACCEPT`. |
| M-15 | Lantmäteriets data (Topografi 50 med flera) | Byggplanen "Tekniken som räcker" | Inte nedladdad. Licensen CC0 ska kontrolleras i Geotorget. |

## 5. Konflikter mellan underlagen

Kolumnen "Avgörs av" anger vilket underlag som vinner enligt rangordningen i [README](README.md). Den fullständiga motiveringen står i beslutsloggen.

| Id | Konflikt | Underlag A | Underlag B | Avgörs av | Utfall |
|---|---|---|---|---|---|
| K-01 | Anti-navigering | PRD 4.4 och Stitch-PRD: "Ge aldrig direkta order", anti-direktiv som "Sväng inte höger" | Byggplanen, ändring 1: beskedet först, rakt och i fast form, tramset efteråt | Byggplanen | Anti-direktiv i fordon REJECT. Full anti-navigering bara till fots och bara som svans. |
| K-02 | Stress-mikropauser | PRD 4.4 och Stitch-PRD: gränssnittet "suckar" och visar citat före uppdatering | Byggplanen, ändring 2. Startprompten §4 ("Ingen humor får fördröja") | Startprompten och byggplanen | REJECT. Under stress tystnar tramset, sucken kommer efteråt. |
| K-03 | Kartmotor | PRD 10, README och PR #1: Google Maps SDK och maps-compose | Byggplanen: MapLibre. Jimmy: ingen Google | Byggplanen och Jimmy | Google Maps REJECT |
| K-04 | Ruttmotor | PRD 13.3 (Google Directions, OSRM eller GraphHopper). Grok Bots chattförslag S-21 (GraphHopper eller Valhalla på server) | Byggplanen och startprompten §7: valhalla-mobile på enheten, ingen GraphHopper | Startprompten | Server- och GraphHopper-varianterna REJECT för v1 |
| K-05 | PostGIS roll | PRD 9.2–9.3: appen frågar ett API framför PostGIS med bbox (`RemotePlacesRepository`) | Byggplanen och startprompten §6: PostGIS är verkstad hemma, telefonen kontaktar den aldrig för att navigera | Startprompten | `RemotePlacesRepository` REJECT. Ersätts av offline-paket. |
| K-06 | Ursprungsklasser | Repot: sex proveniensvärden | Byggplanen: tre klasser (ORIGINAL, BEARBETAD, GENERERAD). Startprompten §5: fyra klasser | Startprompten | Fyra klasser plus en separat verifieringsstatus. Mappning i 04-datamodell. |
| K-07 | Synlighet | Repot och PR #1: saknas | Byggplanen: privat, sällskap, öppen. Startprompten: synlighetsklass, privat som standard för relationscitat | Båda överens | Införs (04-datamodell) |
| K-08 | Babel-Stoners huvudfunktion | PRD 5.3: fem personligheter är huvudläget, omvänt läge sekundärt | Byggplanen: omvänt läge blir huvudfunktion ("frågebokföring"), generatorn blir ett spel med fem motståndare. Startprompten §9: två separata funktioner | Startprompten, preciserad av byggplanen | Två funktioner: Satirläge (spelet) och Analysläge (frågebokföring) |
| K-09 | Live-AI och språkmodell | PRD 13.2: öppen fråga (regel, LLM eller hybrid) | Byggplanen: live-AI aldrig i navigatorn, bara Babel-Stoner, encyklopedin och Don't Panic. Startprompten §9: lokal modell på telefonen eller uttrycklig LAN-tjänst, ingen moln som standard | Båda, ihop | Lokal modell bara i Analysläge och encyklopedin, aldrig i manövervägen och aldrig i moln som standard |
| K-10 | Röstens personlighet | PRD 2: "Yoda on a Bad Day", Inverted Grammar | Byggplanen, ändring 3: lugn myndighetssvenska, ingen omvänd ordföljd. Startprompten §2: åtta mekanismer utan omvänd ordföljd | Byggplanen och startprompten | Inverted Grammar REJECT |
| K-11 | Lånade namn | Stitch-skärmar och PRD: Heart of Gold, Milliways, Vogon, Marvin Mode, Vogon Poetry. Lexikonförslaget "vogonko" | Byggplanen: byt mot egna före spridning | Byggplanen | REVISE: arbetsnamn tillåtna privat, byts före spridning |
| K-12 | Convoy-säkerhet | Byggplanen: delad nyckel i QR, avsändaren äger tidsgränsen, servern ser bara krypterad data | Startprompten §8: självhostad HTTPS/WebSocket där **servern** kontrollerar tillstånd och utgångstid | Båda (kompletterar) | Båda lagren krävs: transportkryptering, nyttolastkryptering, tidsgräns hos avsändaren och hos servern (07) |
| K-13 | Omfattning v1 | PRD: alla funktioner från Stitch behålls (Vibe Protect, slang, temamotor med mera) | Byggplanen: version 1 är två saker, rösten och en offline-navigator för ett område. Vibe Protect "aldrig i kärnan" | Byggplanen | Hela visionen dokumenteras som slutmål. v1 avgränsas (02-PRD). |
| K-14 | Skoter och ATV | Tidigare briefer: navigering överallt | Byggplanen och startprompten §7: ingen automatisk ledruttning utan verifierat underlag | Startprompten | Karta, leder som lager, eget spår och återväg |
| K-15 | Verkliga repliker i Babel-Stoner | Sammanställningen och PRD 5: mönster PAT-01–PAT-05 bygger på observationer av verkliga samtal, och `EX-01-FORMULERING` återger en formulering ur verklig kommunikation (utan arkiv-id) | Byggplanen: verkliga utdrag stannar i arkivet, appen får regler och nyskrivna exempel | Byggplanen | REVISE: `EX-01-FORMULERING` och PAT-01–PAT-05 får inte visas i appen innan Jimmy har granskat dem. Övriga exempel är märkta som nyskrivna. |
| K-16 | Talsyntes kontra "ingen Google" | Startprompten §7: Androids talsyntes | Jimmy: ingen Google. Androids standardmotor på de flesta telefoner är Googles "Speech Recognition & Synthesis" | Öppen | NOT_VERIFIED. Kräver Jimmys besked (F-06) och test av en motor som inte kommer från Google (08, G4). |
| K-17 | Platstjänst kontra "ingen Google" | Ferrostar erbjuder Googles Fused Location | Jimmy: ingen Google | Jimmy | Ferrostars `AndroidSystemLocationProvider` (AOSP) används. Inga Google Play-tjänster. |
| K-18 | Versionsuppgift | Byggplanen: valhalla-mobile 0.6.1 | GitHub 2026-11-20: senaste release 0.6.6 (2026-11-16). README visar fortfarande 0.6.1 i exemplen. | Faktakontroll | Version låses först i G1 |
| K-19 | Kodstatus | PRD 12 och README: "Ingen Android-kod ännu" | PR #1 finns | Fakta | PRD och README inaktuella på den punkten. Rättas inte i originalet, noteras här. |
| K-20 | Rikard | PRD 13.11: fiktiv figur? Stavas både Rikard och Rickard | Byggplanen: verklig person med dokumenterad bakgrund, be om lov | Byggplanen | Rikard är en verklig person. Samtycke krävs före spridning (M-13). Stavning "Rikard". |
| K-21 | Ursprungsklass för BS-01–BS-10 | `rules.json`: saknar provenance (behandlas som arkivmaterial) | Startprompten: `DERIVED_RULE` | Startprompten | `DERIVED_RULE`. BS-11–BS-16 får inte användas i Satirläget förrän de är godkända. |
| K-22 | Mekanismlistor | Byggplanen: åtta "drag" (fyra Adams-grepp och fyra egna) | Startprompten §2: åtta mekanismer | Startprompten, med byggplanens drag som komplement | Mappning i 02-PRD avsnitt 3 |
