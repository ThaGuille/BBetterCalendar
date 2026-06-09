# SessionStart hook — context injection.
# stdout on exit 0 is added to the session context, so keep it short & high-signal.
# Surfaces in-flight /spec changes plus the rules most often forgotten.

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

Write-Output "[BBetter harness] $specLine"
Write-Output "Reminders: new UI -> bb_* tokens + TextAppearance.BBetter.* (rule #2); DB/disk off the main thread, update LiveData via postValue() (rule #3); build CalendarEntry via EventBuilder.build() (rule #4); @Database version bumps wipe data without a real Migration (rule #6)."
Write-Output "Tools: /spec (propose->apply->archive), /check (verify build/lint), /bb-build. Subagents: explorer, planner, code-reviewer, test-writer."
exit 0
