# BBetterCalendar — Style Guide

**Last verified:** 2026-10-13 (u24, end of the Stitch visual migration) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The visual language is **Stitch «Calm Focus»**: dark-first, sage on deep pine, honey amber only for earned moments, one family (Plus Jakarta Sans). Source of truth for look and feel: [`.stitch/DESIGN.md`](../../.stitch/DESIGN.md) and the exported designs in `.stitch/designs/` (PNG) + `.stitch/html/`. History and per-unit decisions: [`migracion-visual-stitch-impl.md`](../plans/migracion-visual-stitch-impl.md); open design questions: [`migracion-visual-stitch-questions.md`](../plans/migracion-visual-stitch-questions.md).

## 0. Layers (read this first)

| Layer | Where | Rule |
|---|---|---|
| 1. Primitives | `values/palette.xml` | Raw colours (`pine_950`, `sage_300`…). Referenced **only** from `colors.xml` |
| 2. Semantic tokens | `values/colors.xml` (`bb_*`, `calendar_item_*`) | Alias primitives; everything else references these |
| 3. Type scale | `values/styles_typography.xml` | `TextAppearance.BBetter.*`; size tokens in `dimens.xml` |
| 4. Shapes & component styles | `values/styles_components.xml`, `themes.xml` | `Widget.BBetter.*`, wired from the theme |
| 5. Reusable widgets | `ui/common/widget/*View`, `ui/common/chart/ChartStyler` | Compose these on screens; see the debug gallery (§4) |
| 6. Per-screen resources | `values/strings_<area>.xml`, `values-es/strings_<area>.xml`, `values/dimens_<area>.xml`, `values/styles_<area>.xml` | One area = one file, see §3b |

When touching any UI: `bb_*` tokens + `TextAppearance.BBetter.*` + `Widget.BBetter.*`. No raw hex, no primitives outside layer 2, no literal `sp`, no `@font/` outside the typography files. The legacy palette (`azul`, `verde`, `purple_500`, `white`, `black`…) was **deleted in u24**; the token audit and the PostToolUse hook keep a blocklist so a reference to any of those names is flagged (and would not link anyway).

---

## 1. Colour palette ([`palette.xml`](../../app/src/main/res/values/palette.xml) → [`colors.xml`](../../app/src/main/res/values/colors.xml))

> **Dark-first since u4 of [`migracion-visual-stitch`](../plans/migracion-visual-stitch.md)** (Stitch «Calm Focus», [`.stitch/DESIGN.md`](../../.stitch/DESIGN.md)); u24 removed the obsolete aliases and the legacy palette.
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
| `bb_chart_*` (fill start/end, band, halo), `bb_focus_dot_ring`, `bb_ripple_dark` | alpha variants of sage / dusk / honey / pine | Chart washes (ChartStyler + `bg_chart_*` only), Focus dot halo, pressed wash |

Removed in u24: `bb_accent_energy` (use `bb_primary`) and `bb_surface_energy_subtle` (use `bb_primary_container`).

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

- Don't reintroduce legacy names (`purple_500`, `azul`, `verde`, `white`, `black`…): they no longer exist, and the audit/hook blocklist flags them.
- Don't paint with `@android:color/white|black` or `Color.WHITE/BLACK/LTGRAY/parseColor`; use `bb_on_*` roles (`WeekViewItemAdapter` picks `bb_on_primary` or `bb_on_surface` by luminance).
- Don't introduce raw `#RRGGBB` literals or palette primitives (`sage_300`…) in layouts/Java — use a `bb_*` token (add one in `colors.xml` aliasing a primitive if needed).
- Don't override `colorPrimary` in a new theme just to recolour one screen — use a `bg_*` drawable or a tint instead.

---

## 2. Typography ([`styles_typography.xml`](../../app/src/main/res/values/styles_typography.xml))

