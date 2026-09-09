# Tasks — progress-screen-visual-polish

- [x] Restructure `fragment_progress.xml`: pull the Day/Week/Month segmented control + range
      stepper out of the bottom of the `LinearLayout` into a fixed header sitting above a
      `NestedScrollView` (which now wraps chart carousel + projects band + usage band only).
- [x] Add new drawables (bb_* tokens only): outline pill for "Add apps", chip/circle background
      for the block toggle, pill background for the enforce-master toggle.
- [x] `item_app_usage_row.xml`: give `usage_block_toggle` the new chip background.
- [x] `fragment_progress.xml`: restyle `usage_add_apps` (icon + label pill) and
      `usage_enforce_master` (pill toggle); wrap `projects_band` and `usage_band` in card surfaces
      with section headers; add a matching section header above the chart carousel.
- [x] `ChartCarouselAdapter`: expose the existing label-by-position logic as a public method for
      the tab callback (no chart-building logic changes).
- [x] `ProgressFragment`: update the `TabLayoutMediator` callback to set each tab's text from the
      adapter; confirm re-labeling still works when swapping Day <-> Week/Month (page count
      changes).
- [x] `AppUsageAdapter.bindEnforceToggle`: tint the new chip background per state alongside the
      existing icon tint/alpha.
- [x] Run `/check` (build/lint) — assembleDebug + lintDebug clean (fixed `android:drawableTint` /
      `android:drawableStart` -> `app:drawableTint` / `app:drawableStartCompat` per lint's minSdk 21
      warning on the new "Add apps" pill).
- [x] Verify on the emulator via the `ui-tester` subagent (rule #7 — substantial UI change):
      confirm the header stays fixed while scrolling, the tab titles are all visible/tappable and
      jump to the right chart, and the restyled buttons/bands render without clipping in both the
      DAY (3 tabs) and WEEK/MONTH (4 tabs) states. All pass, no crash; 4-tab clipping concern
      checked against a screenshot — labels wrap to 2 lines cleanly, no clipping.
- [x] Update `.claude/docs/systems/progress-screen.md`: resolve the bottom-navigator gotcha, add a
      `## History` row for this change.
- [x] Verify: `code-reviewer` pass (no correctness bugs) + `ui-tester` results folded into
      `proposal.md`'s `## Verify` section. Status: verified.
