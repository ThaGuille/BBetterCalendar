# BBetter selector cheatsheet

The id/selector facts the agent needs so it never re-derives them each run.
Resource-ids below are the **suffix** after `:id/` — pass them bare to
`find-node.ps1 -Id <suffix>` (it matches `com.example.bbettercalendar:id/<suffix>`).

## App / device facts

| Thing | Value |
|---|---|
| applicationId (use for `am start -n`, `pm`, install) | `io.github.thaguille.bbettercalendar` |
| namespace (Java package + resource-id prefix `<ns>:id/…`) | `com.example.bbettercalendar` |
| Launcher activity | `.configuration.SplashActivity` (full: `io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity`) |
| Other activities | `com.example.bbettercalendar.MainActivity`, `.calendarEntries.AddEventActivity` (class names keep the namespace; `-n` uses the applicationId before the `/`) |
| Debug APK | `app\build\outputs\apk\debug\app-debug.apk` |
| Build cmd | `.\gradlew.bat assembleDebug` |
| Runtime permission to grant | `android.permission.POST_NOTIFICATIONS` (API 33+; granting avoids the on-screen dialog) |
| Emulator serial | `emulator-5554` |
| SDK root | `C:\Users\guill\AppData\Local\Android\Sdk` |
| Emulator binary | `C:\Users\guill\AppData\Local\Android\Sdk\emulator\emulator.exe` (`-list-avds`, `-avd <name> -no-window`) |

## Bottom navigation (MainActivity)

`navigation_home` · `navigation_progress` · `navigation_calendar_month` · `navigation_projects`

Tab labels (for `-Text` fallback): Home · Progress · Calendar · Projects.

`nav_view` (and `nav_divider`) is **only present on top-level destinations** (the 4 tabs +
Calendar week). On every secondary destination it is GONE → `find-node -Id nav_view` exits 1.

## Secondary destinations (u12)

| Destination id | Screen root id | Layout |
|---|---|---|
| `navigation_focus` | `focusScreen` | `fragment_focus.xml` |
| `navigation_cycle_complete` | `cycleCompleteScreen` | `fragment_cycle_complete.xml` |
| `navigation_all_progress` | `allProgressScreen` | `fragment_all_progress.xml` |
| `navigation_screen_time` | `screenTimeScreen` | `fragment_screen_time.xml` |
| `navigation_app_detail` (arg `packageName`) | `appDetailScreen` | `fragment_app_detail.xml` |
| `navigation_project_detail` (arg `projectId`) | — (`projectDetailBackButton`) | `fragment_project_detail.xml` |

Top bar of the secondary screens: `topBar` (MaterialToolbar); its back arrow has no id → tap it
with `find-node.ps1 -Text "Back"` (content-desc; "Atrás" in Spanish). System back (`input keyevent 4`)
does the same.

**Debug-only jump (debug APK):** open a secondary destination straight from adb, optionally
from a given tab so back has somewhere to go:

```
adb -s emulator-5554 shell am force-stop io.github.thaguille.bbettercalendar
adb -s emulator-5554 shell am start -n io.github.thaguille.bbettercalendar/com.example.bbettercalendar.configuration.SplashActivity --es bb_debug_nav app_detail --es bb_debug_from progress --es bb_debug_pkg com.android.chrome
```

`bb_debug_nav` = `focus|cycle_complete|all_progress|screen_time|app_detail`; `bb_debug_from` =
`home|progress|calendar|projects`. Force-stop first: with the app alive, the Splash launch stacks a
second MainActivity (known u6 quirk).

## Progress screen (`fragment_progress.xml`)

| id suffix | What | Readable from dump? |
|---|---|---|
| `text_dashboard` | Header ("Progress") | text |
| `chart_pager` | ViewPager2 chart carousel | container only |
| `chart_dots` | TabLayout dot indicator | — |
| `granularity_day` / `granularity_week` / `granularity_month` | Day/Week/Month segmented toggle | text; **selected state NOT reliably in dump** |
| `range_prev` | ‹ stepper back | content-desc |
| `range_label` | Current range label (e.g. "This week") | **text — primary assertion target** |
| `range_next` | › stepper forward | content-desc |
| `chart_card_label` | Per-card chart title (inside `item_chart_card.xml`) | text |
| `chart_container` | FrameLayout the MPAndroidChart draws into | **canvas — invisible to dump** |

## Assertion guidance

- **Verify the *effect*, not the visual.** After tapping `granularity_month` or
  `range_prev`, assert `range_label` **text** changed — don't try to read which segmented
  button looks selected (it's a background-drawable swap, not a `checked` attribute).
- **Chart correctness is out of band.** `chart_container` / chart data points are
  canvas-drawn and absent from the XML. Cover chart *data* with JUnit on
  `ProgressViewModel`; this UI layer only confirms the screen builds without crashing.
  To eyeball that a chart actually *rendered*, grab a PNG with
  `scripts\capture-screen.ps1 -Label <name>` and Read it (visual escape hatch — charts only).
- **Crashes** surface in `adb -s emulator-5554 logcat -d -b crash` as `FATAL EXCEPTION`.
