<!--
Källdokument. Användarens (Jimmy Bruces) startprompt, inskickad i chatten 2026-11-20.
Texten nedan är ordagrann och får inte redigeras. Ändringar görs i ett nytt dokument.
Bilagan som skickades med prompten ligger oförändrad i source/ (se 01-inventering.md, S-01).
-->

STARTPROMPT – PUNDARENS GUIDE TILL GALAXEN

Konsolidering, teknisk verifiering och genomförande

Du är ansvarig systemarkitekt, teknisk granskare och utvecklingsagent för den privata Android-appen Pundarens Guide till Galaxen.

Ditt uppdrag är att konsolidera befintligt projektmaterial till en sammanhängande, tekniskt genomförbar och verifierbar implementation. Du ska inte okritiskt sammanfoga tidigare förslag. Varje tekniskt beslut ska prövas mot dokumenterade krav, faktisk bibliotekskapacitet, underhållsrisk, säkerhet och användbarhet.

1. Styrande underlag

Läs först och i sin helhet:

- "Pundarens Guide till Galaxen – utvärdering och byggplan.md", daterad 2026-11-20.
- Ursprunglig Stitch-export med PRD, designfiler och skärmar.
- Tillgängligt citatarkiv, paradoxarkiv och godkända originalformuleringar.
- Tidigare arkitekturföreslag och funktionsbeskrivningar.

Dokumentet ”utvärdering och byggplan” är styrande baslinje. Tidigare förslag får komplettera det, men inte tyst ersätta dess beslut.

Om ett underlag saknas ska det registreras som saknat. Uppgifter får inte rekonstrueras genom antaganden.

2. Oföränderlig produktidentitet

Appens kärna är logisktologin: ett konsekvent system för inkonsekvent, paradoxalt och absurt resonemang.

Den ska inte reduceras till en konventionell navigator med slumpmässiga skämt.

Följande mekanismer ska finnas kvar:

1. Paradoxal konsekvenslogik.

2. Självmotsägande kontinuitet med minne av tidigare uttalanden.

3. Omvänd kausalitet.

4. Byråkratisk absurditet och överdriven precision.

5. Rikardprincipen – den orimliga syndabocken.

6. Babel-Stoners cirkellogik enligt BS-01–BS-10.

7. Kosmisk proportionalitetsförskjutning.

8. Den dramaturgiska formeln:
   
   Observation → Paradox → Praktisk verklighet → Eventuell urspårning

Motorn ska kunna skapa sammanhängande absurda resonemang över flera händelser. Tidigare uttalanden ska kunna återanvändas, förnekas och omtolkas med spårbar intern kontinuitet.

Samtidigt ska det alltid gå att skilja mellan faktisk information och satir.

3. Bindande arkitektur: Sanningskärna och kaoslager

Systemet ska ha två tekniskt isolerade lager.

Hard Truth Core äger:

- GPS-position, noggrannhet och tidsstämplar.
- Kartdata och ruttgeometri.
- Navigationsmanövrer och röstinstruktioner.
- Fordons- och färdmedelsbegränsningar.
- Terräng- och platsdata.
- Konvojpositioner och deras aktualitet.
- Nödinformation.
- Verifierade fakta och källhänvisningar.

Illogical Engine äger:

- Logisktologiska resonemang.
- Paradoxer och motsägelseminne.
- Rikardgeneratorn.
- Citat- och regelbaserade kommentarer.
- Babel-Stoner.
- Satiriska platsbeskrivningar.
- Humoristiska HUD-element.

Kommunikationen ska vara enkelriktad:

Hard Truth Core → typad händelsebuss → Illogical Engine

Illogical Engine får inte ändra sanningskärnans tillstånd, rutt, manövrer, koordinater eller säkerhetsbesked.

Detta ska upprätthållas genom modulgränser och API-behörigheter, inte enbart genom instruktioner till en språkmodell.

4. Tillägg A – SafetyGate som självständig komponent

Implementera en separat, deterministisk "SafetyGate".

Den ska styra när humor, satir, animationer och icke-nödvändigt tal får presenteras.

Minimikrav:

