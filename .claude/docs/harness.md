# The BBetter AI Harness — what it is and how to use it

**Last updated:** 2026-06-07

This is the single reference for the Claude Code "harness" built into this repo: the skills,
subagents, hooks, code-intelligence index, and the spec loop — plus the reusable plugin that
ports all of it to your next app. If you only read one section, read
[Daily usage](#daily-usage--what-to-type) and the [Cheat sheet](#cheat-sheet).

The design rationale lives in [`.claude/plans/ai-harness-roadmap.md`](../plans/ai-harness-roadmap.md);
this doc is the operating manual.

---

## 1. The idea in one picture

The harness is organised as **6 layers**. Every tool slots into exactly one. Claude (paid) is
the only non-free piece; everything else is free and local.

```
DISTRIBUTION   claude-harness plugin + marketplace   -> reuse across N apps
WORKFLOW       /spec  (propose -> apply -> verify -> archive)   -> .claude/specs/
CAPABILITIES   skills + 4 subagents + (a few MCP servers)
KNOWLEDGE      CLAUDE.md + .claude/docs + CodeGraph index (just-in-time)
GUARDRAILS     hooks + /check verification + file memory
```

**How a turn actually flows:** the main chat session is the **orchestrator**. It pulls knowledge
just-in-time (CodeGraph, docs), delegates verbose work to **subagents** (which return ~1–2k-token
summaries, keeping your context clean), runs changes through the **/spec** loop, and **hooks**
enforce/inject context deterministically around tool calls and session start.

---

## 2. What's installed (the inventory)

### 2a. Skills — type `/<name>`

| Skill | Run it when | Scope |
|---|---|---|
| **`/spec`** | Starting/working a non-trivial change: `propose` → `apply` → `archive` | generic |
| **`/save-plan`** | You produced a plan you want to survive the conversation | generic |
| **`/check`** | You want to verify the current change compiles + lints (on demand) | **Android-specific** |
| **`/bb-build`** | "build / assemble / test / lint / clean" via the right `gradlew.bat` invocation | **Android-specific** |
| `/code-review` | Review the current diff for bugs (low→ultra effort; `ultra` = cloud multi-agent) | built-in |
| `/verify`, `/run` | Drive the app on an emulator and confirm a change works | built-in |

Skills are model-invoked too: if you describe a task that matches a skill's description, Claude
will use it without you typing the slash command.

### 2b. Subagents — isolated context, return a summary

Defined in [`.claude/agents/`](../agents/). The orchestrator **auto-delegates** when your request
matches an agent's `description`; you can also force one: *"use the explorer subagent to…"*.
They run in their own context window and **do not nest**.

| Subagent | Use for | Model | Writes? |
|---|---|---|---|
| **explorer** | "where / how does X work", trace a feature, map an area before editing | sonnet | read-only |
| **planner** | turn a goal into a step plan or `/spec` proposal | opus | read-only |
| **code-reviewer** | review the current local diff vs `CLAUDE.md` rules | sonnet | read-only |
| **test-writer** | add/extend JUnit + Espresso tests, run the test task | sonnet | test sources only |

All four are **CodeGraph-first**: they query the index before grepping/reading, so they answer
in a few calls instead of dozens. The local (BBetter) copies are tuned with the project's rules
(bb_* palette, `postValue`, `EventBuilder`, schema policy); the plugin ships generic versions.

### 2c. Hooks — automatic, you don't invoke these

Configured in [`.claude/settings.local.json`](../settings.local.json), scripts in
[`.claude/hooks/`](../hooks/).

| Event | Script | What it does |
|---|---|---|
| **SessionStart** | `session-context.ps1` | Injects active `/spec` changes + the 4 key rule reminders at the top of every session |
| **PostToolUse** (Edit/Write/MultiEdit) | `check-legacy-palette.ps1` | **Warn-only**: if an edit adds a legacy palette token (`azul`, `verde`, `purple_500`…) to a `.java`/`.xml`, prints a notice. The edit is kept (rule #2). |

There is deliberately **no gradle-on-stop hook** — too slow to run every turn. That role is the
manual `/check` skill instead.

### 2d. Knowledge + code intelligence

- **`CLAUDE.md`** — the rules that override defaults (style tokens, threading, EventBuilder, schema).
- **[`.claude/docs/`](.)** — the knowledge base (architecture, patterns, style, workflows, errors).
- **CodeGraph** — a local SQLite knowledge graph of every symbol/edge/file (MCP tools
  `codegraph_explore/search/callers/callees/impact/...`). ~234 files / 2.5k nodes indexed.
  Consulted just-in-time; **not** a context dump.

---

## 3. Daily usage — what to type

### Starting a non-trivial change (the main loop)

```
/spec propose <one-line description of the change>
```

Claude grounds it in the codebase (often via the **explorer**/**planner** subagents), writes
`.claude/specs/changes/<slug>/proposal.md` + `tasks.md`, and **stops for your approval** —
no production code yet. Then:

```
/spec apply <slug>      # implement, ticking off tasks, honoring CLAUDE.md rules
/check                  # verify it builds/lints (apply calls this for you)
/spec verify <slug>     # completeness/scope/coherence pass (code-reviewer) before closing out
/spec archive <slug>    # move to archive/, fold lasting behavior into capabilities/
```

You'll see the in-flight change echoed at the top of every new session (SessionStart hook).

### Smaller / exploratory work — no command needed

- "How does the Pomodoro timer work?" → Claude auto-delegates to **explorer**; you get a
  `file:line` map, not a wall of code.
- "Plan how to add X" → **planner** returns a step plan you can promote to `/spec`.
- Just exploring, not sure it'll ship → `/save-plan` to park it in `.claude/plans/`.

### Building / testing

```
/bb-build               # or describe it: "build the debug APK", "run the unit tests"
/check                  # compile + lint the current change (cheapest sufficient check)
```

### Before you commit

- "review my changes" → **code-reviewer** subagent (fast, local, read-only), or
- `/code-review` for the heavier pass (`/code-review high`, or `/code-review ultra` for the
  cloud multi-agent review — billed, user-triggered).

### Things that happen automatically (don't type anything)

- Session start → active specs + rule reminders injected.
- Any edit → legacy-palette warn check.
- Any architecture/trace question → CodeGraph is consulted before grep.

---

## 4. The reusable plugin (`claude-harness`)

Phase 4 extracted the **generic** half of this harness into an installable plugin so a new app
starts at ~80% instead of zero. It lives in this repo at
[`.claude/harness-marketplace/`](../harness-marketplace/) (a self-contained Claude Code
*marketplace* containing one *plugin*).

**What the plugin ships** (project-agnostic — defers to the host app's `CLAUDE.md`):
`/spec` + `/save-plan` skills, the four subagents, and the SessionStart context hook.

**What it deliberately omits** (too project-specific): `/bb-build`, `/check`, and the
legacy-palette hook — each new project adds its own equivalents.

### Installing it in another project

From the Claude Code prompt in that project:

```
/plugin marketplace add C:\Users\guill\Documents\Proyectos\BBetter\BBetterCalendar\.claude\harness-marketplace
/plugin install claude-harness@harness-marketplace
```

To enable it for **every** project automatically, add to `~/.claude/settings.json`:

```json
{
  "enabledPlugins": {
    "claude-harness@harness-marketplace": true
  }
}
```

Manage installed plugins with `/plugin` (opens the manager UI),
`/plugin marketplace list`, `/plugin marketplace update harness-marketplace`,
`/plugin uninstall claude-harness@harness-marketplace`.

**Recommended companion:** install [CodeGraph](https://github.com/colbymchenry/codegraph) in the
target project so the subagents get their `codegraph_*` tools (they fall back to Grep/Read if it's
absent — functional, just slower). The plugin's hook paths use `${CLAUDE_PLUGIN_ROOT}`, so it works
wherever it's installed.

### Cross-machine reuse

The marketplace currently lives inside BBetter. To use it on another machine, copy the
`harness-marketplace/` folder into its own git repo, push it, and
`/plugin marketplace add <github-user>/<repo>`.

---

## 5. Cheat sheet

| I want to… | Do this |
|---|---|
| Start a real change | `/spec propose <desc>` → `/spec apply <slug>` → `/spec verify <slug>` → `/spec archive <slug>` |
| See what changes are in flight | look at the SessionStart banner, or `.claude/specs/changes/` |
| Understand how some code works | just ask — **explorer** + CodeGraph answer with `file:line` |
| Plan before coding | ask for a plan (**planner**), then `/save-plan` or `/spec propose` |
| Park an idea that may not ship | `/save-plan` |
| Build / test | `/bb-build` or `/check` |
| Review my diff | ask "review my changes" (**code-reviewer**) or `/code-review [high\|ultra]` |
| Add tests | "add tests for X" (**test-writer**) |
| Verify on the emulator | `/verify` or `/run` |
| Reuse this setup in a new app | `/plugin marketplace add …` + `/plugin install claude-harness@harness-marketplace` |
| Force a specific subagent | "use the **<name>** subagent to …" |

### What you never type (it's automatic)

- SessionStart context injection · legacy-palette warn on edits · CodeGraph lookups.

---

## 6. Where everything lives

| Path | What |
|---|---|
| [`.claude/skills/`](../skills/) | `spec`, `save-plan`, `check`, `bb-build` |
| [`.claude/agents/`](../agents/) | explorer, planner, code-reviewer, test-writer |
| [`.claude/hooks/`](../hooks/) | `session-context.ps1`, `check-legacy-palette.ps1` |
| [`.claude/settings.local.json`](../settings.local.json) | hook wiring + permission allow-list (machine-local) |
| [`.claude/specs/`](../specs/) | the `/spec` loop: `changes/`, `capabilities/`, `archive/` |
| [`.claude/plans/`](../plans/) | saved plans (incl. the harness roadmap) |
| [`.claude/docs/`](.) | knowledge base (this file included) |
| [`.claude/harness-marketplace/`](../harness-marketplace/) | the reusable plugin + marketplace (Phase 4) |
