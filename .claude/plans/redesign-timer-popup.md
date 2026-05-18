# Redesign Pomodoro TimerPopup

**Status:** merged
**Created:** 2026-05-18
**Last updated:** 2026-05-18

## Summary

`TimerPopup` is a first-pass sketch: tight rows of `+ value −` `ImageButton` triplets,
`@color/black` tints, `radio_button_selector` masquerading as a switch, an
`@android:drawable/editbox_background` peeking out under the time, and two plain rounded
buttons at the bottom. The functionality is fine; the look is dated and the steppers are
slow to use (one minute per tap). This plan replaces the layout with a card-based UI on
the existing `bb_*` palette, swaps the +/- buttons for a Material `Slider` on the
concentration/rest times (keeps a circular stepper for the small 1–15 cycles range), and
swaps the radio-style toggles for `SwitchMaterial`. No new images: everything is
shape-drawables on the existing tokens.

## Plan

1. **`res/values/colors.xml`** — add subtle tile backgrounds:
   - `bb_surface_energy_subtle` (`#FFFBE9DF`) — sand washed with coral, for the
     Concentration tile.
   - We can reuse `bb_surface_subtle` (existing `#FFE8EFE8`) for the Rest tile.

2. **`res/drawable/`** — three new drawables:
   - `bg_tile_energy.xml` — `bb_surface_energy_subtle` rectangle, `radius_lg` corners.
   - `bg_tile_primary.xml` — `bb_surface_subtle` rectangle, `radius_lg` corners.
   - `bg_chip_primary.xml` — sage pill chip (mirrors `bg_chip_energy.xml`).
   - `bg_stepper_circle.xml` — circular sage-tinted background for the cycles +/- buttons.
   - `bg_infinite_pill.xml` — outlined pill with selected/checked state for the ∞ toggle.

3. **`res/values/strings.xml`** — add the popup strings (`timer_popup_title`,
   `timer_concentration_label`, `timer_rest_label`, `timer_minutes_unit`,
   `timer_cycles_label`, `timer_auto_cycle_label`, `timer_auto_cycle_hint`,
   `timer_infinite_label`, `timer_btn_restore`, `timer_btn_save`,
   `timer_range_min`, `timer_range_max`, plus error messages).

4. **`res/layout/popup_home_timer_configuration.xml`** — full rewrite:
   - Header row: `ic_timer_clock_24` + `TextAppearance.BBetter.Title` "Pomodoro timer"
     + muted subtitle.
   - Concentration tile (coral wash): chip "CONCENTRATION", Fraunces time in
     `bb_accent_energy`, "minutes" label, Material `Slider` 1..90.
   - Rest tile (sage wash): chip "REST" + `SwitchMaterial`, Fraunces time in
     `bb_primary`, slider 1..(concentration minutes). Tile dims when disabled.
   - Cycles row: title on the left, circular `−` / value / `+` stepper, plus
     "∞ Infinite" pill toggle that disables the stepper.
   - Auto-cycle row: title + helper text + `SwitchMaterial`.
   - Footer: text "Restore defaults" (with `ic_restore_24` icon) on the left,
     filled coral "Save" button on the right.

5. **`TimerPopup.java`** rewrite:
   - Bind to the new IDs: `slider_timer`, `slider_rest`, `switch_rest_enabled`,
     `switch_auto_cycle`, `btn_add_cycles`, `btn_subtract_cycles`, `toggle_infinite_cycles`,
     `text_time_timer`, `text_time_resting`, `text_cycles`, `btn_restore`, `btn_save`,
     plus the rest tile container so we can dim it.
   - Load `isInfiniteCycles` from `configuration.isHomeIsInfiniteCycleEnabled()` (current
     code only derives it from `cyclesNumber == 0`, which loses persisted state).
   - On concentration slider change: update `timerTime`, refresh `text_time_timer`,
     and clamp `slider_rest.setValueTo(...)` so rest can't exceed concentration.
   - On rest slider change: update `restTime`, refresh `text_time_resting`.
   - On rest switch toggle: set `isRestEnabled`, dim the tile and disable the slider.
   - On `+`/`−` cycles buttons: same range check (1–15) but with Material ripple.
   - On infinite toggle: dim cycles stepper, set text to `∞`, set `isInfiniteCycles`.
   - On auto-cycle switch: set `isAutoCycleEnabled` (current code wires the toggle
     but never reads `isAutoCycleEnabled` between init and save — keep the same
     semantics, just on a Switch).
   - `saveValues()` keeps the same dirty check + `OnPopupListener.OnClosePopup(...)`.

6. **`res/values/style.xml`** — the old `TimerRow`, `TimerRowLabel`, `StepperButton`,
   `StepperValue`, `StepperToggle` styles are only referenced from this popup. Leave
   them in place for now (dead code cleanup is out of scope and would also require
   editing `style_guide.md`).

## Open questions

- None blocking. The redesign keeps the same `Configuration` fields and the same
  `OnPopupListener.OnClosePopup(TIMER_POPUP, configuration)` contract, so the call
  sites in the Home fragment don't need touching.

## Verify

- Build: `.\gradlew.bat assembleDebug` finishes clean.
- Open the home screen, tap the timer settings entry-point, confirm:
  - Sliders move freely 1–90 / 1–(concentration).
  - Rest switch dims the rest tile and disables the slider; re-enable restores.
  - +/- cycles button respects the 1–15 range, shows the snackbar otherwise.
  - ∞ toggle replaces the cycles number with `∞`, dims the steppers; toggling off
    restores the previous number.
  - Restore defaults resets to 20:00 / 05:00 / rest on / auto-cycle off / 3 cycles.
  - Save persists and dismisses; reopening the popup shows the saved values.
