# Agent-first system documentation layer (`.claude/docs/systems/`)

Status: implemented
Created: 2026-07-05
Last updated: 2026-07-05

## Context

The app has outgrown its docs. `.claude/docs/architecture.md` still calls `ui.progress`/`ui.projects` stubs and says DB v6/destructive-only, while the code has 95 classes including a full Progress usage dashboard (14 classes), a `blocking/` accessibility package, `usage/` + `usage/limits/`, an expanded `stats/` data hub (AppRule, DailyStat, FocusEvent, ConsentRecord), a `notifications/` infra package, and AppDatabase v10 with real migrations. The only record of the new systems is 4 phases of archived specs — deltas organized by change, not current state, which is a poor primary reference for the agent.

The user asked for a documentation system **designed for the agent to read**: easy routing, token-dense, staleness-resistant. Discovery: `specs/capabilities/` already holds two proto-versions of this (`home-pomodoro.md`, `progress-screen.md`) and `/spec archive` step 4 already points there — so this is a consolidation + promotion, not greenfield.

User decisions (asked & answered): backfill **all 7 docs now**; **slim architecture.md + delete overview.md** (one source of truth per fact).

## Design principles

- **One doc per runtime subsystem**, not per screen — tasks arrive as "fix blocking", which spans screens.
- **Docs carry only what code doesn't say.** The codegraph MCP answers "how does X work" from source in one call. Docs hold: intent, manifest/async wiring the call graph can't follow (alarms→receivers, accessibility events, PendingIntents), invariants & gotchas, cross-system contracts, history rationale. Rule printed in the template: *"If codegraph can answer it from source, delete it."*
- **Routing costs zero extra reads**: compact table in CLAUDE.md (always in context) → open exactly one file per task. No `_index.md`, no session-hook change.
- **Contracts live with their owner**: shared-state contracts (esp. AppRule readers/writers) in `data-model.md` with anchors; each system doc carries a 3–4 line Contracts pointer section.
- **Every read is a repair opportunity**: header says "Code wins on conflict — fix this doc and bump the date."

## Files to create — `.claude/docs/systems/` (8)

| File | Covers | Budget |
|---|---|---|
| `_template.md` | Template + inline EXCLUDED rules | — |
| `data-model.md` | `stats/` (10 cls) + `database/` (3) — entities/DAOs, DB v10, migrations 6→7/7→8/9→10 + destructive fallback, **per-entity readers/writers table (AppRule contract hub)** | ~120 |
| `app-limits.md` | `usage/` (2) + `usage/limits/` (4) + `blocking/` (5) as one measure→warn→enforce pipeline. Key invariant: `UsageLimitChecker` and `BlockDecisionEngine` independently compute "over limit" from the same AppRule + UsageStatsRepository inputs. Midnight latch, TTL cache, WarnedTodayStore dedup | ~140 |
| `progress-screen.md` | `ui/progress` (11) + `apppicker/` (3) — UI only; pipeline lives in app-limits. Charts carousel, usage list, AppLimitDialog, disclosure dialogs | ~120 |
| `pomodoro-timer.md` | `ui/home` (4) + `notifications/focus` — states, session persistence, background-fail grace, foreground service | ~80 |
| `calendar.md` | `ui/calendar` (9) + `calendarEntries` (4) + `notifications/event` (reminders, BootReceiver) | ~110 |
| `notifications.md` | `notifications/` root — channels, BBetterNotifier, NotificationSpec, PermissionGate/Helper, Hilt module | ~60 |
| `startup-config.md` | `configuration/` (7) — SplashActivity day-reset/streak, ConfigurationManager, Hilt modules, legacy InitialConfiguration | ~80 |

Declared exclusions (one line in the index): `ui/projects` (genuine stub), `popups`/`helpers`/`feedback` (owned by `architectural_patterns.md`).

### Template skeleton (`_template.md`)

```markdown
# System — <name>

**Last verified:** YYYY-MM-DD (DB v<N>) · Code wins on conflict — if you find drift, fix this doc and bump the date.

<1 paragraph: what this system does and why it exists.>

## Surface (manifest + entry points)
| Kind | Entry |          ← screens/nav dests, services, receivers, alarms, channels
## Files
| Class | Path | Role |  ← one line each, repo-relative paths
## Flow — non-obvious hops only
<numbered; ONLY cross-process/async/lifecycle hops a call graph misses>
## Contracts
- Reads/Writes: <entity> (owner: data-model.md#<anchor>) · Shared with: <sibling docs>
## Invariants & gotchas
<bullets — each must be something an agent would plausibly break without knowing it>
## History
| Date | Change | Spec |  ← links into .claude/specs/archive/<slug>/proposal.md
```

