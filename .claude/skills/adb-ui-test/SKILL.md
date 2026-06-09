---
name: adb-ui-test
description: Drive the running BBetter app on the emulator over adb to catch crashes and verify screen state — screenshot-free and MCP-free. Use when the user says "test the app", "run a UI flow", "drive the emulator", "does screen X work", "click through the progress screen", or asks to verify a change actually runs (not just compiles). Navigates by resource-id via uiautomator XML; records/replays flows cheaply.
---

# adb-ui-test — screenshot-free UI testing over adb

Exercise the **running** app, not just the compiler. Pattern: **adb is the hands,
the uiautomator XML hierarchy is the eyes** — no screenshots, no MCP server. Adapted
from [skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills)
(the "find by stable id, not fragile text" doctrine), Windows/PowerShell-ified, with a
host-side dump locator and a record/replay layer added.

Selectors and device facts live in
[`references/bbetter-selectors.md`](references/bbetter-selectors.md) — read it first; don't
re-derive ids.

## Preconditions

- An emulator/device must be attached. Check: `adb devices` → expect `emulator-5554 device`.
  - No device? An **operator** must create/boot an AVD once (Android Studio Device Manager).
    If an AVD already exists you may boot it headless:
    `& "C:\Users\guill\AppData\Local\Android\Sdk\emulator\emulator.exe" -avd <name> -no-window`
    (list with `-list-avds`). You **cannot** create an AVD, accept SDK licenses, or authorize
    a physical device's "Allow USB debugging" prompt yourself.
- Run scripts from the skill's `scripts\` dir (or pass full paths).

## Workflow

1. **Build & install** (debug variant):
   ```powershell
   .\gradlew.bat assembleDebug
   adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   Build failures → use [`bb-build`](../bb-build/SKILL.md) (stuck daemon → `.\gradlew.bat --stop`).

2. **Grant the runtime permission** (no on-screen tap needed) and **launch**:
   ```powershell
   adb -s emulator-5554 shell pm grant com.example.bbettercalendar android.permission.POST_NOTIFICATIONS
   adb -s emulator-5554 shell am start -n com.example.bbettercalendar/.configuration.SplashActivity
   ```

3. **Navigate by id** with the locator (resolves id → center → tap; never dumps raw XML
   into context):
   ```powershell
   .\scripts\find-node.ps1 -Id navigation_progress -Tap
   .\scripts\find-node.ps1 -Id granularity_month   -Tap
   .\scripts\find-node.ps1 -Id range_prev          -Tap
   .\scripts\find-node.ps1 -List                      # discovery: id | text | desc | bounds
   ```

4. **Assert the effect + scan for crashes**:
   ```powershell
   .\scripts\find-node.ps1 -Id range_label                     # prints its text
   adb -s emulator-5554 logcat -d -b crash                     # must contain no FATAL EXCEPTION
   ```

5. **Reset** between scenarios (hermetic):
   ```powershell
   adb -s emulator-5554 shell pm clear com.example.bbettercalendar
   ```

## Record once, replay cheap

Author a flow with `bb-adb.ps1` (does the step live **and** logs it), then re-run it with
`replay.ps1` — no model in the loop, so re-runs cost ~zero tokens. Checkpoints are
XML-dump assertions, not image diffs.

```powershell
cd .claude\skills\adb-ui-test\scripts
.\bb-adb.ps1 -Scenario progress-smoke -Init
.\bb-adb.ps1 -Scenario progress-smoke -Start .configuration.SplashActivity
.\bb-adb.ps1 -Scenario progress-smoke -Tap navigation_progress
.\bb-adb.ps1 -Scenario progress-smoke -Tap granularity_month
.\bb-adb.ps1 -Scenario progress-smoke -Tap range_prev
.\bb-adb.ps1 -Scenario progress-smoke -AssertId range_label
# later, as many times as you like, for free:
.\replay.ps1 progress-smoke
```

Recordings are flat text under `recordings/<scenario>.log` (epoch-ms `\t` STEP). The
grammar (START / GRANT / CLEAR / TAP / TYPE / KEY / SWIPE / WAIT / ASSERT) is documented in
[`scripts/_steps.ps1`](scripts/_steps.ps1).

## RIGHT / WRONG

- **RIGHT:** `find-node.ps1 -Id range_label` then assert its **text** changed after a tap.
  **WRONG:** trying to read which segmented button "looks selected" — selection is a
  background-drawable swap, invisible to the dump.
- **RIGHT:** assert chart *data* with JUnit on `ProgressViewModel`.
  **WRONG:** expecting `chart_container` data points in the XML — MPAndroidChart is
  canvas-drawn and absent from uiautomator output.
- **RIGHT:** tap by `-Id` (stable). **WRONG:** hardcoding pixel coordinates in a recording —
  replay re-resolves ids live so layout shifts don't break it.
- **RIGHT:** `pm grant POST_NOTIFICATIONS`. **WRONG:** trying to tap a system permission
  dialog (you can't see it, and the grant avoids it entirely).

## Hard limits (by design — this is the cost of "no screenshots")

- No pixel/visual verification: chart shapes, layout, `bb_*` palette can't be judged.
- Custom-canvas content (charts) is invisible to the dump → covered by JUnit, not here.
- Segmented selection state isn't reliably in the dump → assert the resulting `range_label`
  text or a logcat marker instead.

## Verification checklist

- [ ] `adb devices` shows the target.
- [ ] App installs and `am start` launches without `FATAL EXCEPTION` in `logcat -b crash`.
- [ ] `find-node.ps1 -Id range_label` returns `FOUND ...` (proves navigation reached Progress).
- [ ] A non-existent id returns `NOT FOUND` with exit code 1 (proves assertions actually gate).
- [ ] `replay.ps1 <scenario>` reports `PASS`.

## Cross-refs

- [`references/bbetter-selectors.md`](references/bbetter-selectors.md) — ids & device facts.
- [`bb-build`](../bb-build/SKILL.md) — gradle invocation & build-failure recovery.
- [`check`](../check/SKILL.md) — compile/lint/unit gate (run logic/data checks there).
- `ui-tester` subagent — runs this loop in an isolated context (keeps dumps/logcat out of
  the main conversation).
