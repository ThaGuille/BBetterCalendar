# Android Studio — Working Notes

The project is opened with Android Studio (Iguana / Jellyfish era — AGP 8.13 needs Android Studio 2024.x or later). These are the IDE-specific quirks and workflows worth knowing so Claude can give actionable steps when the user is debugging in the IDE.

> When suggesting actions in the IDE, name the menu path verbatim (e.g. *File → Sync Project with Gradle Files*). Vague pointers ("just sync the project") are usually the cause of users going around in circles.

---

## 1. Project structure (as seen in the IDE)

- **Gradle Scripts** node — `build.gradle (project)`, `build.gradle (Module :app)`, `gradle.properties`, `settings.gradle`, `local.properties`.
- **app / java** — production sources rooted at `com.example.bbettercalendar`.
- **app / java (androidTest)** / **(test)** — instrumented & JVM tests (mostly stubs at the moment).
- **app / res** — resources. The IDE groups by qualifier; `values/`, `drawable/`, `layout/`, `navigation/`, `menu/`, `font/`.
- **app / manifests** — `AndroidManifest.xml`. The `android:name="….database.DBMigration"` declares the `Application` class.

When a file shows up under `app/build/` it's generated; don't edit there.

---

## 2. Gradle sync

Triggered automatically by edits to `build.gradle`, `gradle.properties`, `settings.gradle`, or the wrapper. Manual: *File → Sync Project with Gradle Files* (`Ctrl+Shift+O` on Windows is the default for "Synchronize", but the action is in the toolbar as the elephant icon).

Common sync failures and where they surface:

| Where the error shows | Likely cause | Doc |
|---|---|---|
| "Build" tool window — red banner | Plugin / dep version mismatch | [`common_errors.md`](common_errors.md) §Build |
| "Sync" output — `JAVA_HOME` complaint | Wrong JDK | [`windows_commands.md`](windows_commands.md) §Gradle |
| Sync succeeds but classes show red | Hilt annotation processor not run | [`common_errors.md`](common_errors.md) §Hilt |

---

## 3. Running the app from the IDE

1. Pick a device in the toolbar (physical via USB / wireless, or an AVD).
2. Hit **Run** (`Shift+F10`) — installs the debug APK and launches via `SplashActivity` (LAUNCHER intent filter).
3. **Debug** (`Shift+F9`) attaches the debugger; breakpoints on Java work, but for `@Inject` / Hilt-generated classes set them in your own code, not the `Hilt_*` generated subclasses.

If you change resource XML, no rebuild is needed for Apply Changes — Apply Changes & Restart Activity (`Ctrl+F10`) is usually enough. For schema bumps or new Hilt bindings, do a full re-run.

---

## 4. Emulator / AVD

- **Device Manager** in the right rail.
- Pixel 6 API 34 is a reasonable default (matches `targetSdk 34`).
- Cold boot if state seems stale: *Device Manager → ⋮ → Cold Boot Now*.
- Internet inside the emulator goes through host DNS; corporate VPNs sometimes block this.

---

## 5. Logcat

- Open **Logcat** (`Alt+6`).
- Filter by package: `package:com.example.bbettercalendar`.
- The Pomodoro foreground service tags itself; filter on `BBetterCalendar` or look for the channel id `pomodoro_channel`.
- When the user pastes a stack trace, ask whether it's from logcat (good — full context) or from a toast/snackbar (likely truncated).

---

## 6. Layout / preview tooling

- **Split** view (top-right of the layout XML editor) lets you see code + render side-by-side.
- Use the **Theme** picker in the preview to switch between `Theme.BBetter`, `Theme.BBetter.NoActionBar`, and the legacy `ThemeChatGPT*` variants.
- `tools:text` / `tools:visibility` placeholders only render in the preview; they don't affect runtime.
- Downloadable fonts (`@font/plus_jakarta_sans`, `@font/fraunces`) need an initial sync to populate. If preview falls back to Roboto, sync once and reopen the file.

---

## 7. Database inspection

**App Inspection** tool window (`Alt+9` → *Database Inspector*) shows the live Room DB on a connected debug build. Useful for confirming:

- Only one row in `stats` (id 0) and one in `configuration` (id 1).
- `CalendarEntry.type` values are 1/2/3 only.
- `Calendar` / `boolean[]` fields are stored as the expected JSON strings.

It does *not* survive a `pm clear` — you'll need to reconnect.

---

## 8. Code style / formatter

The repo doesn't ship a Spotless / ktlint config. Use Android Studio's default Java formatter:

- *Code → Reformat Code* (`Ctrl+Alt+L`).
- *Code → Optimize Imports* (`Ctrl+Alt+O`).
- The mixed Spanish/Catalan/English comments are intentional — don't run *Auto-translate* or rephrase them unless asked.

---

## 9. Useful Run/Debug configurations to add

Worth saving in *Run → Edit Configurations*:

- **App** (default) — installs and runs on selected device.
- **Gradle: assembleDebug** — explicit assemble task.
- **Gradle: clean assembleDebug** — for when incremental compile gets confused.
- **JUnit: app** — runs the (currently sparse) `test/` tree.

---

## 10. Common Android Studio symptoms

| What you see | Where to look |
|---|---|
| Red squiggles on a class that just built | *File → Invalidate Caches… → Invalidate and Restart*. |
| `Hilt_…` symbols missing | Annotation processor — see [`common_errors.md`](common_errors.md) §Hilt. |
| Preview blank with "Render Problem" | Toggle the theme; the legacy themes sometimes lack a parent attr. Try `Theme.BBetter`. |
| "Could not delete …" on Build → Clean Project | OneDrive lock — see [`windows_commands.md`](windows_commands.md) §6. |
| Gradle sync stuck on "Resolve dependencies" | Often a flaky JCenter/Maven mirror. Stop daemons (`.\gradlew.bat --stop`) and retry. |
| Logcat empty after device reconnect | Re-select the process in the dropdown; the IDE doesn't auto-rebind. |
