# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository. **These rules override defaults — follow them exactly.**

## Project Overview

BBetterCalendar is an Android productivity app: Pomodoro timer + habit streaks + calendar for events / tasks / reminders. Single-module Java project (`com.example.bbettercalendar`).

## Tech Stack (canonical numbers — match `app/build.gradle`)

- **Language:** Java 8 (source/target compat 1.8). No Kotlin sources.
- **Architecture:** MVVM + Hilt DI + Room + LiveData
- **Min / target / compile SDK:** 21 / 34 / 34
- **Build:** AGP 8.13.2, Gradle wrapper 8.13
- **Key libs:** Hilt 2.51.1, Room 2.6.1, Navigation 2.5.3, Material 1.9.0, Gson 2.9.1, Kizitonwose Calendar 2.5.4
- **Pin:** Do not upgrade `com.google.android.material` past `1.9.0` (see `app/build.gradle:47`).

## Environment

- **OS:** Windows 11
- **Shell:** PowerShell 5.1 — no `&&` / `||`. Use `; if ($?) { B }` to chain on success.
- **Path style:** Backslashes in Windows paths (`app\src\main\...`). Forward slashes are accepted by most tools and fine in Markdown/links.
- **Gradle wrapper:** `.\gradlew.bat <task>` from PowerShell. `./gradlew <task>` via the Bash tool also works (allow-listed), but `.\gradlew.bat` is the canonical form.

Full PowerShell / `gradlew.bat` / `adb` reference: [`.claude/docs/windows_commands.md`](.claude/docs/windows_commands.md).

## Build & Test Commands

```powershell
.\gradlew.bat assembleDebug        # build debug APK
.\gradlew.bat assembleRelease      # build release APK
.\gradlew.bat test                 # JVM unit tests
.\gradlew.bat connectedAndroidTest # instrumented (Espresso) tests
.\gradlew.bat lint                 # lint
.\gradlew.bat clean                # clean
```

Use the [`bb-build`](.claude/skills/bb-build/SKILL.md) skill or invoke directly. If a build hangs: `.\gradlew.bat --stop` then retry.

## Project rules

### 1. Plans live in `.claude/plans/`

Any design or implementation plan you produce (an `ExitPlanMode` payload, a written proposal, or a multi-step approach the user wants persisted) is saved as a Markdown file under:

```
.claude/plans/<kebab-slug>.md
```

- Use the [`save-plan`](.claude/skills/save-plan/SKILL.md) skill, or write the file directly following the same template.
- Slug is short and descriptive (`add-reminder-popup.md`), not random words.
- File header has `Status:` (proposed / in progress / merged / abandoned), `Created:`, `Last updated:`.
- Don't delete old plans — update the status line instead.
- Don't write plans inline into CLAUDE.md or PR descriptions; link to the file.
- For changes you intend to **implement**, prefer the [`spec`](.claude/skills/spec/SKILL.md) lifecycle (`.claude/specs/`, proposal → apply → archive) over a loose plan; keep `save-plan` for exploration that may not ship.

### 2. Style tokens are mandatory for new UI

New layouts, drawables, and themes must use the `bb_*` semantic palette and `TextAppearance.BBetter.*` typography. Don't reference the legacy palette (`azul`, `verde`, `purple_500`, etc.) in new code — those exist only so old screens keep rendering. Full rules: [`.claude/docs/style_guide.md`](.claude/docs/style_guide.md).

### 3. Threading

DB / disk work runs on `ExecutorService`. UI updates from background go through `LiveData.postValue(...)`, never `setValue(...)`. Observe with `getViewLifecycleOwner()` inside Fragments. See [`.claude/docs/architectural_patterns.md`](.claude/docs/architectural_patterns.md).

### 4. Entity creation

Build `CalendarEntry` via `CalendarEntry.EventBuilder` and call `.build()`. Direct field assignment skips defaults.

### 5. Don't normalise mixed-language comments

Spanish, Catalan, and English comments coexist on purpose. Don't auto-translate them.

### 6. Schema bumps wipe the DB

`AppDatabase` uses `fallbackToDestructiveMigration()`. Bumping `@Database(version)` wipes user data unless you also add real `Migration` objects. If real migration is needed, follow [`.claude/docs/workflows.md`](.claude/docs/workflows.md) §7.

### 7. Verify big runtime changes on the emulator

