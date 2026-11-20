# Pundarens Guide till Galaxen – utvärdering och byggplan

Nov 20, 2026 · @Jimmy

## Omdöme

Konceptet håller, men planen bygger en plattform där det räcker med en app, och den största risken ligger i rösten, inte i tekniken.

Underlaget namnger ett tiotal moduler. Version 1 bör vara två: rösten och en offline-navigator för ett område. Fyra ändringar betyder mest:

1. **Sanningen först, tramset efter.** PRD:ns anti-navigering och stresspaus gör appen sämre precis när den behövs. Ordningen ska vändas.
2. **En motor, på telefonen.** Karta, ruttning och navigering kan köras helt på enheten med färdiga öppna bibliotek. Server, PostGIS och jämförelsen mellan två ruttmotorer parkeras.
3. **Rösten byggs före kartan.** Illogical Engine och ditt arkiv är det enda som inte går att låna. Det är också det du kan granska själv, rad för rad, utan att läsa kod.
4. **Babel-Stoner byggs på mekanismen, inte på en verklig människas repliker.** Dess starkaste funktion finns redan i underlaget, som "omvänt läge".

Det som gör appen unik är inte kartan. Det är en opålitlig berättare med oklanderlig källhänvisning: Mimer gör beslut spårbara, Guiden gör trams spårbart.

## Tre prov från Adams

Adams vision är mer än en ton: han försökte bygga Guiden på riktigt, och hans teknikhumor är en varning riktad mot just den här sortens app. Varje funktion ska klara tre prov.

