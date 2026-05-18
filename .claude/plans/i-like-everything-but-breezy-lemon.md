# Home Screen Redesign + App-Wide Design Foundation

## Context

The app currently has no coherent design system: ~25 ad-hoc colors with mixed Spanish/English naming, 8 half-defined `Theme*` styles in [themes.xml](app/src/main/res/values/themes.xml), no custom typography ([res/font/](app/src/main/res/font/) is empty), and stock Material icons. The active theme `ThemeChatGPTBlue` (light blue `#AEDFF7` primary) clashes with the brand direction we want for a daily studying app.

This first pass lays the **foundation** (palette, typography, theme migration to Material 3-style tokens, bottom nav refresh) and **redesigns only the Home screen** as the proving ground. Calendar/Progress/Projects inherit the new palette and nav automatically but are not redesigned yet.

End goal of this pass: Home looks polished and intentional, the design system can be reused for the other screens later, and we have test infrastructure for icons + sounds.

---

## Orientative palette (user-approved, semantic naming)

Orientative palette from user: sage-sand base + controlled indigo for trust + coral/amber for energy. Mapped to semantic tokens so layouts reference *roles*, not raw colors.
Any of this colours can change depending on the layout and the specific application, this list is just an orientative palette that respects the theory of colour. 

| Token | Hex | Role |
|---|---|---|
| `surface` | `#F6F1E8` | App background (warm sand) |
| `surface_card` | `#FFFFFF` | Cards, sheets, dialogs |
| `surface_subtle` | `#E8EFE8` | Secondary surfaces, dividers, chips |
| `primary` | `#5F8A78` | Sage — main brand, primary buttons, active nav |
| `primary_dark` | `#2F4F4A` | Pressed/elevated primary, dark text on sage |
| `secondary` | `#3F4E8C` | Indigo — trust accents, links, headers (used sparingly) |
| `accent_energy` | `#E07A5F` | Coral — call-to-action highlights, reminders, "now" |
| `accent_reward` | `#D9A441` | Amber — streaks, achievements, wins |
| `on_surface` | `#1F2A2A` | Primary text |
| `on_surface_muted` | `#6F7772` | Secondary text, hints |
| `on_primary` | `#FFFFFF` | Text/icons on sage |
| `danger` | `#B5524A` | Errors, destructive actions (brick, harmonized with palette) |

