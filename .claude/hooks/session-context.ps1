# SessionStart hook — context injection.
# stdout on exit 0 is added to the session context, so keep it short & high-signal.
# Surfaces the rules most often forgotten.

$ErrorActionPreference = 'SilentlyContinue'

Write-Output "Reminders: new UI -> bb_* tokens + TextAppearance.BBetter.* (rule #2); DB/disk off the main thread, update LiveData via postValue() (rule #3); build CalendarEntry via EventBuilder.build() (rule #4); @Database version bumps wipe data without a real Migration (rule #6)."
Write-Output "Tools: /check (verify build/lint), /bb-build. Subagents: explorer, planner, code-reviewer, test-writer."
exit 0
