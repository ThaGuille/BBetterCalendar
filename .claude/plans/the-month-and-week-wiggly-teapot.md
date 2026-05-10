# Calendar UX polish: month-view redesign + week-view tuning

## Context

The previous plan ([wondrous-gliding-fountain.md](.claude/plans/wondrous-gliding-fountain.md)) shipped the month and week views — both load events and don't crash. But:

- **Month view** is a continuous scroll of small day cells with no header, no month label, no selection, and no way to read events on a given day. The user wants the kizitonwose Example5 layout: `[<] June 2026 [>]` header, weekday legend, single visible month with stacked event indicators, tap-to-select a day, and a day-detail panel below the grid showing that day's events with a quick-add button.
- **Week view** renders correctly but hours are cramped, vertical scrolling feels stuck, and the bottom navigation bar covers the last hours.
- **Toolbar** has 3 icons; only the calendar-toggle is real, the other two (back arrow that calls `activity.finish()`, and a wired-to-nothing stats icon) should be removed.

User decisions captured: month name + arrows live in an **in-fragment header bar** (not the toolbar); day-detail panel is a **list + "Add for this day" quick-add button**; **both swipe and arrows** navigate months.

Reference pattern: kizitonwose `sample/src/main/java/com/kizitonwose/calendar/sample/view/Example5Fragment.kt` (+ `example_5_*.xml`) on https://github.com/kizitonwose/Calendar — closest visual match to the target screenshot.

## Stop-and-ask points

- **R1**: Phase 2 — should the FAB stay on the month view, or is the in-panel "Add for this day" button enough? Default in this plan: keep the FAB but it launches AddEventActivity with **today**, while the panel button uses the **selected day** — two clearly differentiated entry points.
- **R2**: Phase 5 — preselected date applies to both `task_start_date` AND `event_start_date`/`event_end_date` (same day for both end-points on TYPE_EVENT). Confirm this default.
- **R3**: Phase 6 — if `app:hourHeight` doesn't visually take effect after a build, fall back to setting `binding.weekView.setHourHeight(...)` programmatically in `onCreateView` after the view is laid out.

## Phase 1 — Toolbar cleanup

**[app/src/main/res/menu/toolbar.xml](app/src/main/res/menu/toolbar.xml)** — delete the `<item>` blocks for `go_back` and `toolbarButtonShare`. Remove `android:layoutDirection="rtl"` from the root (no longer needed for a single icon). Keep `toolbarButtonSwitchCalendar`.

**[app/src/main/java/com/example/bbettercalendar/helpers/ToolbarHelper.java](app/src/main/java/com/example/bbettercalendar/helpers/ToolbarHelper.java)** — in `onMenuItemSelected`, remove the `case R.id.go_back:` arm so removing the menu item doesn't break compilation. Leave the `toolbarTimer` arm (it belongs to a different menu).

**Verify**: grep `R.id.go_back|R.id.toolbarButtonShare` across `app/src/main` — confirm zero remaining references before deleting.

**Checkpoint**: build; open Calendar → only the calendar-toggle icon shows; the title text "Calendar" still appears (set by Navigation UI from the fragment label, unchanged).

## Phase 2 — Month layouts + DayDetailAdapter

**Modify [cell_month_day.xml](app/src/main/res/layout/cell_month_day.xml)** — root stays `FrameLayout`, change `android:layout_height="48dp"` → `match_parent` (the fragment will set `app:cv_daySize="square"` so each cell becomes 1/7 of the grid width tall). Replace the single `cellEventBar` with three stacked indicator bars `cellEventBar1` (bottom margin 4dp), `cellEventBar2` (9dp), `cellEventBar3` (14dp) — each `View`, height 3dp, gravity bottom, default `visibility="gone"`. Set the root's `android:background="@drawable/bg_calendar_day_selected"` so the binder can toggle selection state via `setSelected(true/false)`.

**Create [bg_calendar_day_selected.xml](app/src/main/res/drawable/bg_calendar_day_selected.xml)** — `<selector>` with two `<item>`s:
- `android:state_selected="true"` → `<shape>` rounded rectangle (radius 6dp), stroke 2dp `@color/calendar_item_event`, transparent fill.
- default → transparent.

