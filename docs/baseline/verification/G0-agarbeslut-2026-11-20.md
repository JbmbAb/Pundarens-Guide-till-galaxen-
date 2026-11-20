# G0 – ägarbeslut och svar på frågorna F-01–F-17

Upprättat 2026-11-20 · gäller baslinje v0.1 (`main` @ `06cd200`)
Föreslagen plats: `docs/baseline/verification/G0-agarbeslut-2026-11-20.md`
Upprättat i en annan chatt än den som byggde baslinjen. Inte incheckat. Ersätter inget i `docs/baseline/v0.1/`.

Markeringar: **BESLUTAT** = Jimmy har svarat själv. **DOKUMENTERAT** = svaret finns redan i underlaget och återanvänds utan ny fråga. **ÖPPEN** = saknar svar. Inget här gör en grind till ACCEPT. Det kräver protokoll enligt 08.

## 1. Beslutat av Jimmy 2026-11-20

| Fråga | Beslut | Följd |
|---|---|---|
| F-01 | Baslinje v0.1 godkänd som arbetsbaslinje. | Etapp 0 kan gå vidare. |
| F-02 | Byggplanens förslag: skärmvarianterna med rubriken Handdukssensorn, med The Sentient Archive som grunddesign. Lämpliga visuella element från Cosmic Drift integreras "utan att kompromissa med navigationssäkerhet eller läsbarhet". | B-17 stängs. PR1-20 kan prövas. Precisering i avsnitt 2. |
| F-03 | Orsa kommun och angränsande kommuner. | Paketgräns för G2 och G5. |
| F-04 | Samsung Galaxy Tab S8 Ultra, SM-X900 (Wi-Fi-modellen). | Målenheten är en surfplatta på 14,6 tum, inte en telefon. Se avsnitt 5. |
| F-07 | GPT-6, oberoende från byggaren. Granskningen ska omfatta faktisk arkitektur, kod och testbevis. En annan modellbeteckning räcker inte ensam för att styrka säkerheten. | R-06 och M-14. Se avsnitt 1.1. |
| F-11 | Radgranskning sker efter fastställda röstregler. | Ordning: röstregler, F-11, dokumentations-PR. |
| F-10 | Båda godkänns: RED-P-007 och den normaliserade P-001 blir `APPROVED_EDIT`. | Arkivimporten: godkänd av Jimmy 2026-11-20. Originalraderna ligger kvar orörda bredvid. |

### 1.1 Krav på den oberoende granskningen (F-07)

- Granskaren får repot, protokollen och testutdata, inte byggarens resonemang eller sammanfattningar.
- Granskaren läser och kör själv: modulgränser och beroendegraf, koden i fråga, testerna och deras utfall. Ett påstående utan bevis i repot får status NOT_VERIFIED.
- Varje grind får ett eget granskningsprotokoll med commit-hash, vad som kördes, vad som sågs och utfall per kriterium.
- Byggaren rättar inte granskarens protokoll. Invändningar skrivs som svar bredvid.
- Jimmy fattar grindbeslutet. Granskningen är underlag, inte beslut.

### 1.2 Arbetsordning (beslutad)

1. Röstreglerna på en sida (M-08).
2. F-11: radgranskning mot röstreglerna.
3. Dokumentations-PR med det här bladet. Ingen implementationskod. Ingen automatisk merge.

G0 förblir öppen tills underlaget har granskats och de blockerande punkterna i avsnitt 4 är lösta.

## 2. Precisering av F-02

Förslag till tolkning som en kodare kan följa. Kontrasttalen är uträknade ur färgkoderna, inte mätta på skärm.

| Del | Regel |
|---|---|
| Grund | Handdukssensorn-skärmarna. Bakgrund `#0e0e0e`, lila `#d896fe` och `#c59aff`, Space Grotesk för rubriker, Manrope för löptext. |
| Från Cosmic Drift | Turkos `#4af8e3` som sanningsfärg. Används bara i sanningsremsan och på nödkortet. Kontrast mot `#0e0e0e` 14,55:1. Svart text på turkos 15,83:1. |
| Förbud | Turkos och lila aldrig som enda skillnad mellan två ytor som ligger intill varandra (kontrast 1,68:1). |
| Sanningsremsan | Raka hörn, Manrope, ingen glaseffekt, ingen glitch, ingen drift, ingen asymmetri. Tramset får se ut hur det vill utanför remsan. |
| Regel i ord | Turkost är sant. Lila är Guiden. |

Status: tolkningen är NOT_VERIFIED tills Jimmy sett den på målenheten (G3, A-07).

## 3. Besvarat av det som redan är dokumenterat

| Fråga | Svar | Källa |
|---|---|---|
| F-05 | v1: till fots och som passagerare. Inte skoter eller ATV. Fordonsreglerna gäller så fort enheten rör sig i fordonsfart. | 02-PRD (etapp 4), 08 G5, B-58 |
| F-06 | Ja, "ingen Google" gäller också talmotorn och Tink. libsodium i stället för Tink. Talmotor väljs i G4. | S-20 ("vill inte vara i närheten av google"), B-46, B-59, B-60 |
| F-09 | C-008 och C-009 är godkända bearbetningar ur den gemensamt bearbetade meddelandetexten 17 november 2026. | `data/quotes/README.md`, bilagan rad 216–222, 04 avsnitt 1.3 |
| F-12 | Bara i arkivet. `EX-01-FORMULERING` och PAT-01–PAT-05 visas inte i appen. | B-29, R-05 |
| F-14 | Mimer är Jimmys eget företag och plattform. Appen har inget beroende dit. Stänger M-07. | Jimmy, tidigare i den här chatten |
| F-16 | Stäng PR #1 med hänvisning till beslutsloggen och behåll grenen som referens. Åtgärden är inte utförd. | 10, rekommendationen under PR #1 |

