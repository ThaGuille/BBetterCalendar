---
name: spec
description: Run the native spec-driven change lifecycle (propose -> apply -> verify -> archive) under `.claude/specs/`. Use when starting a non-trivial change ("propose a spec for X", "/spec propose ..."), implementing an approved proposal ("/spec apply <slug>"), checking the implementation matches the proposal before closing it ("/spec verify <slug>"), or closing one out ("/spec archive <slug>"). The lightweight, dependency-free alternative to OpenSpec.
---

# spec — native spec-driven change lifecycle

A dependency-free spec loop that lives entirely in `.claude/specs/`. This is the
project's chosen alternative to OpenSpec: same **proposal -> apply -> verify -> archive**
discipline, but no npm/Python tooling and fully portable. It is the evolution of
the `save-plan` habit for changes that actually get *implemented* — keep
[`save-plan`](../save-plan/SKILL.md) for loose exploration that may not ship.

## Directory layout

```
.claude/specs/
  changes/<slug>/      # in-flight change
    proposal.md        # WHY + WHAT (expressed as deltas vs current behavior)
    tasks.md           # implementation checklist
    design.md          # OPTIONAL: deeper technical notes / alternatives considered
  capabilities/        # living "how the system behaves now" docs (grows over time)
  archive/<slug>/      # completed changes (moved here on archive)
```

The SessionStart hook lists folders in `changes/` so every new session knows what
is in flight.

## Lifecycle — four verbs

### 1. `propose`  →  `/spec propose <topic>`

1. Derive a short kebab `<slug>` (descriptive, e.g. `add-reminder-snooze`).
2. Create `.claude/specs/changes/<slug>/proposal.md` from the template below and fill
   in Why / What changes / Impact. Set `Status: proposed`.
3. Ground it in reality first — delegate fact-finding to the **explorer** subagent and,
   for non-trivial design, the **planner** subagent. Fold their summaries into the proposal.
4. Create `tasks.md` with the implementation checklist.
5. **Do not write production code yet.** Present the proposal and wait for approval.

### 2. `apply`  →  `/spec apply <slug>`

1. Read `proposal.md` + `tasks.md`.
2. Implement, checking off tasks as you complete them. Stay inside the proposal's scope —
   if scope grows, update `proposal.md` first, then continue.
3. Honor CLAUDE.md rules (bb_* palette #2, threading/postValue #3, EventBuilder #4, schema #6).
4. Verify with the [`check`](../check/SKILL.md) skill. Set `Status: applied`.

### 3. `verify`  →  `/spec verify <slug>`

Catches scope drift and half-finished work *before* it gets archived and forgotten.
Checks the implementation against the proposal's own artifacts along three axes:

1. **Completeness** — every box in `tasks.md` is checked. If not, stop and list what's left;
   don't proceed to archive.
2. **Correctness** — diff the actual touched files (`git diff` / `git status` against the
   change) against proposal.md's "Impact: Files / packages touched". Flag anything touched
   that isn't listed (undisclosed scope creep) and anything listed that wasn't touched.
3. **Coherence** — delegate to the **code-reviewer** subagent for a read-only pass over the
   diff against CLAUDE.md rules (bb_* palette, threading/postValue, EventBuilder, schema
   migrations) and correctness bugs. Fold any findings back into `tasks.md` as follow-ups
   rather than silently fixing them.

Append a short `## Verify` section to `proposal.md` recording the verdict (pass / issues
found + how resolved). Set `Status: verified`. If `/check` wasn't already run during `apply`,
run it now — verify never substitutes for the build/lint check, it's a layer on top of it.

### 4. `archive`  →  `/spec archive <slug>`

1. Confirm tasks are done and the change has been `verify`'d (or at minimum `/check` passed
   for small changes where a full verify pass is overkill).
2. Move the folder: `changes/<slug>/` → `archive/<slug>/` (`git mv` or `Move-Item`).
3. Set `Status: archived`, bump `Last updated:`.
4. Fold any lasting behavior change into a `capabilities/<area>.md` doc so it becomes the
   new "current behavior" baseline future proposals delta against.

## Templates

`proposal.md`:

```markdown
# <Change title>

**Slug:** <slug>
**Status:** proposed | approved | applied | verified | archived
**Created:** YYYY-MM-DD
**Last updated:** YYYY-MM-DD

## Why
<problem / motivation — 1-3 sentences>

## What changes (deltas vs current behavior)
- ADDED: <new behavior / file / screen>
- CHANGED: <current behavior> -> <new behavior>
- REMOVED: <behavior or code going away>

## Impact
- Files / packages touched:
- DB schema: none | bump (CLAUDE.md rule #6 — destructive without real Migration!)
- UI tokens: bb_* only (rule #2)

## Out of scope
- <explicitly not doing>

## Verify
<filled in by `/spec verify` — verdict + any issues found and how resolved>
```

`tasks.md`:

```markdown
# Tasks — <slug>

- [ ] <step 1>
- [ ] <step 2>
- [ ] Verify: <how> (run /check)
```

## Don't

- Don't start coding before the proposal exists and is approved.
- Don't delete a change folder — `archive` it (status header is the history).
- Don't bump `@Database(version)` inside an apply without a real Migration (rule #6).
- Don't put specs inline into CLAUDE.md — `.claude/specs/` is the home.
- Don't skip `verify` on anything non-trivial just to close it out faster — a tiny one-file
  fix can go straight from `apply` to `archive` via `/check`, but if `code-reviewer` or the
  `ui-tester` subagent would normally be warranted, `verify` is where that happens.

## Cross-refs

- [`save-plan`](../save-plan/SKILL.md) — looser plans that may not ship.
- [`check`](../check/SKILL.md) — build/lint step used in `apply` and `verify`.
- `code-reviewer` subagent — diff-vs-rules pass used in `verify`.
- [`.claude/docs/workflows.md`](../../docs/workflows.md) — change recipes.
- Roadmap: [`.claude/plans/ai-harness-roadmap.md`](../../plans/ai-harness-roadmap.md) (Phase 2).
