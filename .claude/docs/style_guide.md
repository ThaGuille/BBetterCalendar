# BBetterCalendar — Style Guide

The visual language is **sage + sand + indigo with coral/amber accents** — a calm, slightly warm "productivity" feel.

When touching any UI, prefer the `bb_*` semantic tokens and `TextAppearance.BBetter.*` styles. The legacy palette (`azul`, `verde`, `purple_500`, etc.) is kept *only* so old layouts keep rendering and must not be added to in new code.

---

## 1. Colour palette ([`palette.xml`](../../app/src/main/res/values/palette.xml) → [`colors.xml`](../../app/src/main/res/values/colors.xml))

> **Dark-first since u4 of [`migracion-visual-stitch`](../plans/migracion-visual-stitch.md)** (Stitch «Calm Focus», [`.stitch/DESIGN.md`](../../.stitch/DESIGN.md)). Full rewrite of this guide is u24; until then the tables below are the source of truth for colour.
> Two layers: `palette.xml` holds raw **primitives** (`pine_950`, `sage_300`, …); `colors.xml` holds the `bb_*` **semantic tokens**, which only alias primitives. Layouts, drawables, themes and Java reference `bb_*` only — a primitive outside `palette.xml`/`colors.xml` is counted by `token_audit.py` (`palette_primitive_ref`, ratchet at 0). A future light theme = `values-notnight/colors.xml` redefining the `bb_*` names.

### Semantic tokens — use these

| Token | → Primitive (hex) | Use for |
|---|---|---|
| `bb_surface` | `pine_950` (`#12201C`) | Window background |
| `bb_surface_card` | `pine_900` (`#1B2D27`) | Cards, sheets, dialogs, bottom nav |
| `bb_surface_subtle` / `bb_surface_raised` | `pine_800` (`#24392F`) | Raised / selected surfaces, active chip, inputs |
| `bb_primary` | `sage_300` (`#7FB69A`) | Primary actions, active nav, progress, chart hero line |
| `bb_on_primary` | `pine_950` | Text/icons on sage (and on any accent fill) |
| `bb_primary_dark` / `bb_primary_container` | `forest_600` (`#2F4F4A`) | Tinted containers |
| `bb_on_primary_container` | `mist_50` | Text on `bb_primary_container` |
| `bb_secondary` / `bb_data_compare` | `dusk_300` (`#8FA8D8`) | Chart comparison series. Never a primary action |
| `bb_accent_reward` | `honey_300` (`#F2B544`) | Earned moments only (streak, best point, celebration) |
| `bb_on_reward` | `pine_950` | Text on honey |
| `bb_on_surface` | `mist_50` (`#EAF2EC`) | Primary text, big numbers |
| `bb_on_surface_muted` | `lichen_300` (`#9DB3A8`) | Secondary labels, hints, timestamps |
| `bb_disabled` | `mist_50_a38` | Disabled content (38 %) |
| `bb_divider` / `bb_outline` | `pine_700` (`#2E453B`) | Dividers, gridlines, unselected outlines |
| `bb_danger` | `ember_300` (`#E8796B`) | Destructive actions, errors |
| `bb_scrim` | `pine_950_a60` | Scrims / dim overlays (never pure black) |
| `bb_project_1..4` | sage_300, honey_300, sage_200, seafoam_200 | Project accents (`ProjectListAdapter.ACCENT_COLORS`) |
| ~~`bb_accent_energy`~~ | alias of `bb_primary` | Obsolete, removed in u24 |
| ~~`bb_surface_energy_subtle`~~ | alias of `bb_primary_container` | Obsolete, removed in u24 |

Contrast (WCAG, computed): every text token ≥ 4.5:1 on `bb_surface` and `bb_surface_card`. On
`bb_primary_container` use only `bb_on_primary_container`/`bb_on_surface`/`bb_accent_reward`;
`bb_danger` text goes on `bb_surface`/`bb_surface_card`, not on `bb_surface_raised`. Table in
[`migracion-visual-stitch-impl.md`](../plans/migracion-visual-stitch-impl.md) (u4).

### Calendar categoricals (provisional until the calendar is designed)

| Token | Maps to | Use |
|---|---|---|
| `calendar_item_event` | `dusk_300` | Events |
| `calendar_item_task` | `sage_300` | Tasks |
| `calendar_item_reminder` | `honey_300` | Reminders |
| `calendar_item_deadline` | `ember_300` | Project deadlines |

Text on them is `bb_on_primary`. These are resolved by [`ColorResolver`](../../app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/ColorResolver.java); if you add a new entry type, add the colour here and the resolver branch together.