After a **substantial** change to UI or runtime behaviour (a plan- or `/spec`-driven
implementation — *not* a small bug fix), verify the app actually runs before declaring it
done. **Don't trust the compiler alone.** Delegate to the [`ui-tester`](.claude/agents/ui-tester.md)
subagent, which drives the running emulator via the [`adb-ui-test`](.claude/skills/adb-ui-test/SKILL.md)
flow (build → install → navigate by resource-id → scan `logcat -b crash` for `FATAL EXCEPTION`)
in an isolated context. The `adb-ui-test` skill runs **inline** and does not spawn the subagent
itself — go through `ui-tester` to keep the XML dumps/logcat out of the main conversation.

This rule is **enforced** by the `Stop` hook `verify-ui-reminder.ps1`: it fires once when the
uncommitted runtime diff (`git diff HEAD` over `app/src/main` java + layout + nav) crosses a size
threshold **and** an emulator is attached, blocking the stop with an instruction to verify. Small
fixes, clean trees, and "no device" stay silent; it re-arms after you commit. Thresholds are
tunable via `BB_UI_VERIFY_LINES` / `BB_UI_VERIFY_FILES`. This hook is the **standing
authorization** to launch the `ui-tester` subagent for this workflow.

## Knowledge index — `.claude/docs/`

Open the file that matches the task before guessing:

| File | When to open it |
|---|---|
| [`architecture.md`](.claude/docs/architecture.md) | Stack table, layer diagram, nav graph, system topology — pointer to the system docs below for per-package detail |
| [`architectural_patterns.md`](.claude/docs/architectural_patterns.md) | Threading, DI wiring, DAO conventions, LiveData, popup pattern, type converters, builder pattern |
| [`style_guide.md`](.claude/docs/style_guide.md) | Palette, typography, dimens, drawables, themes, layout patterns |
| [`workflows.md`](.claude/docs/workflows.md) | Recipes: add popup / entity / screen / colour / schema migration / vendoring |
| [`common_errors.md`](.claude/docs/common_errors.md) | Symptom → root cause → fix tables (build, Room, Hilt, popups, calendar, timer) |
| [`windows_commands.md`](.claude/docs/windows_commands.md) | PowerShell, `gradlew.bat`, `adb`, AVD reference |
| [`android_studio.md`](.claude/docs/android_studio.md) | IDE-specific tips: sync, run, logcat, database inspector, AVD |
| [`harness.md`](.claude/docs/harness.md) | **Operating manual** for the AI harness: skills, subagents, hooks, the `/spec` loop, and the reusable `claude-harness` plugin — what to type, what's automatic |

### System docs — `.claude/docs/systems/`

Per-runtime-subsystem living docs (intent, async/manifest wiring, invariants, contracts) — open the one matching the task, not `architecture.md`, for implementation detail:

| System | Doc | Open for |
|---|---|---|
| Data model | [`data-model.md`](.claude/docs/systems/data-model.md) | Room entities/DAOs, DB version + migrations, AppRule contract table |
| App usage limits | [`app-limits.md`](.claude/docs/systems/app-limits.md) | Usage measurement, warn alarms, accessibility-service blocking |
| Progress screen | [`progress-screen.md`](.claude/docs/systems/progress-screen.md) | Charts + usage-band UI (pipeline lives in `app-limits.md`) |
| Pomodoro timer | [`pomodoro-timer.md`](.claude/docs/systems/pomodoro-timer.md) | Timer state machine, session persistence, background-fail grace |
| Calendar | [`calendar.md`](.claude/docs/systems/calendar.md) | Month/week views, `CalendarEntry`, event reminders |
| Notifications | [`notifications.md`](.claude/docs/systems/notifications.md) | Channels, `BBetterNotifier`, permission gate |
| Startup & config | [`startup-config.md`](.claude/docs/systems/startup-config.md) | `SplashActivity` boot sequence, `ConfigurationManager` |
| Projects | [`projects.md`](.claude/docs/systems/projects.md) | `Project` entity, `projectId` on `CalendarEntry`, Projects tab + detail screen |

Excluded (owned elsewhere): `popups`/`helpers`/`feedback` (see `architectural_patterns.md`). **Code wins on conflict** — if a doc drifts, fix it and bump its `Last verified:` date.

## Skills — `.claude/skills/`

Project-level skills (invokable with `/bb-build`, `/save-plan`, `/spec`, `/check`):

| Skill | Purpose |
|---|---|
| [`bb-build`](.claude/skills/bb-build/SKILL.md) | Run gradle on Windows correctly. Handles common build failures. |
| [`save-plan`](.claude/skills/save-plan/SKILL.md) | Persist a plan to `.claude/plans/<slug>.md` with the right header. |
| [`spec`](.claude/skills/spec/SKILL.md) | Spec-driven change lifecycle: propose → apply → archive under `.claude/specs/`. |
| [`check`](.claude/skills/check/SKILL.md) | On-demand build/lint/test verification (manual — there is no gradle-on-stop hook). |

