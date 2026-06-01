 # Calendar UI polish — month + week views

Status: proposed
Created: 2026-05-18
Last updated: 2026-05-18

## Context

The calendar's month and week screens have working data but rough rendering:
- Month view doesn't mark "today", its FAB floats in the middle of the event list, and the event list shows title + time only.
- Week view renders on a near-black background with no visible month label and a permanent dead band above the bottom nav. Event chips look misaligned with the day columns.
- Both screens still reference legacy palette tokens (`@color/white`, `@color/black`, `@color/gris`, `@color/calendar_item_event`) instead of the `bb_*` semantic palette.

Goal: bring both views in line with the `bb_*` design system and fix the layout / interaction gripes called out from the screenshots.

## Scope (in)

Month view: today indicator, FAB position + palette, scrollable event list with description, palette migration.
Week view: top month/year bar, palette migration (kill black background), grid expanded to bottom-nav, smaller chip text, chip alignment, FAB position + palette.
Plus a small investigation into the "events added are not showing" claim.

## Scope (out)

No data-model changes, no schema bump, no AGP/lib upgrades, no toolbar redesign, no `AddEventActivity` changes.

---

## Changes

### A. Month view

**A1. Highlight today**
- File: [cell_month_day.xml](app/src/main/res/layout/cell_month_day.xml)
- Add a second `View` (or replace the `FrameLayout` background) that draws a subtle filled circle / pill behind `cellDayText` when the day equals `LocalDate.now()`.
- File: [MonthDayBinder.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java)
  - In `bind()` (lines 56-77): compute `boolean isToday = inMonth && day.getDate().equals(LocalDate.now())` and toggle the new today-indicator view.
  - Keep the existing `isSelected` border (drawn by [bg_calendar_day_selected.xml](app/src/main/res/drawable/bg_calendar_day_selected.xml)). Today and selected are independent states.
- New drawable: `app/src/main/res/drawable/bg_calendar_day_today.xml` — filled circle, `bb_surface_subtle` (so the selected-day outline stays readable on top of it). Update the selected drawable's stroke color from `@color/calendar_item_event` to `@color/bb_primary` for consistency with the palette.

**A2. FAB position + palette**
- File: [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml) lines 115-127.
- Change `android:layout_marginBottom="65dp"` → `12dp` so the FAB tucks just above the bottom nav instead of floating mid-panel.
- Change `android:backgroundTint="@color/white"` → `@color/bb_primary`.
- Add `android:tint="@color/bb_on_primary"` (or set `app:tint`) so the `ic_add_24` glyph renders in cream on sage.
- Keep `@drawable/rounded_square`, `elevation="5dp"`, size 56dp.

**A3. Day-detail list shows description, stays scrollable**
- File: [item_day_event.xml](app/src/main/res/layout/item_day_event.xml) — restructure: color swatch on the left, then a vertical `LinearLayout` containing `eventTitle` (top) and a new `eventDescription` TextView (below, smaller, muted, `maxLines=2`, ellipsize=end). Time still on the right.
  - Title: `style="@style/TextAppearance.BBetter.Title"` overridden to 15sp via `android:textSize`, OR just `TextAppearance.BBetter.Body` with `textStyle=bold`.
  - Description: `style="@style/TextAppearance.BBetter.BodyMuted"` with `android:textSize="12sp"`.
  - Time: `TextAppearance.BBetter.Label`.
- File: [DayDetailAdapter.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/DayDetailAdapter.java)
  - Add `description` field binding. Source: [CalendarItem.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItem.java) currently has no description field — add `private final String description` (with getter) and thread it through [CalendarItemMapper.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItemMapper.java) reading `entry.getDescription()` (verify field name on `CalendarEntry`).
  - In `onBindViewHolder` set the new TextView and `setVisibility(GONE)` when description is null/empty.
- File: [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml) lines 104-110 — `RecyclerView` is already scrollable (height=0dp, weight=1). No change needed; verify the row layout doesn't have a fixed height that breaks multi-line.

