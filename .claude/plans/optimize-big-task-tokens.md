# Optimise big-task: fewer tokens per landed unit

**Status:** merged
**Created:** 2026-10-30
**Last updated:** 2026-10-31

Previous round (speed): [speed-up-big-task.md](speed-up-big-task.md), merged 2026-10-28 at AI-kit `50d5a7d`, which is the
cut for this round's baseline. Full report: Bench Log entry «Big-task: dónde se van los tokens y cómo bajar un 25–35 % por
fase» (https://claude.ai/artifact/LXLDpStmLZKgFuSoaamE9G).

## Progress (2026-10-31)

All batches approved by the user on 2026-10-31. Built on AI-kit branch `big-task-tokens` (worktree
`AI-kit/.worktrees/big-task-tokens`), commit 4fb686a for P1-P7, P0 separately. Merge to main and reinstall wait until F4
(`social-f4-presencia`) is between phases: its run w2-k3p9 died at the 5-hour usage limit on 2026-10-30 20:04Z mid-wave
(not live, but the phase is open). Merged and pushed to AI-kit main on 2026-10-31 at 00:58Z after F4 accepted its final check (all 9 units landed, no live run); big-task reinstalled. **Cut SHA: db456f4** (commits 4fb686a P1-P7, 24f2736 P0, db456f4 review fixes). The agent types load only in chats started after the merge.

Changes from the plan, after reading the code and the transcripts again:
- P4: 16 of the 25 bind denials were binds chained to read-only commands, the rest unit-brief or reads before the bind (the brief
  said "run unit-brief first" while the bind was step 1). Done as option (b): a valid leading bind registers first and the rest
  of the command is judged as the bound agent's; the brief is chained after the bind.
- P6: bt-run --poll already blocked up to 540 s, and there were no --poll calls in the current era. The waits came from 22
  "busy" answers (another command held the worktree lock) and 8 bt-run calls moved to the background at the Bash tool's default
  120 s. So bt-run now waits for the lock (BT_RUN_LOCK_WAIT, 240 s) and the rules ask for timeout 600000 on every bt-run call.
- P2: the brief carries goal, constraints, invariants, decisions, glossary and consumers (about 25 of F4's 68 kB), so the
  saving is about 65 % of those reads, not 100 %: about -0.9 M per phase.
- P1 spike (2026-10-31): default workflow agent 46.2k tokens on the first call; an agent type without Skill and ToolSearch
  16.7k. The skill listing and the deferred/MCP blocks leave with those tools; StructuredOutput is still appended; CLAUDE.md
  stays. A custom type is only known to sessions started after it exists (the registry loads at session start), so the workflow
  falls back to the default agent when the runtime refuses it. The types ship through the agents/ symlink (no install step).
  The planner keeps the default agent (one per phase, may research).
- P3: done in bt, not in the workflow (a script cannot read transcripts): orch-next abandons a run the usage limit stopped and
  answers wait-quota until the chat records orch-note --action relaunch after the reset. unit-start already reuses the
  worktree with the earlier attempt's uncommitted work.

Branch review AI-kit/big-task-tokens#1 (6 lenses, ~/.local/state/reviews/AI-kit/big-task-tokens/1/review.md): 16 findings,
12 fixed in db456f4, 2 deferred, 2 for the user. What it changed in the work above:
- unit-brief with the plan context inline was 34-40 kB, over the Bash tool's 30k result cap (agents would have seen a 2 kB
  preview). The brief now prints plan {file, deps} and the fields go to units/<id>/plan-context.json, which the agent Reads.
- PowerShell left the unit agent type: the guard does not judge that tool, so it was a way around the confinement on Windows.
  Default workflow agents still have it (pre-existing; raised with the user).
- The version window starts at the first run's start time, not its collect time; a final-fail counts as a finished phase.
- Quota: abandon-run carries stopped (TaskStop, then --stopped); the lookup searches every session; the reset note is
  orch-note --action relaunch --agent usage-limit, which the final-check relaunch rule ignores; a quota death is only the
  <synthetic> rate_limit / "hit your ... limit" ending, the same test in liveness and telemetry.
- Declared departures: the permission broker still reads plan.json whole (it needs approvals[] and sideEffects[]; one agent per
  refused call); bt telemetry-report has no version window yet (export.mjs and the adapters do).
- Not done (F2): the dispatcher chat's own tokens are not in any telemetry stream, so P5 is measured from the chat transcripts
  with the analysis scripts at the close of F5, not from the export.

## Summary

Measured on the current toolkit version (`50d5a7d`: F3 complete, F4 in progress), a big-task phase costs 20.6 M seq in
workflow agents plus 4.3 M in the Opus orchestrator chat (F3), 2.29 M per landed unit, ≈ 3.4k seq per changed code line
(the same as F2: F3 is cheaper per unit only because its units are smaller). The biggest single cost is a fixed ~50k-token
prefix that every workflow agent writes and re-reads on every call: 31 % of all seq. About half of it is never used
(Artifact tool schema, PowerShell, the skill listing, MCP/deferred-tool blocks). Next come quota deaths retried blindly
(2.86 M in one F3 wave), whole-`plan.json` reads (≈ 1.4 M), the orchestrator's 1 h cache rewrites after long gaps, and
polling. The plan cuts those without removing any check, mutant or verifier; the combined target is 25–35 % less seq per
phase, to be confirmed at the close of F5.

## Baseline (version 50d5a7d, n = 1 complete phase)

| Measure | Value |
|---|---|
| Seq per phase (F3) | 20.6 M workflow, 24.9 M with the orchestrator |
| Seq per landed unit | 2.29 M (2.77 M with the orchestrator); range 0.57–5.06 M over 9 units |
| Seq per changed code line | ≈ 3.4k (F2 also ≈ 3.4k) |
| Seq per agent | mean 412k, p50 160k, sd 589k, p90 1.02 M (n 62) |
| Quality F3 | 9/9 distinct units landed; 5/9 first try (7/9 without quota/guard retries); 1 fix round; 2 units with 2 verify rounds |
| Prefix | 31 % of seq; first call 45–54k tokens per agent |

Per role × model (seq per agent): plan opus 2.67 M; impl sonnet 749k; impl opus 697k; verify opus 863k; verify sonnet
177k; replan sonnet 357k; clerk sonnet 75k; clerk haiku 5.5 5k (53k as telemetry prices it).

## Plan

Nothing is applied while F4 has a live run. Approved items go on an AI-kit branch between F4 and F5, pass
`node --test big-task/test/ telemetry/test/`, then `review-branch`, then merge to main without a PR, then
`big-task/install.sh`; the cut SHA is recorded here.

**Batch A (low risk)**

1. **P0 Telemetry by version** (`telemetry/adapters/big-task*`, `telemetry/export.mjs`, `big-task/lib/telemetry.mjs`,
   `bt telemetry-report`):
   - Default window starts at the newest `toolkitSha` that has at least one finished phase, with a flag for the full
     window.
   - Each recommendation carries its evidence split by SHA, and one whose evidence comes mostly from before the window is
     flagged.
   - `toolkitSha` gets `+dirty` when `big-task/` has uncommitted edits.
   - Landed units are counted by distinct unit, not by attempt.
   - Agent failures are split into quota/API (`<synthetic>`), guard denial, timeout, stall and real failure.
   - The orchestrator chat's own tokens are recorded per run.
   - `PRICES` gets `claude-haiku-5-5` at $0.10/$0.50 per MTok.
   - **Expected:** the export shows the current-era KPIs and none of today's 5 pre-cut-only recommendations.
2. **P2 No whole `plan.json` in unit agents** (`bin/bt.mjs unit-brief` and `workflows/big-task.js` around lines 460, 485,
   550, 699):
   - The brief adds the plan's `constraints[]`, plus each dep's title and status.
   - The impl, fix, verify and broker prompts stop asking for `Read plan.json`.
   - **Expected:** 0 plan.json reads per unit agent; −1.0–1.4 M per phase.
3. **P4 Bind without a denial** (`hooks/guard.mjs` around 1129–1205, plus the steps in `workflows/big-task.js`). Either:
   - the guard registers the binding when an agent runs its own unit's `bt unit-start` (same identity checks); or
   - the guard accepts `bind` chained with read-only commands.

   Whichever is chosen, the prompt puts `bind` alone as call 1. **Expected:** "bind to your unit first" denials go from
   25 to 0 per phase; no agent dies after a bind denial.
4. **P6 Poll that waits** (`bin/bt-run.sh --poll`): the poll blocks until the command exits, or for up to ~270 s, so it
   stays under the 5 min cache TTL. **Expected:** about half the poll calls (68 in the current era); −0.5 M per phase.
5. **P7 Wide clerks on Haiku 5.5** (`workflows/big-task.js:166`): the JSON-parse check and the retry stay. **Expected:**
   clerk seq −0.45 M per phase; at most 1 clerk retry per phase.

**Batch B (spike first)**

6. **P1 Slim prefix.**
   - Spike: one toy workflow run comparing the first-call context of the default `workflow-subagent` with a custom
     `agentType` whose `tools:` frontmatter lists only what the role uses. It answers three questions:
     1. Is the skill listing dropped without the Skill tool?
     2. Are the MCP/deferred blocks dropped without ToolSearch?
     3. Is StructuredOutput still appended?
   - Then the agent definitions themselves:
     - `install.sh` installs `~/.claude/agents/big-task-unit.md` (Bash, Read, Edit, Write, Grep, Glob, Monitor, and
       PowerShell on Windows).
     - `big-task-clerk.md` (Bash only).
     - `big-task-publish.md` (Bash, Read, Artifact).
     - `big-task-plan.md` (the unit set plus WebSearch/WebFetch).
     - Each body mirrors the current workflow-subagent system prompt. The `spawn()` calls pass `agentType`.
   - The guard keys on `agent_id`, not on the type, so its rules are unchanged.
   - **Expected:** first call from ~50k down to ~20–34k tokens; −2.5–4.7 M per phase.

**Batch C**

7. **P3 Quota-aware retries** (`workflows/big-task.js`, `bt` liveness/transcript reader):
   - When an agent comes back null and its transcript ends in a `<synthetic>` session-limit message, the run does not
     retry in place: it stops with `stop: 'quota'` and keeps the unit's branch.
   - The next attempt resumes on that branch (WIP commit plus the resume note) instead of starting again on `-r1`.
   - **Expected:** no retry or clerk dies against the same limit; the half-done work is kept.

**User choice (documentation only)**

8. **P5 Orchestrator** (`commands/big-task.md`, `ORCHESTRATOR.md`):
   - (a) Suggest Sonnet for the dispatcher chat during waves; the plan and approval stay on Opus.
   - (b) After more than 1 h idle, start a fresh dispatcher chat (`bt` prints the state) instead of resuming a 130k one.
   - **Expected:** orchestrator seq per phase −1.1–1.6 M. Risk signal: escalations the user has to correct.

**Not now:** verify on Sonnet for Opus units (it weakens verification; first record findings per verifier tier); planner
at effort medium (high risk; a separate experiment measured on plan-review findings, replans and fix rounds); bigger
units (seq per line is the same in F2 and F3); build changes (builds no longer cost tokens).

## Expected effects (check at the close of F5, the first full phase after db456f4)

| Change | Expected | Signal |
|---|---|---|
| P1 slim types | first call 46k -> ~17-22k tokens; -2.5 to -4.7 M seq per phase | first-call context per role in the transcripts |
| P2 brief + plan-context.json | plan.json reads by unit agents 34 -> ~0; ~-0.9 M | Read paths per role |
| P3 quota | no relaunch or retry dies on the same limit | wait-quota answers, <synthetic> seq |
| P4 bind | "bind to your unit first" denials 25 -> ~0 | guard stream |
| P5 dispatcher | -1.1 to -1.6 M where followed | dispatcher transcript (not in telemetry) |
| P6 lock wait + timeout | busy answers 22 -> ~0; backgrounded bt-run 8 -> 0 | bt-run results in transcripts, builds stream |
| P7 haiku wide clerks | -0.45 M; at most 1 clerk retry | clerk retries |
| P0 telemetry | the card shows the current version from 50d5a7d / db456f4 | export window.version |

Quality bars (must not get worse): distinct units landed, first-try landings, fix rounds per unit, review findings confirmed.

## Open questions

- Which batches does the user approve, and does P5a fit how they run the dispatcher?
- Does a custom `agentType` drop the skill listing and the MCP blocks, or only the tool schemas? The spike in step 6
  answers it.
- P3: can the workflow tell a quota death from another null? Otherwise `bt` reads the agent transcript (as liveness
  already does).
- The Linux machine's telemetry is not on this machine; F3 and F4 ran here.

## Verify

At the close of F5 (or the first full phase after the cut), compare against the baseline above, normalised by changed
lines.

**Cost, which must go down:**
- seq per phase, per landed unit, per changed line, and per role × model;
- first-call context per role (P1);
- plan.json reads (P2);
- bind denials (P4);
- poll calls (P6);
- clerk seq and retries (P7);
- `<synthetic>` seq (P3);
- orchestrator seq (P5).

**Quality, which must not get worse:**
- distinct units landed;
- first-try landings;
- fix rounds per unit;
- confirmed findings in the branch review and the final.

Any regression in a quality indicator rejects the change that caused it. Record each expected effect as a skill-improver
known outcome, or as a note here, so the next round checks it.
