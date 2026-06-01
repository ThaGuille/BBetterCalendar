# Redesign `activity_create_event` (and matching task layout)

Status: in progress
Created: 2026-05-18
Last updated: 2026-05-22

## Context

The "Create Event" / "Create Task" screen looks dated next to the rest of the rebuilt app:

- The coral toolbar (`?attr/colorAccent` = `bb_accent_energy`) is a heavy, dominant block of color that fights with the sage segmented pills and sand background — three saturated colors stacked vertically with no hierarchy.
- The three segmented buttons (`EVENT` / `TASK` / `NOTIFICATION`) are oversized, sit on a flat strip with no container, and the third one is dead UI: it has the id `notificationsButton` but no click handler in `AddEventActivity.java` (lines 530–533 only switch on `switch_to_event_button` / `switch_to_task_button`).
- The form is a flat sequence of identical 70 dp rows separated by 1 dp grey dividers — no grouping, no visual rhythm, no card affordances.
- The Task duration shows two raw `NumberPicker`s with default Android styling — dimmed ghost values (`01 / 05 / 24 / 55`) make the screen read like a 2010-era settings page.

The redesign matches the visual language already established by `popup_home_timer_configuration.xml` and `fragment_home.xml`: calm sand background, content grouped into `bg_card` surfaces with `radius_lg`, sage `bb_primary` for primary action, BBetter typography styles for hierarchy.

**Scope:** layout + drawable changes only. No `AddEventActivity.java` edits — every existing `findViewById` id is preserved so the Java side compiles and behaves identically. The shared `toolbar_close_or_save.xml` is used **only** by `activity_create_event.xml` and `activity_create_task.xml` (confirmed via grep), so we can safely edit it directly.

User decisions locked in:
- Save button stays **top-right** (re-skinned as a sage `bb_primary` pill, no coral bar).
- The third **`notificationsButton` stays visually present but disabled** (placeholder for future TYPE_NOTIFICATION).
- Duration **keeps `NumberPicker`** — we restyle around it (no Java changes).
- Screen title moves from inside the bar to a **`Headline` below the header**.

## Visual direction

```
┌──────────────────────────────────────────────┐
│  (X)                              [ Save  ]  │  toolbar on bb_surface (flat, no coral)
│                                              │
│  New event                                   │  Headline (24sp), Jakarta semibold
│                                              │
│  ┌─ segmented track (bb_surface_subtle) ──┐  │
│  │  [ Event  ]   Task     Reminder        │  │  selected = bb_primary filled
│  └────────────────────────────────────────┘  │  third = visually disabled
│                                              │
│  ┌─ Title card (bg_card) ──────────────────┐ │
│  │  T   Title…                             │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  ┌─ When card (bg_card) ───────────────────┐ │
│  │  📅 Starts          18 May    22:55     │ │
│  │  ─────────────────────────────────────  │ │  1dp bb_divider
│  │  📅 Ends            18 May    23:55     │ │  (task: Duration row + NumberPickers)
│  └─────────────────────────────────────────┘ │
│                                              │
│  ┌─ Options card (bg_card) ────────────────┐ │
│  │  🔁 Repetition                    ⓘ  >  │ │  (task only — toggle + chevron)
│  │  ─────────────────────────────────────  │ │
│  │  🔔 Add notification                 >  │ │
│  │  ─────────────────────────────────────  │ │
│  │  📝 Add description                  >  │ │
│  └─────────────────────────────────────────┘ │
│                                              │
└──────────────────────────────────────────────┘
```

## Files to modify

| File | Change |
|---|---|
| [`app/src/main/res/layout/toolbar_close_or_save.xml`](app/src/main/res/layout/toolbar_close_or_save.xml) | Re-skin: drop coral background, slim 56 dp height bar on `bb_surface`. Close becomes a circular icon button; save becomes a sage pill. Title text hidden (kept as invisible `View` to preserve the `toolbarText` id used by Java). |
| [`app/src/main/res/layout/activity_create_event.xml`](app/src/main/res/layout/activity_create_event.xml) | Full restructure: headline + segmented track + grouped cards. Same ids preserved. |
| [`app/src/main/res/layout/activity_create_task.xml`](app/src/main/res/layout/activity_create_task.xml) | Mirror restructure, with Duration row + NumberPickers grouped into the "When" card, Repetition into the Options card. Same ids preserved. |
| [`app/src/main/res/layout/add_notification.xml`](app/src/main/res/layout/add_notification.xml) | Tighten to fit inside a card row (smaller icon column, 56 dp height instead of 70). Ids kept (`event_notification_text`, `event_notification_close`). |
| [`app/src/main/res/values/style.xml`](app/src/main/res/values/style.xml) | Add **new** styles for the redesigned components (do not break existing `FormRow*` / `SegmentedButton` — other screens still use them). New: `BBetter.CardFormRow`, `BBetter.CardFormRow.Title`, `BBetter.CardFormRow.Value`, `BBetter.SegmentedTrack`, `BBetter.SegmentedTrack.Item`. |
| [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml) | Add: `create_entry_headline_event`, `create_entry_headline_task`, `create_entry_segment_event`, `create_entry_segment_task`, `create_entry_segment_reminder`, `create_entry_btn_save`, `create_entry_starts`, `create_entry_ends`, `create_entry_duration`, `create_entry_repetition`, `create_entry_notification_hint`, `create_entry_description_hint`, `create_entry_title_hint`. |