One family: **Plus Jakarta Sans**, bundled as TTF (400/500/600/700/800) in `res/font/` and exposed
as the family **`@font/bb_font`** (`res/font/bb_font.xml`, the default, 400/700) plus two
single-weight families for 500/600: **`@font/bb_font_medium`** and **`@font/bb_font_semibold`** (u9b). No serif, no downloadable fonts
(Fraunces and the Google Fonts provider were removed in u6). `Theme.BBetter` sets `fontFamily` /
`android:fontFamily` = `bb_font`, so dialogs, buttons, snackbars and framework text use it too.
Changing the font = swapping the files listed in the `res/font/bb_font*.xml` files; nothing else names a font
(the token audit counts `@font/` / `R.font.` outside `styles_typography.xml` / `themes.xml`).
Views that draw text with a `Paint` (WeekView, charts) get it via `FontHelper.appTypeface(ctx)`, or
at a given weight via `FontHelper.appTypeface(ctx, weight)` (400–800; exact on API 28+, nearest of
regular/bold below — e.g. chart axes = 500, the Caption weight).

Weights (u9b, identical on every API ≥ 21): 400 = `bb_font`; 700 = `bb_font` + `textStyle="bold"` (picks
the real 700 face of the family); 500 = `fontFamily` `bb_font_medium`; 600 = `bb_font_semibold`. The
single-weight families declare their face as the 400 slot and the **Bold TTF as the 700 slot**, so a
`textStyle="bold"` on top of Label/Caption/Chip gives the real 700, never synthetic bolding. Numbers use tabular figures
(`android:fontFeatureSettings="tnum"`; Jakarta ships `tnum`, its default digits are proportional).

| Style | Weight | Size | Use |
|---|---|---|---|
| `TextAppearance.BBetter` | 400 | — | Base (family + colour); rarely applied directly |
| `TextAppearance.BBetter.Display` | 700, tracking −0.01, `tnum` | `text_display` (80sp; 96sp sw600dp) | Countdown, hero number |
| `TextAppearance.BBetter.Stat` | 700, `tnum` | `text_stat` (28sp) | Big stat numbers ("5h 52m") |
| `TextAppearance.BBetter.Headline` | 700 | `text_headline` (22sp) | Screen title |
| `TextAppearance.BBetter.Title` | 700 | `text_title` (17sp) | Section / card title |
| `TextAppearance.BBetter.Body` | 400, line height `text_body_line_height` (22sp = 1.45) | `text_body` (15sp) | Body text |
| `TextAppearance.BBetter.BodyMuted` | as Body, muted colour | `text_body` (15sp) | Secondary copy |
| `TextAppearance.BBetter.Label` | 500, muted | `text_label` (13sp) | Small label above a number |
| `TextAppearance.BBetter.Chip` | 600, ALL CAPS, tracking 0.06, `bb_on_primary` | `text_chip` (12sp) | Tiny status chips on an accent fill |
| `TextAppearance.BBetter.Caption` | 500, muted | `text_caption` (12sp) | Meta, axis-ish captions |
| `TextAppearance.BBetter.Stat.Tile` | 700, `tnum` | `text_stat_tile` (22sp) | Number inside a stat tile / compact strip (u9) |
| `TextAppearance.BBetter.BodyStrong` | 700 | `text_body` (15sp) | List-row title, emphasised value (u9) |
| `TextAppearance.BBetter.Micro` / `.Micro.Bold` | 500 / 700, muted, `tnum` | `text_micro` (11sp) | Weekday letters, delta chips, "87% reached" (u9) |
| `TextAppearance.BBetter.Caption.Semibold` | 600, muted | `text_caption` (12sp) | Header meta ("8h 15m logged") (u9) |
| `TextAppearance.BBetter.Default` | 400, `bb_on_surface` | 14sp (framework Small) | Implicit default of every TextView without a `textAppearance` (theme `android:textViewStyle` → `Widget.BBetter.TextView`) |

**Colour comes from the TextAppearance.** The theme deliberately has **no** `android:textColor`:
a theme-level `android:textColor` is the last fallback of every TextView attribute and silently
overrode every TextAppearance colour (muted labels rendered white). Set `android:textColor` on a
view only when it really differs from its appearance.

**Never `android:textFontWeight` (u9 finding, fixed in u9b).** AppCompatTextHelper re-reads the
view's own attributes after the TextAppearance and resets the weight to "none" (so 500/600/700 rendered
400). Weight lives in the **family** instead (above).

