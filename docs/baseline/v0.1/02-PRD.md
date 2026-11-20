# 02 – PRD, konsoliderad

Baslinje v0.1 · 2026-11-20 · Ersätter `docs/PRD.md` som utvecklingsunderlag (originalet är oförändrat)

## 1. Produktidentitet (oföränderlig)

Pundarens Guide till Galaxen är en privat Android-app med en opålitlig berättare och oklanderlig källhänvisning. Kärnan är **logisktologin**: ett konsekvent system för inkonsekvent, paradoxalt och absurt resonemang. Appen får inte reduceras till en vanlig navigator med slumpade skämt.

Överordnad princip, ordagrant ur startprompten: *"Det är appen som ska vara opålitlig som berättare – aldrig dess faktiska navigering, säkerhet eller hantering av användarnas data."*

Varje funktion ska klara byggplanens tre prov:

1. **Fältforskarprovet.** Encyklopedin fylls med egna fältanteckningar, inte genererad text (C-003: "Skriv vad du hittar, inte vad du antar.").
2. **Sirius-provet.** En funktion får aldrig bli sämre av sin personlighet. Måste föraren vänta på skämtet har vi byggt fel.
3. **Precisionsprovet.** Skämten matas med riktiga värden ur sanningskärnan: minuter, meter, antal.

Provfrågan för varje mening: *kan någon agera på den och ta skada om den är fel?* Då hör den till Hard Truth Core.

## 2. Två lager

| Lager | Äger | Egenskaper |
|---|---|---|
| **Hard Truth Core** (sanningslagret) | Position, noggrannhet och tidsstämplar. Kartdata och ruttgeometri. Manövrer och röstinstruktioner. Färdmedelsbegränsningar. Terräng- och platsdata. Konvojpositioner och deras ålder. Nödinformation. Verifierade fakta med källa. | Deterministiskt, testat och tråkigt |
| **Illogical Engine** (tramslagret) | Logisktologiska resonemang. Paradoxer och motsägelseminne. Rikardgeneratorn. Citat- och regelbaserade kommentarer. Babel-Stoner. Satiriska platsbeskrivningar. Humoristiska HUD-element. | Får säga vad som helst om sådant som uppenbart inte är fakta |

Flödet är enkelriktat: **Hard Truth Core → typad händelsebuss → Illogical Engine**. Mellan dem och skärmen eller högtalaren sitter **SafetyGate**, som bestämmer om och när tramset får presenteras. Se 03, 05 och 06.

## 3. Logisktologins mekanismer

Startprompten kräver åtta mekanismer. Byggplanens åtta "drag" är komplement och mappas så här.

| # | Mekanism (startprompten) | Byggplanens drag | Källa i arkivet |
|---|---|---|---|
| M1 | Paradoxal konsekvenslogik | Konsekvens av absurd premiss. Paradoxen. | P-001–P-007 (`ORIGINAL_VERBATIM`) |
| M2 | Självmotsägande kontinuitet med minne | Förnekelsen (motorn motsäger något den själv sagt) | Motsägelseminnet (04, 05) |
| M3 | Omvänd kausalitet | – (nytt i startprompten) | BS-02, BS-06 |
| M4 | Byråkratisk absurditet och överdriven precision | Byråkratisk exakthet | Precisionsprovet |
| M5 | Rikardprincipen, den orimliga syndabocken | Rikard (skuld utan logisk koppling) | R-001–R-003, BS-09 |
| M6 | Babel-Stoners cirkellogik enligt BS-01–BS-10 | – | BS-01–BS-10 (`DERIVED_RULE`) |
| M7 | Kosmisk proportionalitetsförskjutning | Det triviala i det kosmiska. Fel prioritering. | Perspektivvyn |
| M8 | Observation → Paradox → Praktisk verklighet → Eventuell urspårning | Svansens struktur | – |
| – | – | Fel fråga (sanna fakta som ingen bad om) | Bärs av M4 |

Inverted Grammar och "Yoda on a Bad Day" ingår inte (REJECT, se beslutsloggen B-03).

Två röstregler från byggplanen gäller alltid. Guiden skyller aldrig på användaren: skulden går till Rikard, universum eller Guiden själv. Rikard hörs aldrig utan omnämns bara.

## 4. Omfattning: slutmål, första implementation och senare

Allt i slutmålet behålls dokumenterat. Inget av det är lovat i v1.

### 4.1 Funktionstabell

