# System — Progress screen (`ui/progress/` + `ui/progress/apppicker/`)

**Last verified:** 2026-09-10 (DB v13) · Code wins on conflict — if you find drift, fix this doc and bump the date.

The Progress tab: a fixed Day/Week/Month + range-stepper header pinned under the toolbar, then
three stacked, scrolling bands below it. A charts carousel (always renders, no permission needed,
selected via a titled `TabLayout` rather than blind swiping), then a project-progress band (also
ungated, local DB only), then a phone-usage band (gated on Usage Access) that lists tracked apps
with their daily limit and enforce toggle. The projects and usage bands are each their own card
surface so the three sections read as distinct blocks. This doc is UI/presentation only — the
usage-limit measure→warn→enforce pipeline it displays and writes to lives in `app-limits.md`, and
the `Project` entity itself lives in `projects.md`.

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
| `ProgressViewModel` | `ui/progress/ProgressViewModel.java` | Owns `TimeRange`, `ChartBundle`, usage band state, app list, project-progress rows — all DB work off `ExecutorService`, published via `postValue` |
| `TimeRange` / `Granularity` | `ui/progress/TimeRange.java`, `Granularity.java` | Immutable `(anchor day, DAY/WEEK/MONTH)`; `stepped(±1)`, `canStepForward(today)`, `label()` — single source of truth driving both bands |
| `ChartBundle` | `ui/progress/ChartBundle.java` | Plain aggregated-series holder, no MPAndroidChart types (keeps the VM chart-library-agnostic) |
| `ChartCarouselAdapter` | `ui/progress/ChartCarouselAdapter.java` | `ViewPager2` adapter, 4 chart pages (concent / fails / by-hour / time-per-project); 3 in DAY mode, which drops by-hour. Page indices are positional and remapped per mode. `pageTitle()` exposes the same label the `chart_dots` `TabLayout` shows per tab |
| `UsageBandState` | `ui/progress/UsageBandState.java` | `LOCKED` / `LOADING` / `EMPTY_NO_APPS` / `READY` — charts band never gates on this |
| `ProjectProgressRow` | `ui/progress/ProjectProgressRow.java` | Plain row data (projectId, name, colorIndex, done/total + `percent()`, minutes-in-range, `ProjectDeadlineState`) — no entity types, mirroring `AppUsageRow` |
| `ProjectProgressAdapter` | `ui/progress/ProjectProgressAdapter.java` | Renders project rows; accent colour via `ProjectListAdapter.accentColorResFor(colorIndex)` so a project looks identical here and in the Projects tab |
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
4. **Same `TimeRange` drives all three bands** — changing the Day/Week/Month stepper re-triggers the chart query, the project-minutes query and the usage-row query from one `applyRange()` call in the ViewModel.
5. **Usage/limit writes flow into `app-limits.md`'s pipeline immediately**: `setDailyLimit`/`setEnforceAtLimit` write `AppRule` off the executor, then re-arm `UsageLimitScheduler` before refreshing the row list — a stale alarm never sees a rule that no longer matches the UI.
6. **The project band merges a live source with a range-scoped one.** `ProgressViewModel` composes a `MediatorLiveData` over `ProjectsRepository.observeAllWithCounts()` (Room `LiveData`, auto-invalidating on `project` *or* `calendarEntry` writes) and a `MutableLiveData<Map<Integer,Integer>>` of minutes-in-range posted from `applyRange()`'s executor pass. Only the merge runs on the main thread — it is pure in-memory composition, the DB reads are already off it. One `FocusEventDAO.getMinutesByProject()` call feeds *both* the band and the chart page, which is why `ProjectMinutes` carries `projectId` as well as the name.
7. **Band membership is not "active projects".** A project shows if it is `STATUS_ACTIVE` **or** has attributed minutes inside the selected range — so a project you finished last month still appears for the week you actually worked on it, without the list growing unbounded as completed projects accumulate. Sorted by minutes-in-range desc, ties by name (same ordering as the chart).