**The theme's `fontFamily` overrides a TextAppearance's family — same trap as `android:textColor`.**
The theme value is the last fallback of every TextView's own attributes, so a `bb_font_medium` in
the appearance lost to the theme's `bb_font` (verified in u9b: without the fix, Caption.Semibold and
ChipLabel rendered 400). Fix: `Widget.BBetter.TextView` (the theme `android:textViewStyle`) and
`Widget.BBetter.SegmentedControl.Item` set `android:fontFamily` / `fontFamily` = **`@null`**, which stops
the lookup before the theme and reads as "no value". Any new **widget style** (Button, CheckBox,
RadioButton…) whose `android:textAppearance` uses a 500/600 appearance must do the same; 400/700
appearances don't need it (they use `bb_font`, like the theme). Appearances applied at runtime
(`setTextAppearance`, Toolbar, BottomNav, Chip) are not affected.

Always set typography via `android:textAppearance="@style/TextAppearance.BBetter.Foo"` rather than `android:fontFamily` + `android:textSize` separately, so future scale changes propagate.

---

## 3. Spacing & sizes ([`dimens.xml`](../../app/src/main/res/values/dimens.xml))

| Token | Value | Use |
|---|---|---|
| `spacing_xs / sm / card_gap / md / lg / xl` | 4 / 8 / 12 / 16 / 24 / 36 dp | 4/8 grid spacing scale (u7: `lg` was 25) |
| `radius_sm / md / inner / card / lg / sheet / pill` | 8 / 12 / 16 / 20 / 24 / 24 / 999 dp | Corner radii (u7, from `.stitch/html`). Cards = `radius_card`; dialogs = `radius_lg`; sheets = `radius_sheet` (top only); any pill `<shape>` = `radius_pill` |
| `elevation_card / floating / fab` | 0 / 8 / 6 dp | Tonal design: cards are flat (u7). Only the one FAB per screen keeps a shadow |
| `button_height / button_text_height / button_padding_horizontal` | 52 / 48 / 24 dp | `Widget.BBetter.Button.*` |
| `play_button_size` (`home_play_button_size` aliases it) | 72 dp | Play / pause circle |
| `segmented_track_height / _padding` | 40 / 4 dp | Segmented control |
| `chip_min_height / chip_status_min_height` | 32 / 24 dp | Chips (`chip_status_min_height` is used by `Widget.BBetter.Chip.Status`) |
| `progress_linear_thickness / progress_ring_thickness` | 6 / 7 dp | Progress indicators |
| `bottom_nav_height / _item_padding_top / _bottom`, `bottom_nav_indicator_width / height` | 72 / 12 / 12, 64 / 32 dp | Bottom navigation |
| `switch_thumb_size / _off / switch_track_height` | 24 / 16 / 28 dp | Switch drawables |
| `home_card_padding` | 20 dp | Padding inside home cards |
| `icon_button_size` | 56 dp | Square icon buttons |
| `form_row_inner_height` | 45 dp | Inner height of the `BBetter.CardFormRow*` rows |
| `text_display / stat / headline / title / body / label / chip / caption` | 80 / 28 / 22 / 17 / 15 / 13 / 12 / 12 sp | Type scale (see above); `text_display_compact` 44sp = compact countdown (TimerPopup, FirstFocus badge) |
| `dialog_min_width` | 240 dp | Minimum popup width |

**Rule**: any new layout uses tokens. If a value is missing from the scale, add a token rather than hard-coding the dp/sp. Per-screen sizes go to `dimens_<area>.xml` (§3b), not here.

## 3b. Per-screen resources (`*_<area>.xml`)

Each migrated screen keeps **its own resource files**, so parallel work never collides in the big shared files. Decision of u24: they **stay** (do not fold them back into `strings.xml` / `dimens.xml`).

