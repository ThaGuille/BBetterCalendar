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

1. **Progress Phase 2 — phone & app usage** (`PACKAGE_USAGE_STATS` + usage-access disclosure +
   **user-curated app-picker** + per-app list). First phase that needs a permission; Play-safe.
   Next action: `/spec propose` it (no spec exists yet). Captures the picker, the daily-snapshot
   table, the locked-state onboarding, and block toggles stubbed for Phase 4.
2. **Then Phase 3 (per-app daily limits + a notification a few min before)**, then **Phase 4 (soft
   block: full-screen cover, bounce fallback, after the limit)** — Phase 4 carries the Play
   compliance work ([docs/progress/07-legal-and-compliance.md](docs/progress/07-legal-and-compliance.md)).

   _Done: `persist-pomodoro-session-state` (committed 9926fa9, archived). Progress Phase 1 (Charts
   MVP) — on-device QA passed 2026-06-28, archived to `.claude/specs/archive/progress-charts-mvp/`._

**Decisions — RESOLVED 2026-06-28:** ship to **Google Play with the full blocking system** (compliance
mandatory, sideload/F-Droid fallback) · block style = **full-screen cover + bounce fallback**, triggered
**after a per-app daily limit** · **websites dropped** (apps only). Details:
[docs/progress/](docs/progress/README.md) decisions banner + [07-legal-and-compliance.md](docs/progress/07-legal-and-compliance.md).

---

<!-- AUTO:BEGIN -->
_Tables below are auto-generated from repo state by `.claude/hooks/update-status.ps1`. Do not edit between the AUTO markers._

**Branch:** `main`  |  **State as of commit:** b4869e3 (2026-07-06)

### 1. What we touched last (recent commits)
- b4869e3 2026-07-06 roadmap to the next BIG STEPS - PROJECTS + time completeness system + dayly tasks + deadlines
- bde26d6 2026-07-05 documentation system + propse for strict blocking pomodoro
- f92f570 2026-07-05 bugs solved
- 383fcd2 2026-07-05 Fix potential NPE in isNoise() namespace check
- 6b84d9c 2026-07-05 Archive progress-phase4b-play-release spec

### 2. In flight - active `/spec` changes
| Change | Status | Tasks | Proposal |
|---|---|---|---|
| Pomodoro focus block mode ("🚫 Block mode 🚫") | verified | 15/16 | [proposal](.claude/specs/changes/pomodoro-block-mode/proposal.md) |

### 3. Living capability docs (how the system behaves now)
- [Capability — Home Pomodoro timer (moved)](.claude/specs/capabilities/home-pomodoro.md)
- [Capability — Progress screen (moved)](.claude/specs/capabilities/progress-screen.md)

**Archived changes:** `persist-pomodoro-session-state`, `progress-charts-mvp`, `progress-phase2-usage`, `progress-phase3-limits`, `progress-phase4a-blocking`, `progress-phase4b-play-release`, `tasks-home-today`

### 4. Plans (`.claude/plans`) - undone first
| Plan | Status | File |
|---|---|---|
| Advanced AI Harness Roadmap | in progress | [ai-harness-roadmap.md](.claude/plans/ai-harness-roadmap.md) |
| Agent-first system documentation layer (`.claude/docs/systems/`) | implemented | [ok-let-s-design-a-encapsulated-adleman.md](.claude/plans/ok-let-s-design-a-encapsulated-adleman.md) |
| Phase 0 — Persist Progress history (DailyStat + FocusEvent) | in progress (implemented + builds; runtime verification pending) | [phase-0-progress-history-tables.md](.claude/plans/phase-0-progress-history-tables.md) |
| Projects & Tasks Roadmap | in progress (Phase 1 spec `tasks-home-today` proposed) | [projects-tasks-roadmap.md](.claude/plans/projects-tasks-roadmap.md) |
| Redesign `activity_create_event` (and matching task layout) | in progress | [improve_addEvent_layout.md](.claude/plans/improve_addEvent_layout.md) |
| Calendar UI polish — month + week views | proposed | [now-let-s-improve-the-crystalline-sloth.md](.claude/plans/now-let-s-improve-the-crystalline-sloth.md) |
| Plan: Screenshot-free, MCP-free ADB UI-testing system for BBetterCalendar | proposed | [forget-about-the-skills-ticklish-breeze.md](.claude/plans/forget-about-the-skills-ticklish-breeze.md) |
| Calendar refactor: month + week views with vendored libraries | (none) | [wondrous-gliding-fountain.md](.claude/plans/wondrous-gliding-fountain.md) |
| Calendar UX polish: month-view redesign + week-view tuning | (none) | [the-month-and-week-wiggly-teapot.md](.claude/plans/the-month-and-week-wiggly-teapot.md) |
| Home Screen Redesign + App-Wide Design Foundation | (none) | [i-like-everything-but-breezy-lemon.md](.claude/plans/i-like-everything-but-breezy-lemon.md) |
| Layout Migration Plan — Unify on ConstraintLayout + Responsive Dimens | (none) | [greedy-spinning-hickey.md](.claude/plans/greedy-spinning-hickey.md) |
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