## Subagents — `.claude/agents/`

The main session is the orchestrator: it auto-delegates to a subagent when the task matches that
agent's `description` (the descriptions **are** the routing logic — there is no central router).
Each runs in an isolated context window and returns a summary; they don't nest. Invoke explicitly
with e.g. "use the code-reviewer subagent".

| Agent | Role |
|---|---|
| [`explorer`](.claude/agents/explorer.md) | Read-only code search/trace; codegraph-first; returns `file:line` summaries. |
| [`planner`](.claude/agents/planner.md) | Read-only; turns a goal into a step plan / `/spec` proposal (opus). |
| [`code-reviewer`](.claude/agents/code-reviewer.md) | Read-only diff review vs CLAUDE.md rules (local; cloud review = `/code-review`). |
| [`test-writer`](.claude/agents/test-writer.md) | Writes JUnit/Espresso tests; may edit test sources + run `test`. |

## Hooks — `.claude/settings.local.json` + `.claude/hooks/`

| Event | Script | Behavior |
|---|---|---|
| PostToolUse (Edit/Write/MultiEdit) | `check-legacy-palette.ps1` | **Warn-only**, non-blocking: flags a legacy palette token added to a `.java`/`.xml` edit (rule #2). |
| SessionStart | `session-context.ps1` | Injects active `/spec` changes + key rule reminders into the session. |
| Stop | `verify-ui-reminder.ps1` | **Blocking-once**: when the uncommitted runtime diff is *big* (not a small fix) and an emulator is attached, blocks the stop with an instruction to verify via the `ui-tester` subagent (rule #7). Silent on small/clean/no-device; re-arms after commit. |

> Hook commands use absolute Windows paths in `settings.local.json` (a machine-local file). Generalize them to `$CLAUDE_PROJECT_DIR` when extracting the reusable plugin (roadmap Phase 4).

## Key directories

| Path | Purpose |
|---|---|
| `app/src/main/java/.../ui/` | MVVM screens: `home/`, `calendar/`, `progress/`, `projects/` |
| `app/src/main/java/.../calendarEntries/` | `CalendarEntry` entity + `EventBuilder`, `AddEventActivity` |
| `app/src/main/java/.../configuration/` | `Configuration` entity, `ConfigurationManager`, `SplashActivity`, Hilt modules |
| `app/src/main/java/.../database/` | `AppDatabase` (v10, 3 real migrations + destructive fallback), `DBConverter`, `DBMigration` (Application class) |
| `app/src/main/java/.../stats/` | `Stats`, `DailyStat`, `FocusEvent`, `AppRule`, `ConsentRecord` entities + DAOs |
| `app/src/main/java/.../usage/` | Usage measurement (`UsageStatsRepository`) + `limits/` (alarm-based warn pipeline) |
| `app/src/main/java/.../blocking/` | Accessibility-service enforcement (cover overlay / bounce-to-home) |
| `app/src/main/java/.../notifications/` | Channels, `BBetterNotifier`, permission gate + per-feature notifiers (`focus/`, `usage/`, `event/`) |
| `app/src/main/java/.../helpers/` | `FormatHelper`, `ScreenHelper`, `ToolbarHelper`, toolbar listener interfaces |
| `app/src/main/java/.../popups/` | `PopupHelper` base + 6 concrete dialog fragments |
| `app/src/main/java/.../feedback/` | `HapticFeedback`, `SoundFeedback` |
| `app/src/main/res/navigation/` | Bottom-nav graph (Home, Progress, Calendar, Projects) |
| `app/src/main/res/values/` | `colors.xml` (palette), `themes.xml`, `styles_typography.xml`, `style.xml`, `dimens.xml`, `strings.xml` |
| `.claude/docs/` | Knowledge base (see index above) |
| `.claude/docs/systems/` | Per-runtime-subsystem living docs (see System docs table above) |
| `.claude/plans/` | Saved design plans |
| `.claude/skills/` | Project-level skills |
| `.claude/specs/` | Spec-driven changes (`/spec`): `changes/`, `capabilities/`, `archive/` |
| `.claude/agents/` | Subagent definitions (explorer, planner, code-reviewer, test-writer) |
| `.claude/hooks/` | PowerShell hook scripts referenced by `settings.local.json` |
| `.claude/harness-marketplace/` | Reusable `claude-harness` plugin + marketplace (Phase 4) — install in new apps |