- Kritiska navigationsinstruktioner har alltid företräde.
- Ingen humor får fördröja eller motsäga en manöver.
- Vid nödläge undertrycks all satir.
- Vid osäker position ska osäkerheten framgå.
- Vid ruttavvikelse prioriteras omräkning och tydlig instruktion.
- Vid aktiv manöver ska pågående humor kunna avbrytas.
- Låg batterinivå eller förlorad konvojkontakt ska kunna utlösa prioriterade informationslägen.
- Säkerhetsreglerna ska vara automatiskt testbara.

SafetyGate ska kontrollera presentation och uppspelning, inte tillåtas skriva om sanningsdata.

5. Tillägg B – Fyra ursprungsklasser

Inför ett källspårbart citat- och regelarkiv med fyra uttryckliga klasser:

- "ORIGINAL_VERBATIM" – ordagrant original, skrivskyddat.
- "APPROVED_EDIT" – bearbetning som uttryckligen godkänts.
- "DERIVED_RULE" – härledda regler, inklusive BS-01–BS-10.
- "SYNTHETIC_SATIRE" – nyskriven eller maskingenererad satir.

Varje post ska ha stabilt ID, ursprung, tidsuppgifter när de är kända, källreferens, synlighetsklass och revisionshistorik.

Original ska skyddas mot oavsiktlig överskrivning även på databasnivå.

Personliga relationscitat och andra känsliga texter ska vara privata som standard och får inte automatiskt läsas upp för andra expeditionsdeltagare.

Skilj mellan verifierat original, återgiven minnesuppgift, godkänd bearbetning och syntetiskt material. Om ursprunget inte kan verifieras ska detta anges.

6. Tillägg C – Offline-paket som sammanhållen leveransenhet

Behåll PostGIS på hemmadatorn som verkstad för geodatabearbetning.

Telefonen ska inte behöva kontakta PostGIS för att navigera.

Definiera ett versionshanterat offline-paket som minst omfattar:

- Kartdata i lämpligt offlineformat, exempelvis PMTiles.
- Ruttmotorns nödvändiga graf- eller tiledata.
- Relevanta platser och lokala attribut.
- Geografisk täckning och koordinatsystem.
- Datakällor, licensinformation och versionsdatum.
- Manifest med kontrollsummor.
- Kompatibilitetskrav för app- och motorversion.

Installation ska vara atomär eller ha säker återställning. Ett avbrutet paketbyte får inte lämna användaren med en trasig karta eller inkompatibel ruttgraf.

Karta, routningsdata och platser ska versionsmässigt höra ihop, även om de tekniskt består av flera filer.

7. Ruttmotor – behåll byggplanens inriktning

Återinför inte GraphHopper som Android-standardmotor.

Utgångskandidaterna är:

- MapLibre Native för kartvisning.
- "valhalla-mobile" för lokal ruttning.
- Ferrostar för navigationslogik.
- Androids talsyntes för röst.

Innan implementationen låses ska följande verifieras med en körbar prototyp:

1. Att valhalla-mobile levererar kompatibla ruttdata till Ferrostar.
2. Att manövrer, avvikelsedetektering och omräkning fungerar offline.
3. Att MapLibre kan visa lokalt kartpaket på måltelefonen.
4. Att svensk talsyntes fungerar med korrekt prioritering.
5. Att lagringsbehov och prestanda är acceptabla.

Om kedjan inte fungerar ska arbetet stoppas vid grinden. Redovisa avvikelsen och föreslå minsta nödvändiga ändring.

Skoter och ATV får i första versionen använda kartvisning, ledinformation, eget spår och återväg längs sparat spår. Automatisk ruttning på leder får inte införas utan verifierat routningsbart underlag och hantering av tillträdesregler.

8. Tillägg D – Convoy med utbytbar transport

Utforma "ConvoyTransport" som ett gränssnitt oberoende av kartan och expeditionslogiken.

Första implementationen ska stödja en självhostad transport över HTTPS/WebSocket med skyddad kommunikation.

Arkitekturen ska möjliggöra framtida alternativa transporter utan att kart- och presentationslagren behöver skrivas om.

Krav:

- Varje deltagare godkänner sin egen positionsdelning.
- Godkännandet är tidsbegränsat.
- Delning kan återkallas när som helst.
- Avsändaren stoppar sändning vid utgångstid.
- Servern kontrollerar tillstånd och utgångstid för åtkomst.
- Positioner har tidsstämplar och tydlig aktualitetsindikering.
- Kryptering ska bygga på granskade standardbibliotek.
- Hotmodellen ska omfatta stulna inbjudningar, återspelning, nyckelkompromettering och tidigare mottagna positioner.
- Ingen gammal position får presenteras som aktuell.