Blockerar inte G0 och lämnas därför utan fråga nu: F-08 (Rikard, före etapp 2 och spridning), F-15 (Convoy-server, etapp 6), F-17 (licens, före spridning). Till dess gäller R-05: allt `PRIVATE`, Rikard omnämns men hörs aldrig, inga verkliga utdrag i appen.

## 4. Öppet

| Punkt | Vad som saknas | Behövs för |
|---|---|---|
| F-03 | Exakt vilka grannkommuner som ingår i paketet. Avgörs när paketgränsen ritas. | G2, G5 |
| F-04 | Android-version och faktisk GNSS-funktion, verifierat på enheten. Ledigt utrymme: cirka 60 GB enligt Jimmy 2026-11-20, mäts igen vid G5 (A-10). | G2–G5 |
| F-11 | Jimmys genomgång rad för rad, efter röstreglerna (M-08). Förgranskning i avsnitt 7. | Etapp 1 |
| F-13 | Se avsnitt 6. | M-01, M-04, M-05 |
| G0, övrigt | Nya namn eller märkning som arbetsnamn (B-12, B-13). Röstreglerna på en sida (M-08). Frysning av händelselistan (05) och av kandidatvärdena i 06. Val mellan Room och SQLDelight samt triggerprototyp (A-13). Gränsvärden för G5. | G0 |

## 5. Följder av målenheten (F-04)

Uppgifterna kommer från webbkällor 2026-11-20 och är NOT_VERIFIED tills de kontrollerats på Jimmys enhet.

1. **Positionering.** Enheten är Wi-Fi-modellen SM-X900, och källorna säger emot varandra om just den. GSMArena anger GPS, GLONASS, BDS och Galileo bara för mobilnätsmodellen. Samsungs egen produktsida för SM-X900 listar GPS, Glonass, Beidou, Galileo och QZSS, och Notebookchecks test anger GNSS. Avgörs på enheten före G2: flygplansläge utomhus, `AndroidSystemLocationProvider`, tid till första position. Hör ihop med A-12.
2. **Talmotor.** Samsungs egen talmotor har ett svenskt röstpaket (`com.samsung.SMT.lang_sv_se_f00`). Det ger en tredje kandidat utan Google i G4, bredvid Piper via sherpa-onnx. Lägg till i lyssningstestet under A-08.
3. **Storlek.** 14,6 tum och 726 gram. Byggplanens "telefon i fäste" behöver ett fäste som håller en surfplatta, och testet "till fots" blir något annat än med en telefon. Layouten för sanningsremsan och svansytan (A-07) ska göras för liggande surfplatta först.
4. **Ordval i baslinjen.** "Måltelefon" i 08 och 11 bör bli "målenhet". Ren textändring.

## 6. Arkiv och saknat underlag (F-13)

| Sak | Läge |
|---|---|
| Personliga arkivet | Finns på Google Drive som `Personligt_Arkiv`, med biblioteksmapparna 00–07. Mappen `04_Citat_och_paradoxer` innehåller bara `00_KONTEXT.md`. |
| `Citat_och_paradoxer_falsifiering.md` | Hittades inte på Drive. Fortfarande MISSING. |
| "Bilagan på 912 rader" (M-02) | Finns hos Claude i den här chatten. SHA-256 börjar `5761ace7`. Repots S-15 är en delmängd: 102 långa rader är gemensamma, 295 finns bara i bilagan. Kan läggas i `docs/baseline/source/` på Jimmys ord. |
| Diagrammet i byggplanen (M-03) | Innehåll: Sanningslagret → besked → Beskedet → föraren. Sanningslagret → händelser → Tramslagret → "sedan, om tiden räcker" → föraren. Ingen pil från Tramslagret tillbaka. |
| B-21 | "Tolv" händelsetyper i byggplanen var en uppskattning. Fjorton enligt 05 går bra. |
| Arkivering av baslinjen i arkivet | Inte gjord. Kräver Jimmys ord, eftersom det är en skrivning till hans Drive. |

## 7. Förgranskning inför F-11

Maskinell genomsökning av Grok Bots förslag. Den ersätter inte Jimmys granskning och godkänner ingenting.

| Fynd | Rader | Förslag |
|---|---|---|
| Riktningsord ur spärrlistan (06, R-03) | NAV-001, NAV-012, NAV-014, V-010, EX-F-04 | Stryks i fordonsläge. Kan prövas för läget till fots. |
| Lånade namn (B-12) | 14 rader, bland dem NAV-013, NAV-016, EX-F-06, BS-13 och lexikonorden "vogonkö" och "panikbuffert" | Byt namn eller märk som arbetsnamn före godkännande. |
| Bygger på relationscitat | V-005, V-008–V-012, V-019 | Alltid `PRIVATE`, även om de godkänns. |
| Låter som sakpåståenden | NAV-003, NAV-007 | Granskaren avgör om de kan misstas för besked. |

Spärrlistan som användes: vänster, höger, rakt fram, sväng, U-sväng, avfart, rondell.

## 8. Vad som inte är gjort

- Inget är incheckat, pushat eller ändrat i repot eller på Drive.
- Den andra agenttråden, som ställde F-02, har inte fått svaren. Det här bladet kan klistras in där.
- Inget i bladet är prövat av en oberoende granskare.
