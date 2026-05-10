# Layout Migration Plan — Unify on ConstraintLayout + Responsive Dimens

## Context

The project's XML layouts mix three root containers (`RelativeLayout`, `LinearLayout` with deeply nested weights, and `ConstraintLayout`) and rely heavily on hard-coded `dp` magic numbers (e.g. `marginTop="60dp"`, `height="70dp"` repeated across rows). This causes two real problems:

1. **Poor responsiveness** — fixed margins on [fragment_home.xml](app/src/main/res/layout/fragment_home.xml) cause elements to overlap on short devices and float in empty space on tall ones; no tablet behavior.
2. **Maintenance cost** — [activity_create_event.xml](app/src/main/res/layout/activity_create_event.xml) and [activity_create_task.xml](app/src/main/res/layout/activity_create_task.xml) duplicate the same icon-label-arrow row 6× each, and the popup files do the same with toggle rows. Any visual tweak requires editing N copies.

**Outcome:** every screen keeps its current visual form, but moves to a single layout system (`ConstraintLayout`), uses percent guidelines instead of magic margins, references a shared `dimens.xml` for sizes, reuses styles for repeated text/buttons, and extracts duplicated rows into `<include>` files. Calendar layouts are excluded — they are migrated in a separate effort.

---

## Scope

**In scope (12 files):**
- [fragment_home.xml](app/src/main/res/layout/fragment_home.xml)
- [activity_create_event.xml](app/src/main/res/layout/activity_create_event.xml)
- [activity_create_task.xml](app/src/main/res/layout/activity_create_task.xml)
- [popup_notifications.xml](app/src/main/res/layout/popup_notifications.xml)
- [popup_repetition.xml](app/src/main/res/layout/popup_repetition.xml)
- [popup_home_timer_configuration.xml](app/src/main/res/layout/popup_home_timer_configuration.xml)
- [popup_description.xml](app/src/main/res/layout/popup_description.xml)
- [popup_message.xml](app/src/main/res/layout/popup_message.xml)
- [popup_error.xml](app/src/main/res/layout/popup_error.xml)
- [add_notification.xml](app/src/main/res/layout/add_notification.xml)
- [activity_splash.xml](app/src/main/res/layout/activity_splash.xml)
- [toolbar_close_or_save.xml](app/src/main/res/layout/toolbar_close_or_save.xml) — keep root, add styles only
- [values/dimens.xml](app/src/main/res/values/dimens.xml) — expand
- [values/style.xml](app/src/main/res/values/style.xml) — expand

**Out of scope (excluded by user):**
- `fragment_calendar_month.xml`, `fragment_calendar_week.xml`, `fragment_calendar_week_simple.xml`, `recyclerview_week_event.xml`
- `activity_main.xml` (already a clean ConstraintLayout)
- `fragment_progress.xml`, `fragment_projects.xml` (already ConstraintLayout placeholders)

---

## Migration Rules (apply uniformly)

1. **Root container:** `androidx.constraintlayout.widget.ConstraintLayout` for every screen and popup. Exception: keep `<LinearLayout>` only for the inner `RecyclerView`-style item rows where it's already 17 lines and trivial, and for `ScrollView`'s single child (must be a single `ViewGroup`).
2. **All IDs preserved** — Java code in `HomeFragment`, `AddEventActivity`, popup classes binds by ID. No ID renames in this migration.
3. **Replace magic margins with `Guideline`s** at percent positions for vertical rhythm in `fragment_home`.
4. **Use `0dp` (match_constraint)** with start/end constraints instead of `match_parent` inside the root.
5. **Promote magic numbers to `dimens.xml`** with semantic names. Don't extract structural `1dp` dividers.
6. **Replace duplicated rows with `<include>`** files using `android:id` overrides on the include itself.
7. **Use existing styles where they fit** (`AppTheme.WeekDays`, etc. in [style.xml](app/src/main/res/values/style.xml)), and add new ones for repeated text patterns (`textSize="20sp"` appears 30+ times).
8. **No code changes.** Visual form, IDs, and behavior must be identical.

---

## Phase 1 — Foundation (do first, blocks everything else)

