#!/usr/bin/env bash
# SessionStart hook — context injection (Linux/macOS twin of session-context.ps1; keep the text in sync).
# stdout on exit 0 is added to the session context, so keep it short & high-signal.
echo "Reminders: new UI -> bb_* tokens + TextAppearance.BBetter.* (rule #2); DB/disk off the main thread, update LiveData via postValue() (rule #3); build CalendarEntry via EventBuilder.build() (rule #4); @Database version bumps wipe data without a real Migration (rule #6)."
echo "Tools: /check (verify build/lint), /bb-build. Subagents: explorer, planner, code-reviewer, test-writer. Shell here is bash: ./gradlew, adb-ui-test via scripts/bbadb.py."
exit 0
