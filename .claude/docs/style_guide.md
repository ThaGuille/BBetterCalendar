# BBetterCalendar — Style Guide

The visual language is **sage + sand + indigo with coral/amber accents** — a calm, slightly warm "productivity" feel.

When touching any UI, prefer the `bb_*` semantic tokens and `TextAppearance.BBetter.*` styles. The legacy palette (`azul`, `verde`, `purple_500`, etc.) is kept *only* so old layouts keep rendering and must not be added to in new code.

---

## 1. Colour palette ([`res/values/colors.xml`](../../app/src/main/res/values/colors.xml))

### Semantic tokens — use these

| Token | Hex | Use for |
|---|---|---|
| `bb_surface` | `#F6F1E8` | Window background ("sand") |
| `bb_surface_card` | `#FFFFFF` | Card / dialog surface |
| `bb_surface_subtle` | `#E8EFE8` | Subtle sage tint (nav indicator pill, hover) |
| `bb_primary` | `#5F8A78` | Brand sage — primary actions, FAB |
| `bb_primary_dark` | `#2F4F4A` | Status bar, dark sage emphasis |
| `bb_secondary` | `#3F4E8C` | Indigo — secondary actions, info chips |
| `bb_accent_energy` | `#E07A5F` | Coral — Pomodoro "concentration" / play button |
| `bb_accent_reward` | `#D9A441` | Amber — streaks, rewards |
| `bb_on_surface` | `#1F2A2A` | Body text |
| `bb_on_surface_muted` | `#6F7772` | Secondary text, hints |
| `bb_on_primary` | `#FFFFFF` | Text on sage/indigo/coral backgrounds |
| `bb_danger` | `#B5524A` | Errors, destructive actions |
| `bb_divider` | `#22000000` | Subtle dividers (13% black) |

### Calendar categoricals

| Token | Maps to | Use |
|---|---|---|
| `calendar_item_event` | indigo (`bb_secondary`) | Type 1 — events |
| `calendar_item_task` | sage (`bb_primary`) | Type 2 — tasks |
| `calendar_item_reminder` | coral (`bb_accent_energy`) | Type 3 — reminders |

These are resolved by [`ColorResolver`](../../app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/ColorResolver.java); if you add a new entry type, add the colour here and the resolver branch together.

### What not to do

- Don't reach for `purple_500`, `teal_700`, `azul`, `verde`, etc. for new UI. They exist for backwards compatibility.
- Don't introduce raw `#RRGGBB` literals in layouts — promote to a `bb_*` token first.
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
