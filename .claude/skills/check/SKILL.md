---
name: check
description: Verify the current changes compile and pass lint, on demand. Use when the user says "/check", "verify my changes build", or as the verification step of `/spec apply`. Replaces an auto-run-gradle-on-stop hook (gradle is too slow to run every turn). Runs lint/compile via gradlew.bat and reports.
---

# check — on-demand build/lint verification

This is the **manual** verification guardrail. We deliberately do NOT run gradle on every
turn (the Stop-hook option was rejected as too slow). Run this after a change to confirm
it's clean before committing.

## What to run (cheapest sufficient check first)

1. **Compile / build** (catches the most, fastest signal for Java changes):
   ```powershell
   .\gradlew.bat assembleDebug
   ```
2. **Lint** (run when UI / resources changed — surfaces palette + a11y issues):
   ```powershell
   .\gradlew.bat lintDebug
   ```
3. **Unit tests** (when logic/helpers/ViewModels changed):
   ```powershell
   .\gradlew.bat test
   ```

Use the [`bb-build`](../bb-build/SKILL.md) skill for the correct Windows invocation and
failure recovery (stuck daemon → `.\gradlew.bat --stop` then retry; OneDrive file lock → retry).

## Report

- State **pass/fail** plus the key error lines — don't dump full gradle output.
- If lint flags legacy palette usage, cross-check CLAUDE.md rule #2 (warn-only hook already
  fires on edits, but lint catches what slipped through).

## Cross-refs

- [`bb-build`](../bb-build/SKILL.md) — underlying gradle invocation conventions.
- [`spec`](../spec/SKILL.md) — `/spec apply` calls `/check` as its verify step.
