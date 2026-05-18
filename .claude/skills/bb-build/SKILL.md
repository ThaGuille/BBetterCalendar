---
name: bb-build
description: Build, lint, or test the BBetterCalendar Android app from PowerShell using the right `gradlew.bat` invocation. Use when the user asks to "build the app", "run gradle", "run tests", "lint", "assemble", or "make a debug APK". Picks the correct Windows-style command and handles the common failure modes (stuck daemon, OneDrive lock).
---

# bb-build

Invoke gradle on this repo correctly from PowerShell.

## When to use

User asks for any of: build, assemble, install, test, lint, clean. On Windows the canonical command is `.\gradlew.bat <task>`, not `./gradlew <task>`.

## Task → command map

| Intent | Command |
|---|---|
| Debug APK | `.\gradlew.bat assembleDebug` |
| Release APK | `.\gradlew.bat assembleRelease` |
| Unit tests | `.\gradlew.bat test` |
| Instrumented tests | `.\gradlew.bat connectedAndroidTest` |
| Lint | `.\gradlew.bat lint` |
| Clean | `.\gradlew.bat clean` |
| Stop daemons | `.\gradlew.bat --stop` |
| No-daemon build (CI / locked daemon) | `.\gradlew.bat assembleDebug --no-daemon` |
| Install + launch | `adb install -r app\build\outputs\apk\debug\app-debug.apk && adb shell am start -n com.example.bbettercalendar/.configuration.SplashActivity` (note: PS5 has no `&&`; chain with `; if ($?) { … }`) |

Run gradle commands via the **Bash tool** (the project already allow-lists `Bash(./gradlew.bat *)`) or the **PowerShell tool**. Use `run_in_background: true` for long builds and stream progress with the `Monitor` tool.

## When it goes wrong

1. **Build hangs** → `.\gradlew.bat --stop`, then retry.
2. **`Could not delete …` on clean** → OneDrive locking files. Ask the user to pause OneDrive briefly, then retry.
3. **`Unsupported class file major version …`** → Wrong JDK. Need JDK 17 (Android Studio's bundled JBR works). See [`.claude/docs/common_errors.md`](../../docs/common_errors.md) §Build.
4. **Build fails citing `material` 1.10+** → Someone bumped it. Revert to `1.9.0` ([`app/build.gradle:47`](../../../app/build.gradle#L47) has the warning comment).

## After a successful build

Report:
- APK path (`app\build\outputs\apk\debug\app-debug.apk`).
- Any warnings worth surfacing (deprecations, lint baseline drift).
- Whether you installed/launched it (only if the user asked).

Don't summarise the gradle output verbatim — pull out the actionable bits.

## Don't

- Don't bypass hooks (`--no-verify` flags don't apply here, but the principle does).
- Don't `gradlew clean` without checking if the user has uncommitted build outputs they care about (rare, but `app/build/` sometimes holds the only copy of a release-signed APK).