## New drawables (add to `app/src/main/res/drawable/`)

| File | Purpose | Spec |
|---|---|---|
| `bg_segmented_track.xml` | Container behind the segmented group | `<shape oval=false>` rect, fill `bb_surface_subtle`, corners `radius_lg` (24 dp). |
| `bg_segmented_item_selected.xml` | Selected segment fill | rect, fill `bb_primary`, corners `radius_md` (14 dp). |
| `bg_segmented_item_unselected.xml` | Unselected segment | rect, fill `@android:color/transparent`. |
| `bg_segmented_item.xml` | State-list selector | `state_selected=true` → `bg_segmented_item_selected`; else `bg_segmented_item_unselected`. |
| `bg_segmented_item_disabled.xml` | Disabled third segment | rect, fill `@android:color/transparent`, alpha applied at view level (`android:alpha="0.35"`). Use directly on the third segment so it never enters the selector. |
| `bg_save_pill.xml` | Toolbar save button | rect, fill `bb_primary`, corners `radius_md`. |
| `bg_close_circle.xml` | Toolbar close button | oval, fill `@android:color/transparent`, stroke 1 dp `bb_divider`. Tap target 40 dp, icon 20 dp. |

Existing drawables we reuse without changes: `bg_card`, `bg_stepper_circle` (future duration follow-up), `ic_close_24`, `ic_title_24`, `ic_calendar_empty_24`, `ic_calendar_range_24`, `ic_more_time_clock_24`, `ic_restore_24`, `ic_notifications_empty_24`, `ic_text_snippet_24`, `ic_arrow_forward_24`, `toggle_button_selector`.

## Preserved IDs (Java contract — must not change)

From `AddEventActivity.java`:

**Toolbar:** `toolbar_close_or_save`, `btnClose`, `btnSaveEvent`, `toolbarText`
**Event layout:** `create_event_linear_layout`, `switch_to_event_button`, `switch_to_task_button`, `notificationsButton`, `event_title`, `event_start_date`, `event_start_hour`, `event_end_date`, `event_end_hour`, `event_notification_1`, `event_description`, `event_description_layout`
**Task layout:** `create_task_linear_layout`, `task_title`, `task_start_date`, `task_start_hour`, `task_notification_1`, `task_description`, `task_description_layout`, `number_picker_hours`, `number_picker_minutes`, `task_repetition_layout`, `task_repetition` (ToggleButton), `task_repetition_text`
**Notification injection:** `indexLayout` in Java points to the position where dynamic notification rows are inserted into the main vertical `LinearLayout`. The redesign keeps a single vertical `LinearLayout` as the root content (children = headline, segmented track, cards). We must verify `indexLayout` (currently `10` for EVENT, `12` for TASK at `AddEventActivity.java:478–502`) still points to the slot **before the description card** after the restructure. **Action:** count children after restructure and update the two constants if needed — this is a 1-line Java change each, the minimum needed to keep notification injection working. Allowed by the user's "no functional regressions" intent.

## `toolbar_close_or_save.xml` — new structure

```xml
<androidx.appcompat.widget.Toolbar
    android:id="@+id/toolbar_close_or_save"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:background="@color/bb_surface"
    android:contentInsetStart="0dp"
    android:contentInsetEnd="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="@dimen/spacing_md">

        <ImageButton
            android:id="@+id/btnClose"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="@drawable/bg_close_circle"
            android:scaleType="centerInside"
            android:src="@drawable/ic_close_24"
            app:tint="@color/bb_on_surface" />

        <!-- Invisible spacer; preserves toolbarText id required by Java -->
        <TextView
            android:id="@+id/toolbarText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:visibility="invisible" />

        <androidx.appcompat.widget.AppCompatButton
            android:id="@+id/btnSaveEvent"
            android:layout_width="wrap_content"
            android:layout_height="36dp"
            android:background="@drawable/bg_save_pill"
            android:minWidth="88dp"
            android:paddingHorizontal="@dimen/spacing_md"
            android:text="@string/create_entry_btn_save"
            android:textAllCaps="false"
            android:textAppearance="@style/TextAppearance.BBetter.Label"
            android:textColor="@color/bb_on_primary" />
    </LinearLayout>
</androidx.appcompat.widget.Toolbar>
```