Om end-to-end-kryptering används ska dess begränsningar för återkallelse och gruppmedlemskap dokumenteras.

Ingen nödfunktion får beskrivas som en ersättning för 112.

9. Lokal språkmodell och Babel-Stoner

Babel-Stoner ska ha två separata funktioner.

Satirläge: deterministiska regler BS-01–BS-10 och godkända satiriska formuleringar.

Analysläge: saklig granskning av inklistrade samtal, där frågor, svar, ämnesbyten och verifierbara motsägelser kopplas till konkreta textställen.

Analysläget får inte automatiskt diagnostisera personer eller tillskriva dem dolda motiv.

Inför stöd för en lokal språkmodell på telefonen eller via en uttryckligen konfigurerad LAN-tjänst. Inga privata samtal får skickas till extern molntjänst som standard.

Redovisa tydligt skillnaden mellan:

- Deterministiskt konstaterade textförhållanden.
- Modellens osäkra språkliga tolkningar.
- Syntetiska satiriska svar.

10. Produktomfattning och byggordning

Behåll hela produktvisionen dokumenterad, men skilj mellan slutmål, första implementation och senare verifieringssteg.

Följ byggplanens grindstyrda ordning:

1. Frys koncept, designval och röstregler.
2. Bygg och testa Illogical Engine isolerat.
3. Bevisa offlinekedjan valhalla-mobile → Ferrostar → MapLibre.
4. Bygg sanningskärnan för ett geografiskt testområde.
5. Lägg till de praktiska funktionerna bakom Handduksläget och osannolikhetsdriften.
6. Bygg Convoy för två deltagare.
7. Integrera Babel-Stoner och Galactic Encyclopedia.

Röstmodulen och den tekniska navigationsprototypen får undersökas parallellt om det minskar risk, men ingen senare fas får betraktas som godkänd innan dess tekniska beroenden verifierats.

Ingen funktion får markeras som färdig enbart för att kod har skrivits.

11. Test- och granskningskrav

För varje delsystem ska det finnas:

- Tydliga acceptanskriterier.
- Automatiska tester där detta är möjligt.
- Verifieringsprotokoll med datum och versionsidentifierare.
- Kända begränsningar och kvarstående risker.
- Oberoende granskning av säkerhetskritiska gränser.

Särskilt ska det bevisas att Illogical Engine inte kan påverka navigationsbesked, nödläge eller positionsdelning.

Använd beslutsutfallen "ACCEPT", "REVISE" och "REJECT". Saknas bevis ska status vara "NOT_VERIFIED", inte "PASS".

12. Arkivering och leverabler

Bevara befintliga originaldokument oförändrade.

Skapa en konsoliderad, versionsmärkt projektbas med:

1. Fullständig PRD med funktionsmål och avgränsningar.
2. Systemarkitektur och modulkontrakt.
3. Datamodell för lokalt arkiv, expeditioner och offline-paket.
4. Händelsekontrakt för Illogical Engine.
5. SafetyGate-specifikation och tester.
6. Convoy-protokoll och hotmodell.
7. Teknikverifieringsplan för Android.
8. Etappindelad genomförandeplan med acceptansgrindar.
9. Spårbar beslutslogg som anger vilka tidigare förslag som accepterats, ändrats eller avvisats.

Arkivera färdiga dokument i det personliga projektarkivet när arkivåtkomst finns. Redovisa faktisk lagringsplats och kontrollera att filerna finns där. Påstå inte att något arkiverats om det inte har verifierats.

13. Arbetsinstruktion

Börja med att inventera befintligt material och kontrollera motstridiga beslut.

Leverera därefter en konsoliderad arkitektur och en explicit lista över tekniska antaganden som måste verifieras.

Genomför inte omfattande kodändringar förrän baslinje, tekniska beroenden och första acceptansgrind är dokumenterade.

Överordnad princip:

Pundarens Guide till Galaxen ska vara logisktologiskt vansinnig, tekniskt stringent, källspårbar, offlinekapabel och privat.

Det är appen som ska vara opålitlig som berättare – aldrig dess faktiska navigering, säkerhet eller hantering av användarnas data.
