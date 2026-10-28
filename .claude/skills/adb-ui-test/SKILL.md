---
name: adb-ui-test
description: Drive the running BBetter app on the emulator over adb to catch crashes and verify screen state — screenshot-free and MCP-free. Use when the user says "test the app", "run a UI flow", "drive the emulator", "does screen X work", "click through the progress screen", or asks to verify a change actually runs (not just compiles). Navigates by resource-id via uiautomator XML; records/replays flows cheaply. For the one case the XML dump can't see — a chart/graph rendering or a layout/palette visual check — capture-screen.ps1 grabs a real PNG to inspect (use only when a visual judgement is actually needed).
---

# adb-ui-test — screenshot-free UI testing over adb

Exercise the **running** app, not just the compiler. Pattern: **adb is the hands,
the uiautomator XML hierarchy is the eyes** — no screenshots, no MCP server. Adapted
from [skydoves/android-testing-skills](https://github.com/skydoves/android-testing-skills)
(the "find by stable id, not fragile text" doctrine), Windows/PowerShell-ified, with a
host-side dump locator and a record/replay layer added.

## Windows vs Linux/macOS

The commands below are written for **Windows** (PowerShell, `scripts\*.ps1`). On **Linux/macOS**
the same flow runs through one Python script, [`scripts/bbadb.py`](scripts/bbadb.py): same
behaviour, exit codes, recording grammar and `recordings/` / `captures/` folders, so a flow recorded
on one OS replays on the other. `adb` is often not on PATH there; `bbadb.py adb <args>` runs the one
it finds (`$ADB`, PATH, `$ANDROID_HOME`, `local.properties` `sdk.dir`, `~/Android/Sdk`).

| Windows (from `scripts\`) | Linux/macOS (from `scripts/`) |
|---|---|
| `.\find-node.ps1 -Id X -Tap` / `-Text T` / `-List` | `python3 bbadb.py find-node --id X --tap` / `--text T` / `--list` |
| `.\bb-adb.ps1 -Scenario S -Init` | `python3 bbadb.py record S --init` |
| `.\bb-adb.ps1 -Scenario S -Tap X` (`-Start`, `-Grant`, `-Clear`, `-TapText`, `-Type`, `-Key`, `-Swipe`, `-Wait`, `-AssertId X -Expect Y`, `-Shot L`) | `python3 bbadb.py record S --tap X` (`--start`, `--grant`, `--clear`, `--tap-text`, `--type`, `--key`, `--swipe x1 y1 x2 y2 ms`, `--wait`, `--assert-id X --expect Y`, `--shot L`) |
| `.\replay.ps1 S [-Speed 0.5]` | `python3 bbadb.py replay S [--speed 0.5]` |
| `.\capture-screen.ps1 -Label L` | `python3 bbadb.py capture --label L` |
| `adb -s emulator-5554 …` | `python3 bbadb.py adb -s emulator-5554 …` |
| `.\gradlew.bat assembleDebug`, `app\build\…` | `./gradlew assembleDebug`, `app/build/…` |
| `& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <name> -no-window` | `~/Android/Sdk/emulator/emulator -avd <name> -no-window -no-audio` (run in background) |
| `.claude\scripts\emulator-lease.ps1 -Acquire -Unit u1` | `python3 .claude/scripts/emulator-lease.py --acquire --unit u1` |

`--serial` / `-Serial` behave the same: default is the first attached `emulator-*`, never a phone.

Selectors and device facts live in
[`references/bbetter-selectors.md`](references/bbetter-selectors.md) — read it first; don't
re-derive ids.

## Preconditions

- An emulator/device must be attached. Check: `adb devices` → expect `emulator-5554 device`.
  - No device, or not sure? Run `powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\avd-up.ps1`.
    It **reuses** a running emulator of that AVD (`-Avd`, default `api36`; one of another AVD is left alone) (~1.5 s), else boots `api36` headless from its `bt_quick` snapshot
    (~10-30 s; it creates the snapshot on the first cold boot), and prints `AVD READY <serial> (...)`.
    If the snapshot boot fails it falls back once to a cold boot. `avd-up` does **not** lease: when several units
    share emulators, take the lease with `.claude\scripts\emulator-lease.ps1 -Acquire` after it.
    Add `-Clean` only when a check needs a clean boot (`-no-snapshot`, ~50-60 s cold; it refuses while any emulator
    is attached: `adb -s <serial> emu kill` it first or lease another). Never boot the
    emulator through `bt-run.sh` (it would hold the worktree's build lock), and never kill a running one
    another unit leases (`.claude\scripts\emulator-lease.ps1 -Status`). The AVD's own `default_boot`
    snapshot was broken (every "quick boot" fell back to a cold one), hence the named snapshot.
    Measured 2026-10-25: reuse 1.4 s, `bt_quick` 10-30 s, cold boot 49-61 s.
    You **cannot** create an AVD, accept SDK licenses, or authorize a physical device's
    "Allow USB debugging" prompt yourself (list AVDs with `emulator -list-avds`).
- Run scripts from the skill's `scripts\` dir (or pass full paths).
- **Targets the emulator, never the phone, by default.** Every script resolves `-Serial`
  through [`_device.ps1`](scripts/_device.ps1): if you don't pass `-Serial` explicitly, it
  picks the first attached `emulator-*` device from `adb devices`, ignoring any physical
  phone — even if the phone is connected and is the active/preselected device in Android
  Studio. Pass `-Serial <phone-serial>` explicitly only if you genuinely want to drive the
  phone.

## Workflow

1. **Build & install** (debug variant):
   ```powershell
   .\gradlew.bat assembleDebug
   adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   Build failures → use [`bb-build`](../bb-build/SKILL.md) (stuck daemon → `.\gradlew.bat --stop`).

2. **Grant the runtime permission** (no on-screen tap needed) and **launch**:
   ```powershell
   adb -s emulator-5554 shell pm grant io.github.thaguille.bbettercalendar android.permission.POST_NOTIFICATIONS
   adb -s emulator-5554 shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity
   ```

3. **Navigate by id** with the locator (resolves id → center → tap; never dumps raw XML
   into context):
   ```powershell
   .\scripts\find-node.ps1 -Id navigation_progress -Tap
   .\scripts\find-node.ps1 -Id progressGranularity   -Tap
   .\scripts\find-node.ps1 -Id periodNavPrevious          -Tap
   .\scripts\find-node.ps1 -List                      # discovery: id | text | desc | bounds
   ```

4. **Assert the effect + scan for crashes**:
   ```powershell
   .\scripts\find-node.ps1 -Id periodNavTitle                     # prints its text
   adb -s emulator-5554 logcat -d -b crash                     # must contain no FATAL EXCEPTION
   ```

5. **Visual checkpoint — ONLY for charts / layout** (the one escape hatch from the
   screenshot-free rule). Custom-canvas content (MPAndroidChart) is invisible to the XML
   dump, so when you must actually *see* a graph rendered, capture a real PNG and Read it:
   ```powershell
   .\scripts\capture-screen.ps1 -Label progress-month-chart   # -> SAVED <abs-path>.png
   ```
   Then use the **Read** tool on the printed path to inspect the image. Use this sparingly —
   it costs image tokens and isn't free to replay. For structure/text/crash, stay on
   `find-node.ps1`. (Why device-side `screencap` + `adb pull`, not `exec-out ... > out.png`:
   PowerShell's `>` re-encodes the byte stream and corrupts the PNG; `adb pull` is binary-clean.)

6. **Reset** between scenarios (hermetic):
   ```powershell
   adb -s emulator-5554 shell pm clear io.github.thaguille.bbettercalendar
   ```
   `pm clear` revokes runtime grants: re-run the step-2 `pm grant POST_NOTIFICATIONS` after it,
   or the permission dialog comes back on the next launch.

## Record once, replay cheap

Author a flow with `bb-adb.ps1` (does the step live **and** logs it), then re-run it with
`replay.ps1` — no model in the loop, so re-runs cost ~zero tokens. Checkpoints are
XML-dump assertions, not image diffs.

```powershell
cd .claude\skills\adb-ui-test\scripts
.\bb-adb.ps1 -Scenario progress-smoke -Init
.\bb-adb.ps1 -Scenario progress-smoke -Start .configuration.SplashActivity
.\bb-adb.ps1 -Scenario progress-smoke -Tap navigation_progress
.\bb-adb.ps1 -Scenario progress-smoke -Tap progressGranularity
.\bb-adb.ps1 -Scenario progress-smoke -Tap periodNavPrevious
.\bb-adb.ps1 -Scenario progress-smoke -AssertId periodNavTitle
# later, as many times as you like, for free:
.\replay.ps1 progress-smoke
```

Recordings are flat text under `recordings/<scenario>.log` (epoch-ms `\t` STEP). The
grammar (START / GRANT / CLEAR / TAP / TYPE / KEY / SWIPE / WAIT / ASSERT / SHOT) is
documented in [`scripts/_steps.ps1`](scripts/_steps.ps1). `SHOT <label>` (recorder flag
`-Shot <label>`) drops a visual-checkpoint PNG into `captures/` — include it in a flow only
when that scenario renders a chart you need to eyeball.

## RIGHT / WRONG

- **RIGHT:** `find-node.ps1 -Id periodNavTitle` then assert its **text** changed after a tap.
  **WRONG:** trying to read which segmented button "looks selected" — selection is a
  background-drawable swap, invisible to the dump.
- **RIGHT:** assert chart *data* with JUnit on `ProgressViewModel`.
  **WRONG:** expecting `chart_container` data points in the XML — MPAndroidChart is
  canvas-drawn and absent from uiautomator output.
- **RIGHT:** tap by `-Id` (stable). **WRONG:** hardcoding pixel coordinates in a recording —
  replay re-resolves ids live so layout shifts don't break it.
- **RIGHT:** `pm grant POST_NOTIFICATIONS`. **WRONG:** trying to tap a system permission
  dialog (you can't see it, and the grant avoids it entirely).

## Hard limits (the cost of "no screenshots" — with one deliberate escape hatch)

- Default flow is screenshot-free: chart shapes, layout, and `bb_*` palette are **not** in the
  XML dump and can't be judged from it.
- **Escape hatch for visuals:** `capture-screen.ps1` (step `SHOT`) grabs a real PNG you can
  Read — use it *only* when a graph/layout actually needs eyeballing, not as a default. It
  costs image tokens and isn't free to replay, so the no-screenshot default still stands for
  structure/text/crash checks.
- Chart *data* correctness still belongs in JUnit on `ProgressViewModel`; a screenshot proves
  "it rendered / looks right", not "the numbers are correct".
- Segmented selection state isn't reliably in the dump → assert the resulting `periodNavTitle`
  text or a logcat marker instead.

## Verification checklist

- [ ] `adb devices` shows the target.
- [ ] App installs and `am start` launches without `FATAL EXCEPTION` in `logcat -b crash`.
- [ ] `find-node.ps1 -Id periodNavTitle` returns `FOUND ...` (proves navigation reached Progress).
- [ ] A non-existent id returns `NOT FOUND` with exit code 1 (proves assertions actually gate).
- [ ] `replay.ps1 <scenario>` reports `PASS`.
- [ ] (charts only) `capture-screen.ps1 -Label x` prints `SAVED ...png` and the file opens as a
      real PNG; Reading it shows the rendered graph.

## Cross-refs

- [`references/bbetter-selectors.md`](references/bbetter-selectors.md) — ids & device facts.
- [`scripts/capture-screen.ps1`](scripts/capture-screen.ps1) — visual-checkpoint PNG (charts/layout only).
- [`bb-build`](../bb-build/SKILL.md) — gradle invocation & build-failure recovery.
- [`check`](../check/SKILL.md) — compile/lint/unit gate (run logic/data checks there).
- `ui-tester` subagent — runs this loop in an isolated context (keeps dumps/logcat out of
  the main conversation).