1. **Fältforskarprovet.** [h2g2](https://en.wikipedia.org/wiki/H2g2) startade den 28 april 1999 som Guidens jordutgåva, skriven av sina egna användare. En [trådlös version för handhållna enheter](https://www.technologyreview.com/2000/09/01/41405/an-earthly-prequel-to-a-galactic-guide/amp/) fanns redan i december samma år. Guiden skrivs alltså av den som har varit på platsen. Encyklopedin ska därför fyllas med dina egna fältanteckningar, inte med genererad text. Ditt eget C-003 är fältforskarens regel: "Skriv vad du hittar, inte vad du antar."
2. **Sirius-provet.** I böckerna är maskiner med påklistrad personlighet själva skämtet: dörrar som njuter av att öppnas, en automat som nästan gör te. Personligheten står i vägen för funktionen. En funktion i appen får därför aldrig bli sämre av sin personlighet. Måste föraren vänta på skämtet för att få beskedet har du byggt det Adams drev med.
3. **Precisionsprovet.** Humorn uppstår när en orimlig premiss behandlas med exakt detaljnivå. Skämten ska därför matas med riktiga värden från appen: minuter, meter, antal. Exakthet är bränslet, inte en begränsning.

Guidens mest kända egenskap är dessutom ett lugnande omslag. Det är användbarhet, inte dekoration: i skarpt läge ska appen bli lugnare och tydligare, inte roligare.

## Det som håller

Det mesta i underlaget är rätt tänkt och ska behållas som det är.

- Kartvisning, ruttberäkning och personlighet hålls isär och kan bytas var för sig.
- Google Maps byts mot MapLibre innan fler beroenden byggs in. Tre kartlägen blir tre stilfiler över samma data.
- Ingen egen ruttmotor skrivs.
- Citatarkivets källdisciplin: original lämnas orörda, och bearbetat och nyskrivet märks.
- Rikard har en dokumenterad bakgrund som syndabock och är inte ett slumpnamn.
- Convoy kräver varje deltagares eget ja, har tidsgräns, visar positionens ålder ärligt och tystnar vid nödläge.
- Kartlagd, framkomlig och verifierat tillåten sträcka hålls isär.
- Positionsdelning beskrivs som redan uppfunnen. Stickprov bekräftar det: [BRP GO!](https://ski-doo.brp.com/us/en/discover-ski-doo/technologies/brp-go/faq.html) har leder i Sverige och gruppspårning som kräver nät, och [Life360](https://www.life360.com/features/temporary-circles) har tillfälliga cirklar som slutar av sig själva.
- Rådet att låna Adams berättarteknik men inte hans namn.

## Sex saker att ändra

Sex saker i PRD, skärmar och arkitektur bör ändras innan något byggs.

1. **Anti-navigering utan raka besked.** PRD:n säger "Never give direct orders". En förare som hör "sväng inte höger" har hört ordet höger. Beskedet ska komma först, rakt och i fast form, och tramset efteråt: "Vänster om 300 meter. Höger är en fälla." Full anti-navigering hör hemma till fots.
2. **Stresspausen.** Vid missade svängar ska gränssnittet enligt PRD:n "sucka" och visa ett citat före uppdateringen. Det fördröjer beskedet när det behövs mest. Gör tvärtom: under stress tystnar tramset, skärmen visar bara nästa manöver, och sucken kommer efteråt.
3. **Lånad röst och lånade namn.** "Yoda on a Bad Day" bygger på en annan världs figur och på omvänd ordföljd, som slits fort. Skärmarna innehåller dessutom Heart of Gold, Milliways, Vogon, Marvin och en replik ur böckerna, trots att underlaget självt säger "skapa egna namn". Rösten bör vara lugn myndighetssvenska om orimliga saker, plus dina egna paradoxer ordagrant. Bilagans navigatorrepliker ligger redan där.
4. **Skärmarna ritar bara tramset.** Ingen av de åtta skärmarna har svängpil, gatunamn eller ankomsttid. Rubriken i den ena navigatorn får inte plats, och störningsvarningen är glitchad så att den blir svårläst. Lägg en sanningsremsa överst som aldrig glitchar och aldrig täcks. Välj sedan ett av de två designsystemen och en variant av varje skärm; varianten med rubriken Handdukssensorn är den bättre, med textade flikar och svenska repliker.
5. **Plattformen.** Förslaget kräver en server med Valhalla, PostGIS och Martin, plus en annan ruttmotor för offline. Två motorer ger dubbla tester och rutter som inte stämmer överens, och skoter och ATV kör där servern inte nås. Att först jämföra två ruttmotorer är med din egen C-002b forskning: "när man börjar leta efter en tredje kandidat". Se Tekniken som räcker.
6. **Babel-Stoner byggd på verkliga samtal.** Se avsnittet Babel-Stoner.

## Grundlagen: två lager, en diod

Allt appen gör hör hemma i ett av två lager, och information får bara gå åt ett håll.

- **Sanningslagret** äger position, rutt, manöver, avstånd, tider, konvojens lägen och fakta med källa. Det är deterministiskt, testat och tråkigt.
- **Tramslagret** äger Illogical Engine, Rikard, citaten, glitcharna och kartans undertitlar. Det får säga vad som helst.
- **Dioden** är regeln mellan dem. Tramslagret får läsa händelser från sanningslagret men kan aldrig skriva tillbaka. Det kan inte ändra en rutt, flytta en pil, dölja en varning eller fördröja ett besked.

Provfrågan för varje mening appen säger: kan någon agera på den och ta skada om den är fel? Då tillhör den sanningslagret. Guiden får bara ljuga om sådant som uppenbart inte är fakta.

Regeln ska sitta i kodens uppbyggnad, inte i goda föresatser. Sanningslagret byggs som en egen modul som inte känner till tramslagret, och då kan en granskare kontrollera regeln på en minut.

&#91;embedded content: två lager · enkelriktat flöde\]

Tramslagret får veta allt som händer men kan inte ändra något. Beskedet når föraren på en egen väg, och svansen spelas bara om tiden räcker.

## Varje skämt ska bära last

Varje fånig detalj på ytan ska vila på något verkligt under den: en kontroll, ett mätvärde eller en källa. Då blir tramset användbart i stället för dekoration.

| På ytan | Under |
| --- | --- |
| Towel Secured | Avfärdskontroll: batteri, kartpaketet täcker området, hemväg sparad, dagsljus kvar vid ankomst |
| Sannolikhet för snacks | Matställen inom vald omväg och om de har öppet vid ankomst. "Okänd" när öppettider saknas |
| Don't Panic, långt tryck | Nödkort: koordinater att läsa upp, riktning och avstånd hem, batteri, konvojens senaste lägen |
| Rikard-störning | GPS-noggrannhet och signal just nu |
| Fel fråga | Sanna fakta som ingen bad om: vänstersvängar kvar, rondeller, höjdmeter |
| Perspektivvyn | Omvägens andel av universums ålder, uträknad på riktigt |
| Osannolikhetsdriften | Slumpat men verkligt nåbart mål inom vald restid |
| Osynlighetslagret (underlagets SEP-fält) | Det samhället är byggt för att inte se: pumpstationer, reningsverk, ställverk |
| Expeditionsprotokollet | Resans riktiga logg i myndighetsform, sparad till arkivet |
| Redaktionens strykning | Din långa fältanteckning kortad till två ord. Originalet ligger kvar |

Nödkortet ersätter inte 112. Det gör bara att du har rätt uppgifter framme när du ringer.

## Illogical Engine

Illogical Engine bör vara en regelstyrd replikmotor som matas med exakta händelser, inte en chattbot som hittar på i stunden.

- **In:** typade händelser från sanningslagret, till exempel fel väg, ny rutt, stillastående med varaktighet, framme, manöver nära med tid kvar, svag GPS och lucka i konvojen med avstånd. Dessutom tid på dygnet och vad motorn själv har sagt tidigare.
- **Drag:** åtta komiska grepp. Fyra är underlagets Adams-grepp: konsekvens av absurd premiss, fel prioritering, byråkratisk exakthet och det triviala i det kosmiska. Fyra kommer ur ditt eget material: paradoxen (ditt citat, ordagrant), förnekelsen (motorn motsäger något den själv har sagt), Rikard (skuld utan logisk koppling) och fel fråga.
- **Ut:** ett besked och en valfri svans. Beskedet kommer från sanningslagret och ändras aldrig. Svansen spelas bara om tiden till nästa manöver räcker, och klipps om ett nytt besked kommer.
- **Radbank med källa:** varje rad har id och klass: ORIGINAL, BEARBETAD eller GENERERAD. Långtryck på en replik visar källan. Det är arkivets krav på åtskillnad, flyttat in i appen.
- **Synlighet per rad:** privat, sällskap eller öppen. Citat ur relationen är privata som standard, så att appen inte läser upp dem för hela konvojen.

Generering i tre nivåer, i den ordning de behövs:

1. Handskrivna mallar med luckor för exakta värden. Det räcker för version 1: tolv händelsetyper, åtta drag, omkring 150 rader.
2. AI-skrivna rader i omgångar hemma. Du godkänner eller förkastar varje rad innan den hamnar i banken.
3. Live-AI bara där ingen kör: Babel-Stoner, encyklopedin och Don't Panic. Aldrig i manövervägen.

Två regler för rösten. Guiden skyller aldrig på dig: skulden går till Rikard, universum eller Guiden själv. Och Rikard hörs aldrig, han omnämns bara. Enda undantaget: låt den riktige Rikard läsa in dementierna.

Exempel på en svans, klass GENERERAD: "Rikards inblandning är inte verifierad. Den är däremot dokumenterad."

## Babel-Stoner

Babel-Stoner blir både starkare och säkrare om den byggs på mekanismen i stället för på en verklig människas repliker.

Underlaget föreslår att faktiska samtalssekvenser utvinns ur chattar och transkriptioner. Gör inte det i appen, av tre skäl:

1. Reglerna BS-01 till BS-10 är redan allmänna retoriska grepp. De behöver inga verkliga repliker för att fungera.
2. Satir blir bättre på avstånd från källan. Adams skrev om byråkratin, inte om en namngiven handläggare.
3. En privatpersons meddelanden i en app som andra kan se är något annat än satir. Det rör den personens integritet och kan få rättsliga följder för dig. Jag är inte jurist.

Verkliga utdrag stannar i arkivet. Appen får regler och nyskrivna exempel.

**Vänd på produkten.** Gör det underlaget kallar "omvänt läge" till huvudfunktion och kalla den frågebokföring. Du klistrar in ett samtal. Verktyget listar varje sakfråga, raden där den ställdes, om den fick svar och var ämnet byttes. Samma regler gäller båda parter, även dina egna repliker. Inga motiv och inga diagnoser: verktyget beskriver text, inte människor.

Det är spårbarhet tillämpad på ett samtal: frågan ska överleva diskussionen. Två förbehåll hör till. En språkmodell läser fel ibland, så varje fynd ska peka på ett radnummer som du kan kontrollera. Och andras meddelanden bör analyseras på din egen enhet eller inte alls.

**Generatorn blir ett spel.** Du ställer en fråga och Babel-Stoner slingrar sig med ett grepp i taget. Du vinner när du namnger greppet och får tillbaka din fråga. Poängen är antalet repliker tills frågan är återställd. De fem lägena i underlaget blir fem motståndare.

**Rösten stannar här.** Greppen används aldrig mot dig i navigatorn eller i Don't Panic. En följeslagare som dagligen svarar så blir tröttsam, inte rolig.

## Convoy för två

Convoy kan byggas mindre än förslaget och ändå bli säkrare, om avsändarens telefon står för tidsgränsen och servern bara ser krypterad data.

- **Nyckeln ligger i QR-koden.** En expedition är en delad hemlig nyckel, ett kanalnamn och en sluttid. Positionen krypteras på telefonen innan den skickas, så relät i mitten ser bara oläslig data. [OwnTracks](https://owntracks.org/booklet/features/encrypt/) använder samma mönster: en delad hemlig nyckel ovanpå vanlig transportkryptering.
- **Tidsgränsen sitter hos avsändaren.** Din telefon slutar sända vid sluttiden. Kraschar appen skickas ingenting. Du behöver inte lita på att en server drar in en behörighet.
- **Ärlig ålder.** Varje prick visar när den senast uppdaterades, som i underlaget.
- **Mötespunkt.** Räknas ut från bådas restid till ett fåtal kandidatplatser, inte från kartans mittpunkt.
- **Allvar vid nödläge.** Nödsignal och förlorad kontakt visas utan trams.

Två ärliga gränser. En delad nyckel kan inte dras in för en enskild deltagare, så större grupper kräver ny nyckel när någon lämnar. Och utan mobilnät kommer inga positioner fram; [BRP GO!](https://ski-doo.brp.com/us/en/discover-ski-doo/technologies/brp-go/faq.html) säger samma sak om sin gruppspårning. Det som fungerar utan nät är din egen karta, ditt spår, hemvägen och den mötespunkt ni har avtalat i förväg.

Bygg inte egen kryptering. Använd bibliotekets färdiga funktion och låt en granskare kontrollera just den delen.

## Tekniken som räcker

Hela sanningslagret kan köras på telefonen med färdiga öppna komponenter, utan egen server. Måttet är ditt eget C-002a: "Optimering frågar vad som räcker för det här ändamålet".

| Behov | Val | Kontrollerat |
| --- | --- | --- |
| Karta | [MapLibre Native](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/) med lokal PMTiles-fil | Läser en .pmtiles-fil direkt från telefonens lagring sedan version 11.7.0 |
| Ruttning | Valhalla på enheten via [valhalla-mobile](https://github.com/Rallista/valhalla-mobile) | Rutt, kartmatchning, höjd och restidsmatris körs mot rutor på telefonen. Version 0.6.1, MIT-licens |
| Navigering | [Ferrostar](https://stadiamaps.github.io/ferrostar/print.html) | Upptäcker avvikelse, räknar om, byter steg och utlöser röstinstruktioner. Compose-gränssnitt på MapLibre, BSD-licens, betastatus |
| Röst | Androids inbyggda talsyntes | Ferrostar har kopplingen färdig |
| Test | Ferrostars simulerade körning | Spelar upp en hel rutt i valfri hastighet |

Tre saker är inte bevisade:

- Att Ferrostar och valhalla-mobile fungerar ihop. Ferrostar tar egna ruttkällor och läser Valhallas OSRM-format, och valhalla-mobile kan svara i det formatet. Jag har inte hittat ett färdigt recept, så det ska bevisas på en dag innan något byggs på det.
- Hur stora kartfilen och ruttrutorna blir för ditt område.
- Hur svensk talsyntes låter i din telefon.

Fyra följder för planen:

- **GraphHopper faller bort.** Enligt [projektets forum](https://discuss.graphhopper.com/t/offlne-routing-on-android/9176) släpptes offline-stödet för Android efter version 1.0. Det går att få igång men underhålls inte officiellt, så jämförelsen behövs inte.
- **Ingen ruttning i terräng i version 1.** Valhallas profiler, så som [Stadia Maps dokumenterar dem](https://docs.stadiamaps.com/guides/getting-the-best-routes-with-valhalla-turn-by-turn-directions-apis/), gäller bil, cykel, gång, moped med flera. Ingen är gjord för snöskoter eller ATV. Där får du karta, leder som lager, eget spår och hemväg längs samma spår, vilket också är det säkraste.
- **Isokroner saknas på enheten.** Osannolikhetsdriften och mötespunkten byggs på restidsmatrisen i stället.
- **"Spatial Intelligence Engine" blir tre frågor.** Vad kostar omvägen, varför valdes rutten, var möts vi. Alla tre är anrop till ruttmotorn plus en liten poängsättning. Ditt C-005 gäller här: "vi ska inte sälja vision plattformen ska sälja kapacitet och kompetens".

Terrängdata finns öppet. Lantmäteriets öppna geodata ligger under [CC0](https://lantmateriet.se/en/geodata/our-products/open-data). [Topografi 50](https://www.lantmateriet.se/globalassets/geodata/geodataprodukter/pb-topografi-50-nedladdning-vektor.pdf) innehåller vandringsled, vinterled med uppgift om skoterkörning, traktorväg och höjdkurvor, i SWEREF 99 TM. [OpenStreetMaps svenska källista](https://wiki.openstreetmap.org/wiki/Sv:Permissions) anger CC0 för produkten; kontrollera det i Geotorget före användning.

PostGIS hör hemma på din dator, i verkstaden som bygger kartpaketen. Det ska aldrig vara något telefonen måste nå.

## Byggordning och grindar

Bygg rösten först och kartan sedan, och släpp ingenting vidare utan grind.

1. **Frys konceptet.** Välj skärmvariant och designsystem, byt lånade namn och skriv röstens regler på en sida. Klart när en AI-kodare kan följa sidan utan att fråga.
2. **Rösten i en låda.** Illogical Engine som fristående modul med radbank, händelsesimulator och talsyntes. Ingen karta. Grind: hundra simulerade händelser utan att en svans motsäger ett besked, skyller på dig eller upprepas inom tio repliker.
3. **Bevisa kedjan.** En dag: valhalla-mobile ger en rutt som Ferrostar navigerar efter, i flygplansläge. Faller det, stanna och välj om. Bygg inte vidare på hopp.
4. **Sanningslagret, ett område, ensam.** Karta, rutt och navigering offline i hemområdet, med sanningsremsan och rösten inkopplad genom dioden. Grind: tio verkliga turer till fots och som passagerare, i flygplansläge. Beskedet kommer alltid först, och inget trams hörs de sista sekunderna före en manöver.
5. **Skämt som bär last.** Handduken, snacks, hemväg, nödkortet, osannolikhetsdriften och protokollet.
6. **Convoy för två.** Grind: sändningen upphör vid sluttiden med appen i bakgrunden, och den andra telefonen visar då "senast sedd" med rätt ålder.
7. **Babel-Stoner och encyklopedin.**

Grindarna är dem du redan använder: fryst kandidat, kall granskning av någon som inte har byggt den, sedan ACCEPT, REVISE eller REJECT. Granskaren får en stående fråga i varje fas: kan tramslagret på något sätt påverka ett besked?

Rösten kommer först av tre skäl. Den är det enda som inte går att låna, den är billig att bygga, och är den inte rolig spelar resten ingen roll.

## Gränser

Fyra gränser gör appen rimlig: säkerhet, lag, integritet och lånat gods.

- **Säkerhet.** Pundaren är appen, inte föraren. I fordon gäller röst först och telefonen i fäste. [Handhållen mobil är förbjuden vid körning på väg](https://www.transportstyrelsen.se/sv/vagtrafik/trafikregler-och-vagmarken/trafikregler/i-fordonet/regler-for-mobilanvandning-och-kommunikationsutrustning/sluta-rattsurfa/vad-sager-regeln/) sedan den 1 februari 2018. Testa som passagerare.
- **Lag i terräng.** [Barmarkskörning i terräng är förbjuden som huvudregel, även på egen mark](https://www.lansstyrelsen.se/nationellt-innehall/natur-och-landsbygd/aktiviteter-och-atgarder-i-naturen/motortrafik-i-naturen.html). [Snöskoter kräver ordentligt snötäcke, och i fjällens regleringsområden gäller markerade leder eller förbud](https://www.naturvardsverket.se/amnesomraden/friluftsliv/motortrafik-i-naturen/snoskoter). Appen visar leder. Den intygar inte att du får köra.
- **Integritet.** Positionsdelning kräver varje deltagares eget ja. Andras meddelanden hör inte hemma i appen, och varje citat har en synlighet. Rikard är en verklig person: be honom om lov.
- **Lånat gods.** Namn, figurer och repliker ur Adams böcker och ur Star Wars byts mot egna innan appen delas med någon. "Pundarens Guide till Galaxen" ligger nära originalets titel. Det fungerar som arbetsnamn, liksom de lånade arbetsnamnen i det här dokumentet, men välj eget före spridning. Jag är inte jurist.

## Parkerat

Det här dokumenteras och väntar. Inget av det behövs för att appen ska fungera.

| Vad | Väcks när |
| --- | --- |
| Server med Valhalla, PostGIS och Martin i drift | Något visar sig omöjligt att göra på telefonen |
| Jämförelsen Valhalla mot GraphHopper | Aldrig, om kedjan i fas 3 håller |
| Sammanslagning av OSM och NVDB | En verklig felrutt beror på vägdata |
| Ruttning på skoterleder | Det finns leddata med verifierad status för området |
| LoRa och Meshtastic | Två turer har misslyckats på grund av täckning |
| Grupper större än två | Convoy för två har klarat sin grind |
| Vibe Protect med samtalsblockering | Aldrig i kärnan. Egen app, om alls |
| Live-AI i navigatorn | Aldrig |
| Publicering i butik | Namn, lånat gods och Rikards samtycke är lösta |

## Kontrollerat och okontrollerat

Tekniken och reglerna ovan är kontrollerade mot källorna nedan den 20 november 2026. Resten är min bedömning.

Inte kontrollerat:

- Att Ferrostar och valhalla-mobile fungerar ihop. Fas 3 bevisar det.
- Storleken på kartfil och ruttrutor, och kvaliteten på svensk talsyntes.
- Täckning och ledstatus där du kör.
- Juridiken kring namn och verkliga personer.

Underlaget är bilagan på 912 rader och Stitch-exporten: tre projektbeskrivningar, två designsystem och åtta skärmar. Citaten ur underlaget är ordagranna. Exempelreplikerna är mina.

Källor:

- [Ferrostar, dokumentation](https://stadiamaps.github.io/ferrostar/print.html)
- [valhalla-mobile](https://github.com/Rallista/valhalla-mobile)
- [MapLibre Native för Android, PMTiles](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)
- [GraphHopper-forumet, offline på Android](https://discuss.graphhopper.com/t/offlne-routing-on-android/9176)
- [Stadia Maps, Valhallas ruttprofiler](https://docs.stadiamaps.com/guides/getting-the-best-routes-with-valhalla-turn-by-turn-directions-apis/)
- [Lantmäteriet, öppna data](https://lantmateriet.se/en/geodata/our-products/open-data)
- [Lantmäteriet, produktbeskrivning Topografi 50](https://www.lantmateriet.se/globalassets/geodata/geodataprodukter/pb-topografi-50-nedladdning-vektor.pdf)
- [OpenStreetMap-wikin, svenska datakällor](https://wiki.openstreetmap.org/wiki/Sv:Permissions)
- [Wikipedia, h2g2](https://en.wikipedia.org/wiki/H2g2)
- [MIT Technology Review, september 2000](https://www.technologyreview.com/2000/09/01/41405/an-earthly-prequel-to-a-galactic-guide/amp/)
- [Länsstyrelsen, motortrafik i naturen](https://www.lansstyrelsen.se/nationellt-innehall/natur-och-landsbygd/aktiviteter-och-atgarder-i-naturen/motortrafik-i-naturen.html)
- [Naturvårdsverket, snöskoter](https://www.naturvardsverket.se/amnesomraden/friluftsliv/motortrafik-i-naturen/snoskoter)
- [Transportstyrelsen, regeln om mobil vid körning](https://www.transportstyrelsen.se/sv/vagtrafik/trafikregler-och-vagmarken/trafikregler/i-fordonet/regler-for-mobilanvandning-och-kommunikationsutrustning/sluta-rattsurfa/vad-sager-regeln/)
- [OwnTracks, kryptering av nyttolast](https://owntracks.org/booklet/features/encrypt/)
- [BRP GO!, vanliga frågor](https://ski-doo.brp.com/us/en/discover-ski-doo/technologies/brp-go/faq.html)
- [Life360, Temporary Circles](https://www.life360.com/features/temporary-circles)
