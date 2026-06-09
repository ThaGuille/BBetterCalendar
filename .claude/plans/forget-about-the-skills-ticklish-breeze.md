# Plan: Screenshot-free, MCP-free ADB UI-testing system for BBetterCalendar

Status: proposed
Created: 2026-06-09
Last updated: 2026-06-09

## Context

We want AI agents to actually exercise the running app on the emulator — not just
compile it — to catch crashes and verify screen state (e.g. the new progress-charts
screen). The proven, low-cost pattern is **adb as the hands + the uiautomator XML
hierarchy as the eyes**, with **no screenshots** and **no MCP server**. The user has
adb installed and an emulator already running (`emulator-5554`, API 34); the SDK is at
`C:\Users\guill\AppData\Local\Android\Sdk`.

We base the work on an existing, tested Claude Code skill set —
**[skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills)**
(pure-markdown `SKILL.md` + `references/`, no runtime deps beyond adb, already follows
the "find by stable id, not fragile text" doctrine) — Windows/PowerShell-ify it, drop
the `screencap`/`screenrecord` parts, and add the one piece it lacks: host-side
uiautomator-dump navigation (recipe borrowed from
[hah23255/adb-android-control](https://github.com/hah23255/adb-android-control) and
[nighteblis/uiautomatorDump](https://github.com/nighteblis/uiautomatorDump)). We also
add a screenshot-free **record/replay** layer (the
[tobrun/android-qa-agent](https://github.com/tobrun/android-qa-agent) idea, reimplemented
in PowerShell with XML-dump assertions instead of image diffs) so re-running a known flow
costs ~zero tokens.

**Intended outcome:** an agent (or a dedicated subagent) can build → install → drive the
UI by resource-id → assert state and scan logcat → reset, and can record a flow once and
replay it cheaply thereafter.

## Hard limitations (inherent to "no screenshots" — accepted)

- **No pixel/visual verification.** Chart shapes, layout, and the `bb_*` palette cannot
  be judged. We assert structure (ids/text present), state (e.g. `range_label` text),
  and crashes (logcat) only.
- **Custom-drawn charts are invisible to the dump.** MPAndroidChart renders into
  `chart_container` on a canvas; uiautomator XML won't expose data points. **Chart data
  correctness must be covered by JUnit tests on `ProgressViewModel`, not this UI agent.**
- **Segmented selection state** (day/week/month uses different background drawables, not a
  `checked` attribute) may not be readable from the dump. Assert the *effect* instead —
  `range_label` text change, or a logcat marker — not the button's visual state.

## Commands the agent CANNOT run itself (operator steps)

- **Create an AVD / accept SDK licenses / first-time Device Manager setup** — GUI in
  Android Studio. Not needed while an emulator is running. If none is, the operator must
  create one once.
- **Authorize a physical device** — the on-device "Allow USB debugging?" prompt.
- The agent **can** boot an *existing* AVD headless in the background:
  `& "$SDK\emulator\emulator.exe" -avd <name> -no-window` (list with `-list-avds`).
- The agent **can** grant the only runtime permission needed without a tap:
  `adb shell pm grant com.example.bbettercalendar android.permission.POST_NOTIFICATIONS`.

## Deliverables (files to create)

All under a new project skill `app`-agnostic enough to reuse later:

1. **`.claude/skills/adb-ui-test/SKILL.md`** — adapted from skydoves' ADB set.
   YAML frontmatter (trigger vocab: "test the app", "run a UI flow", "drive the
   emulator", "does screen X work"), numbered workflow, RIGHT/WRONG pairs, a verification
   checklist. Documents the loop with the BBetter cheatsheet baked in (below). PowerShell
   command forms; `pm clear` for hermetic reset; `pm grant` for POST_NOTIFICATIONS.

2. **`.claude/skills/adb-ui-test/references/bbetter-selectors.md`** — the id/selector
   cheatsheet so the agent never re-derives selectors (token saver, record-once spirit):
   - Package: `com.example.bbettercalendar`; launcher:
     `com.example.bbettercalendar/.configuration.SplashActivity`
   - Debug APK: `app\build\outputs\apk\debug\app-debug.apk` (build: `.\gradlew.bat assembleDebug`)
   - Bottom nav ids: `navigation_home`, `navigation_progress`,
     `navigation_calendar_month`, `navigation_projects`
   - Progress screen ids: `text_dashboard`, `chart_pager`, `chart_dots`,
     `granularity_day` / `granularity_week` / `granularity_month`, `range_prev`,
     `range_label`, `range_next`; card: `chart_card_label`, `chart_container`
   - Emulator/SDK facts: `emulator-5554`, SDK `C:\Users\guill\AppData\Local\Android\Sdk`

3. **`.claude/skills/adb-ui-test/scripts/find-node.ps1`** — the token-efficiency core
   (the bit skydoves lacks). Params: `-Id <suffix>` or `-Text <substring>`, `-Tap`
   switch, `-List` mode, `-Serial` (default `emulator-5554`). It runs
   `adb -s <serial> shell uiautomator dump /sdcard/bb_dump.xml`, reads it
   (`adb shell cat`), finds the node whose `resource-id` ends with `:id/<suffix>` (or
   whose `text`/`content-desc` contains the substring), parses `bounds="[x1,y1][x2,y2]"`,
   computes the center, and prints **compact** output (`id  center=(cx,cy)  text='…'`) —
   never the raw XML into context. `-Tap` then runs `adb shell input tap cx cy`. `-List`
   prints all interactable nodes' `id|text|bounds` (token-bounded) for discovery.

4. **`.claude/skills/adb-ui-test/scripts/bb-adb.ps1`** (record) + **`replay.ps1`**
   (replay) — the record/replay layer:
   - `bb-adb.ps1` proxies the mutating verbs (`am start`, `input tap/text/swipe/keyevent`,
     `pm clear/grant`) and appends each, with a timestamp, to
     `.claude/skills/adb-ui-test/recordings/<scenario>.log`. Checkpoints are written as
     `ASSERT id=<id> expect~=<substr>` lines (a dump-and-check, no image).
   - `replay.ps1 <scenario>` re-executes each logged command with recorded inter-command
     delays (scaled by a speed factor), re-dumps at each `ASSERT` line via `find-node.ps1`,
     and fails fast on a missing/mismatched node or any `FATAL EXCEPTION` in
     `adb logcat -d`. Flat text format — no JSON ceremony.

5. **`.claude/agents/ui-tester.md`** — a subagent (tools: Bash/PowerShell, Read, Glob,
   Grep) whose `description` routes "run/verify a UI flow on the emulator". It invokes the
   skill, runs the loop **in its own context** so raw dumps/logcat stay out of the main
   conversation, and returns a concise pass/fail + any logcat excerpt. (Not wired into
   `CLAUDE.md`'s subagent table in this change — optional follow-up.)

## The loop (what SKILL.md documents)

```powershell
$pkg='com.example.bbettercalendar'; $S='emulator-5554'
.\gradlew.bat assembleDebug
adb -s $S install -r app\build\outputs\apk\debug\app-debug.apk
adb -s $S shell pm grant $pkg android.permission.POST_NOTIFICATIONS
adb -s $S shell am start -n "$pkg/.configuration.SplashActivity"
# navigate (helper resolves id -> center -> tap):
.\scripts\find-node.ps1 -Id navigation_progress -Tap
.\scripts\find-node.ps1 -Id granularity_month   -Tap
.\scripts\find-node.ps1 -Id range_prev          -Tap
# assert state + no crash:
.\scripts\find-node.ps1 -Id range_label            # prints its text
adb -s $S logcat -d -b crash                       # must be empty of FATAL EXCEPTION
adb -s $S shell pm clear $pkg                       # hermetic reset
```

## Verification (end-to-end, after build)

1. **Loop smoke test:** run the block above. Expect: progress tab opens, `range_label`
   text changes after `range_prev`, and `logcat -b crash` shows no `FATAL EXCEPTION`.
2. **Record once:** drive Home → Progress → granularity day→month → `range_prev` ×2 via
   `bb-adb.ps1 -Scenario progress-smoke`, inserting an `ASSERT id=range_label` checkpoint.
3. **Replay cheap:** `replay.ps1 progress-smoke` re-runs with no model in the loop and
   passes the checkpoint + logcat scan.
4. **Negative check:** temporarily point a tap at a non-existent id → `find-node.ps1`
   reports "not found" and the run fails fast (proves assertions actually gate).
5. **Confirm the boundary:** note in the run output that chart *data* is not asserted here
   (covered by `ProgressViewModel` JUnit), demonstrating the documented limitation.

## Out of scope

- Screenshots / image-diff / visual regression (by request).
- Any MCP server (by request).
- Espresso/`am instrument` on-device tests — complementary, but this system is the
  black-box adb driver; logic/data correctness stays in JUnit.