**Create [item_day_event.xml](app/src/main/res/layout/item_day_event.xml)** — horizontal `LinearLayout`: 8dp circle `View` (color swatch — set in code via `setBackgroundColor`), TextView `eventTitle` (weight 1), TextView `eventTime` (right-aligned). Used by `DayDetailAdapter`.

**Create [view_calendar_header.xml](app/src/main/res/layout/view_calendar_header.xml)** — horizontal `LinearLayout` with 7 weighted TextViews (`legendDay0`..`legendDay6`), populated by the fragment locale-aware in code.

**Rewrite [fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml)** — root `FrameLayout` (so the FAB still overlays). Inside it, a vertical `LinearLayout` containing:
1. **Header bar** (horizontal LinearLayout): `ImageButton monthPrevButton` (chevron left), `TextView monthTitleText` (weight 1, `gravity="center"`, `textSize="18sp"`, `textStyle="bold"`), `ImageButton monthNextButton` (chevron right). Use existing `ic_arrow_back_24` rotated, or add `ic_chevron_left_24` / `ic_chevron_right_24` drawables.
2. `<include layout="@layout/view_calendar_header" />` — weekday legend.
3. `<com.kizitonwose.calendar.view.CalendarView android:id="@+id/monthCalendarView" ... app:cv_dayViewResource="@layout/cell_month_day" app:cv_daySize="square" app:cv_orientation="horizontal" app:cv_outDateStyle="endOfGrid" app:cv_scrollPaged="true" android:layout_height="wrap_content" />` — height wraps to 6 rows.
4. **Day-detail panel** (vertical LinearLayout, `weight=1` to fill remainder): a row with `TextView dayDetailTitle` ("Events for {date}") on the left and `Button dayDetailAddButton` ("Add for this day") on the right; below it `RecyclerView dayDetailRecycler` (`layout_height="0dp"`, `weight="1"`).
5. The existing FAB `calendarAddEventButton` overlaid bottom-end (unchanged from current XML).

**Create [DayDetailAdapter.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/DayDetailAdapter.java)** — `RecyclerView.Adapter<DayDetailAdapter.VH>`. Holds `List<CalendarItem>`; `submitList(List)` calls `notifyDataSetChanged()` (DiffUtil optional, defer). VH inflates `item_day_event.xml`, binds title via `CalendarItem.getTitle()`, time via `SimpleDateFormat("HH:mm")` on `getStartMillis()`, swatch color via `setBackgroundColor(getColorArgb())`. Click listener (set via setter) fires with the `CalendarItem` for future edit-event navigation (out of scope for now — wire to a no-op or open AddEventActivity in a later phase).

**Checkpoint**: build; layout previews render; app shows header bar + legend + grid (6 rows) + empty day-detail list.

## Phase 3 — MonthDayBinder rewrite

**Rewrite [MonthDayBinder.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java)**:

- New nested interface `OnDayClickListener { void onDayClick(LocalDate date); }`.
- New fields: `LocalDate selectedDate`, `OnDayClickListener listener`. Setters for both.
- `DayContainer` constructor finds `cellEventBar1/2/3` (array `View[3]`) and `dayText`. Holds a `CalendarDay currentDay` field; the root view's `setOnClickListener` fires `listener.onDayClick(currentDay.getDate())` if `currentDay.getPosition() == DayPosition.MonthDate` and listener non-null.
- `bind(container, day)`:
  - `container.currentDay = day`
  - `dayText.setText(String.valueOf(day.getDate().getDayOfMonth()))`
  - `dayText.setAlpha(day.getPosition() == DayPosition.MonthDate ? 1.0f : 0.3f)`
  - For `i in 0..2`: if `items != null && i < items.size() && day.getPosition() == DayPosition.MonthDate` → bar visible + `setBackgroundColor(items.get(i).getColorArgb())`; else `setVisibility(GONE)`.
  - Selection: `container.itemView.setSelected(day.getPosition() == DayPosition.MonthDate && day.getDate().equals(selectedDate))`.

Reuse existing `setItemsByDate(Map)`.

**Checkpoint**: build; days with multiple events show stacked colored bars (after Phase 4 wiring). Tap a day → callback fires.

## Phase 4 — CalendarFragmentMonth rewrite

**Modify [CalendarFragmentMonth.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java)**:

- New fields: `LocalDate selectedDate = LocalDate.now()`, `DayDetailAdapter dayDetailAdapter`, `Map<LocalDate, List<CalendarItem>> currentItemsByDate = Collections.emptyMap()`, `DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())`.
- In `onCreateView`:
  - **Populate weekday legend** (after binding inflation): compute `DayOfWeek firstDow = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek()`; for `i in 0..6`, find `legendDayN` via `binding.viewHeader.legendDayN` (or `binding.getRoot().findViewById(...)` if include doesn't expose), set text to `firstDow.plus(i).getDisplayName(TextStyle.SHORT, Locale.getDefault())`.
  - **Wire arrows**: `binding.monthPrevButton.setOnClickListener(v -> { CalendarMonth m = binding.monthCalendarView.findFirstVisibleMonth(); if (m != null) binding.monthCalendarView.smoothScrollToMonth(m.getYearMonth().minusMonths(1)); });` — same for next.
  - **monthScrollListener** (in `setupCalendar`): after `updateRangeForMonth(...)`, also `binding.monthTitleText.setText(month.getYearMonth().format(monthFormatter))`. Initial: set title to `currentMonth.format(monthFormatter)`.
  - **Day click**: `dayBinder.setOnDayClickListener(date -> { LocalDate old = selectedDate; selectedDate = date; dayBinder.setSelectedDate(date); binding.monthCalendarView.notifyDateChanged(old); binding.monthCalendarView.notifyDateChanged(date); refreshDayDetail(); });`
  - Initial: `dayBinder.setSelectedDate(selectedDate)`.
  - **Day-detail RV**: `binding.dayDetailRecycler.setLayoutManager(new LinearLayoutManager(getContext())); dayDetailAdapter = new DayDetailAdapter(); binding.dayDetailRecycler.setAdapter(dayDetailAdapter);`
  - **Quick-add button**: `binding.dayDetailAddButton.setOnClickListener(v -> launchAddEvent(selectedDate));`
  - **FAB** (`calendarAddEventButton`): unchanged — launches with `LocalDate.now()` (or `null` for no preselect).
- In `observeItems`: cache `currentItemsByDate = groupByDate(items)`, then `dayBinder.setItemsByDate(currentItemsByDate); binding.monthCalendarView.notifyCalendarChanged(); refreshDayDetail();`
- New helper `refreshDayDetail()`:
  - `binding.dayDetailTitle.setText(getString(R.string.events_for, selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))));`
  - `dayDetailAdapter.submitList(currentItemsByDate.getOrDefault(selectedDate, Collections.emptyList()));`
- New helper `launchAddEvent(LocalDate date)`: builds intent with `EXTRA_PRESELECTED_DATE_MILLIS` (Phase 5) when `date != null`.

**Add string `events_for`** to [strings.xml](app/src/main/res/values/strings.xml): `<string name="events_for">Events for %1$s</string>`. Also add `<string name="add_for_this_day">Add for this day</string>` and `<string name="month_view_prev">Previous month</string>` / `<string name="month_view_next">Next month</string>` (contentDescriptions for arrows).

**Reuse**: `CalendarViewModel.setRange()`, `viewModel.getItems()`, existing `groupByDate`, `MonthDayBinder.setItemsByDate`.

**Checkpoint**: build; arrows scroll one month with smooth animation; horizontal swipe also flips months; title updates on both; tapping a day highlights it (selection ring); day-detail list shows that day's events; "Add for this day" launches AddEventActivity pre-filled with that date.

## Phase 5 — AddEventActivity pre-fill

**Modify [AddEventActivity.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java)**:

- Add constant near line ~65: `public static final String EXTRA_PRESELECTED_DATE_MILLIS = "preselected_date_millis";`
- In `onCreate`, **after** `getIntent()` but **before** `initializeComponents(layoutType)` (around line ~131): read `long preMillis = intent.getLongExtra(EXTRA_PRESELECTED_DATE_MILLIS, -1L); if (preMillis > 0L) localCalendar.setTimeInMillis(preMillis);`
- This works because `initializeComponents` reads `localCalendar` to populate `startDateView`/`endDateView` (lines 238/269) — flowing the new value through with no other code changes.
- For TYPE_EVENT: also call `eventBuilder.setEventEndDay(localCalendar)` so the end_date defaults to the same day (per R2).

**Checkpoint**: tap a future day → "Add for this day" → AddEventActivity opens with that date in the start (and end, for events) date field.

## Phase 6 — Week view tuning

**Modify [fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml)**:

- On the `<com.example.bbettercalendar.ui.calendar.weekview.WeekView>` element, add:
  - `app:hourHeight="72dp"` (was implicit ~50dp)
  - `app:minHourHeight="48dp"`
  - `app:maxHourHeight="120dp"` (so pinch-zoom is bounded)
- Add `android:paddingBottom="64dp"` on the root `FrameLayout` (NOT on the WeekView itself — Thellmund draws to canvas and ignores its own padding; padding the parent reserves space).

If after the build the bottom hours are still hidden (Thellmund may extend its draw area to fill the FrameLayout regardless): fall back to wrapping the WeekView in its own `FrameLayout` sized with `android:layout_marginBottom="64dp"`. Or programmatically: `binding.weekView.setHourHeight(...)` after first layout. R3 escalation point.

**Checkpoint**: open week view → hours are noticeably taller; vertical scrolling has runway and feels smooth (24 hours × 72dp = 1728dp content, exceeds typical visible area); the 22:00–23:00 row is no longer hidden behind the bottom nav bar.

## Verification

End-to-end smoke test after Phase 6:

1. **Build**: `./gradlew.bat assembleDebug` succeeds with no errors.
2. **Toolbar**: only the calendar-switch icon visible at the top of the calendar fragment.
3. **Month view**:
   - Top bar shows `[<] {Month YYYY} [>]` centered.
   - Weekday legend row uses the device locale's first day of week.
   - Grid shows exactly 6 rows; out-of-month dates dimmed.
   - Days with events show 1–3 stacked colored bars at the bottom.
   - Tap a day → cell gets the selection border; day-detail panel below shows that day's events as a list with time + title + color swatch.
   - "Add for this day" launches AddEventActivity pre-filled with the selected date.
   - FAB still works (defaults to today).
   - Tap prev/next arrows → smooth scroll one month, title updates.
   - Horizontal swipe also flips months.
4. **Week view**:
   - Hours are taller (~72dp); whole 24h grid fits within ~3 swipes.
   - Vertical scrolling feels smooth, no stuck behavior.
   - 22:00 / 23:00 hour labels and grid lines are fully visible above the bottom nav.
   - Existing events still render as colored blocks at correct times.
5. **Toggle persistence**: tap the calendar-switch icon repeatedly → no crashes, data still rendered after each toggle.
6. **Add an event flow**: create new event via FAB or "Add for this day" → returns → both views show it without restart.

## Critical files

- [app/src/main/res/menu/toolbar.xml](app/src/main/res/menu/toolbar.xml)
- [app/src/main/java/com/example/bbettercalendar/helpers/ToolbarHelper.java](app/src/main/java/com/example/bbettercalendar/helpers/ToolbarHelper.java)
- [app/src/main/res/layout/cell_month_day.xml](app/src/main/res/layout/cell_month_day.xml)
- [app/src/main/res/layout/fragment_calendar_month.xml](app/src/main/res/layout/fragment_calendar_month.xml)
- [app/src/main/res/layout/fragment_calendar_week.xml](app/src/main/res/layout/fragment_calendar_week.xml)
- New: [app/src/main/res/layout/item_day_event.xml](app/src/main/res/layout/item_day_event.xml)
- New: [app/src/main/res/layout/view_calendar_header.xml](app/src/main/res/layout/view_calendar_header.xml)
- New: [app/src/main/res/drawable/bg_calendar_day_selected.xml](app/src/main/res/drawable/bg_calendar_day_selected.xml)
- New: [app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/DayDetailAdapter.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/DayDetailAdapter.java)
- [app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/MonthDayBinder.java)
- [app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java](app/src/main/java/com/example/bbettercalendar/ui/calendar/CalendarFragmentMonth.java)
- [app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java](app/src/main/java/com/example/bbettercalendar/calendarEntries/AddEventActivity.java)
- [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml) — three new strings

## Out of scope

- Persisting `selectedDate` across configuration changes (rotation will reset to today). If needed, lift it into `CalendarViewModel` later.
- Editing/deleting events from the day-detail list (the per-row click hook is in place but wired to a no-op).
- Improving the WeekView day-header text format ("L 5/04" Spanish-letter-plus-month/day) — user didn't flag it.
- The vendored `WeekViewGestureHandler` is left untouched; if scroll feel is still bad after hourHeight bump, that's a follow-up investigation.