### What not to do

- Don't reach for `purple_500`, `teal_700`, `azul`, `verde`, etc. for new UI. They exist for backwards compatibility.
- Don't introduce raw `#RRGGBB` literals or palette primitives (`sage_300`…) in layouts/Java — use a `bb_*` token (add one in `colors.xml` aliasing a primitive if needed).
- Don't override `colorPrimary` in a new theme just to recolour one screen — use a `bg_*` drawable or a tint instead.

---

## 2. Typography ([`styles_typography.xml`](../../app/src/main/res/values/styles_typography.xml))

Two families, both loaded as downloadable fonts with system sans fallback:

- **Plus Jakarta Sans** — body / UI
- **Fraunces** — display (large numbers, e.g. the Pomodoro countdown)

| Style | Family / weight | Size | Use |
|---|---|---|---|
| `TextAppearance.BBetter` | Jakarta regular | body | Generic base — rarely applied directly |
| `TextAppearance.BBetter.Display` | Fraunces | `text_display` (56sp) | Countdown, hero numbers |
| `TextAppearance.BBetter.Headline` | Jakarta semibold | `text_headline` (24sp) | Screen header ("Hi there") |
| `TextAppearance.BBetter.Title` | Jakarta semibold | `text_title` (18sp) | Card titles |
| `TextAppearance.BBetter.Body` | Jakarta regular | `text_body` (16sp) | Standard body text |
| `TextAppearance.BBetter.BodyMuted` | Jakarta regular | `text_body` (16sp) | Secondary copy (muted colour) |
| `TextAppearance.BBetter.Label` | Jakarta medium | `text_label` (13sp), letter-spacing `0.04` | Form labels, subtitles |
| `TextAppearance.BBetter.Chip` | Jakarta semibold ALL CAPS | `text_label` (13sp), letter-spacing `0.06` | Chips ("CONCENTRATION") |

Always set typography via `android:textAppearance="@style/TextAppearance.BBetter.Foo"` rather than `android:fontFamily` + `android:textSize` separately, so future scale changes propagate.

---

## 3. Spacing & sizes ([`dimens.xml`](../../app/src/main/res/values/dimens.xml))

| Token | Value | Use |
|---|---|---|
| `spacing_xs / sm / md / lg` | 4 / 8 / 16 / 25 dp | 4-step spacing scale |
| `radius_sm / md / lg` | 8 / 14 / 24 dp | Corner radii |
| `elevation_card / floating` | 2 / 8 dp | Elevations |
| `home_card_padding` | 20 dp | Padding inside home cards |
| `home_play_button_size` | 88 dp | Pomodoro play button |
| `home_stat_icon_size` | 28 dp | Stat icons in the today card |
| `icon_button_size` | 56 dp | Square icon buttons |
| `primary_button_size` | 128 dp | Big primary action |
| `form_row_height / icon / arrow / inner` | 70 / 45 / 20 / 45 dp | Form rows in AddEventActivity |
| `text_display / headline / title / body / label / small / dialog` | 56 / 24 / 18 / 16 / 13 / 12 / 18 sp | Type scale (see above) |
| `dialog_min_width` | 240 dp | Minimum popup width |

**Rule**: any new layout uses tokens. If a value is missing from the scale, add a token rather than hard-coding the dp/sp.

---

## 4. Reusable component styles

### From [`style.xml`](../../app/src/main/res/values/style.xml)

| Style | Purpose |
|---|---|
| `FormRow` / `FormRowText` / `FormRowIcon` / `FormRowArrow` | Building blocks for the AddEventActivity form rows |
| `SegmentedButton` | Pill button group |
| `IconButtonSquare` | 56dp rounded-square icon button |
| `ToggleRow` / `ToggleRowButton` / `ToggleRowText` | Toggle list rows (notifications popup, repetition popup) |
| `TimerRow` / `TimerRowLabel` / `StepperButton` / `StepperValue` / `StepperToggle` | Timer-popup steppers |
| `DialogText` | Default dialog text style |
| `CalendarLegendDay` / `AppTheme.WeekDays` / `AppTheme.CalendarHours` | Calendar legend & headers |
| `RoundedDialog` | 90% width rounded-corner dialog theme |
| `fab_3_rounded` | FAB shape — 50% rounded corners |
| `Widget.BBetter.BottomNav.ActiveIndicator` / `ShapeAppearance.BBetter.NavIndicator` | Bottom-nav active-item pill |