| Area | Files |
|---|---|
| `focus` | `strings_focus.xml` (+ `values-es`), `dimens_focus.xml` |
| `cycle` | `strings_cycle.xml`, `dimens_cycle.xml`, `styles_cycle.xml` |
| `projects` | `strings_projects.xml`, `dimens_projects.xml` |
| `progress` | `strings_progress.xml`, `dimens_progress.xml` |
| `all_progress` | `strings_all_progress.xml` |
| `screen_time` | `strings_screen_time.xml`, `dimens_screen_time.xml` |
| `app_detail` | `strings_app_detail.xml`, `dimens_app_detail.xml`, `styles_app_detail.xml` |
| `calendar` | `dimens_calendar.xml` |
| `splash` | `dimens_splash.xml` |

Rules: new strings in `values/strings_<area>.xml` **and** `values-es/strings_<area>.xml`; dimens in `dimens_<area>.xml`; no new colour primitives (an unavoidable colour goes to `colors_<area>.xml` as an alias of a primitive and is explained in the unit notes). Shared widgets and the original Home/popups resources still live in `strings.xml` / `dimens.xml`. Unused resources: run `./gradlew lint` and review `UnusedResources` (u24 cleaned the backlog).

---

## 4. Reusable component styles

### Shapes & components — [`styles_components.xml`](../../app/src/main/res/values/styles_components.xml) (u7)

Default styles are wired from `Theme.BBetter` **and** `Base.Theme.BBetter.Dialog` (keep both
blocks identical — a MaterialComponents dialog parent would otherwise reset them), so a plain
`<Button>` / `MaterialCardView` / `Chip` / `SwitchMaterial` / `FloatingActionButton` /
`BottomNavigationView` / progress indicator already looks right without `style=""`.

| Style | Theme attr | What |
|---|---|---|
| `ShapeAppearance.BBetter.Card / .Pill / .Sheet` | `shapeAppearanceMedium/Small/LargeComponent` | 20dp · 50% · 24dp top only |
| `Widget.BBetter.Card` | `materialCardViewStyle` | `MaterialCardView`: surface_card, 20dp, no elevation/stroke, 16dp padding |
| `Widget.BBetter.Button.Primary` | `materialButtonStyle` | Sage pill, pine text, 52dp |
| `Widget.BBetter.Button.Secondary` | `materialButtonOutlinedStyle` | Hairline (`bb_outline`) outlined pill, Mist text |
| `Widget.BBetter.Button.Reward` | — | Honey pill (celebration only) |
| `Widget.BBetter.Button.Text` / `.Danger` | — | Text-only, sage / ember, 48dp |
| `Widget.BBetter.FAB` | `floatingActionButtonStyle` | Sage circle, pine icon, `elevation_fab` |
| `Widget.BBetter.SegmentedControl` + `.Item` | — | Pill track + pill items; selection via `setSelected` (or `checked` on a RadioButton). Text/background are selectors (`bb_segmented_item_text`, `bg_segmented_item`). Not `MaterialButtonToggleGroup`: MDC 1.9.0 squares the inner corners and throws on custom backgrounds |
| `Widget.BBetter.Chip` / `.Chip.Status` | `chipStyle` | Choice chip (outline → sage when checked) / uppercase status tag on `bb_primary_container` |
| `Widget.BBetter.Switch` | `switchStyle` | `SwitchMaterial` with MD3-like drawables `bb_switch_track` / `bb_switch_thumb`. Don't set `thumbTint` / `trackTint` in layouts |
| `Widget.BBetter.LinearProgress` / `.CircularProgress` | `linear/circularProgressIndicatorStyle` | 6dp sage bar on raised track / 7dp ring on surface_card |
| `Widget.BBetter.BottomNav` (+ `.ActiveIndicator`) | `bottomNavigationStyle` | Pine Card bar, labels on, sage active icon over a Forest pill |
| `Widget.BBetter.Toolbar` | `toolbarStyle` | surface background, Headline title |
| `Widget.BBetter.BottomSheet` | `bottomSheetStyle` (in `ThemeOverlay.BBetter.BottomSheetDialog`) | surface_card, 24dp top corners. The sheet layout's root must not set a background |

Button / chip label appearances: `TextAppearance.BBetter.Button` (theme `textAppearanceButton`,
15sp 700 sentence case) and `TextAppearance.BBetter.ChipLabel` (12sp 600 sentence case).

### Reusable widgets — `ui/common/widget/` (u9)

