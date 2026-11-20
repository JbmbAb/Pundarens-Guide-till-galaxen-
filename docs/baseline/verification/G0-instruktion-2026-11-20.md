# Instruktion till utvecklingsagenten – G0, 2026-11-20

Ägarbesluten för G0 är fattade. Ställ inte frågorna igen.

| Fråga | Fastställt beslut |
|---|---|
| F-01 | Baslinje v0.1 godkänd som arbetsbaslinje |
| F-02 | The Sentient Archive med Handdukssensorn. Turkos (`#4af8e3`, från Cosmic Drift) för sanningsinformation |
| F-03 | Orsa kommun och angränsande kommuner |
| F-04 | Samsung Galaxy Tab S8 Ultra, SM-X900 |
| F-07 | GPT-6, oberoende från byggaren |
| F-10 | Båda posterna `APPROVED_EDIT` |
| F-11 | Radgranskning efter fastställda röstregler |

Redan dokumenterat och gäller utan ny fråga: F-05 (till fots och som passagerare), F-06 (ingen Google gäller också talmotor och Tink), F-09, F-12 (bara i arkivet), F-14 (Mimer är ägarens företag, inget beroende), F-16 (rekommendationen i 10). F-08, F-15 och F-17 blockerar inte G0.

## Arbetsordning

1. Skriv röstreglerna på en sida (M-08).
2. Förbered F-11: radgranskning mot röstreglerna. Ägaren godkänner per rad.
3. Öppna en dokumentations-PR med G0-beslutsbladet (`docs/baseline/verification/G0-agarbeslut-2026-11-20.md`, bifogas).

## Gränser

- Ingen implementationskod.
- Ingen automatisk merge. Ägaren slår ihop själv.
- G0 förblir öppen (NOT_VERIFIED) tills underlaget har granskats och de kvarvarande blockerande punkterna är lösta.
- Ledigt utrymme på enheten: cirka 60 GB enligt ägaren. Återstår att verifiera på enheten: Android-version och faktisk GNSS-funktion. Skriv "målenhet", inte "måltelefon".

## Oberoende granskning (skärpning av F-07)

GPT-6:s granskning ska omfatta faktisk arkitektur, kod och testbevis. En annan modellbeteckning räcker inte ensam för att styrka säkerheten.

- Granskaren får repot, protokollen och testutdata, inte dina resonemang eller sammanfattningar.
- Påståenden utan bevis i repot får status NOT_VERIFIED.
- Varje grind får ett eget granskningsprotokoll med commit-hash, vad som kördes och utfall per kriterium.
- Du rättar inte granskarens protokoll. Invändningar skrivs som svar bredvid.
- Ägaren fattar grindbeslutet.

## Kvar för G0

Nya namn eller märkning som arbetsnamn (B-12, B-13), röstreglerna (M-08), frysning av händelselistan (05) och kandidatvärdena i 06, val mellan Room och SQLDelight med triggerprototyp (A-13), gränsvärden för G5.
