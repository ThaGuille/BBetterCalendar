# `.claude/specs/` — spec-driven change lifecycle

Home of the native spec loop (Phase 2 of the AI harness roadmap). Managed via the
[`/spec`](../skills/spec/SKILL.md) skill. Dependency-free alternative to OpenSpec.

```
changes/<slug>/   in-flight change  (proposal.md, tasks.md, optional design.md)
capabilities/     living "how the system behaves now" docs
archive/<slug>/   completed changes
```

Flow: **`/spec propose`** → review/approve → **`/spec apply`** → **`/spec archive`**.

The SessionStart hook (`.claude/hooks/session-context.ps1`) lists active `changes/`
folders at the start of every session, so in-flight work is always visible.