**A4. Palette pass on month layout**
- [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml):
  - Root `background="@color/white"` → `@color/bb_surface`.
  - `monthTitleText` (line 33-42): use `TextAppearance.BBetter.Title`, drop hardcoded `textColor`/`textSize`/`textStyle`.
  - `dayDetailTitle` (line 84-92): use `TextAppearance.BBetter.Title` with `textSize="14sp"` override, color via style.
  - `dayDetailAddButton` (line 94-101): `textColor="@color/bb_primary"`, textSize via `TextAppearance.BBetter.Chip` or stay 13sp.
- [view_calendar_header.xml](app/src/main/res/layout/view_calendar_header.xml) + `CalendarLegendDay` style in [style.xml:27-35](app/src/main/res/values/style.xml#L27-L35): change `textColor="@color/black"` → `@color/bb_on_surface_muted`.

**A5. "Events not being shown" — investigate, don't pre-fix**
- The screenshots actually show both indigo (EVENT) and sage (TASK) bars on days 18/19, so the symptom may be different than stated. Before changing rendering, capture a precise repro:
  - Verify [CalendarItemMapper.java:42-43](app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItemMapper.java#L42-L43) — items with `startMillis == 0` are dropped silently. Check if any EVENT rows in the DB have `startMillis==0` (e.g. legacy rows where only the JSON `startDayAndHour` was set and the fallback at lines 21-23 also yields 0). Add a Log.w + a fallback to `System.currentTimeMillis()` only as a diagnostic, not a permanent fix.
  - [MonthDayBinder.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java) caps bars at 3 (the cell only has `cellEventBar1/2/3`). If a day has >3 items, the 4th+ silently disappears. If this is the symptom, add a "+N" indicator in a 4th slot.
- Action in this PR: only add a Log.d in the mapper's drop branch to confirm whether any rows are being filtered out. Real fix lands once we know which case it is.

---

### B. Week view

**B1. Kill the black background — apply the palette**
- The actual culprit is *not* the layout's `android:background="@color/white"` (that paints behind the canvas). The black comes from [ViewStateFactory.kt:84-86, 122-124](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/ViewStateFactory.kt#L84-L86) falling back to `context.colorBackground` — which under `ThemeColorGreen` resolves to whatever the active theme exposes (and produces the dark look in the screenshot). Fix by passing explicit XML attributes on the `WeekView`, not by changing the theme.
- File: [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) lines 12-21. Add:
  ```
  app:dayBackgroundColor="@color/bb_surface"
  app:headerBackgroundColor="@color/bb_surface_card"
  app:timeColumnBackgroundColor="@color/bb_surface"
  app:headerTextColor="@color/bb_on_surface"
  app:todayHeaderTextColor="@color/bb_primary"
  app:weekendHeaderTextColor="@color/bb_on_surface_muted"
  app:timeColumnTextColor="@color/bb_on_surface_muted"
  app:hourSeparatorColor="@color/bb_divider"
  app:daySeparatorColor="@color/bb_divider"
  app:showHeaderBottomLine="true"
  app:headerBottomLineColor="@color/bb_divider"
  app:nowLineColor="@color/bb_accent_energy"
  app:eventTextColor="@color/bb_on_primary"
  ```
- Also change the root `android:background="@color/white"` → `@color/bb_surface` (line 8) for consistency while the canvas is laying out.

**B2. Add a month/year top bar (own row)**
- File: [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) — wrap the `WeekView` in a vertical `LinearLayout`. Add a header row above it:
  ```
  <TextView
      android:id="@+id/weekMonthTitleText"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:gravity="center"
      android:paddingTop="6dp"
      android:paddingBottom="6dp"
      style="@style/TextAppearance.BBetter.Title"
      android:background="@color/bb_surface"
      tools:text="mayo 2026" />
  ```
- File: [CalendarFragmentWeek.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentWeek.java) — set its text in `onCreateView()`, and update it from a scroll listener:
  - Use `WeekView`'s `setOnRangeChangedListener { firstVisibleDate, lastVisibleDate -> binding.weekMonthTitleText.setText(firstVisibleDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))) }`. If the visible range straddles two months, show both (e.g. "may – jun 2026").
  - Match the formatter currently used in [CalendarFragmentMonth.java:67-68](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java#L67-L68) so the styling matches.

**B3. Grid fills to the bottom nav**
- File: [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) line 9. Remove `android:paddingBottom="64dp"` on the root. The bottom nav is part of `MainActivity`'s layout, not the fragment — the fragment's bounds already stop at the top of the nav.
- After removing, the FAB's `marginBottom` is now the only thing keeping it off the nav, which is what we want.

**B4. Smaller event-chip text + alignment**
- File: [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) — add `app:eventTextSize="10sp"` (default is `sp(12)`, see [ViewStateFactory.kt:112,284](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/ViewStateFactory.kt#L112)). Also turn on `app:adaptiveEventTextSize="true"` so it shrinks further when chips are narrow (handled by [TextFitter.kt](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/TextFitter.kt)).
- Alignment: chips appear offset because [EventChipBoundsCalculator.kt:21,38](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/EventChipBoundsCalculator.kt#L21-L38) applies `viewState.columnGap` as a left offset (defaults to `dp(8)` per [ViewStateFactory.kt:182](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/ViewStateFactory.kt#L182)) but the day-grid separators don't move. Set `app:columnGap="0dp"` to make chips flush with the column they belong to. Add 2dp horizontal padding inside the chip via `app:eventPaddingHorizontal="2dp"` to compensate.

**B5. FAB position + palette (same as month)**
- File: [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml) lines 23-35.
- `layout_marginBottom="65dp"` → `12dp`.
- `backgroundTint="@color/white"` → `@color/bb_primary`, add `android:tint="@color/bb_on_primary"`.

---

## Files touched

- `app/src/main/res/layout/fragment_calendar_month.xml`
- `app/src/main/res/layout/fragment_calendar_week.xml`
- `app/src/main/res/layout/cell_month_day.xml`
- `app/src/main/res/layout/item_day_event.xml`
- `app/src/main/res/layout/view_calendar_header.xml`
- `app/src/main/res/drawable/bg_calendar_day_selected.xml`
- `app/src/main/res/drawable/bg_calendar_day_today.xml` (new)
- `app/src/main/res/values/style.xml` (`CalendarLegendDay`)
- `app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentWeek.java`
- `app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java`
- `app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/DayDetailAdapter.java`
- `app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItem.java` (add description)
- `app/src/main/java/com/example/bbettercalendar/ui/calendar/domain/CalendarItemMapper.java` (carry description + diag log)

No schema changes. No `AppDatabase` version bump.

## Reuse / patterns

- All new text uses `TextAppearance.BBetter.*` from [styles_typography.xml](app/src/main/res/values/styles_typography.xml).
- All new color refs use `bb_*` from [colors.xml](app/src/main/res/values/colors.xml).
- FAB layout mirrors the month-view pattern; we're only changing margins + tint, not the drawable.
- Day-detail item layout follows the dual-line title + description pattern already used elsewhere (verify against `item_*` layouts before invention).
- WeekView attributes are all existing `R.styleable.WeekView_*` keys read by [ViewStateFactory.kt](app/src/main/java/com/example/bbettercalendar/ui/calendar/weekview/ViewStateFactory.kt) — no library changes.

## Verification

1. Build: `.\gradlew.bat assembleDebug` (use the `bb-build` skill). Fix any lint about deprecated attrs.
2. Lint: `.\gradlew.bat lint` — should not introduce new `HardcodedText` / `NewApi` warnings.
3. Install + run on the emulator:
   - **Month**: today's date shows a sage-tinted disc; tapping a different day still draws the indigo selected outline. The `+` FAB sits ~12dp above the bottom nav, sage with cream icon. Open a day with multiple events — the list scrolls and each row shows title + (smaller, muted) description. Add an entry with no description — that row hides the description line cleanly.
   - **Week**: background is sand (`bb_surface`), not black. The top bar reads e.g. "mayo 2026" and updates when you swipe to the next week. The grid extends fully down to the nav (no dead band). Event chips align with their day column; chip text is ~10sp and still readable. FAB matches the month one.
4. Investigation log (A5): tap "ADD FOR THIS DAY", create an EVENT (toggle to event in `AddEventActivity`), confirm it shows as an indigo bar AND in the day-detail list. If something is missing, check logcat for the diag log added in `CalendarItemMapper`.
5. Rotate / dark-mode sanity check (Theme.BBetter is Light-only — confirm we're not accidentally inheriting a DayNight ancestor).