The Java `setText(R.string.event/task)` on `toolbarText` becomes a no-op (the view is invisible); the headline below carries the title instead. No code change needed in `initializeComponents`.

## `activity_create_event.xml` — new structure

Root: `ConstraintLayout` → toolbar pinned top + `ScrollView` filling rest → single vertical `LinearLayout` with `bb_surface` background and `paddingHorizontal=@dimen/spacing_md`.

Children of that vertical `LinearLayout` (in order):

1. **Headline** — `TextView` `@style/TextAppearance.BBetter.Headline`, text `@string/create_entry_headline_event`, top margin `spacing_md`.
2. **Segmented track** — `LinearLayout` (horizontal, `bg_segmented_track`, 4 dp padding, 44 dp height, `spacing_md` top margin):
   - `Button id=switch_to_event_button` `style=BBetter.SegmentedTrack.Item`, `bg_segmented_item`, `selected=true` default, text `@string/create_entry_segment_event`.
   - `Button id=switch_to_task_button` `style=BBetter.SegmentedTrack.Item`, `bg_segmented_item`, text `@string/create_entry_segment_task`.
   - `Button id=notificationsButton` `style=BBetter.SegmentedTrack.Item`, `bg_segmented_item_disabled`, `android:alpha=0.35`, `android:enabled=false`, text `@string/create_entry_segment_reminder`.
3. **Title card** — `LinearLayout id=event_title_layout`, `bg_card`, `radius_lg`, `elevation_card`, padding `spacing_md`, top margin `spacing_md`. Children: 20 dp icon (`ic_title_24`, tint `bb_primary`) + EditText `id=event_title` styled `BBetter.CardFormRow.Title`, `hint=@string/create_entry_title_hint`.
4. **When card** — `bg_card`, top margin `spacing_sm`. Children:
   - Row 1 `id=event_start_date_layout` (`style=BBetter.CardFormRow`): icon (`ic_calendar_empty_24`, `bb_primary`) + `TextView id=event_start_date` (`BBetter.CardFormRow.Value`) + `TextView id=event_start_hour` (`BBetter.CardFormRow.Value`, end-aligned, weight 0).
   - 1 dp `View` divider, fill `bb_divider`, horizontal margins `spacing_md`.
   - Row 2 `id=event_end_date_layout`: icon `ic_calendar_range_24` + `event_end_date` + `event_end_hour`.
5. **Options card** — `bg_card`, top margin `spacing_sm`. Children:
   - Row `id=event_notifications_layout`: icon `ic_notifications_empty_24` + `TextView id=event_notification_1` + `ImageView` arrow `ic_arrow_forward_24`.
   - 1 dp divider.
   - Row `id=event_description_layout`: icon `ic_text_snippet_24` + `TextView id=event_description` + arrow.
6. **Bottom spacer** — 24 dp `View` so scroll content doesn't sit against the nav.

## `activity_create_task.xml` — new structure

Same shell as event, with these row changes:

- **Headline** text → `@string/create_entry_headline_task`.
- **Segmented track** — `switch_to_task_button` is the one with `selected=true` initial state (selector handles visuals; Java already toggles `backgroundTint` for the legacy path, but with the new selector we instead need `setSelected(true/false)` — see Java touch-up note below).
- **When card**:
   - Row 1 `id=task_start_date_layout`: icon + `task_start_date` + `task_start_hour`.
   - Divider.
   - Row 2 `id=task_duration_layout`: icon `ic_more_time_clock_24` + `TextView id=task_duration` (label, `BBetter.CardFormRow.Value`, `text=@string/create_entry_duration`) + a horizontal cluster on the right containing `number_picker_hours` (50 dp), `texts_number_picker_hours` (":"), `number_picker_minutes` (50 dp), `texts_number_picker_minutes` ("h"). Row height grows to 80 dp here to accommodate NumberPicker spinner.
- **Options card**:
   - Row `id=task_repetition_layout`: icon `ic_restore_24` + `TextView id=task_repetition_text` (`text=@string/create_entry_repetition`) + `ToggleButton id=task_repetition` (existing styling preserved) + arrow.
   - Divider.
   - Row `id=task_notifications_layout` with `task_notification_1`.
   - Divider.
   - Row `id=task_description_layout` with `task_description`.

## Style additions to `style.xml`