Excluded (stated in the template): method signatures/bodies, code snippets restating source, class-by-class prose, UI copy detail — anything one `codegraph_explore` call answers. Exception kept: manifest wiring (codegraph indexes symbols, not XML). Hard cap 150 lines/doc.

### Authoring order & method

1. `_template.md`
2. `data-model.md` first (contract hub the others link into — forces getting v10/migrations/readers-writers right once)
3. `app-limits.md`, `progress-screen.md` (least documented today; consume data-model anchors)
4. `pomodoro-timer.md`, `calendar.md`, `notifications.md`, `startup-config.md`

Write from **current code**, using `codegraph_explore` per system; distill (don't transplant) the two `specs/capabilities/` docs — e.g. home-pomodoro's bundle-restore ordering + no-wall-clock-anchor limitation + 4s grace timer become Invariants bullets; progress-screen's phase table becomes History rows split across `progress-screen.md`/`app-limits.md`. Per-doc check while authoring: Glob the owner packages and reconcile the Files table against actual `.java` files; grep `AndroidManifest.xml` for the Surface table.

## Files to modify (7)

1. **`CLAUDE.md`** — add a `### System docs` table (7 rows: System | Doc | Open for, + exclusions footer with the "code wins" sentence, ≤16 lines) under the Knowledge index; update the architecture.md row description; **remove the overview.md row**; fix stale Key-directories rows (`AppDatabase (v6)` → v10, expand `stats/` description, add `usage/`, `blocking/`, `notifications/` rows).
2. **`.claude/docs/architecture.md`** — slim ~333 → ~80 lines: keep stack table, layer mermaid diagram, nav graph; add a one-screen **system topology** (7 systems + AppRule/UsageStatsRepository/Stats coupling arrows) with pointer per system; delete per-package class tables and the 4 flow walkthroughs (content moves, distilled, into owners). Fixes its staleness by deletion.
3. **`.claude/docs/harness.md`** — §6 path table: `.claude/docs/` row mentions `systems/`; `capabilities/` noted as retired → systems.
4. **`.claude/skills/spec/SKILL.md`** — replace archive step 4 with:
   > 4. Fold any lasting behavior change into the owning **system doc** under `.claude/docs/systems/<system>.md` (create from `_template.md` if missing): add a `## History` row linking this archive, update the Files table / Flow / Invariants that changed, and bump `Last verified:`. If the change spans systems, update every owner's doc. Keep it distilled — invariants and contracts, not a change-log narrative.

   Also: Directory-layout comment for `capabilities/` → `# RETIRED — living docs moved to .claude/docs/systems/`; add Don't: *"Don't archive a change without updating the affected `.claude/docs/systems/` doc — it is the baseline the next proposal deltas against."*
5. **`.claude/harness-marketplace/claude-harness/skills/spec/SKILL.md`** — mirror the same spec-skill edits (portable copy must stay in sync).
6. **`.claude/specs/capabilities/home-pomodoro.md`** → 3-line stub pointing at `../../docs/systems/pomodoro-timer.md` (archived proposals link here; keep the path alive).
7. **`.claude/specs/capabilities/progress-screen.md`** → 3-line stub pointing at `../../docs/systems/progress-screen.md` (+ app-limits).

## Files to delete (1)

- `.claude/docs/overview.md` (content = CLAUDE.md ∩ architecture.md; guaranteed drift source).

Deliberately **not** built: `sys-doc` skill (backfill is one-time; ongoing updates are small edits driven by the template + archive step), `_index.md` (extra hop), session-hook changes (CLAUDE.md already routes; hook stays for dynamic spec state). Revisit a hook-based staleness warning only if drift recurs.

## Phasing

1. **Author** — `_template.md`, then the 7 system docs in the order above.
2. **Routing + slimming** (after docs exist so links resolve) — CLAUDE.md edits; slim architecture.md; delete overview.md; harness.md §6.
3. **Maintenance loop** — spec SKILL.md + marketplace mirror; capabilities stubs.

## Verification

- **Coverage audit**: enumerate all `.java` files under `app/src/main/java`; each maps to exactly one system doc's Files table or a declared exclusion (projects stub, popups/helpers/feedback). Orphans = routing hole.
- **Link audit**: every relative link in CLAUDE.md systems table, `systems/*.md`, harness.md, and both spec SKILL.md files resolves to an existing path.
- **Freshness spot-check**: no remaining "v6", "stub" (for progress), or destructive-only-migration claims in CLAUDE.md/architecture.md; `data-model.md` says v10 with migrations 6→7, 7→8, 9→10.
- **Budget check**: each system doc ≤150 lines; CLAUDE.md addition ≤16 lines.
- **Mirror check**: the two spec SKILL.md files match in the edited sections.

Docs-only change — no build/emulator verification needed; `verify-ui-reminder` hook won't fire (no runtime diff).
