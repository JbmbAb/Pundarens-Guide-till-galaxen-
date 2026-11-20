# Design System Document: The Sentient Archive

## 1. Overview & Creative North Star: "The Sentient Archive"
This design system is built to navigate the intersection of cosmic vastness and brutalist utility. Our Creative North Star is **The Sentient Archive**—a digital entity that feels alive, mystical, yet mathematically precise. 

We move beyond the "template" look by rejecting rigid grids in favor of **Intentional Asymmetry** and **Tonal Depth**. The UI should feel like a series of organic, floating data-shards suspended in a vacuum. We achieve this through extreme corner radii (`ROUND_FULL`), high-contrast typography scales, and a departure from traditional structural lines. The interface doesn't just display information; it "breathes" it.

---

## 2. Color Philosophy & Surface Architecture
Our palette transitions from the absolute void of deep space to the electric hum of neon energy.

### The "No-Line" Rule
**Borders are forbidden.** We do not use 1px solid lines to define sections. Layout boundaries must be defined exclusively through:
1.  **Background Color Shifts:** Placing a `surface_container_low` section against a `surface` background.
2.  **Tonal Transitions:** Using subtle value shifts to imply containment.
3.  **Negative Space:** Leveraging the Spacing Scale (specifically `8` and `12`) to create mental groupings.

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of "frosted obsidian." Use the surface-container tiers to create nested depth:
*   **Base Layer:** `surface` (#0e0e0e) or `surface_container_lowest` (#000000).
*   **Secondary Layer:** `surface_container` (#191919) for main content areas.
*   **Active/Floating Layer:** `surface_container_highest` (#262626) for cards or interactive elements.

### The "Glass & Gradient" Rule
To capture the "Cosmic Chaos" vibe, use **Glassmorphism**. Floating elements should utilize semi-transparent surface colors with a `backdrop-blur` (recommended 12px–20px). 
*   **Signature Texture:** For primary CTAs or Hero sections, apply a linear gradient from `primary` (#c59aff) to `primary_dim` (#9547f7) at a 135-degree angle. This adds "soul" and dimension that flat hex codes lack.

---

## 3. Typography: The Galactic Ledger
We pair the eccentric, technical character of **Space Grotesk** with the neutral, high-readability of **Manrope**.

*   **The Display Scale:** `display-lg` and `display-md` (Space Grotesk) should be used for atmospheric moments. Do not be afraid of tight letter-spacing (-2%) to give it a "dense data" feel.
*   **The Utility Scale:** `body-md` and `label-md` (Manrope) handle the heavy lifting. They provide the "Modern Utility" required to balance the cosmic aesthetic.
*   **Hierarchy Note:** Use `tertiary` (#ff8ba0) for small labels or accents to create a "glitch" of warmth against the cool purple and black, drawing the eye to critical data points.

---

## 4. Elevation & Depth: Tonal Layering
Traditional shadows are too "earthly." In the Sentient Archive, objects float via **Tonal Layering**.

*   **The Layering Principle:** Instead of a drop shadow, place a `surface_container_low` element on a `surface_container_lowest` background. The shift in value creates a natural, soft lift.
*   **Ambient Shadows:** If a floating effect is required (e.g., a modal), use an ultra-diffused shadow: `blur: 40px`, `opacity: 6%`, using the `on_surface` color as the shadow tint. This mimics light refracting through cosmic dust.
*   **The "Ghost Border" Fallback:** If a container is lost against its background, use the `outline_variant` token at **15% opacity**. This creates a "shimmer" edge rather than a hard boundary.

---

## 5. Components

### Buttons
*   **Primary:** `primary` background with `on_primary` text. Shape: `ROUND_FULL`. Apply a subtle `primary_container` outer glow (4px blur) on hover to simulate energy activation.
*   **Secondary:** `surface_container_highest` background with `primary` text. No border.
*   **Tertiary:** Transparent background, `primary` text, with a subtle `primary_dim` underline that glows on hover.

### Cards & Lists
*   **The Rule of Zero Dividers:** Never use lines between list items. Use vertical spacing (Scale `3` or `4`) or alternating background tints (e.g., `surface_container` to `surface_container_high`).
*   **Glitch State:** Interactive cards should have a "subtle glitch" on hover—a 2px horizontal offset of a `secondary` (#d896fe) ghost-layer behind the card.

### Input Fields
*   **Style:** Minimalist. Use `surface_container_low` as the fill. 
*   **Focus State:** The "Ghost Border" becomes 100% opaque `primary`, and the label (`label-sm`) shifts to `primary`.
*   **Error:** Use `error` (#ff6e84) with a soft `error_container` glow behind the text.

### Floating Shards (Specialty Component)
Floating, non-linear UI elements used for tooltips or "Sentient" notifications. Use `surface_bright` with 40% opacity, `backdrop-blur: 16px`, and `ROUND_FULL` corners. These should appear to "drift" slightly using CSS keyframe animations.

---

## 6. Do's and Don'ts

### Do:
*   **Embrace Asymmetry:** Offset your display headings. Let elements overlap slightly (e.g., a floating chip overlapping the edge of a card).
*   **Use Full Radii:** Every interactive container and button must use `ROUND_FULL` to maintain the "organic" feel.
*   **Prioritize Breathing Room:** Use the upper end of the Spacing Scale (`12`, `16`, `20`) to prevent the "Cosmic Chaos" from becoming "Visual Clutter."

### Don't:
*   **Don't use 1px Solid Borders:** They break the immersion of the Sentient Archive. Use background shifts instead.
*   **Don't use Pure White for Body Text:** Use `on_surface_variant` (#ababab) for secondary text to reduce eye strain against the `0e0e0e` background.
*   **Don't use Standard Grids:** Avoid perfectly centered, 12-column layouts. Shift columns by 1–2 units to create a more curated, editorial feel.