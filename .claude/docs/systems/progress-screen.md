# System — Progress screen (`ui/progress/` + `ui/progress/apppicker/`)

**Last verified:** 2026-07-05 (DB v10) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Progress tab: a charts carousel (always renders, no permission needed) stacked above a
phone-usage band (gated on Usage Access) that lists tracked apps with their daily limit and
enforce toggle. This doc is UI/presentation only — the usage-limit measure→warn→enforce pipeline
it displays and writes to lives in `app-limits.md`.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Fragment | `ProgressFragment` — bottom-nav destination |
| Activity | `.ui.progress.apppicker.AppPickerActivity` (`exported=false`) — multi-select installed-apps picker |
| Permission (special-access) | `PACKAGE_USAGE_STATS` — see `app-limits.md` for the access-check helper |

## Files
| Class | Path | Role |
|---|---|---|
| `ProgressFragment` | `ui/progress/ProgressFragment.java` | Hosts carousel + usage band; re-checks Usage Access/Accessibility state in `onResume()` (no return callback from Settings) |
| `ProgressViewModel` | `ui/progress/ProgressViewModel.java` | `AndroidViewModel`; owns `TimeRange`, `ChartBundle`, usage band state, app list — all off `ExecutorService`, published via `postValue` |
| `TimeRange` / `Granularity` | `ui/progress/TimeRange.java`, `Granularity.java` | Immutable `(anchor day, DAY/WEEK/MONTH)`; `stepped(±1)`, `canStepForward(today)`, `label()` — single source of truth driving both bands |
| `ChartBundle` | `ui/progress/ChartBundle.java` | Plain aggregated-series holder, no MPAndroidChart types (keeps the VM chart-library-agnostic) |
| `ChartCarouselAdapter` | `ui/progress/ChartCarouselAdapter.java` | `ViewPager2` adapter, 3 chart pages (concent / fails / by-hour) |
| `UsageBandState` | `ui/progress/UsageBandState.java` | `LOCKED` / `LOADING` / `EMPTY_NO_APPS` / `READY` — charts band never gates on this |
| `AppUsageRow` | `ui/progress/AppUsageRow.java` | Plain row data (package, label, foreground ms, limit, enforce flag) — icon resolved by the adapter, not held in the VM |
| `AppUsageAdapter` | `ui/progress/AppUsageAdapter.java` | Renders usage rows incl. the 3-state enforce toggle (off / active / pending-permission) |
| `AppLimitDialog` | `ui/progress/AppLimitDialog.java` | Set/clear a tracked app's daily limit; shares the parent Fragment's `ProgressViewModel` instance |
| `UsageDisclosureDialog` | `ui/progress/UsageDisclosureDialog.java` | Consent dialog shown before the Usage Access Settings deep-link |
| `AppPickerActivity` | `ui/progress/apppicker/AppPickerActivity.java` | Lists launchable installed apps, pre-checks currently tracked, persists selection via `AppRuleDAO` |
| `AppPickerAdapter` / `AppPickItem` | `ui/progress/apppicker/AppPickerAdapter.java`, `AppPickItem.java` | Multi-select list backing the picker |

## Flow — non-obvious hops only

1. **No return callback from Settings.** Both Usage Access and Accessibility grants are checked by re-reading system state in `ProgressFragment.onResume()` — there's no `ActivityResult` callback, so state can only change on the next resume.
2. **Dialogs share the host Fragment's ViewModel**, not a listener interface — `AppLimitDialog` does `new ViewModelProvider(requireParentFragment()).get(ProgressViewModel.class)` to call `setDailyLimit()` directly, avoiding a fragile dialog→fragment listener wire-up across configuration changes.
3. **The enforce toggle's 3 visual states are a permission projection, not a rule field**: `enforceAtLimit=false` → muted; `true` + accessibility service enabled → active (`bb_danger`); `true` + service **not yet** enabled → pending (`bb_accent_reward`, amber) — so the UI never shows "active" without the OS grant actually present.
4. **Same `TimeRange` drives both bands** — changing the Day/Week/Month stepper re-triggers both the chart query and the usage-row query from one `applyRange()` call in the ViewModel.
5. **Usage/limit writes flow into `app-limits.md`'s pipeline immediately**: `setDailyLimit`/`setEnforceAtLimit` write `AppRule` off the executor, then re-arm `UsageLimitScheduler` before refreshing the row list — a stale alarm never sees a rule that no longer matches the UI.

## Contracts
- Reads/Writes: `AppRule`, `ConsentRecord` (owner: `data-model.md#per-entity-readerswriters-contract-table`); `DailyStat`, `FocusEvent`, `Stats` (chart source data) · Shared with: `app-limits.md` (pipeline this screen controls), `data-model.md`

## Invariants & gotchas

- **Charts never gate on Usage Access.** Only the usage band (band 3) is permission-locked; the chart carousel (band 2) always renders from `DailyStat`/`FocusEvent`/`Stats` regardless of permission state.
- **Usage is measured for all apps but only shown for tracked ones** — `AppRule.tracked` is a display filter over data that `UsageStatsRepository` already computed for every package; don't assume an untracked app has no usage data.
- **The over-limit red tint only applies in DAY view** — a week/month's accumulated usage isn't comparable to a daily limit, so `AppUsageRow.showLimitProgress` suppresses the color (but not the limit display) outside single-day granularity.
- **`fragment_progress.xml` is a `NestedScrollView`** with `wrap_content` lists and `nestedScrollingEnabled=false` — a naive height/weight change here tends to break either the carousel or the usage band's scroll interaction. Watch for `<include android:id>` shadowing a card root id ([[include-id-overrides-root-id]]) if you touch this layout.
- **Consent is versioned, not one-shot**: `ConsentRecord.disclosureVersion` must be bumped (in `ConsentRecord.java` constants) to re-force the disclosure dialog if either consent copy changes — an existing acceptance at an older version does not count.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | Phase 1 — charts MVP (carousel, Day/Week/Month stepper) | `.claude/specs/archive/progress-charts-mvp/proposal.md` |
| 2026-06-29 | Phase 2 — usage band, app picker, disclosure/consent | `.claude/specs/archive/progress-phase2-usage/proposal.md` |
| 2026-07-04 | Phase 3 — `AppLimitDialog`, limit/progress row rendering | `.claude/specs/archive/progress-phase3-limits/proposal.md` |
| 2026-07-04 | Phase 4a — enforce toggle (3-state), accessibility disclosure | `.claude/specs/archive/progress-phase4a-blocking/proposal.md` |
| 2026-07-05 | Phase 4b — Play release readiness (no screen-behavior change) | `.claude/specs/archive/progress-phase4b-play-release/proposal.md` |