**Categorical reuse for calendar items** (replaces current `calendar_item_*`):
- `calendar_item_event` → `secondary` (#3F4E8C indigo)
- `calendar_item_task` → `primary` (#5F8A78 sage)
- `calendar_item_reminder` → `accent_energy` (#E07A5F coral)

---

## Typography

Empty [res/font/](app/src/main/res/font/) — add via Downloadable Fonts (no APK bloat, no manual `.ttf` shipping).

- **Body / UI**: **Plus Jakarta Sans** — humanist sans, slightly warm, very legible at small sizes. Pairs naturally with sage/sand without feeling sterile like Inter.
- **Display (timer countdown, streak number)**: **Fraunces** (variable serif, soft-modern) — gives the big numbers personality and a "studying / book" feel without being decorative everywhere.
- Fallback: `sans-serif` system default.

Type scale tokens to add in [dimens.xml](app/src/main/res/values/dimens.xml):
- `text_display` 56sp (timer countdown)
- `text_headline` 24sp (section titles)
- `text_title` 18sp (card titles)
- `text_body` 16sp (was 20sp — slightly smaller for density)
- `text_label` 13sp (chips, captions)
- `text_small` 12sp (keep)

---

## Plan

### Step 1 — Design system foundation

**Files modified:**
- [app/src/main/res/values/colors.xml](app/src/main/res/values/colors.xml) — add the semantic tokens above; **keep** legacy color names for now to avoid breaking unrelated layouts, but annotate them as deprecated in a comment block.
- [app/src/main/res/values/themes.xml](app/src/main/res/values/themes.xml) — add a new `Theme.BBetter` style inheriting `Theme.MaterialComponents.Light.NoActionBar` mapping `colorPrimary`/`colorSecondary`/`colorAccent`/`android:windowBackground` etc. to the new semantic tokens. Leave the old `ThemeChatGPTBlue*` aliases in place but point them at the new colors so existing references keep working.
- [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) — switch the `<application android:theme>` (and MainActivity override if any) to `@style/Theme.BBetter`.
- [app/src/main/res/values/dimens.xml](app/src/main/res/values/dimens.xml) — add the type scale tokens above; add `radius_sm` 8dp / `radius_md` 14dp / `radius_lg` 24dp; add `elevation_card` 2dp / `elevation_floating` 8dp.
- [app/src/main/res/font/](app/src/main/res/font/) — create folder; add `plus_jakarta_sans.xml` and `fraunces.xml` font-family files referencing Google Fonts provider certs. Add a `text_default.xml` in `res/values/styles_typography.xml` defining `TextAppearance.BBetter.*` styles per token.
- [app/build.gradle](app/build.gradle) — confirm `material:1.9.0` (keep — do not upgrade), no other dependencies needed for downloadable fonts.

**Reuse:** the existing `FormRow`, `SegmentedButton`, `IconButtonSquare`, `RoundedDialog` styles in [style.xml](app/src/main/res/values/style.xml) — re-tint via the new tokens; do not rewrite.

---

### Step 2 — Bottom navigation + scaffold

**Files modified:**
- [app/src/main/res/layout/activity_main.xml](app/src/main/res/layout/activity_main.xml) — set BottomNavigationView background to `?attr/colorSurface` (white-ish card), `app:itemActiveIndicatorStyle` to a sage pill, `app:itemIconTint` and `app:itemTextColor` to a new color-state-list selector (`primary` when checked, `on_surface_muted` otherwise). Add a 1dp top divider in `surface_subtle`.
- [app/src/main/res/color/bottom_nav_item_tint.xml](app/src/main/res/color/bottom_nav_item_tint.xml) — **new** color-state-list.
- [app/src/main/res/drawable/bottom_nav_indicator.xml](app/src/main/res/drawable/bottom_nav_indicator.xml) — **new** rounded pill drawable for the active item.
- [app/src/main/res/menu/bottom_nav_menu.xml](app/src/main/res/menu/bottom_nav_menu.xml) — swap icon references to the new icon names from Step 4.

This is the only thing that visibly changes on Calendar/Progress/Projects in this pass.

---

### Step 3 — Home screen redesign

Current Home ([fragment_home.xml](app/src/main/res/layout/fragment_home.xml)) is a ConstraintLayout with 4 guidelines and floating widgets on a flat colored background. We restructure it into **three stacked cards** on the sand surface for better hierarchy.

**Files modified:**
- [app/src/main/res/layout/fragment_home.xml](app/src/main/res/layout/fragment_home.xml) — rewrite layout:
  1. **Header strip** — greeting (`text_headline`, sage), small date below (`text_label`, muted). Left-aligned, no card.
  2. **Timer card** (`surface_card`, `radius_lg`, `elevation_card`):
     - Mode label chip at top (`accent_energy` background for "Concentration", `secondary` for "Rest").
     - `homeTimerText` countdown in `Fraunces` `text_display` weight 500, `on_surface`.
     - `homePlayButton` 88dp circle (down from 128dp for better proportion in card), `primary` background, white play glyph, `elevation_floating`.
     - `homeTimerButton` (mode toggle) moves to top-right of card as a 36dp ghost icon button instead of floating bottom-right.
  3. **Today card** (`surface_card`, `radius_lg`):
     - Three stat rows: Studied today (clock icon, sage), Current streak (flame icon, amber), Missed (cross icon, brick).
     - `homeStreakImage` integrated into the streak row as a 32dp leading icon instead of a separate 56dp floating image.
  4. **Quick action row** (optional, scope-flag below) — small chips: "Open calendar", "View progress" linking to nav destinations.
- [app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java](app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java) — **only** update view IDs / bindings to match. ViewModel, CountDownTimer logic, popup wiring (`AlertPopup`, `TimerPopup`, `MessagePopup`), `OnToolbarListener`, study cycle logic — all untouched.
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) — add new string keys for greeting / section titles.

**Scope flag:** The "Quick action row" is optional. If it complicates the layout review, drop it from this pass.

---

### Step 4 — Test vector drawables (sample new icons)

The goal is to **prove the icon migration path** with 3 sample icons in a unified stroke style (1.5px stroke, rounded caps, 24×24 viewport) before committing to a full set. User will source/commission the rest using these as a reference.

**Style direction**: outline + 1.5px stroke + rounded joins; filled variant only for active states. Matches the "Phosphor / Iconoir / Lucide" family aesthetic — see References section.

**New drawables (added, originals kept until visual review):**
- `app/src/main/res/drawable/ic_home_v2_24.xml` — stroke house with door, sage-tintable.
- `app/src/main/res/drawable/ic_calendar_v2_24.xml` — stroke calendar with subtle date dot.
- `app/src/main/res/drawable/ic_play_v2_filled_24.xml` — softened triangle, rounded corners — replaces `ic_play_circle_filled_24` inside the home play button.

**Wired into:** [bottom_nav_menu.xml](app/src/main/res/menu/bottom_nav_menu.xml) (home, calendar items) and [fragment_home.xml](app/src/main/res/layout/fragment_home.xml) (play button). Old drawables (`ic_home_black_24dp`, `ic_calendar_24`, `ic_play_circle_filled_24`) **kept on disk** so we can revert per-icon if you don't like them.

---

### Step 5 — Sound + haptic scaffolding (empty fillers)

Set up the infrastructure with no-op placeholders so the redesign is "sound-ready" without committing to specific assets yet.

**Files added:**
- `app/src/main/java/com/example/bbettercalendar/feedback/SoundFeedback.java` — `SoundPool`-backed singleton with methods `playTap()`, `playSuccess()`, `playStart()`, `playStop()`. Initialized with empty sound IDs (-1); methods early-return if not loaded.
- `app/src/main/java/com/example/bbettercalendar/feedback/HapticFeedback.java` — thin wrapper around `view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)` with `lightTap()`, `confirm()`, `error()`.
- `app/src/main/res/raw/` — folder created, README noting expected filenames: `tap.ogg`, `success.ogg`, `start.ogg`, `stop.ogg`. No audio files committed.

**Wired into:** Home `homePlayButton` and `homeTimerButton` click handlers in `HomeFragment` — `HapticFeedback.lightTap(view)` immediately (works without assets), `SoundFeedback.playStart()` (no-op until files added).

---

## References

### Icon families that fit the sage/sand aesthetic

Pick one and stay consistent across the app:

- **[Phosphor Icons](https://phosphoricons.com/)** — 6 weights (Thin → Duotone), free + open source (MIT). Friendly, slightly rounded. **Top pick for this palette.**
- **[Iconoir](https://iconoir.com/)** — 1500+ icons, MIT, single stroke style. Minimalist, calm — pairs beautifully with sage.
- **[Lucide](https://lucide.dev/)** — fork of Feather, very clean geometric strokes, MIT, huge set.
- **[Tabler Icons](https://tabler.io/icons)** — 5000+, MIT, stroke-based, slightly more "techy" than Phosphor.
- **[Solar Icons](https://solar-icons.com/)** — Linear / Bold / Duotone variants, free. Great if you want active/inactive states from the same family.

If you commission custom icons, give the artist these constraints to match: 24×24 viewport, 1.5px stroke, rounded caps + joins, no inner detail finer than 1px stroke equivalent.

### Free sound sources (no-attribution preferred)

- **[Pixabay Sounds](https://pixabay.com/sound-effects/search/ui/)** — free, commercial use, **no attribution required**. Best first stop for UI tap/success sounds.
- **[Mixkit](https://mixkit.co/free-sound-effects/click/)** — free, no attribution, curated UI/click packs.
- **[Material Design Sound Resources](https://m2.material.io/design/sound/sound-resources.html)** — Google's official UI sounds; consistent, designed for Material apps.
- **[Freesound.org](https://freesound.org/)** — huge library, but **check per-clip license** (CC0 vs CC-BY).
- **[Zapsplat](https://www.zapsplat.com/)** — free with account, attribution required on free tier.

Recommended specs for the 4 sounds: `.ogg` format, 22kHz mono, < 100ms for taps, < 800ms for success/start/stop. Volume normalized to -12 LUFS so they don't startle.

---

## Files modified summary

**New:**
- `app/src/main/res/font/plus_jakarta_sans.xml`
- `app/src/main/res/font/fraunces.xml`
- `app/src/main/res/values/styles_typography.xml`
- `app/src/main/res/color/bottom_nav_item_tint.xml`
- `app/src/main/res/drawable/bottom_nav_indicator.xml`
- `app/src/main/res/drawable/ic_home_v2_24.xml`
- `app/src/main/res/drawable/ic_calendar_v2_24.xml`
- `app/src/main/res/drawable/ic_play_v2_filled_24.xml`
- `app/src/main/res/raw/` (folder, with README)
- `app/src/main/java/com/example/bbettercalendar/feedback/SoundFeedback.java`
- `app/src/main/java/com/example/bbettercalendar/feedback/HapticFeedback.java`

**Edited:**
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/fragment_home.xml`
- `app/src/main/res/menu/bottom_nav_menu.xml`
- `app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java` (view-ID/binding updates + feedback hooks only)

**Unchanged (verify they still render):**
- Calendar fragments, Progress fragment, Projects fragment, all popups, `AddEventActivity`, `AddTaskActivity`.

---

## Verification

1. **Build**: `./gradlew assembleDebug` — must compile with no resource errors. Run `./gradlew lint` and address any new warnings introduced by the changes (ignore pre-existing).
2. **Home screen visual review** (run on emulator or device):
   - Greeting + date show in the new typography.
   - Timer card displays countdown in Fraunces, play button is sage with white glyph, mode chip toggles color when entering rest cycle.
   - Today card shows the 3 stats with icons; streak number animates / updates as before.
   - Tap the play button → haptic tick fires (sound is silent — expected).
3. **Bottom nav**:
   - Active item shows sage pill indicator + sage icon + sage label; inactive items are muted gray.
   - New v2 icons render at correct size and alignment.
4. **No-regression sweep** — open each screen and confirm nothing crashes or renders garbage:
   - Calendar (month + week views, day detail panel)
   - Progress
   - Projects
   - Add event / Add task activities
   - All popups: notifications, repetition, description, error, message, timer configuration
5. **Dark mode**: confirm `values-night/` is **not** overridden — system dark mode should fall back to the light theme cleanly (no white-on-white).
6. **Manual sound test (post-asset)**: once user drops `tap.ogg` in `res/raw/`, uncomment the resource ID load in `SoundFeedback` and re-tap play — audio + haptic fire together.

## Out of scope (next passes)

- Full Calendar/Progress/Projects redesign.
- Material 3 component migration (`Theme.Material3.*`) — needs separate evaluation given `material:1.9.0` pin.
- MotionLayout transitions between fragments.
- Real dark mode palette in `values-night/`.
- Final icon set (waiting on user-sourced assets).
- Real sound assets.
