# Common Errors & Fixes

Symptom → root cause → fix table for the failure modes that keep coming up in this project. Add to it whenever you spend more than 10 minutes diagnosing something.

---

## Build / Gradle

### `Unsupported class file major version 65` (or similar) during build
- **Cause**: Wrong JDK on `JAVA_HOME`. The build wants **JDK 17** (AGP 8.x requirement); Android Studio bundles one under `…\Android Studio\jbr`.
- **Fix**: Set `JAVA_HOME` to that JBR path *or* run from inside Android Studio's terminal.
- Verify with `.\gradlew.bat --version`.

### Build fails after a Material lib upgrade
- **Cause**: `com.google.android.material` was bumped past **1.9.0**. See the explicit comment in `app/build.gradle:47` (`// No actualitzar a la 1.7.0, petarà`).
- **Fix**: Revert to `1.9.0`. Do not upgrade unless the user explicitly asks and accepts the regressions.

### `Could not delete … app\build\…` on `gradlew clean`
- **Cause**: OneDrive (or Android Studio) holds a file handle on a build artifact.
- **Fix**: Pause OneDrive sync, close Android Studio's Build window, retry. Stale daemons: `.\gradlew.bat --stop`.

### `Failed to resolve: com.kizitonwose.calendar:view:X.Y.Z`
- **Cause**: Tried to upgrade the Kizitonwose calendar without bumping `compileSdk`.
- **Fix**: Keep at `2.5.4`. We are on `compileSdk 34` now, so a future upgrade is possible, but the existing month-view binders/cell layouts were written against `2.5.4`. Do a tracked PR if upgrading.

### "Dependency requires libraries and applications that depend on it to compile against version 34 or later"
- **Cause**: A transitive dep wants compileSdk > 34.
- **Fix**: Pin the offending dep to a 34-compatible version. Don't blindly bump `compileSdk` to 35 without checking the targetSdk/policy implications.

---

## Room database

### App crashes on first launch after a schema change, *without* a migration
- **Cause**: `fallbackToDestructiveMigration()` is active in `AppDatabase`. The DB was nuked. Real bug is somewhere else — likely `SplashActivity.initializeStats()` not seeding the row it expects.
- **Fix**: Confirm the destructive wipe actually happened (uninstall app or `adb shell pm clear …`), then verify `initializeStats()` runs to completion before `MainActivity` starts.

### `IllegalStateException: Cannot access database on the main thread`
- **Cause**: A DAO call escaped the `ExecutorService`.
- **Fix**: Wrap in `executor.execute { … }`. Push results to UI via `LiveData.postValue(...)`. See [`architectural_patterns.md`](architectural_patterns.md) "Threading Model".

### New entity field doesn't persist / comes back null
- **Cause**: Field is a non-primitive type (`Calendar`, `boolean[]`, custom class) without a `@TypeConverter`.
- **Fix**: Add a converter pair to `DBConverter` and bump `AppDatabase` version. Because destructive migration is on, the DB will wipe — fine in dev, but flag if production data matters.

### Two `Stats` (or `Configuration`) rows appear
- **Cause**: First-run insert ran twice (e.g. `SplashActivity` racing with `InitialConfiguration` legacy code).
- **Fix**: Both should respect `if (dao.get() == null)` before inserting. `Stats` always lives at `id = 0`, `Configuration` at `id = 1`.

---

## Hilt / DI

### `cannot find symbol class DaggerXxx_HiltComponents` or `Hilt_…`
- **Cause**: Annotation processor didn't run. Usually because `hilt-android-compiler` is missing as `annotationProcessor` or the Gradle plugin `dagger.hilt.android.plugin` isn't applied.
- **Fix**: Confirm both lines exist in `app/build.gradle`, then `.\gradlew.bat clean assembleDebug`.

### `@Inject` field is null at runtime
- **Cause**: The host Fragment/Activity is missing `@AndroidEntryPoint`.
- **Fix**: Add the annotation. Every Activity and Fragment that needs injection must have it.