### 1a. Expand `dimens.xml`

Add to [values/dimens.xml](app/src/main/res/values/dimens.xml):
```xml
<!-- Spacing -->
<dimen name="spacing_xs">4dp</dimen>
<dimen name="spacing_sm">8dp</dimen>
<dimen name="spacing_md">16dp</dimen>
<dimen name="spacing_lg">25dp</dimen>

<!-- Form rows -->
<dimen name="form_row_height">70dp</dimen>
<dimen name="form_row_icon_size">45dp</dimen>
<dimen name="form_row_arrow_size">20dp</dimen>

<!-- Buttons -->
<dimen name="icon_button_size">56dp</dimen>
<dimen name="primary_button_size">128dp</dimen>
<dimen name="segmented_button_height">36dp</dimen>

<!-- Text -->
<dimen name="text_body">20sp</dimen>
<dimen name="text_small">12sp</dimen>

<!-- Fixed positions formerly hardcoded -->
<dimen name="home_timer_button_margin_bottom">45dp</dimen>
<dimen name="calendar_fab_margin_bottom">65dp</dimen>
```

Also create **`res/values-sw600dp/dimens.xml`** overriding 4–5 of the largest values (`primary_button_size`, `form_row_height`, `text_body`) for tablets. Optional but cheap and unblocks future tablet support.

### 1b. Add styles to `style.xml`

Add to [values/style.xml](app/src/main/res/values/style.xml):
```xml
<style name="FormRow">
    <item name="android:layout_width">match_parent</item>
    <item name="android:layout_height">@dimen/form_row_height</item>
    <item name="android:orientation">horizontal</item>
    <item name="android:gravity">center|start</item>
    <item name="android:clickable">true</item>
    <item name="android:focusable">true</item>
</style>

<style name="FormRow.Text">
    <item name="android:textSize">@dimen/text_body</item>
    <item name="android:textColorHint">@color/black</item>
    <item name="android:gravity">center|start</item>
</style>

<style name="IconButton.Square">
    <item name="android:layout_width">@dimen/icon_button_size</item>
    <item name="android:layout_height">@dimen/icon_button_size</item>
    <item name="android:background">@drawable/rounded_square</item>
    <item name="android:elevation">5dp</item>
    <item name="android:clickable">true</item>
    <item name="android:focusable">true</item>
</style>
```

### 1c. Create reusable include layouts

New file **`res/layout/row_form_field.xml`** — leading icon + label TextView + trailing arrow. Used by `event_start_date_layout`, `event_notifications_layout`, `event_description_layout`, etc. Driver pattern:
```xml
<include
    android:id="@+id/event_start_date_layout"
    layout="@layout/row_form_field" />
```
(Java code finds child IDs via `findViewById` on the included root — verify by grep before committing; if Java binds to nested IDs like `event_start_date`, the include must keep those IDs.)

New file **`res/layout/row_toggle_option.xml`** — toggle button + label TextView, used by `popup_notifications` (7×) and `popup_repetition` (4×).

New file **`res/layout/divider_horizontal.xml`** — the 1dp `#D3D3D3` line repeated everywhere.

---

## Phase 2 — High-impact screen rewrites

### 2a. `fragment_home.xml` (highest visual gain)

Convert `RelativeLayout` → `ConstraintLayout`. Replace every `marginTop="60dp"` / `"50dp"` / `"25dp"` with **percent Guidelines**:
- Guideline at `0.10` → top of `homeTimerText`
- Guideline at `0.30` → top of `homePlayButton`
- Guideline at `0.50` → top of `homeTodayTimeText`
- Guideline at `0.70` → top of `homeCurrentStreakText`

Center `homeStreakImage` with `app:layout_constraintCircle` is overkill — just `centerInParent`-equivalent constraints. Move `homeTimerButton` to bottom-right via `layout_constraintBottom_toBottomOf="parent"` + `layout_constraintEnd_toEndOf="parent"` with `@dimen/home_timer_button_margin_bottom`.

### 2b. `activity_create_event.xml` and `activity_create_task.xml`

