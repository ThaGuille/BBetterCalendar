---
name: image-to-code
description: Turn a reference image, screenshot, or mockup of another app into a design system for BBetterCalendar — infer palette, type scale, spacing rhythm, radius/depth language and layout archetype from the reference, map them onto the `bb_*` semantic tokens and `TextAppearance.BBetter.*` styles, then rebuild in Android XML. Use when the user supplies a screenshot, photo, Figma export, or mockup and wants BBetter's UI to match that visual language. Matches the SYSTEM, never a pixel copy.
invocation: user
---

# Skill: Image to Code (Android / BBetterCalendar)

Reconstruct a design from a visual reference as a real design system, not a one-off copy. Match the *system* (color / type / spacing language), never lift copyrighted imagery or brand assets.

**Platform:** this project is Java + Android XML Views (Material Components 1.9.0), *not* Compose, not web. Output is `res/layout/*.xml`, `res/values/*.xml`, and `res/drawable/*.xml`.

## Steps

1. **Read the reference like a designer.** Infer and write down:
   - **Palette** — 1 dominant surface family, text colors, 1 primary action + at most 1 accent (sample the hues; don't guess random hex).
   - **Type** — family feel (geometric / grotesk / serif), the scale jumps, display vs. body contrast, weights.
   - **Spacing & density** — base unit, section rhythm, card padding; airy vs. compact. On Android express this as a dp scale in `dimens.xml`, on a 4dp grid.
   - **Radius & depth** — radius language (sharp / soft / pill), and whether separation is shadow, hairline, or **tonal elevation** (MD3's mechanism — prefer it over arbitrary drop shadows).
   - **Layout archetype + sequence** — see `reference/design-taste.md` → Variance Mandate.

2. **Anchor to a known system** if it's close — browse `reference/aesthetic-systems.md`, or query the local database:
   ```powershell
   python "..\ui-ux-pro-max\scripts\search.py" "<term>" --domain color
   python "..\ui-ux-pro-max\scripts\search.py" "<term>" --design-system
   ```
   Adopt that recipe to stabilize decisions rather than inventing every value.

3. **Map onto BBetter's tokens — never introduce a parallel system.** Every inferred value lands on an existing semantic token, or the token set gains a deliberate new member:
   | Inferred role | BBetter token |
   |---|---|
   | Page background | `bb_surface` |
   | Card / dialog surface | `bb_surface_card`, `bb_surface_subtle` |
   | Primary action | `bb_primary` / `bb_primary_dark` |
   | Secondary action | `bb_secondary` |
   | Accent / energy | `bb_accent_energy` |
   | Reward / streak | `bb_accent_reward` |
   | Body / muted text | `bb_on_surface`, `bb_on_surface_muted` |
   | Error / destructive | `bb_danger` |

   Type maps onto `TextAppearance.BBetter.{Display,Headline,Title,Body}`. **Never write raw `#RRGGBB` into a layout** — promote to a token first (project rule #2).

4. **Check contrast before committing a colour.** Every foreground/background pair must clear WCAG AA (4.5:1 body, 3:1 large text), in light *and* dark. A sampled brand colour that fails gets adjusted — taste never overrides accessibility. `ui-ux-pro-max`'s palette data ships pre-corrected pairs and states when it has adjusted one.

5. **Rebuild in Android XML**, token-driven: ConstraintLayout for structure, Material Components for controls, `48×48dp` minimum touch targets with 8dp separation, `sp` for text so it follows the system font scale. Icons are vector drawables, not emoji or PNG.

6. **Verify on the emulator, never in a browser.** Use the `ui-tester` subagent (project rule #7), which drives `adb-ui-test`. For a visual judgement, `capture-screen.ps1` grabs a real PNG. Also check the two things a single screenshot hides:
   ```powershell
   adb shell cmd uimode night yes      # dark theme
   adb shell settings put system font_scale 1.3   # restore 1.0 after
   ```

## Verification (definition of done)

- Every colour resolves through a `bb_*` token; `grep` finds no new hex literals in the touched layouts.
- Contrast AA holds in light and dark.
- The rebuilt UI uses ONE inferred token theme — no per-screen palettes.
- An emulator screenshot visibly matches the reference's *design language*.
- No crash in `logcat -b crash`.

> **Honest limit:** this matches the design **system**, not a pixel-perfect copy. Do not reproduce the reference's photographs, logos, or copyrighted copy — substitute your own or generic placeholders.

## Related

- `impeccable` — owns the aesthetic direction and the critique/audit verbs. If the reference is being used to *redesign*, run `/impeccable shape` first and let this skill supply the extracted values.
- `ui-ux-pro-max` — the palette / type / spacing database this skill queries.
- `material-3` — MD3 colour roles, type scale, shape and elevation the mapping should respect.
