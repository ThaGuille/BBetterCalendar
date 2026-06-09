# SessionStart hook (claude-harness plugin) — context injection.
# stdout on exit 0 is appended to the session context, so keep it short & high-signal.
# Surfaces in-flight /spec changes plus the harness tools available this session.
# Project-specific reminders belong in the host project's own SessionStart hook.

$ErrorActionPreference = 'SilentlyContinue'

$root = $env:CLAUDE_PROJECT_DIR
if (-not $root) { $root = (Get-Location).Path }
$changesDir = Join-Path $root '.claude\specs\changes'

$active = @()
if (Test-Path $changesDir) {
    $active = Get-ChildItem -Path $changesDir -Directory -ErrorAction SilentlyContinue |
              Select-Object -ExpandProperty Name
}

if ($active.Count -gt 0) {
    $specLine = "Active /spec changes: " + ($active -join ', ') + " (see .claude/specs/changes/<slug>/proposal.md)."
} else {
    $specLine = "Active /spec changes: none."
}

Write-Output "[claude-harness] $specLine"
Write-Output "Harness: /spec (propose->apply->archive) + /save-plan skills; subagents explorer, planner, code-reviewer (read-only) and test-writer. Project rules live in this repo's CLAUDE.md."
exit 0
