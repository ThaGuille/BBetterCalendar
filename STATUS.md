# BBetterCalendar — Where am I?

Re-orientation page for when you come back after time away. Hand-maintained: update it when you
finish a thing or change direction. For recent work use `git log`; for design plans see
[.claude/plans/](.claude/plans/).

> Big picture: the app is a Pomodoro timer + habit streaks + calendar, plus a **Progress screen**
> (focus/fail stats + digital-wellbeing), Projects/tasks and a frontend overhaul in progress.
> Progress roadmap: [docs/progress/06-screen-mapping-and-roadmap.md](docs/progress/06-screen-mapping-and-roadmap.md).
> Per-subsystem behaviour: [.claude/docs/systems/](.claude/docs/systems/).

---

## Next focus

- **Frontend overhaul** — see [frontend-overhaul-roadmap.md](.claude/plans/frontend-overhaul-roadmap.md).
- **Architecture refactor** — see [architecture-refactor-roadmap.md](.claude/plans/architecture-refactor-roadmap.md)
  (T3: schema hygiene — indexes, legacy-column cleanup, drop `fallbackToDestructiveMigration()` —
  is the remaining tranche).

**Decisions — RESOLVED 2026-06-28:** ship to **Google Play with the full blocking system** (compliance
mandatory, sideload/F-Droid fallback) · block style = **full-screen cover + bounce fallback**, triggered
**after a per-app daily limit** · **websites dropped** (apps only). Details:
[docs/progress/](docs/progress/README.md) decisions banner + [07-legal-and-compliance.md](docs/progress/07-legal-and-compliance.md).
