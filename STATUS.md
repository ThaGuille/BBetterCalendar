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

**Branch:** `main`  |  **State as of commit:** 821c6d3 (2026-06-29)

### 1. What we touched last (recent commits)
- 821c6d3 2026-06-29 phase 3 proposition
- c364157 2026-06-29 Archive progress-phase2-usage spec (phase 2 QA-passed)
- b7a3e54 2026-06-29 app time tracking functionality 0 to 100 with app selector and permissions
- 58c888e 2026-06-28 new phase proposal: app blocker in progress screen + time tracker
- 0344a59 2026-06-28 Archive progress-charts-mvp spec and update status

### 2. In flight - active `/spec` changes
| Change | Status | Tasks | Proposal |
|---|---|---|---|
| Progress Phase 3 — Per-app daily limits + pre-limit warnings | proposed | 0/23 | [proposal](.claude/specs/changes/progress-phase3-limits/proposal.md) |

### 3. Living capability docs (how the system behaves now)
- [Capability — Home Pomodoro timer](.claude/specs/capabilities/home-pomodoro.md)
- [Capability: Progress Screen](.claude/specs/capabilities/progress-screen.md)

**Archived changes:** `persist-pomodoro-session-state`, `progress-charts-mvp`, `progress-phase2-usage`

### 4. Plans (`.claude/plans`) - undone first
| Plan | Status | File |
|---|---|---|
| Advanced AI Harness Roadmap | in progress | [ai-harness-roadmap.md](.claude/plans/ai-harness-roadmap.md) |
| Phase 0 — Persist Progress history (DailyStat + FocusEvent) | in progress (implemented + builds; runtime verification pending) | [phase-0-progress-history-tables.md](.claude/plans/phase-0-progress-history-tables.md) |
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
