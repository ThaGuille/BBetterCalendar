---
name: spec
description: Run the native spec-driven change lifecycle (propose -> apply -> archive) under `.claude/specs/`. Use when starting a non-trivial change ("propose a spec for X", "/spec propose ..."), implementing an approved proposal ("/spec apply <slug>"), or closing one out ("/spec archive <slug>"). A lightweight, dependency-free alternative to OpenSpec.
---

# spec — native spec-driven change lifecycle

A dependency-free spec loop that lives entirely in `.claude/specs/`. Same
**proposal -> apply -> archive** discipline as OpenSpec, but no npm/Python tooling and
fully portable. Use it for changes that actually get *implemented*; keep `/save-plan`
for loose exploration that may not ship.

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

A SessionStart hook lists folders in `changes/` so every new session knows what is in flight.

## Lifecycle — three verbs

### 1. `propose`  →  `/spec propose <topic>`

1. Derive a short kebab `<slug>` (descriptive, e.g. `add-reminder-snooze`).
2. Create `.claude/specs/changes/<slug>/proposal.md` from the template below and fill in
   Why / What changes / Impact. Set `Status: proposed`.
3. Ground it in reality first — delegate fact-finding to the **explorer** subagent and,
   for non-trivial design, the **planner** subagent. Fold their summaries into the proposal.
4. Create `tasks.md` with the implementation checklist.
5. **Do not write production code yet.** Present the proposal and wait for approval.

### 2. `apply`  →  `/spec apply <slug>`

1. Read `proposal.md` + `tasks.md`.
2. Implement, checking off tasks as you complete them. Stay inside the proposal's scope —
   if scope grows, update `proposal.md` first, then continue.
3. Honor this project's `CLAUDE.md` rules (style/conventions, concurrency, data/schema policy).
4. Verify with the project's build/test command (or a `/check`-style skill if it has one).
   Set `Status: applied`.

### 3. `archive`  →  `/spec archive <slug>`

1. Confirm tasks are done and verification passed.
2. Move the folder: `changes/<slug>/` → `archive/<slug>/` (`git mv` or `Move-Item`).
3. Set `Status: archived`, bump `Last updated:`.
4. Fold any lasting behavior change into a `capabilities/<area>.md` doc so it becomes the
   new "current behavior" baseline future proposals delta against.

## Templates

`proposal.md`:

```markdown
# <Change title>

**Slug:** <slug>
**Status:** proposed | approved | applied | archived
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
- Data / schema / migration impact: none | migration needed
- Conventions: follow CLAUDE.md (style, concurrency, public API)

## Out of scope
- <explicitly not doing>
```

`tasks.md`:

```markdown
# Tasks — <slug>

- [ ] <step 1>
- [ ] <step 2>
- [ ] Verify: <how> (project build/test command)
```

## Don't

- Don't start coding before the proposal exists and is approved.
- Don't delete a change folder — `archive` it (status header is the history).
- Don't put specs inline into `CLAUDE.md` — `.claude/specs/` is the home.

## Cross-refs

- `/save-plan` — looser plans that may not ship.
