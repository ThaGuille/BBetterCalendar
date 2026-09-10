# BBetterCalendar — Where am I?

Re-orientation page for when you come back after time away. **Read top to bottom: the
hand-written "Next focus" tells you what to pick up; the auto-generated tables below show
the live state** (active specs, plans, recent commits). It refreshes itself — see
*How this stays current* at the bottom.

> Big picture: the app is a Pomodoro timer + habit streaks + calendar. The current build-out
> is the **Progress screen** (2nd bottom-nav tab: focus/fail stats + digital-wellbeing), shipped
> in phases. Full roadmap: [docs/progress/06-screen-mapping-and-roadmap.md](docs/progress/06-screen-mapping-and-roadmap.md).
> Phase-by-phase status lives in [.claude/specs/capabilities/progress-screen.md](.claude/specs/capabilities/progress-screen.md).

---

## Next focus (hand-maintained — the one part you edit)

_This is the only section that isn't auto-derived. Update it when you finish a thing or change direction._

**Corrected 2026-08-29 — this section was stale.** It still described Progress Phases 2–4 as
upcoming, but all of them shipped and archived long ago: `progress-phase2-usage` (2026-06-29),
`progress-phase3-limits` + `progress-phase4a-blocking` (2026-07-04), and the Play compliance
close-out `progress-phase4b-play-release`. `docs/progress/06-screen-mapping-and-roadmap.md`'s
"Status" line has the same staleness (still says "Phases 0–1 shipped, next up is Phase 2") — fix
that doc too next time it's touched. The Progress screen roadmap is fully shipped; the real open
thread is the architecture refactor below.

1. **Architecture refactor — tranche T2 (repository layer)**, per
   [architecture-refactor-roadmap.md](.claude/plans/architecture-refactor-roadmap.md): T1 (DI +
   threading) and T4 (reminder generalization) are both done/archived; the plan's own sequencing
   note names **T2 next**. Adds `FocusAttributionRepository` (dedupes `enrichWithAttributedMinutes`,
   currently copy-pasted in `HomeViewModel` + `ProjectDetailViewModel`) and `ProjectsRepository`
   (replaces `ProjectsViewModel`'s manual Observer/recompute with one Room `@Query` join, so the
   `InvalidationTracker` watches both tables and the `refresh()`/`onResume()` staleness workaround
   can be deleted). No schema change, no new UI — pure internal consolidation; verify via `/check`
   + a `ui-tester` pass confirming Home/Calendar/Projects still update correctly after a
   cross-screen insert. Next action: `/spec propose` it (no spec exists yet).
2. **After T2:** propose the Phase 5 spec (`project-deadlines-progress`, project deadlines surfaced
   on Calendar + Projects + a reminder) — T4 is its prerequisite (already done); T3 (schema v13→v14
   hygiene: indexes, legacy-column cleanup, drop `fallbackToDestructiveMigration()`) merges into
   Phase 5's migration if one is needed, per the roadmap's sequencing note.

   _Done: `pomodoro-block-mode` (archived 2026-08-29 — see `app-limits.md`/`pomodoro-timer.md`
   History). `persist-pomodoro-session-state` (committed 9926fa9, archived). Progress Phase 1
   (Charts MVP) — archived to `.claude/specs/archive/progress-charts-mvp/`._

**Decisions — RESOLVED 2026-06-28:** ship to **Google Play with the full blocking system** (compliance
mandatory, sideload/F-Droid fallback) · block style = **full-screen cover + bounce fallback**, triggered
**after a per-app daily limit** · **websites dropped** (apps only). Details:
[docs/progress/](docs/progress/README.md) decisions banner + [07-legal-and-compliance.md](docs/progress/07-legal-and-compliance.md).

---

<!-- AUTO:BEGIN -->
_Tables below are auto-generated from repo state by `.claude/hooks/update-status.ps1`. Do not edit between the AUTO markers._

**Branch:** `main`  |  **State as of commit:** 75f0a87 (2026-09-10)

### 1. What we touched last (recent commits)
- 75f0a87 2026-09-10 improvements in home and progress pages
- 47eef83 2026-08-29 Correct stale Next-focus section in STATUS.md
- 515cd88 2026-08-29 Archive pomodoro-block-mode spec
- 4d6f9a8 2026-08-29 Fix activityWindowCache race after T1 executor consolidation
- 74bc6d7 2026-07-17 Merge T4: reminder generalization

