# Advanced AI Harness Roadmap

**Status:** in progress
**Created:** 2026-06-06
**Last updated:** 2026-06-07

## Summary

A layered, reusable AI coding harness built around Claude Code (the only paid tool).
The goal: replace the "too many systems to keep up with" problem with one opinionated
6-layer model, a short free tool stack, a phased adoption plan, and an explicit
kill-list. The setup is designed to be extracted into a reusable plugin so future apps
start at ~80%. We already have ~60% of this (CLAUDE.md, `.claude/docs`, `.claude/skills`,
`.claude/plans`, file memory); this roadmap fills the gaps.

## Principles (the durable part — tools churn, these don't)

1. Context engineering > prompt engineering — smallest set of high-signal tokens.
2. The harness matters more than the model — initializer, structured state, decomposition, verification, commit discipline.
3. Spec/plan before code — the spec is the durable artifact, code is regenerable.
4. Just-in-time retrieval, not dump-everything — paths/symbols loaded on demand.
5. Decompose with subagents — isolate verbose work, return ~1–2k-token summaries.
6. Deterministic guardrails via hooks — enforce, don't ask.
7. Close the loop with verification — let the agent see its output (tests, emulator).
8. Curate ruthlessly — every MCP server / CLAUDE.md line costs context budget.
9. Reuse via packaging — plugin + personal marketplace; AGENTS.md for portability.

## Reference architecture (6 layers)

```
DISTRIBUTION   plugin + personal marketplace  -> reuse across N apps
WORKFLOW       spec-driven loop  (native /spec skill -> .claude/specs)
CAPABILITIES   skills - subagents - a FEW MCP servers
KNOWLEDGE      CLAUDE.md - .claude/docs - code graph (just-in-time)
GUARDRAILS     hooks - verification loop - file memory
```

Every tool slots into exactly one layer. Triage rule: "which layer is this, and is that
layer already covered?"

## Free stack (only Claude is paid)

| Layer | Pick | Notes |
|---|---|---|
| Instructions | CLAUDE.md + AGENTS.md symlink | Have it; symlink = portability |
| Workflow | Native `/spec` skill | Proposal->apply->archive in `.claude/specs/`; zero deps, portable. Chosen over OpenSpec/Spec Kit/Agent OS for solo + brownfield |
| Code intelligence | CodeGraph (colbymchenry) | MIT, 100% local, Java support, MCP, ~47% fewer tokens |
| Capabilities | Native skills + 3–4 subagents | Add explorer/planner/reviewer/test-writer |
| Docs retrieval | Context7 MCP | On-demand API docs for Hilt/Room/etc. |
| Repo/PR ops | gh CLI (have it) or GitHub MCP | CLI lighter on context |
| Verification | adb + emulator via /verify + /run skills | Screenshot loop |
| Guardrails | Claude Code hooks | gradle lint on stop, legacy-palette blocker |
| Memory | File memory (have it) + claude-progress.md | Cross-session state |
| Distribution | Plugin + git marketplace repo | Install user-scope |

## Plan (phased)

1. **Phase 1 — Code intelligence + portability (≈1 evening) [STARTING HERE]**
   - Install CodeGraph (Windows one-line installer), `codegraph install`, `codegraph init -i`.
   - Verify MCP tools (`codegraph_explore/search/impact`) are exposed to Claude Code.
   - Add `AGENTS.md` (symlink or pointer to CLAUDE.md) for cross-tool portability.
2. **Phase 2 — Spec-driven loop (DONE 2026-06-07)**
   - Built a **native `/spec` skill** instead of adopting OpenSpec: proposal -> apply -> archive
     under `.claude/specs/` (`changes/`, `capabilities/`, `archive/`). Zero dependencies, fully
     portable, evolves the `save-plan` habit. Chosen over OpenSpec / GitHub Spec Kit / Agent OS
     for a solo + brownfield + Claude-only setup (lightest, no npm/Python tooling to maintain).
     Skill: `.claude/skills/spec/SKILL.md`.
