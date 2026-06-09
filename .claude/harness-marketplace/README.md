# harness-marketplace

A personal [Claude Code](https://claude.com/claude-code) plugin marketplace. It ships one
plugin, **`claude-harness`**, the generic (project-agnostic) extraction of the AI coding
harness developed in BBetterCalendar — so a new app starts at ~80% instead of zero.

## What's inside `claude-harness`

| Layer | Component |
|---|---|
| Workflow | `/spec` skill — proposal → apply → archive under `.claude/specs/` |
| Workflow | `/save-plan` skill — persist a design plan to `.claude/plans/<slug>.md` |
| Capabilities | `explorer`, `planner`, `code-reviewer`, `test-writer` subagents (read-only except test-writer; codegraph-first) |
| Guardrails | SessionStart hook that lists in-flight `/spec` changes |

Project-specific pieces (the Android `bb-build`/`check` skills and the legacy-palette hook)
are intentionally **left out** — each project adds its own on top via its local `.claude/`
and `CLAUDE.md`.

## Install

From the Claude Code prompt:

```
/plugin marketplace add <path-to-this-folder-or-git-url>
/plugin install claude-harness@harness-marketplace
```

For example, locally:

```
/plugin marketplace add C:\Users\guill\Documents\Proyectos\BBetter\BBetterCalendar\.claude\harness-marketplace
/plugin install claude-harness@harness-marketplace
```

To enable it automatically in every project, add to `~/.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "claude-harness@harness-marketplace": true
  }
}
```

## Recommended companion

Install [CodeGraph](https://github.com/colbymchenry/codegraph) (MIT, local) in the target
project so the subagents can use the `codegraph_*` MCP tools. Without it they fall back to
Grep/Read — slower but still functional.

## Relocating to its own repo (for cross-machine reuse)

This marketplace currently lives inside BBetterCalendar for convenience. To reuse it across
machines, copy this `harness-marketplace/` folder into its own git repo and push it, then:

```
/plugin marketplace add <your-github-user>/<repo>
```