These two files are ~95% identical and together are ~750 lines. Rewrite as `ConstraintLayout` root (toolbar at top, ScrollView below with `0dp` height + constraints to fill remaining space). Inside the ScrollView keep a single `LinearLayout vertical` (ScrollView requires one direct child) but each form row becomes:

```xml
<include layout="@layout/row_form_field" android:id="@+id/event_title_layout" />
<include layout="@layout/divider_horizontal" />
```

Verify: grep [AddEventActivity.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java) and the task creation activity for every `findViewById` to confirm which child IDs (`event_title`, `event_start_date`, etc.) must survive inside the include — those IDs go inside `row_form_field.xml` as the canonical names.

### 2c. Popups with repeated rows

- **[popup_notifications.xml](app/src/main/res/layout/popup_notifications.xml)** (267 → ~80 lines): 7× `<include layout="@layout/row_toggle_option" android:id="@+id/notif_5min" />` etc. Root → `ConstraintLayout` wrapping a `LinearLayout vertical` (or `ScrollView` if these need scrolling on small screens — verify).
- **[popup_repetition.xml](app/src/main/res/layout/popup_repetition.xml)** (138 → ~50 lines): same pattern with 4 includes.
- **[popup_home_timer_configuration.xml](app/src/main/res/layout/popup_home_timer_configuration.xml)** (311 lines): the timer block and rest block are near-duplicates. Extract `block_timer_config.xml` and include twice with different IDs.

---

## Phase 3 — Small popups & helpers

Convert each to `ConstraintLayout` root and replace hardcoded values with dimens. Low risk, mechanical:

- [popup_description.xml](app/src/main/res/layout/popup_description.xml)
- [popup_message.xml](app/src/main/res/layout/popup_message.xml) — drop hardcoded `width="240dp"`, use `wrap_content` with min-width via dimens
- [popup_error.xml](app/src/main/res/layout/popup_error.xml)
- [add_notification.xml](app/src/main/res/layout/add_notification.xml)
- [activity_splash.xml](app/src/main/res/layout/activity_splash.xml) — only 13 lines but inconsistent

[toolbar_close_or_save.xml](app/src/main/res/layout/toolbar_close_or_save.xml): keep `Toolbar>LinearLayout` structure (Toolbar requires it); only swap magic numbers for dimens.

---

## Critical files to read before each phase

- [AddEventActivity.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java) — confirms which IDs must survive in `row_form_field.xml`
- [HomeFragment.java](app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java) — confirms `fragment_home` ID usage
- All `popups/*.java` classes — each binds to one of the popup XMLs by ID
- [style.xml](app/src/main/res/values/style.xml) and [dimens.xml](app/src/main/res/values/dimens.xml) — current state

---

## Verification

After each phase:

1. **Build:** `./gradlew assembleDebug` — must compile cleanly.
2. **Lint:** `./gradlew lint` — no new layout warnings (unbounded weights, deep nesting).
3. **Layout Inspector / Preview:** open every modified XML in Android Studio Design view; confirm it matches the previous preview side-by-side. Use the device picker to preview on `Pixel 4a`, `Pixel Fold`, `Nexus 7` to confirm responsive behavior.
4. **Manual smoke test on emulator:**
   - Home screen renders correctly on a small (4.7") and tall (6.7") AVD.
   - Open AddEvent, switch Event/Task tabs, fill every row, tap each row's popup.
   - Open every popup from its trigger and verify all toggles/buttons respond.
5. **Diff check:** `git diff --stat` — Java files should be **untouched** (or only auto-generated R.java references).

Stop and reassess if any IDs went missing or Java compilation fails — those are signals an `<include>` lost a nested ID.

---

## Suggested commit boundaries

1. Phase 1a + 1b + 1c (foundation, no screens changed yet)
2. `fragment_home.xml`
3. `activity_create_event.xml` + `activity_create_task.xml` (paired — they share `row_form_field`)
4. `popup_notifications.xml` + `popup_repetition.xml`
5. `popup_home_timer_configuration.xml`
6. Remaining small popups + `activity_splash.xml` + `add_notification.xml`

Each commit independently buildable and testable.