3. **Phase 3 — Capabilities + guardrails (DONE 2026-06-07)**
   - Subagents in `.claude/agents/`: **explorer** (read-only, codegraph-first), **planner**
     (opus, read-only, plan/spec proposals), **code-reviewer** (read-only diff review vs CLAUDE.md
     rules), **test-writer** (JUnit/Espresso).
   - Hooks in `.claude/settings.local.json`:
     - PostToolUse **legacy-palette warn** (rule #2) — *warn-only*, non-blocking
       (`.claude/hooks/check-legacy-palette.ps1`).
     - SessionStart **context injection** — lists active spec changes + key reminders
       (`.claude/hooks/session-context.ps1`).
     - *No* gradle-on-stop hook (too slow to run every turn) — replaced by the on-demand
       **`/check`** skill (`.claude/skills/check/SKILL.md`).
   - **Orchestration model:** the main interactive session is the orchestrator; it auto-delegates
     to a subagent when the task matches that agent's `description`. Definitions live in
     `.claude/agents/*.md`; there is no central router file. Subagents run in isolated context
     windows and return summaries; they don't nest.
4. **Phase 4 — Package for reuse (first cut DONE 2026-06-07)**
   - Built a self-contained marketplace + plugin at `.claude/harness-marketplace/`: marketplace
     `harness-marketplace` → plugin **`claude-harness`** (`.claude-plugin/plugin.json`).
   - The plugin ships the **generic** layers only: `/spec` + `/save-plan` skills, the four
     subagents (explorer / planner / code-reviewer / test-writer), and a SessionStart context
     hook — all generalized to defer to the host project's `CLAUDE.md` (no Android / `bb_*`
     hardcoding).
   - Project-specific pieces (`bb-build`, `check`, the `check-legacy-palette` hook) intentionally
     stay in BBetter's local `.claude/`.
   - **Portability caveat resolved:** the plugin hook path uses `${CLAUDE_PLUGIN_ROOT}` instead of
     the absolute Windows paths still used in the machine-local `settings.local.json`.
   - Install: `/plugin marketplace add <path>` → `/plugin install claude-harness@harness-marketplace`;
     auto-enable per user via `enabledPlugins` in `~/.claude/settings.json`.
   - Iterating: relocate the marketplace to its own git repo for cross-machine reuse; optionally add
     a bash variant of the hook for non-Windows projects. Usage guide: `.claude/docs/harness.md`.

## Deliberately ignore (anti-overwhelm kill-list)

- LangGraph / CrewAI / AutoGen for coding (product-building frameworks, not harnesses).
- Embedding/RAG over own code (graph + grep is enough at this scale).
- 15-MCP-server setups (context bloat; cap at ~3).
- Kiro / paid SDD IDEs (leaves Claude Code, adds cost).
- BMAD (heavy persona system; only if multi-developer).
- Building a bespoke harness from scratch (reimplements native features).

## Open questions

- Java/Android: confirm CodeGraph indexes the single-module Gradle project cleanly.
- ~~OpenSpec vs. keeping the lighter `.claude/plans` convention~~ — **RESOLVED 2026-06-07:** built a
  native `/spec` skill (`.claude/specs/`) rather than OpenSpec. Solo + brownfield + portability +
  no extra tooling won. Revisit GitHub Spec Kit / Agent OS only if this goes multi-developer.
- Which MCP servers actually earn their context cost (Context7 first, measure).
- ~~**Phase 4 portability caveat:** the Phase 3 hook commands use *absolute Windows paths* in
  `settings.local.json`~~ — **RESOLVED 2026-06-07:** the `claude-harness` plugin hook uses
  `${CLAUDE_PLUGIN_ROOT}`. BBetter's local `settings.local.json` keeps its absolute paths (it's
  machine-local and not distributed); the portable copy now lives in the plugin.

## Verify

- Phase 1 — **✅ validated 2026-06-07:** `codegraph_status` healthy (234 files / 2514 nodes /
  4420 edges); the explorer subagent answered a Pomodoro-timer trace via `codegraph_explore`
  (codegraph-first, no grep/read loop) and returned a `file:line` summary.
- Phase 2 — **✅ validated 2026-06-07 (propose step):** `/spec propose` scaffolded
  `.claude/specs/changes/persist-pomodoro-session-state/` (proposal.md + tasks.md, Status: proposed).
  apply / archive still to be exercised on a real change.
- Phase 3 — **✅ validated 2026-06-07:** the SessionStart hook lists the active spec change + the
  rule reminders; the four subagents are listed and auto-delegate by description (explorer ran).
  Legacy-palette warn + on-demand `/check` are wired.
- Phase 4 — **✅ built 2026-06-07; install not yet exercised in a second project:** plugin +
  marketplace created at `.claude/harness-marketplace/`, all JSON validated. Next: `/plugin
  marketplace add` it in a fresh app and confirm skills / agents / hook load.

## Sources

- Anthropic — Effective context engineering for AI agents
- Anthropic — Effective harnesses for long-running agents
- OpenSpec (github.com/Fission-AI/OpenSpec), GitHub Spec Kit
- CodeGraph (github.com/colbymchenry/codegraph)
- Martin Fowler — Understanding Spec-Driven Development
