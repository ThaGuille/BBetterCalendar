# claude-harness

Reusable Claude Code harness, extracted generic from BBetterCalendar.

Bundles:

- **Skills** — `/spec` (spec-driven change lifecycle) and `/save-plan` (persist a plan).
- **Subagents** — `explorer`, `planner`, `code-reviewer` (read-only) and `test-writer`
  (writes test sources). All prefer the [CodeGraph](https://github.com/colbymchenry/codegraph)
  index and fall back to Grep/Read if it's absent.
- **Hook** — a SessionStart context injector that lists in-flight `/spec` changes.

These are deliberately **project-agnostic**: they defer to the host project's `CLAUDE.md`
for concrete rules (style tokens, threading, schema policy, build/test commands) rather than
hardcoding any. Add those rules in your project's `CLAUDE.md`, and add project-specific skills
(e.g. a build wrapper) and hooks in your project's local `.claude/`.

See the marketplace README for install instructions.