### 2. In flight - active `/spec` changes
_None active._

### 3. Living capability docs (how the system behaves now)
- [Capability — Home Pomodoro timer (moved)](.claude/specs/capabilities/home-pomodoro.md)
- [Capability — Progress screen (moved)](.claude/specs/capabilities/progress-screen.md)

**Archived changes:** `di-threading-consolidation`, `focus-attribution`, `focus-mode-and-streak`, `persist-pomodoro-session-state`, `pomodoro-block-mode`, `progress-charts-mvp`, `progress-phase2-usage`, `progress-phase3-limits`, `progress-phase4a-blocking`, `progress-phase4b-play-release`, `progress-screen-visual-polish`, `project-deadlines-progress`, `projects-mvp`, `recurrence-calendar-visibility`, `reminder-generalization`, `repository-layer-consolidation`, `tasks-home-today`, `tasks-recurrence`

### 4. Plans (`.claude/plans`) - undone first
| Plan | Status | File |
|---|---|---|
| Advanced AI Harness Roadmap | in progress | [ai-harness-roadmap.md](.claude/plans/ai-harness-roadmap.md) |
| Agent-first system documentation layer (`.claude/docs/systems/`) | implemented | [ok-let-s-design-a-encapsulated-adleman.md](.claude/plans/ok-let-s-design-a-encapsulated-adleman.md) |
| Architecture refactor roadmap — data/DI infrastructure consolidation | in progress — T1 (DI + threading consolidation), T4 (reminder generalization), and T2 | [architecture-refactor-roadmap.md](.claude/plans/architecture-refactor-roadmap.md) |
| Phase 0 — Persist Progress history (DailyStat + FocusEvent) | in progress (implemented + builds; runtime verification pending) | [phase-0-progress-history-tables.md](.claude/plans/phase-0-progress-history-tables.md) |
| Redesign `activity_create_event` (and matching task layout) | in progress | [improve_addEvent_layout.md](.claude/plans/improve_addEvent_layout.md) |
| Calendar UI polish — month + week views | proposed | [now-let-s-improve-the-crystalline-sloth.md](.claude/plans/now-let-s-improve-the-crystalline-sloth.md) |
| Plan: Screenshot-free, MCP-free ADB UI-testing system for BBetterCalendar | proposed | [forget-about-the-skills-ticklish-breeze.md](.claude/plans/forget-about-the-skills-ticklish-breeze.md) |
| Calendar refactor: month + week views with vendored libraries | (none) | [wondrous-gliding-fountain.md](.claude/plans/wondrous-gliding-fountain.md) |
| Calendar UX polish: month-view redesign + week-view tuning | (none) | [the-month-and-week-wiggly-teapot.md](.claude/plans/the-month-and-week-wiggly-teapot.md) |
| Home Screen Redesign + App-Wide Design Foundation | (none) | [i-like-everything-but-breezy-lemon.md](.claude/plans/i-like-everything-but-breezy-lemon.md) |
| Layout Migration Plan — Unify on ConstraintLayout + Responsive Dimens | (none) | [greedy-spinning-hickey.md](.claude/plans/greedy-spinning-hickey.md) |
| Projects & Tasks Roadmap | merged (all 5 phases archived: `tasks-home-today`, `tasks-recurrence`, `projects-mvp`, `focus-attribution`, `project-deadlines-progress`) | [projects-tasks-roadmap.md](.claude/plans/projects-tasks-roadmap.md) |
| Redesign Pomodoro TimerPopup | merged | [redesign-timer-popup.md](.claude/plans/redesign-timer-popup.md) |
<!-- AUTO:END -->

---

## How this stays current

- The four numbered tables above are regenerated by **[.claude/hooks/update-status.ps1](.claude/hooks/update-status.ps1)**,
  wired as a **Stop** hook (after Claude finishes a turn) and a **SessionStart** hook (when you open the
  project) in [.claude/settings.local.json](.claude/settings.local.json). No manual step.
- It reads the source-of-truth files — the `Status:` lines in `.claude/specs/` and `.claude/plans/`,
  the `tasks.md` checkboxes, and `git log` — so **the way to move an item is to edit those files**
  (or `/spec apply` / `/spec archive`), not this page. Editing inside the AUTO markers is pointless;
  it gets overwritten.
- The script only rewrites when something actually changed, so it adds no spurious git noise.
- **You only ever hand-edit "Next focus."** Everything else mirrors the repo.
