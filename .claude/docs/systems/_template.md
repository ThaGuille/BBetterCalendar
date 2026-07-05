# System — <name>

**Last verified:** YYYY-MM-DD (DB v<N>) · Code wins on conflict — if you find drift, fix this doc and bump the date.

<1 paragraph: what this system does and why it exists.>

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
<!-- screens/nav destinations, services, receivers, alarms, notification channels -->

## Files
| Class | Path | Role |
|---|---|---|
<!-- one line each, repo-relative paths -->

## Flow — non-obvious hops only
<!-- numbered; ONLY cross-process/async/lifecycle hops a call graph misses -->

## Contracts
- Reads/Writes: <entity> (owner: data-model.md#<anchor>) · Shared with: <sibling docs>

## Invariants & gotchas
<!-- bullets — each must be something an agent would plausibly break without knowing it -->

## History
| Date | Change | Spec |
|---|---|---|
<!-- links into .claude/specs/archive/<slug>/proposal.md -->

---

**Excluded from this doc** — if a `codegraph_explore` call on this system answers it from source, delete it instead of writing it here:
- Method signatures / bodies, code snippets that restate source
- Class-by-class prose beyond the one-line Files table
- UI copy detail

**Exception kept:** manifest wiring (activities, services, receivers, permissions, `<queries>`) — codegraph indexes symbols, not `AndroidManifest.xml`, so that belongs in Surface.

**Hard cap:** 150 lines per doc.
