# Konsoliderad baslinje v0.1 – Pundarens Guide till Galaxen

| | |
|---|---|
| **Version** | v0.1 |
| **Datum** | 2026-11-20 |
| **Status** | REVISE – kandidat för granskning. Ingenting i den här baslinjen är verifierat på telefon. |
| **Styrande underlag** | [`../source/Pundarens Guide till Galaxen – utvärdering och byggplan.md`](../source/) (byggplanen, 2026-11-20) och [`../00-startprompt.md`](../00-startprompt.md) (Jimmys startprompt, 2026-11-20) |
| **Ersätter som utvecklingsunderlag** | [`docs/PRD.md`](../../PRD.md) (v0.1, commit `0eb7b87`). Filen är **inte** ändrad och behålls som historiskt underlag. Där den strider mot den här baslinjen gäller baslinjen, se [10-beslutslogg.md](10-beslutslogg.md). |
| **Bas-commit** | `main` @ `969ed14` |
| **Kod** | Inga kodändringar i den här versionen. PR #1 är inventerad men inte sammanslagen. |

## Rangordning vid konflikt

1. Jimmys startprompt (`00-startprompt.md`), som är senast och uttryckligen bindande.
2. Byggplanen (`source/…utvärdering och byggplan.md`), som är styrande baslinje.
3. Den här baslinjen v0.1, som tolkar och preciserar 1 och 2.
4. Tidigare material: `docs/PRD.md`, Stitch-exporten, PR #1 och förslag i chatten. Det får komplettera men inte tyst ersätta 1 och 2.

## Statusord

Alla beslut och grindar använder bara dessa fyra värden.

| Status | Betydelse |
|---|---|
| `ACCEPT` | Beslutet eller förslaget gäller. För en grind krävs bevis i ett verifieringsprotokoll. |
| `REVISE` | Behålls i ändrad form. Ändringen står i beslutsloggen. |
| `REJECT` | Gäller inte. Skälet står i beslutsloggen. |
| `NOT_VERIFIED` | Bevis saknas. Används i stället för "PASS" och "klar". |

Ordet PASS används inte någonstans i baslinjen. En grind blir `ACCEPT` först när det finns ett daterat verifieringsprotokoll med versionsidentifierare och en oberoende granskares namn.

## Innehåll

| Nr | Dokument | Innehåll |
|---|---|---|
| 00 | [../00-startprompt.md](../00-startprompt.md) | Startprompten ordagrant (källdokument) |
| 01 | [01-inventering.md](01-inventering.md) | Alla källor, status, MISSING och konflikter |
| 02 | [02-PRD.md](02-PRD.md) | Slutmål, första implementation och senare, med avgränsningar |
| 03 | [03-arkitektur.md](03-arkitektur.md) | Moduler, kontrakt och hur gränserna upprätthålls i Gradle |
| 04 | [04-datamodell.md](04-datamodell.md) | Arkiv med fyra ursprungsklasser, expeditioner och offline-paket |
| 05 | [05-handelsekontrakt.md](05-handelsekontrakt.md) | Händelsebussen från Hard Truth Core till Illogical Engine |
| 06 | [06-safetygate.md](06-safetygate.md) | SafetyGate-regler och testmatris |
| 07 | [07-convoy-protokoll-och-hotmodell.md](07-convoy-protokoll-och-hotmodell.md) | Convoy-protokoll och hotmodell |
| 08 | [08-teknikverifieringsplan.md](08-teknikverifieringsplan.md) | Grindarna G0–G8 och bibliotekskontroller med källor |
| 09 | [09-genomforandeplan.md](09-genomforandeplan.md) | De sju etapperna med acceptansgrindar |
| 10 | [10-beslutslogg.md](10-beslutslogg.md) | Varje tidigare förslag med ACCEPT, REVISE eller REJECT |
| 11 | [11-tekniska-antaganden.md](11-tekniska-antaganden.md) | Antaganden som måste verifieras |
| 12 | [12-rostregler.md](12-rostregler.md) | Röstregler (M-08), kandidat, inte fryst |

## Verifiering och ägarbeslut

Dokument under [`../verification/`](../verification/): G0-ägarbeslutet 2026-11-20 (inlagt byte för byte), instruktionen, svaret på sakavvikelser och underlaget för F-11. G0 är fortfarande `NOT_VERIFIED`.

## Arkivering

Startprompten ber om arkivering i "det personliga projektarkivet". Det arkivet är **MISSING**: det har inte funnits åtkomst till det i det här arbetet. **Ingenting har arkiverats där.** Den enda verifierade lagringsplatsen är det privata GitHub-repot `JbmbAb/Pundarens-Guide-till-galaxen-`, grenen `baseline/v0.1`, katalogen `docs/baseline/`.

## Kontrollsummor för källdokumenten (SHA-256)

| Fil | SHA-256 |
|---|---|
| `source/Pundarens Guide till Galaxen – utvärdering och byggplan.md` | `b0927726027a6fe4852c918d38665f16fdd3a87f72ded597057d391f0ea40ef5` |
| `source/Citatarkiv och samtalslogik för Babel-Stoner (sammanställning 2026-11-20).txt` | `f5feca09109298fa1a8bae304314440f96827fa21ee1015764576ae8597a82f5` |
| `docs/PRD.md` (oförändrad) | `2d7f3ed11d32c9d706e0706c820202d0b12d6ceac1d7a3d583ee8242886a5989` |
| `data/quotes/quotes.json` (oförändrad) | `df5ea0053caa601c28073be2ab3aced047e04c1e9cb9295c49daed9ba117c311` |
| `data/babel-stoner/rules.json` (oförändrad) | `9f406d3ecbe520988b4629907855d9c562a1bac0bca98a9c9a9b0006c418cdef` |
| `data/lexicon/lexicon.json` (oförändrad) | `e2fa62827da50a66bb33c3f6f233914055afc868a60c774608dad36c45101860` |
