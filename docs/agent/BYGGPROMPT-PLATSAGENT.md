# Byggprompt: platsagenten i Pundarens Guide till galaxen

Klistra in allt under strecket i en ny byggsession som står i det här repot.

---

Du ska bygga en agent i Android-appen Pundarens Guide till galaxen. Agenten hittar platser värda omvägen, det man annars aldrig hade hittat, och låter Guiden uttala sig om dem. Appen är ägarens privata, icke-kommersiella app. Den ska bli rolig och användbar. Lägg ingen tid på licenser, upphovsfrågor eller ceremoni.

## Vad som redan finns (läs det innan du skriver något)

- `online/src/main/kotlin/se/pundaren/guide/online/Sights.kt`: `Overpass` (OpenStreetMap), `Wikipedia` (platssökning och artikelinledning med bild), `SightKind`, `Sight`, `RouteSamples`.
- `online/.../ClaudeChooser.kt`: dagens enda Claude-anrop. Claude väljer ett av bankens färdiga beslut och skriver ingenting själv.
- `online/.../Https.kt`: `HttpPort` och `JdkHttps`. All nätåtkomst går genom den porten.
- `app/.../OnlineServices.kt`: `sights()`, `wikiSummary()`, `image()`, `chooseDecree()`.
- `app/.../ui/SightPanels.kt`: rutan Sevärt längs vägen med besökskort och Guidens utlåtande.
- `app/.../SecretStore.kt`: nycklarna, krypterade på enheten. `Secret.CLAUDE` är Claude-nyckeln.
- `app/.../AppGraph.kt`: `dontPanicChosen()`, `wouldAskClaude()` och hur allt kopplas ihop.
- `engine/.../galax/GalacticBank.kt` och `DontPanic.kt`: Guidens beslut. Rader märkta `Source.OWNER` är ägarens egna och är stilförebilden.
- Ruttmotorn på enheten: `graph.navigator.travelSeconds(from, targets, mode)` och `travelSecondsTo(sources, target, mode)` ger verklig restid och därmed verklig omväg.

Provat på plattan 2026-11-28: Sevärt hittar platser via Wikipedia, räknar restid, visar kort med bild och ger ett utlåtande. Svagheterna är det agenten ska lösa: fynden är tama (mest naturreservat och kyrkor), de lokala pärlorna saknas, och utlåtandet är ett allmänt beslut som inte handlar om platsen.

Kända fel hos källorna samma dag: OpenStreetMaps sökservrar (Overpass) svarade inte, Wikidatas frågetjänst gav 502, och Wikipedia svarar 429 om frågorna kommer tätare än ungefär fyra i sekunden. Bygg så att en källa som tiger aldrig fäller en sökning.

## Vad du ska bygga

En agent som användaren når från rutan Sevärt, med ett fritt önskemål i stället för bara kryssrutor:

> "Grottor och övergivna platser, högst 20 minuters omväg, vi orkar gå en kilometer."

Agenten ska, i den här ordningen:

1. **Förstå önskemålet.** Vilka slags platser, hur lång omväg, hur långt man vill gå, övriga villkor.
2. **Leta.** Först med appens egna källor, sedan på webben efter det som registren missar: lokala destinationssidor, hembygdsföreningar, kommunernas utflyktssidor, grottregister, kulturmiljöregister.
3. **Reda ut.** Slå ihop dubbletter och alternativa namn. Skilj sevärdhetens koordinat från parkeringen och stigens början.
4. **Hålla isär tre saker:** att platsen finns, att man kan ta sig dit, och att den är värd besöket. Skilj också belagd historia från sägen. Sägnen ska med, den är ofta det roligaste, men märkt som sägen.
5. **Rangordna** efter besöksvärde och verklig omväg. Omvägen räknar appen, inte agenten.
6. **Skriva besökskortet:** vad det är, varför det är speciellt, hur lång tid det tar, sista biten till fots, och vad som är osäkert.
7. **Skriva Guidens utlåtande:** ett galaktiskt myndighetsbeslut om just den platsen, i ägarens stil. Det är det här som gör appen till något ingen annan navigator är.
8. **Spara** kontrollerade fynd på enheten, så att samma arbete inte görs två gånger och så att sparade fynd visas utan nät.

Agenten får inte hitta på platser eller fakta. Det den inte vet säger den att den inte vet. Mer ceremoni än så ska det inte vara.

## Verktyg agenten ska ha

Verktyg som appen utför:

- `sok_platser(lat, lon, radie_km, slag)`: appens egna källor via `OnlineServices.sights()`.
- `restid(platser)`: restid dit och omväg i sekunder från ruttmotorn. Platser utanför kartpaketet får `null`.
- `kanda_fynd(omrade)` och `spara_fynd(fynd)`: den lokala fyndlådan.

Verktyg som Anthropic utför: webbsökning och webbhämtning.

Slutsvaret ska vara strukturerat (en lista fynd med fasta fält), så att appen kan rita korten utan att tolka fri text.

## Tekniska ramar