Compound views with their own `view_*.xml` and `declare-styleable` attrs
([`attrs_widgets.xml`](../../app/src/main/res/values/attrs_widgets.xml), every attr prefixed per widget).
Screens (u13–u20) compose these instead of re-building the panel. All states are visible in the
debug-only `DebugGalleryActivity` (`app/src/debug/`):
`adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugGalleryActivity`.
The one-line Java API of each view is in `.claude/plans/migracion-visual-stitch-impl.md` § u9.

| View | Panel |
|---|---|
| `StatTileView` | label · number (+unit) · [bar] · [delta chip] · [sub]; own card or bare (`tileCard`) |
| `SectionHeaderView` | [icon] title [NEW badge] / subtitle · meta (text or pill) / text action |
| `StreakWeekView` | 7 day dots from `StreakCalculator.Result` + flame label (top) or honey pill (end) |
| `ProgressRowView` | title · value / 6dp bar (colour per project) |
| `AppUsageRowView` | app icon · name [status chip] / usage + bar or "Set limit" · lock switch · chevron |
| `PeriodNavigatorView` | "This week · Mon – Sun" · ‹ › |
| `SegmentedControlView` | Pill segmented control (wraps `Widget.BBetter.SegmentedControl(.Item)`) |
| `AvatarView` | Canvas-drawn initials avatar: none / studying (sage ring) / away (40 %), dot, badge |
| `StepperView` | − value unit / caption + on a dark track, min/max/step |
| `EmptyStateView` | icon disc · title · message · [secondary action] |

### Charts — `ui/common/chart/` (u10, u13b)

`ChartStyler` (instance per context) styles every MPAndroidChart: `styleChart`, `styleUsageLine`, `focusBandsData`, `styleLimitBars` / `splitAtLimit` / `addLimitLine` and the legend/axis helpers; fonts via `FontHelper`. Bars and FOCUS bands are rounded by `RoundedBarChartRenderer`, `RoundedCombinedChartRenderer` and `BandBarDataSet`, hooked from `ChartStyler.styleChart` (idempotent) — never call `chart.setRenderer` yourself. The API is only ever extended, not renamed.

### Debug gallery

`DebugGalleryActivity` (`app/src/debug/`, debug builds only) shows every widget state and chart variant: `adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.debug.DebugGalleryActivity`. Captures `mig-u13b-gallery-chips.png` and `mig-u13b-gallery-bars.png`. Debug builds also have the secondary-destination jump (`bb_debug_nav`, see `bbetter-selectors.md`).

### Per-screen styles

| Style | File | Purpose |
|---|---|---|
| `TextAppearance.BBetter.CycleTitle` / `.CycleTileValue` | `styles_cycle.xml` | Cycle complete title / tile values |
| `Widget.BBetter.Chip.Preset` | `styles_app_detail.xml` | Limit presets (30m / 45m / 1h / no limit) |
| `TextAppearance.BBetter.Body.Medium` | `styles_typography.xml` | 500 body (task titles, u13b) |

### From [`style.xml`](../../app/src/main/res/values/style.xml) (what is left after u24)

| Style | Purpose |
|---|---|
| `BBetter.CardFormRow` / `.Title` / `.Value` | Card form rows of the create event / task screens |
| `BBetter.WeekdayToggle` | Round weekday toggle of the repetition popup |
| `CalendarLegendDay` | Calendar legend header (the one literal `12sp` left in the audit baseline) |
| `Base.Theme.BBetter.Dialog` | DayNight dialog theme carrying the `bb_*` colour roles — parent of the two below; keep its block in sync with `Theme.BBetter` |
| `RoundedDialog` | 90% width dialog on `rounded_square` (`bb_surface_card`, radius 24) — pass to `AlertDialog.Builder(ctx, R.style.RoundedDialog)` |
| `RewardDialog` | Transparent floating window for the reward card (`FirstFocusCelebrationPopup`) |

The 17 dead styles of the pre-migration era (`FormRow*`, `ToggleRow*`, `TimerRow*`, `Stepper*`, `DialogText`, `IconButtonSquare`, `fab_3_rounded`…) were deleted in u24.

When adding a new visual primitive, look here and in `styles_components.xml` first — there is usually a style to extend.