| Funktion | Slutmål | v1 (etapp 1–4) | Senare (etapp 5–7 och parkerat) |
|---|---|---|---|
| Illogical Engine med radbank | Fullständig logisktologi över flera händelser | Etapp 2: regelstyrd radbank, 12 händelsetyper, cirka 150 rader, motsägelseminne, ingen karta | AI-skrivna rader i omgångar, granskade rad för rad |
| Sanningsremsa | Alltid överst, glitchar aldrig | Etapp 4 | – |
| Offline-navigering | Flera områden och färdmedel | Etapp 4: ett testområde, till fots och som passagerare, i flygplansläge | Fler områden |
| Kartlägen Praktisk, Poetisk, Osannolik | Tre stilfiler över samma data | Praktisk i etapp 4. Poetisk och Osannolik som stilfiler om de inte påverkar sanningsremsan. | – |
| Röst | Svensk talsyntes för besked och svans | Etapp 2 och 4 | Rikards dementier inlästa av Rikard själv, om han vill |
| Handduken ("Towel Secured") | Avfärdskontroll | – | Etapp 5 |
| Snacks-sannolikhet | Matställen inom vald omväg med öppettider, "Okänd" när de saknas | – | Etapp 5 |
| Don't Panic | Nödkort med koordinater, riktning hem, batteri och konvojens senaste lägen | Nödkortet utan konvoj i etapp 4, eftersom det är säkerhetsinformation | Konvojdelen i etapp 6 |
| Rikard-störning | Visar GPS-noggrannhet och signal | Etapp 4 | – |
| Osannolikhetsdriften | Slumpat men nåbart mål inom vald restid, via restidsmatris | – | Etapp 5 |
| Perspektivvyn, Fel fråga, Osynlighetslagret, Expeditionsprotokollet, Redaktionens strykning | Se byggplanen "Varje skämt ska bära last" | – | Etapp 5 |
| Convoy | Positionsdelning med samtycke | – | Etapp 6, två deltagare |
| Babel-Stoner Satirläge | Spel med fem motståndare | Motorns regelkärna testas i etapp 2 | Etapp 7 |
| Babel-Stoner Analysläge | Frågebokföring av inklistrade samtal | – | Etapp 7 |
| Galactic Encyclopedia | Egna fältanteckningar | – | Etapp 7 |
| Lokal språkmodell | På telefonen eller via uttryckligen konfigurerad LAN-tjänst | – | Etapp 7, bara Analysläge och encyklopedi |
| Skoter och ATV | Karta, leder, eget spår, återväg | Kartvisning och eget spår om leddata finns | Ledruttning parkerad tills verifierat underlag och tillträdesregler finns |
| Probability Drive (inställningar) | Reglage för kaos, Rikard-frekvens och sarkasm | Bara reglage som påverkar Illogical Engine, aldrig sanningskärnan | – |
| Intergalactic Slang | Sekundärt läge | – | Parkerat |
| Vibe Protect | – | – | Aldrig i kärnan (byggplanen) |
| PostGIS och server | Verkstad hemma för att bygga offline-paket | Inte på telefonens väg | Server i drift bara om något visar sig omöjligt på telefonen |
| Butikspublicering | – | – | Parkerat tills namn, lånat gods och Rikards samtycke är lösta |

### 4.2 Version 1 (definition)

Version 1 är etapp 1–4 i [09-genomforandeplan.md](09-genomforandeplan.md): rösten i en låda och en offline-navigator för ett testområde, ihopkopplade genom dioden och SafetyGate. Den är klar först när grind G5 är `ACCEPT`. Att koden är skriven räcker inte.

## 5. Funktionskrav

### 5.1 Navigering (Hard Truth Core)

- **N-1.** Beskedet kommer först, rakt och i fast form: "Vänster om 300 meter." Tramset får följa som svans: "Höger är en fälla."
- **N-2.** Anti-direktiv ("sväng inte höger") används inte i fordonsläge. Full anti-navigering är tillåten till fots och bara som svans efter ett rakt besked.
- **N-3.** Under stress (missad sväng, omräkning, hög hastighet, manöver nära) visar skärmen bara nästa manöver. Tramset tystnar. En eventuell suck kommer efteråt.
- **N-4.** Pil, avstånd, gatunamn, ankomsttid och ruttlinje kommer från Hard Truth Core och passerar aldrig Illogical Engine.
- **N-5.** Vid osäker position visas osäkerheten ärligt, med noggrannhet i meter och positionens ålder. Rikard får skyllas i svansen.
- **N-6.** Glitch-effekter ligger aldrig över sanningsremsan, pilen, avståndet eller ruttlinjen.
- **N-7.** Hela kedjan fungerar i flygplansläge med ett installerat offline-paket.
- **N-8.** Skoter och ATV: ingen automatisk ruttning på leder. Kartvisning, leder som lager, eget spår och återväg längs sparat spår.

