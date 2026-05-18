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
| Uninstall | `adb uninstall com.example.bbettercalendar` |
| Clear app data | `adb shell pm clear com.example.bbettercalendar` |
| Launch app | `adb shell am start -n com.example.bbettercalendar/.configuration.SplashActivity` |
| Logcat (project tag) | `adb logcat -s BBetterCalendar:V AndroidRuntime:E` |
| Take a screenshot | `adb exec-out screencap -p > screen.png` |
| Pump a deep link | `adb shell am start -W -a android.intent.action.VIEW -d "<url>"` |
| List AVDs | `emulator -list-avds` |
| Start an AVD | `emulator -avd Pixel_6_API_34 -no-snapshot-load` |

> Use the foreground service notification channel name `pomodoro_channel` when filtering notification logs.

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