```xml
<style name="BBetter.SegmentedTrack.Item" parent="">
    <item name="android:layout_width">0dp</item>
    <item name="android:layout_height">match_parent</item>
    <item name="android:layout_weight">1</item>
    <item name="android:textAllCaps">false</item>
    <item name="android:textAppearance">@style/TextAppearance.BBetter.Label</item>
    <item name="android:textColor">@color/bb_on_surface</item>
    <item name="android:minWidth">0dp</item>
    <item name="android:minHeight">0dp</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:elevation">0dp</item>
    <item name="android:stateListAnimator">@null</item>
</style>

<style name="BBetter.CardFormRow" parent="">
    <item name="android:layout_width">match_parent</item>
    <item name="android:layout_height">56dp</item>
    <item name="android:orientation">horizontal</item>
    <item name="android:gravity">center_vertical</item>
    <item name="android:paddingHorizontal">@dimen/spacing_md</item>
    <item name="android:clickable">true</item>
    <item name="android:focusable">true</item>
    <item name="android:background">?attr/selectableItemBackground</item>
</style>

<style name="BBetter.CardFormRow.Title" parent="">
    <item name="android:layout_width">0dp</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:layout_weight">1</item>
    <item name="android:layout_marginStart">@dimen/spacing_md</item>
    <item name="android:textAppearance">@style/TextAppearance.BBetter.Body</item>
    <item name="android:background">@null</item>
</style>

<style name="BBetter.CardFormRow.Value" parent="">
    <item name="android:layout_width">0dp</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:layout_weight">1</item>
    <item name="android:layout_marginStart">@dimen/spacing_md</item>
    <item name="android:textAppearance">@style/TextAppearance.BBetter.Body</item>
    <item name="android:textColorHint">@color/bb_on_surface_muted</item>
</style>
```

## Minimal Java touch-ups (only what's strictly necessary)

1. **`switchEventAndTask(int previousType)`** in `AddEventActivity.java` (around lines 562–569) currently flips `backgroundTint` on the two `Button`s to visualize the active segment. Replace those two lines with `switchToEventButton.setSelected(layoutType == TYPE_EVENT)` and `switchToTaskButton.setSelected(layoutType == TYPE_TASK)`. The new selector drawable does the rest.
2. **`indexLayout`** (Event = 10, Task = 12) — recount after restructure and update to the index of the description row's parent card (or its position within the parent vertical `LinearLayout`). The notifications are injected into the **Options card's** child list now, so the insertion target view (currently the activity's root `LinearLayout`) also needs to change to the Options card. This means changing `createEntryLinearLayout.addView(...)` to `optionsCard.addView(...)` in `addNotificationRow()` (around line 488). To keep the change local: assign a stable id to the Options card LinearLayout (`@+id/options_card`) and resolve it in `initializeComponents` for both Event and Task.

These two adjustments are the minimum needed for the redesign to compile and behave identically to today. They keep the change firmly in "layout polish" territory and avoid touching `EventBuilder`, the date/time popups, or any business logic.

## Verification

1. Build: `.\gradlew.bat assembleDebug` — must compile (no missing ids, no resource errors).
2. Run on emulator/device. Open Home → Calendar → "+" FAB → Create entry sheet appears.
3. **Visual checks**:
   - Top bar is flat sand (`bb_surface`), no coral. Close is a small circle, Save is a sage pill.
   - Headline "New event" reads below the bar in Jakarta semibold.
   - Segmented track is a single rounded sage-tinted container; selected segment fills sage with white text; "Reminder" segment is visibly muted and does not respond to taps.
4. **Functional checks** (regression):
   - Tap Task segment → headline + form swap to Task layout (Java calls `switchLayout(TYPE_TASK)`).
   - Tap Event segment → switches back.
   - Title input accepts text.
   - Tap start date / start hour / end date / end hour → opens date / time picker dialogs.
   - Tap "Add notification" → notifications popup opens; selecting one inserts a new dynamic row inside the Options card with the close `X` working.
   - Tap "Add description" → description popup opens and round-trips text.
   - Task: tap repetition toggle → repetition popup; NumberPickers spin and update duration in `EventBuilder` (verify by saving and re-opening).
   - Save → returns to calendar with the new entry visible.
5. **Persistence check**: rotate device after entering a title — the EditText keeps its value (current behavior; redesign must not break it). The screen does not call `setContentView` on rotation by itself, but `switchLayout` does — make sure the segmented control state survives a switch (existing `eventBuilder.setEventTitle(titleView.getText().toString())` call at line 572 handles this).
6. **Hardcoded text audit**: grep the new layouts to confirm no `EVENT`, `TASK`, `Notification`, `Guardar`, `Add notification`, `Add description`, `Date`, `00:00`, `24:00` literals remain — all should be `@string/...`.
7. **Lint**: `.\gradlew.bat lint` — no new `HardcodedText` or `MissingTranslation` warnings introduced. (Existing baseline issues are not in scope.)