### 5.2 Illogical Engine

- **E-1.** Regelstyrd radbank. Varje rad har id, ursprungsklass, synlighet och källreferens. Långt tryck på en replik visar källan.
- **E-2.** Indata är bara typade händelser från bussen (05) och motorns eget minne.
- **E-3.** Utdata är en *svans*, aldrig ett besked. Svansen kan klippas när som helst.
- **E-4.** Motsägelseminne: motorn kan återanvända, förneka och omtolka egna tidigare uttalanden med spårbara id.
- **E-5.** Ingen svans upprepas inom tio repliker. Ingen svans skyller på användaren. Ingen svans motsäger ett besked (byggplanens grind för etapp 2).
- **E-6.** Live-AI används aldrig i manövervägen.

### 5.3 Arkivet

- **A-1.** Fyra ursprungsklasser och separat verifieringsstatus (04).
- **A-2.** Original är skrivskyddade även på databasnivå.
- **A-3.** Relationscitat är privata som standard och läses aldrig upp för andra deltagare.
- **A-4.** Grok Bots förslag (V-001–V-020, BS-11–BS-16, lexikonförslag, NAV-001–NAV-020) är `SYNTHETIC_SATIRE` eller `DERIVED_RULE` med `approved = false` och används inte i appen innan Jimmy godkänt dem rad för rad.

### 5.4 Babel-Stoner

- **B-1. Satirläge.** Deterministiska regler BS-01–BS-10 och godkända satiriska formuleringar. Spelet: användaren ställer en fråga, Babel-Stoner slingrar sig med ett grepp i taget, och användaren vinner genom att namnge greppet. De fem lägena är fem motståndare.
- **B-2. Analysläge (frågebokföring).** Listar varje sakfråga, raden där den ställdes, om den fick svar och var ämnet byttes. Samma regler gäller båda parter. Varje fynd pekar på ett radnummer.
- **B-3.** Analysläget ställer inga diagnoser och tillskriver inga motiv.
- **B-4.** Resultat märks i tre klasser: deterministiskt konstaterat textförhållande, modellens osäkra tolkning och syntetiskt satiriskt svar.
- **B-5.** Inklistrade samtal behandlas på telefonen eller via en uttryckligen konfigurerad LAN-tjänst. Inget skickas till ett externt moln som standard. Inget sparas utan uttryckligt val.
- **B-6.** Greppen används aldrig mot användaren i navigatorn eller i Don't Panic.
- **B-7.** Vid tecken på hot eller våld bryts satiren och appen hänvisar till 112 och stöd.

### 5.5 Convoy

Se [07-convoy-protokoll-och-hotmodell.md](07-convoy-protokoll-och-hotmodell.md). Ingen nödfunktion beskrivs som en ersättning för 112.

## 6. Icke-mål och avgränsningar

- Ingen Google-tjänst i appen: inga Google Maps, inga Google Play-tjänster för plats. Talsyntesmotorn är en öppen fråga (F-06).
- Ingen egen ruttmotor och ingen egen kryptering.
- Ingen GraphHopper som Android-motor.
- Telefonen kontaktar aldrig PostGIS.
- Ingen automatisk ledruttning för skoter och ATV.
- Ingen live-AI i navigatorn.
- Babel-Stoner bygger inte på verkliga personers repliker i appen. Verkliga utdrag stannar i arkivet.
- Inga diagnoser och inga motivtillskrivningar.
- Appen intygar aldrig att man får köra någonstans.
- Inga andra plattformar än Android.
- Ingen spridning eller butikspublicering innan namn, lånat gods och Rikards samtycke är lösta.

## 7. Säkerhet, lag och integritet

Ur byggplanen, oförändrat i sak:

- Handhållen mobil är förbjuden vid körning på väg. Röst först, telefon i fäste, test som passagerare.
- Barmarkskörning i terräng är förbjuden som huvudregel. Appen visar leder men intygar inte att man får köra.
- Positionsdelning kräver varje deltagares eget ja.
- Rikard är en verklig person och ska tillfrågas.
- Namn ur Adams böcker och Star Wars är arbetsnamn och byts före spridning. "Pundarens Guide till Galaxen" är också ett arbetsnamn.

## 8. Öppna frågor

Samlade i [11-tekniska-antaganden.md](11-tekniska-antaganden.md#frågor-som-bara-jimmy-kan-besvara).
