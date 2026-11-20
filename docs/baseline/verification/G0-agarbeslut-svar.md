# Svar till G0-ägarbeslutet den 2026-11-20

Svar till [`G0-agarbeslut-2026-11-20.md`](G0-agarbeslut-2026-11-20.md). Beslutsbladet är inlagt byte för byte och är inte redigerat. Det här dokumentet är bara svaret bredvid, enligt samma princip som för F-07 (invändningar skrivs som svar, protokollet rättas inte).

Kontrollen gjordes 2026-11-20 mot `main` @ `06cd200` och GitHub API:t för `JbmbAb/Pundarens-Guide-till-galaxen-`. Tider nedan är i UTC+1 (Europe/Stockholm).

## 1. Saklig avvikelse: F-16 och stängningen av PR #1

Beslutsbladet avsnitt 3 säger om F-16:

> Stäng PR #1 med hänvisning till beslutsloggen och behåll grenen som referens. Åtgärden är inte utförd.

Det stämmer inte längre mot repots tillstånd, och stämde inte heller när bladet upprättades den 2026-11-20, om klockslaget i API:t jämförs med bladets datum.

Verifierat via GitHub API (`GET /repos/JbmbAb/Pundarens-Guide-till-galaxen-/pulls/1` och `GET /repos/…/branches/cursor/android-milestone-1-7e26`), HTTP 200:

| Uppgift | Värde |
|---|---|
| PR | [#1](https://github.com/JbmbAb/Pundarens-Guide-till-galaxen-/pull/1), "Android-milstolpe 1: Illogical Engine, karta och Babel-Stoner" |
| `state` | `closed` |
| `merged` | `false` |
| Stängd | 2026-11-20 19:40:39 UTC+1 (Europe/Stockholm) |
| Gren | `cursor/android-milestone-1-7e26` finns kvar |
| Grens topp | `eacb01415209926d42d0609d21d1379545255231` avkortat `eacb014`, samma som i 10-beslutsloggen |

Beslutet i sak (stäng utan sammanslagning, behåll grenen) är alltså redan utfört. Fältet `merge_commit_sha` i API-svaret pekar på `6bee1d60ea2be99b8052f0a7becb55e8d3a87262`, men det objektet finns inte i den lokala klonen och är inte förfader till `main` @ `06cd200`. Det är därför inte en sammanslagning, utan GitHubs tillfälliga testmerge.

Fältet `closed_by` saknades i API-svaret, så vem som stängde PR:n har inte verifierats här.

## 2. Övriga sakuppgifter som kontrollerades

- **Bas-commit.** Bladet anger `main` @ `06cd200`. Det stämmer med den lokala klonen.
- **Kontrasttalen i avsnitt 2.** Uträknade på nytt ur färgkoderna (WCAG 2.1, relativ luminans), inte mätta på skärm. Turkos `#4af8e3` mot `#0e0e0e` ger 14,55:1. Svart text på turkos ger 15,83:1. Båda stämmer. Talet 1,68:1 för turkos mot lila stämmer mot `#c59aff` (1,6766, avrundat 1,68). Mot den andra lilan, `#d896fe`, blir kontrasten 1,63:1. Båda underkänner turkos och lila som enda skillnad mellan ytor intill varandra. Avvikelsen är alltså bara vilken av de två lilatonerna som 1,68:1 avser.
- **Avsnitt 8.** "Inget är incheckat, pushat eller ändrat i repot" beskriver bladets tillstånd när det skrevs. Det här svaret och den tillhörande dokumentations-PR:en är just den incheckningen. Bladet har inte ändrats.

## 3. Vad som inte kunde verifieras

Inget av följande har rekonstruerats.

- Det personliga arkivet på Google Drive (`Personligt_Arkiv`, mapparna 00–07, och att `04_Citat_och_paradoxer` bara innehåller `00_KONTEXT.md`). Drive har inte öppnats i det här arbetet. F-13 är fortfarande öppen. `Citat_och_paradoxer_falsifiering.md` och "bilagan på 912 rader" (M-02, SHA-256 som enligt bladet börjar med `5761ace7`) är fortfarande MISSING i repot.
- Hänvisningen under F-09 till "bilagan rad 216–222", eftersom bilagan saknas.
- Innehållet i byggplanens saknade diagram (M-03), utöver det bladet återger.
- Samsungs svenska talmotor (`com.samsung.SMT.lang_sv_se_f00`), GNSS-funktionen på SM-X900, Android-versionen och det lediga utrymmet på cirka 60 GB. Det är uppgifter att mäta på målenheten (avsnitt 5, A-08, A-10, A-12).
- Att Mimer är Jimmys företag. Det är dokumenterat som Jimmys eget svar (F-14) och har inte kontrollerats mot någon annan källa.
