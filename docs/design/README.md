> **STYRANDE BESLUT:** [Gällande inriktning](../GALLANDE-INRIKTNING.md) har företräde framför äldre planer och designförslag.

# Design

Exporterat från Google Stitch (projektet *Pundarens vän till galaxen*). Filerna är oförändrade. De är designreferenser, inte implementationsbeslut.

## Skärmar (`screens/`)

Varje mapp innehåller `code.html` (Stitch-genererad HTML/Tailwind) och, när den finns, `screen.png`.

| Mapp | Innehåll |
|---|---|
| `don_t_panic_home/` | Hem: Cosmic HUD, Wisdom Transmission #42, DON'T PANIC-knapp, Probability Meter 87.4 % |
| `don_t_panic_home_screen/` | Hem, variant med Handdukssensorn, Rikard Interference och Next Meal: Milliways |
| `cosmic_hud_hitchhiker_s_guide_companion/` | Samma HTML som `don_t_panic_home/` (ingen `screen.png` i exporten) |
| `cosmic_navigator/` | Karta med Anti-Navigering-kort, Signal Stability, 87 % chance for snacks |
| `cosmic_navigator_map_screen/` | Karta, variant: "Höger är en fälla. Framåt är en illusion. Vänster är det enda som återstår." |
| `babel_stoner_translator/` | Babel-Stoner (slang), Vibe Intensity, Random Nebula Fact |
| `babel_stoner_translator_screen/` | Babel-Stoner, variant: "Rymden väntar, men chipsen är här." |
| `probability_drive_settings/` | Probability Drive: Rikard Frequency, Poetic Mode Sensitivity, Anti-Navigering Intensity, Glitch Level |
| `probability_drive_settings_screen/` | Inställningar, variant: Vibe Protect, Handdukssensorn (Shake to test), Rikard Sensitivity |

Babel-Stoner-skärmarna visar det ursprungliga slang-läget. Den nya samtalsmotorn (fem personligheter, galaktisk analys och omvänt läge) har ingen design ännu.

## Designsystem (`design-systems/`)

- `cosmic_drift/DESIGN.md`: "Cosmic Chaos & Modern Utility"
- `sentient_archive/DESIGN.md`: "The Sentient Archive"

## Ursprungliga briefer (`original-briefs/`)

- `project_brief_pundarens_v_n_till_galaxen.html`: första briefen
- `updated_project_brief_stoner_s_friend_to_the_galaxy.html`: uppdaterad brief (lägger till Towel Tracker, Vibe Protect, Room och Retrofit)
- `final_project_prd_pundarens_v_n_till_galaxen.html`: slutlig PRD från Stitch, underlaget för den arkiverade `docs/archive/PRD-fore-2026-11-21.md`