---

## 5. Drawable conventions

Selected drawables you should know about (all under [`res/drawable/`](../../app/src/main/res/drawable/)):

- `bg_card.xml` — `bb_surface_card` rectangle with `radius_card` (20dp). The standard card surface for non-`MaterialCardView` containers.
- `bg_chip_energy.xml` / `bg_chip_primary.xml` / `bg_chip_secondary.xml` — Pill chips (energy and primary map to `bb_primary`).
- `bg_play_button.xml` — Round background of the play button.
- `bg_widget_inset_pill.xml` — Forest (`bb_primary_container`) pill behind delta/status chips (u13b).
- `bg_calendar_day_selected.xml` / `bg_calendar_day_today.xml` — Month-grid highlights, same inset (`calendar_cell_inset`).
- `bg_<area>_*.xml` — Per-screen backgrounds (`bg_project_*`, `bg_cycle_*`, `bg_progress_*`, `bg_screen_time_*`, `bg_app_detail_*`, `bg_focus_*`), same naming as the strings/dimens areas.
- `ic_*_24.xml` — **Icon contract (u8):** every in-app vector carries `android:tint="?attr/colorControlNormal"` on the root (= `bb_on_surface_muted`) and paints its paths with `@color/bb_on_surface` (irrelevant under the `src_in` tint; no hex / `@android:color/white`). Override per use with `app:tint` / `app:drawableTint` / `setImageTintList` (e.g. `bb_on_primary` on the sage play button, `bb_on_surface` for nav chevrons); BottomNav / FAB tint through their `Widget.BBetter.*` style. New icons = Material Symbols **Outlined**, weight 400, 24dp, converted from the official SVG (viewport 960 + `<group android:translateY="960">`): `ic_shield/lock/share/eco(leaf)/skip_next/stop/chevron_left/chevron_right/expand_more/more_vert/info/timer/trophy/person_add/group_add/check_24`. Reused rather than duplicated: bell = `ic_notifications_empty_24`, add = `ic_add_24`, flame = `ic_flame_v2_24`.
- `ic_stat_bbetter.xml` — Notification small icon (leaf). **No theme-attr tint on purpose:** SystemUI can't resolve `?attr` and only uses alpha. The accent comes from `setColor(bb_primary)`.
- `ic_*_v2_24.xml` — Older custom stroke set (home/calendar/flame/clock/cross/play/pause), same tint contract.
- `rounded_square.xml` — `bb_surface_card` + `radius_lg` (24dp). Window background of `RoundedDialog`; also still used by a few legacy stepper / segmented buttons.

### Naming rule

- `bg_*` for backgrounds.
- `ic_*_<n>` for icons sized `<n>` dp (almost always 24).
- Add the `_v2` suffix only when a new variant must coexist with an older one. Otherwise replace in place.

---

## 6. Themes ([`themes.xml`](../../app/src/main/res/values/themes.xml))

Dark-only since the Stitch migration (u5). One theme tree, no runtime `setTheme()`:

- `Theme.BBetter` — parent `Theme.MaterialComponents.DayNight`; night mode is **forced** with
  `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)` in `DBMigration.onCreate` (before any
  activity exists, so no recreation). The dark tokens live in `values/colors.xml`; a future light
  theme = `values-notnight/colors.xml` + dropping the forced mode. There is no `values-night/`.
  Carries all colour roles (`colorSecondary`/`colorAccent` = `bb_primary`, not dusk),
  `colorControlNormal/Activated`, `elevationOverlayEnabled=false`, status bar = `bb_surface`,
  nav bar = `bb_surface_card`, light bars off, `android:textColorPrimary/Secondary/Hint*` (never
  `android:textColor`, see §2), `fontFamily` = `@font/bb_font` (overrides an appearance's family unless the widget style sets `fontFamily=@null`, see §2), `android:textViewStyle`, and the dialog hooks below.
- `Theme.BBetter.NoActionBar` — every activity in the manifest (and the `<application>` default).
  The splash uses it too (u23: window background `bb_surface`); `SplashTheme` no longer exists.
- Dialog hooks wired from `Theme.BBetter`: `alertDialogTheme` / `android:alertDialogTheme` →
  `ThemeOverlay.BBetter.Dialog.Alert`; `materialAlertDialogTheme` →
  `ThemeOverlay.BBetter.MaterialAlertDialog`; `bottomSheetDialogTheme` →
  `ThemeOverlay.BBetter.BottomSheetDialog` (sheet `backgroundTint` = `bb_surface_card`);
  `android:datePickerDialogTheme` / `android:timePickerDialogTheme` → `Theme.BBetter.PickerDialog`.
- `Theme.BBetter.PickerDialog` — framework pickers (`android.app.DatePickerDialog` /
  `TimePickerDialog`). Must stay on a **framework** parent (`@android:style/Theme.Material.Dialog`):
  under MaterialComponents the framework OK/Cancel buttons render invisible. Sage accent and
  buttons (`Widget.BBetter.PickerDialogButton`), `bb_surface_card` background, headers / clock face
  on `bb_surface_raised` (`Widget.BBetter.DatePicker` / `Widget.BBetter.TimePicker`). Pass it
  explicitly: `new DatePickerDialog(ctx, R.style.Theme_BBetter_PickerDialog, …)`.
- Custom dialogs: `RoundedDialog` / `RewardDialog` (see §4), both on `Base.Theme.BBetter.Dialog`.

### Rule of thumb

New activity → `Theme.BBetter.NoActionBar` in the manifest. New dialog → `RoundedDialog`; new picker
→ `Theme.BBetter.PickerDialog`. Never `setTheme()` at runtime, never parent on
`Theme.MaterialComponents.Light*` (the token audit counts `setTheme(`).

---

## 7. Layout patterns

Common patterns you'll see across `res/layout/`:

- **Vertical `LinearLayout` inside a `ScrollView`** for any screen with a column of cards (see [`fragment_home.xml`](../../app/src/main/res/layout/fragment_home.xml)). Use `android:fillViewport="true"` and `android:scrollbars="none"`.
- **Card surface** = `MaterialCardView` (gets `Widget.BBetter.Card` from the theme), or a `FrameLayout` / `LinearLayout` with `android:background="@drawable/bg_card"` + padding. No elevation (tonal design; `elevation_card` = 0dp).
- **Form row** = `BBetter.CardFormRow` + `.Title` / `.Value` inside a card, see `activity_create_event.xml` / `activity_create_task.xml`.
- **Toolbar overlay** = `toolbar_close_or_save.xml`, shared by the event and task forms (48dp touch areas via `bg_close_circle_touch` / `bg_save_pill_touch`).
- **Stepper** = `StepperView` (widget layer); **toggle rows** in popups use `Widget.BBetter.Switch`.
- **Secondary screens** use `topBar` (`MaterialToolbar`) and hide `nav_view`; only the 4 tabs show the bottom bar. Touch targets are at least 48dp (insets via `*_touch` drawables when the visual is smaller).
- **Compose widgets, not views**: lists of stats → `StatTileView` / `ProgressRowView` / `AppUsageRowView`, headers → `SectionHeaderView`, period selection → `SegmentedControlView` + `PeriodNavigatorView`.

---

## 8. Quick checklist before merging a UI change

- [ ] No new raw hex colours, dp/sp, or font-family declarations — all via tokens.
- [ ] No references to the deleted legacy palette names (`azul`, `verde`, `purple_500`, `white`, `black`…); `python .claude/scripts/token_audit.py --baseline .claude/scripts/token_audit_baseline.json` stays at its baseline (every category but one `textsize_literal_sp` is 0).
- [ ] Themes parent on `Theme.BBetter*` (or `RoundedDialog` / `Theme.BBetter.PickerDialog` for dialogs). No `setTheme()`.
- [ ] If a value isn't in `dimens.xml` / `colors.xml` / `styles_typography.xml`, add it there before referencing.
- [ ] `tools:text` previews stay in sync with `@string/*` (don't leave stale placeholders).
- [ ] Strings live in `strings.xml` or `strings_<area>.xml` (EN **and** ES), not inline.
- [ ] `./gradlew lint` is green; review `UnusedResources` after deleting a screen.
