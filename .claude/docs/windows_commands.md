# Windows / PowerShell Command Reference

This dev machine is **Windows 11 + PowerShell 5.1**. Most online Android docs assume macOS/Linux shells. This page is the translation layer.

> Bash is also available via the Bash tool, but the *project's primary shell is PowerShell*. Prefer PowerShell unless a command is documented to need a POSIX environment (e.g. shell scripts under `gradle/`).

---

## 1. Gradle wrapper

| What | Windows (preferred) | macOS/Linux equivalent (don't use here) |
|---|---|---|
| Debug build | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Release build | `.\gradlew.bat assembleRelease` | `./gradlew assembleRelease` |
| Unit tests | `.\gradlew.bat test` | `./gradlew test` |
| Instrumented tests | `.\gradlew.bat connectedAndroidTest` | `./gradlew connectedAndroidTest` |
| Lint | `.\gradlew.bat lint` | `./gradlew lint` |
| Clean | `.\gradlew.bat clean` | `./gradlew clean` |
| Stop daemons | `.\gradlew.bat --stop` | `./gradlew --stop` |
| Build without daemon | `.\gradlew.bat assembleDebug --no-daemon` | — |

`.\gradlew.bat` works from PowerShell. `./gradlew` (bash-style) is currently allow-listed in [`.claude/settings.local.json`](../settings.local.json) for the `Bash` tool, but the canonical command on this machine is `.\gradlew.bat`.

### When a build hangs

`.\gradlew.bat --stop` then re-run. If still stuck:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
```

---

## 2. PowerShell quirks to remember

These bite often when translating shell snippets:

- **No `&&` / `||`** (PowerShell 5.1). Use `; if ($?) { B }` for "A then B if A succeeded", or just `;` for unconditional sequencing.
- **`2>&1` on native exes lies**: stderr lines become ErrorRecords and `$?` flips to `$false` even on exit 0. The Bash tool / PowerShell tool captures stderr for you — don't redirect it.
- **Default file encoding is UTF-16 LE with BOM** for `Out-File` / `Set-Content`. Pass `-Encoding utf8` when writing files other tools will read.
- **Env vars**: `$env:VAR = "x"`, never `export` / `set`.
- **Null sink**: `2>$null`, not `2>/dev/null`.
- **`-ErrorAction SilentlyContinue` suppresses the message but the cmdlet still returns failure** — wrap in `try { … -ErrorAction Stop } catch {}` if you actually want it ignored.

---

## 3. Common file ops

Prefer the dedicated Claude tools (`Read`, `Edit`, `Grep`, `Glob`) over running these by hand. These are for when you must shell out.

| Task | PowerShell |
|---|---|
| List files | `Get-ChildItem` (alias `ls`, `dir`) |
| Find a file by name | `Get-ChildItem -Recurse -Filter "*.java"` (or use Glob tool) |
| Search file contents | `Select-String -Path *.java -Pattern "foo"` (or use Grep tool) |
| Read file head | `Get-Content file -TotalCount 50` |
| Read file tail | `Get-Content file -Tail 50` |
| Find which | `(Get-Command name).Source` |
| Make a dir | `New-Item -ItemType Directory -Force path` |
| Touch a file | `if (-not (Test-Path p)) { New-Item -ItemType File p }` |
| Recursive delete | `Remove-Item -Recurse -Force path` |
| Word count lines | `(Get-Content file \| Measure-Object -Line).Lines` |

---

## 4. Android SDK / emulator (`adb`, `emulator`)

These tools live under the Android SDK; on most setups that's `%LOCALAPPDATA%\Android\Sdk` or `%USERPROFILE%\AppData\Local\Android\Sdk`. Put `platform-tools` and `emulator` on `PATH` or call them with full paths.

| Task | Command |
|---|---|
| List devices | `adb devices` |
| Install the debug APK | `adb install -r app\build\outputs\apk\debug\app-debug.apk` |
| Uninstall | `adb uninstall io.github.thaguille.bbettercalendar` |
| Clear app data | `adb shell pm clear io.github.thaguille.bbettercalendar` |
| Launch app | `adb shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity` |
| Logcat (project tag) | `adb logcat -s BBetterCalendar:V AndroidRuntime:E` |
| Take a screenshot | `adb exec-out screencap -p > screen.png` |
| Pump a deep link | `adb shell am start -W -a android.intent.action.VIEW -d "<url>"` |
| List AVDs | `emulator -list-avds` |
| Get a ready AVD (reuse a running one, else boot `api36` from its `bt_quick` snapshot) | `powershell -NoProfile -ExecutionPolicy Bypass -File .claude\scripts\avd-up.ps1` (`-Clean` = cold boot with `-no-snapshot`, only when a test needs a clean device; `-Count 2` = two instances of `api36`, the second `-read-only`, for units in parallel) |
| Run one command on a free emulator (parallel units) | `python .claude\scripts\with-emulator.py -- "<cmd>"`: leases a free `emulator-*`, sets `ANDROID_SERIAL` for the command (adb, `installDebug`, bbadb, the adb-ui-test scripts follow it), releases it at the end |
| Start an AVD by hand | `emulator -avd api36 -snapshot bt_quick -no-snapshot-save` (reuse ~1.5 s, snapshot ~10-30 s, cold `-no-snapshot` ~50-60 s) |

> Use the foreground service notification channel name `pomodoro_channel` when filtering notification logs.

### Memory with two emulators (measured 2026-11-13)

Measurement command (PowerShell, free physical memory):

```powershell
$o=Get-CimInstance Win32_OperatingSystem; 'FreeMB=' + [int]($o.FreePhysicalMemory/1024) + ' TotalMB=' + [int]($o.TotalVisibleMemorySize/1024)
```

- With two `api36` emulators running (`emulator-5554`, `emulator-5556`) the 31 GB machine had about 1.8 GB free (`FreeMB=~1800 TotalMB=32013`). A second reading, taken while other builds were also running, was `FreeMB=989 TotalMB=32013`.
- Rule: **do not add a third emulator while free memory with two stays under 3 GB.**
- `gradle.properties` sets `org.gradle.daemon.idletimeout=900000`: an unused Gradle daemon (about 2 GB each, `-Xmx2048m` stays) exits after 15 min instead of the 3 h default, so parallel units keep memory free.
- The emulator RAM was not lowered: the `bt_quick` snapshot is tied to 3072 MB, and a different size would invalidate it (cold boot ~50-60 s instead of a snapshot load).

---

## 5. Git on this repo

OS-agnostic, but PowerShell-friendly:

```powershell
git status
git add app/src/main/java/...
git diff --stat
git log --oneline -n 10
git fetch --all --prune
```

The path uses `OneDrive\Documentos\Proyectos\…`. OneDrive sync occasionally locks files (build outputs, `app.apk`) — if a `gradlew clean` fails, pause OneDrive sync for a minute and retry.

---

## 6. Permissions allow-list

`.claude/settings.local.json` is the place to allow-list new commands you'll run repeatedly. Use the form Claude Code actually sees in tool calls:

- `Bash(./gradlew.bat *)` — covers any `gradlew.bat` invocation through the Bash tool.
- `PowerShell(.\gradlew.bat *)` — same but through the PowerShell tool.
- `Bash(adb *)` — adb commands.

See [`fewer-permission-prompts`](../../README.md) (`/fewer-permission-prompts` skill) to scan past sessions and auto-suggest allow-list additions.