## Contracts
- Reads/Writes: `AppRule`, `ConsentRecord` (owner: `data-model.md#per-entity-readerswriters-contract-table`); `DailyStat`, `FocusEvent`, `Stats` (chart source data) · Shared with: `app-limits.md` (pipeline this screen controls), `data-model.md`

## Invariants & gotchas

- **Only the usage band is permission-locked.** The chart carousel and the project band both render from local DB rows regardless of permission state — the project band deliberately has *no* state enum (unlike `UsageBandState`), because there is no permission it could be locked on; empty simply hides its heading + list.
- **Usage is measured for all apps but only shown for tracked ones** — `AppRule.tracked` is a display filter over data that `UsageStatsRepository` already computed for every package; don't assume an untracked app has no usage data.
- **The over-limit red tint only applies in DAY view** — a week/month's accumulated usage isn't comparable to a daily limit, so `AppUsageRow.showLimitProgress` suppresses the color (but not the limit display) outside single-day granularity.
- **`fragment_progress.xml`'s root is a `LinearLayout`**, not a scroll container: a fixed header (Day/Week/Month + range stepper) sits above a `NestedScrollView` (`layout_height="0dp"` + `layout_weight="1"`) that wraps everything else. The scroll region still uses `wrap_content` lists with `nestedScrollingEnabled=false` — a naive height/weight change here tends to break the carousel or either list's scroll interaction. The project band was added **inline, not via `<include>`**, so it sidesteps `<include android:id>` shadowing a card root id ([[include-id-overrides-root-id]]) — keep it that way, or watch for that trap. `view_usage_locked.xml`'s root card has no background/elevation of its own (spec `progress-screen-visual-polish`) because it now nests inside the `usage_band` card — don't add one back without removing the outer card, or it'll double-box.
- **The Day/Week/Month selector and range stepper are now the fixed header**, pinned above the scrolling bands (spec `progress-screen-visual-polish`, 2026-09-10) — previously they sat at the bottom of the layout, below every band, which needed a scroll to reach and got worse as bands were added. Don't move them back into the scrolling `LinearLayout` without a reason; every band's data depends on this control, so it's meant to always be reachable.
- **Consent is versioned, not one-shot**: `ConsentRecord.disclosureVersion` must be bumped (in `ConsentRecord.java` constants) to re-force the disclosure dialog if either consent copy changes — an existing acceptance at an older version does not count.

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | Phase 1 — charts MVP (carousel, Day/Week/Month stepper) | `.claude/specs/archive/progress-charts-mvp/proposal.md` |
| 2026-06-29 | Phase 2 — usage band, app picker, disclosure/consent | `.claude/specs/archive/progress-phase2-usage/proposal.md` |
| 2026-07-04 | Phase 3 — `AppLimitDialog`, limit/progress row rendering | `.claude/specs/archive/progress-phase3-limits/proposal.md` |
| 2026-07-04 | Phase 4a — enforce toggle (3-state), accessibility disclosure | `.claude/specs/archive/progress-phase4a-blocking/proposal.md` |
| 2026-07-05 | Phase 4b — Play release readiness (no screen-behavior change) | `.claude/specs/archive/progress-phase4b-play-release/proposal.md` |
| 2026-09-01 | Phase 5 — time-per-project chart page (4th, 3rd in DAY) + a third band (project progress) between the carousel and the usage band; new `FocusEventDAO.getMinutesByProject()` + `ProjectMinutes` projection feed both. Rows deep-link to project detail via `action_global_project_detail`. No schema change. | `.claude/specs/archive/project-deadlines-progress/proposal.md` |
| 2026-09-10 | Visual polish — Day/Week/Month + range stepper moved into a fixed header above the scrolling content; chart carousel's dot indicator replaced with a titled, fixed-mode `TabLayout` (tap-to-jump); projects/usage bands wrapped in card surfaces; "Add apps", the block-toggle chip, and the enforce-master toggle restyled as visible buttons. No schema/logic change. | `.claude/specs/archive/progress-screen-visual-polish/proposal.md` |