### From [`styles_typography.xml`](../../app/src/main/res/values/styles_typography.xml)

| Style | Purpose |
|---|---|
| `Widget.BBetter.Card` | Card surface: `bg_card` + `elevation_card` + `home_card_padding` |

When adding a new visual primitive, look here first — there's usually a style to extend.

---

## 5. Drawable conventions

Selected drawables you should know about (all under [`res/drawable/`](../../app/src/main/res/drawable/)):

- `bg_card.xml` — `bb_surface_card` rectangle with `radius_lg`. The standard card surface.
- `bg_chip_energy.xml` — Coral pill (used as the "Concentration" chip).
- `bg_chip_secondary.xml` — Indigo pill.
- `bg_play_button.xml` — Round button background for the play button.
- `bg_mode_toggle.xml` — Background for the concentration/rest toggle.
- `bg_calendar_day_selected.xml` — Selected-day highlight in the month grid.
- `bottom_nav_indicator.xml` — Pill behind the active bottom-nav item.
- `ic_*_v2_24.xml` — The current visual icon set (24dp). Older `ic_*_24.xml` variants exist for backwards compatibility.
- `rounded_square.xml` — Generic rounded background for stepper / segmented buttons.

### Naming rule

- `bg_*` for backgrounds.
- `ic_*_<n>` for icons sized `<n>` dp (almost always 24).
- Add the `_v2` suffix only when a new variant must coexist with an older one. Otherwise replace in place.

---

## 6. Themes ([`themes.xml`](../../app/src/main/res/values/themes.xml))

- `Theme.BBetter` is the canonical app theme — sage primary, indigo secondary, coral accent. Used as the parent for everything in active development.
- `Theme.BBetter.NoActionBar` for activities that supply their own `Toolbar`.
- `Theme.BBetterCalendar` is the alias the manifest uses.
- `ThemeChatGPTBlue` / `ThemeColorBlue` / `ThemeColorGreen` / `ThemeColorPurple` / `ThemeColorNavyBlue` / `ThemeColorEvenTalk` / `FakeTheme` — legacy themes kept so old references compile. Several inherit from `Theme.BBetter` now, so most screens get the new palette transparently. Don't introduce new references to these.
- `ThemeChatGPTBlue_AndroidPopups` is the dialog-alert theme — extend it (don't replace) if you need a new dialog look.
- `SplashTheme` uses `blue_oval` as window background for the splash screen.

### Rule of thumb

If adding a new screen: parent your theme on `Theme.BBetter` or `Theme.BBetter.NoActionBar`. Don't parent on `Theme.MaterialComponents.*` directly — you lose the palette overrides.

---

## 7. Layout patterns

Common patterns you'll see across `res/layout/`:

- **Vertical `LinearLayout` inside a `ScrollView`** for any screen with a column of cards (see [`fragment_home.xml`](../../app/src/main/res/layout/fragment_home.xml)). Use `android:fillViewport="true"` and `android:scrollbars="none"`.
- **Card surface** = `FrameLayout` (or `LinearLayout`) with `android:background="@drawable/bg_card"`, `android:elevation="@dimen/elevation_card"`, `android:padding="@dimen/home_card_padding"`. Equivalent to applying `Widget.BBetter.Card`.
- **Form row** = `LinearLayout style="@style/FormRow"` containing an icon, a label/`EditText`, and a trailing arrow. See `activity_create_event.xml`.
- **Toolbar overlay** = `toolbar_close_or_save.xml` is reused by activities that need a close + save toolbar (AddEventActivity).
- **Stepper** = `TimerRow` + `StepperButton`(`−`) + `StepperValue` + `StepperButton`(`+`) (see `popup_home_timer_configuration.xml`).
- **Toggle row** = `ToggleRow` + `ToggleRowButton` (a `ToggleButton`) + `ToggleRowText`. Used by the notifications and repetition popups.

---

## 8. Quick checklist before merging a UI change

- [ ] No new raw hex colours, dp/sp, or font-family declarations — all via tokens.
- [ ] No new references to legacy palette names (`azul`, `verde`, `purple_500`, …).
- [ ] Themes parent on `Theme.BBetter*` (or extend `RoundedDialog` / `ThemeChatGPTBlue_AndroidPopups` for dialogs).
- [ ] If a value isn't in `dimens.xml` / `colors.xml` / `styles_typography.xml`, add it there before referencing.
- [ ] `tools:text` previews stay in sync with `@string/*` (don't leave stale placeholders).
- [ ] Strings live in `strings.xml`, not inline.
