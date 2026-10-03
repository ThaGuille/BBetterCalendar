# SessionStart hook (claude-harness plugin) — context injection.
# stdout on exit 0 is appended to the session context, so keep it short & high-signal.
# Surfaces the harness tools available this session.
# Project-specific reminders belong in the host project's own SessionStart hook.

$ErrorActionPreference = 'SilentlyContinue'

Write-Output "Harness: /save-plan skill; subagents explorer, planner, code-reviewer (read-only) and test-writer. Project rules live in this repo's CLAUDE.md."
exit 0