### `IllegalStateException: hilt instance is not available yet`
- **Cause**: Trying to inject into something built before `Application.onCreate()` completed (e.g. a Content Provider).
- **Fix**: Move the access into `onCreate` of an `@AndroidEntryPoint`, or use `EntryPointAccessors` if you really need it earlier.

---

## ViewModel / LiveData

### `Cannot invoke observe on a null object reference` or observers not firing
- **Cause**: Observing with `this` instead of `getViewLifecycleOwner()` inside a Fragment.
- **Fix**: Always `liveData.observe(getViewLifecycleOwner(), observer)` in Fragments — see [`HomeFragment.java:118`](../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeFragment.java#L118).

### "Only the original thread that created a view hierarchy can touch its views"
- **Cause**: `setValue()` from a background thread.
- **Fix**: Use `postValue()` from any non-main thread. From the main thread either is fine, but the convention here is `postValue()` everywhere from VMs.

---

## Popups / dialogs

### Popup result never arrives
- **Cause**: The host Fragment/Activity didn't implement `OnPopupListener<T>` (or `OnNotificationsPopupListener`), or `setOnPopupListener(this)` wasn't called before `show(...)`.
- **Fix**: Implement the interface, call the setter, then `show(getChildFragmentManager(), "tag")`. Constants live in `PopupHelper`.

### Dialog appears with the wrong theme (corners not rounded, button colours wrong)
- **Cause**: Using a stock `AlertDialog.Builder` without passing the project dialog theme.
- **Fix**: Use `ThemeChatGPTBlue_AndroidPopups` (alert-style) or wrap the layout in `RoundedDialog` for full custom dialogs.

---

## Calendar

### Day cell shows wrong colour / no event indicator
- **Cause**: `ColorResolver` not updated for a new entry type, or `MonthDayBinder` not refreshed when entries change.
- **Fix**: Walk through `CalendarItemMapper` → `ColorResolver` → `MonthDayBinder`. The binder reads the items map; recompose by calling `notifyMonthChanged` on the `CalendarView`.

### Week view shows literal text "EUREKA"
- **Cause**: Historical placeholder in an older `CalendarWeekAdapter`. The new code uses `WeekViewItemAdapter` + the vendored Alamkanak Week-View.
- **Fix**: Don't try to revive the old adapter. Wire data through `WeekViewItemAdapter`.

---

## Pomodoro timer

### Timer "fails" as soon as the user swipes away
- **Cause**: Working as designed — backgrounding for >4s triggers `failTimer()`. See `HomeFragment` background-detection block.
- **Fix**: If you're trying to test the success path, keep the app foregrounded. If you want to relax the rule, change the 4 s grace window in `HomeFragment`.

### Notification doesn't appear / foreground service dies
- **Cause**: Channel not created (Android O+) or `startForegroundService` not called before `startForeground` inside the service.
- **Fix**: `MainActivity` creates the channel — make sure it ran. The service then calls `startForeground(id, notification)` in `onStartCommand`.

### Stats not updating after a completed cycle
- **Cause**: `HomeViewModel.completeTimer(timerTime)` is being called from main thread, or with `timerTime` in millis when the DAO expects minutes.
- **Fix**: Background-thread the call, and double-check the units against `StatsDAO.addTimeStudied(...)`.

---

## Resources / themes

### Layout preview is broken in Android Studio but app runs fine
- **Cause**: Downloadable fonts (Plus Jakarta Sans / Fraunces) not cached locally yet.
- **Fix**: Sync project; preview falls back to system sans. No code change needed.

### TextView appears with default Roboto instead of project font
- **Cause**: `android:fontFamily` was set explicitly, overriding the `textAppearance` style.
- **Fix**: Remove `fontFamily`, use `android:textAppearance="@style/TextAppearance.BBetter.X"` only.

### Wrong colours everywhere on a screen
- **Cause**: Screen's theme parents on `Theme.MaterialComponents.*` directly instead of `Theme.BBetter`.
- **Fix**: Re-parent. See [`style_guide.md`](style_guide.md) §6.

---

## Adding to this list

When a future session burns time on a new failure mode, add an entry here with: symptom (verbatim error text or visible behaviour), cause (one line), fix (concrete steps or files). Keep entries terse — this file is read top-to-bottom.