- **Läs claude-api-skillen först.** Den avgör vilken yta som passar (Managed Agents eller Messages med verktyg) och ger exakta fältnamn, verktygstyper och API-version. Gissa aldrig en fältstavning, och följ skillens val av modell om inte kostnaden talar emot det.
- **Inget nätverksbibliotek i de rena modulerna.** `:online` får bara bero på kotlin och kotlinx (`build.gradle.kts`, `allowedExternalGroups`). Claude anropas därför med vanlig HTTPS genom `HttpPort`, som i `ClaudeChooser.kt`.
- **Arkitekturtestet** (`verification/architecture/.../TruthBoundaryTest.kt`) tillåter nät i exakt tre filer. Lägg ny nätkod bakom `HttpPort` så behöver listan inte växa.
- **Nyckeln** skriver ägaren själv in under Reglage. Den får aldrig hamna i loggen, i ett felmeddelande eller i repot. Finns ingen nyckel ska Sevärt fungera som i dag.
- **Allvar skickas aldrig.** Text som `DontPanic.classify` känner igen som `SERIOUS` lämnar aldrig enheten, och nödkortet är alltid fritt från skämt.
- **Sanning och Guide hålls isär på skärmen.** Fakta om platsen står i turkost (`GuideColors.Truth`). Guidens utlåtande står i lila och märks GENERERAD. Rutten och beskeden rörs aldrig.
- **Kostnad och tålamod.** Sätt ett tak för antal verktygsvarv och för svarstid, visa att agenten arbetar, och ge en knapp för att avbryta. En sökning får hellre ge fem bra fynd på tjugo sekunder än tjugo på två minuter.
- **Aldrig under körning.** Under en pågående tur har beskeden företräde. Agenten får inte prata eller ta fokus då.

## Agentens egen instruktion (utgångspunkt, skriv om efter prov)

```
Du är platsagenten i Pundarens Guide till galaxen, en svensk navigator med humor.
Din uppgift är att hitta platser som är värda en omväg: grottor, raviner, vattenfall,
brandtorn, försvarsminnen, ruiner, övergivna miljöer, märkliga naturformationer och
platser med sägner. Du letar efter det folk kör förbi utan att veta om.

Arbeta så här:
1. Läs önskemålet och rutten. Fråga inte användaren om något du kan slå upp.
2. Sök först med appens verktyg. Sök sedan på webben efter lokala källor: destinationssidor,
   hembygdsföreningar, kommunens utflyktstips, grott- och kulturmiljöregister.
3. Håll isär tre saker för varje plats: att den finns, att man kan ta sig dit, och att den är
   värd besöket. Skilj belagd historia från sägen, och ta gärna med sägnen, märkt som sägen.
4. Be appen om restid och omväg. Räkna aldrig restid själv.
5. Välj de bästa fynden, hellre få och bra än många och tama.

För varje fynd skriver du ett besökskort på svenska: vad det är, varför det är speciellt,
hur lång tid besöket tar, hur man går sista biten, och vad du inte lyckades reda ut.
Hitta aldrig på en plats, en koordinat eller ett faktum. Vet du inte, så skriv det.

Sist skriver du Guidens utlåtande om platsen: ett kort beslut från en galaktisk myndighet,
tre till fem rader. Myndigheten är fullständigt självsäker, tar aldrig ansvar, omtolkar
verkligheten tills den passar, och optimerar gärna en del så att helheten havererar.
Skämtet ska bygga på något som är sant om just den platsen. Exemplen nedan visar tonen.
Skriv i den tonen, men skriv nytt och kopiera inte.

[appen lägger in åtta till tolv av ägarens egna beslut här]
```

## Klart när

- Ett fritt önskemål ger rangordnade fynd med verklig omväg, besökskort och ett utlåtande som handlar om platsen.
- Provet går igenom: med start i Orsa och en timmes räckvidd ska agenten hitta minst fyra av Storstupet, Styggforsen, Pilkalampinoppi, Risuhälla och Rövarbergsgrottan, med rimliga koordinater.
- Sparade fynd visas utan nät, och en andra sökning i samma trakt går märkbart fortare.
- Utan Claude-nyckel fungerar Sevärt som i dag.
- `./gradlew check assembleDebug` är grönt, och det nya är provat på plattan, inte bara på datorn.

## Arbetsregler i det här repot

- Svara ägaren på svenska, kort, utan tabeller och utan förbehåll om licenser.
- Text som ägaren lämnar som dokument rörs inte, inte en stavelse.
- Repot är publikt: lägg aldrig in privat material, namn på enskilda eller nycklar.
- Commita som `Claude <noreply@anthropic.com>` och pusha grenen `etapp4/sanningskarna` efter varje commit. Rör aldrig main, och öppna ingen PR utan att fråga.
- Ägarens fyra lokala Gradle-ändringar (`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts`, `gradle/gradle-daemon-jvm.properties`) ska varken committas eller återställas. Köa dina egna filer en och en.
- Appen på plattan är signerad med en äldre felsökningsnyckel. Ett nytt bygge måste signeras om med den (säkerhetskopian ligger i `~/.android`) för att gå att installera över appen utan att data försvinner.
- Säg till innan du tar över plattans skärm, och skriv aldrig in nycklar eller lösenord åt ägaren.
