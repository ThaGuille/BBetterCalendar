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

## Home (u13, `fragment_home.xml`)

| Id | What |
|---|---|
| `homePlayButton` | Idle: start session + open Focus (desc "Start timer"). Session alive: opens Focus (desc "Open focus session") |
| `homeTimerCard` | Clickable only while a session is alive → Focus |
| `homeTimerText` / `homeTimerTypeText` | Countdown / mode chip text |
| `homeCycleChip` (`homeCycleText`) | "Cycle x of N · Next: …" → timer settings popup |
| `homeStreakButton` (`homeStreakWeek`) | Streak card → `FocusStreakPopup` |
| `homeFriendsCard` | Social mock block (debug only) |
| `homeAddTaskButton`, `homeTodayTaskList`, `homeShowOverdueButton`, `homeOverdueTaskList` | Tasks card |
| row ids: `taskDoneCheckbox`, `taskTitleText`, `taskProjectChip`, `taskTimeText`, `taskFocusButton`, `taskRemoveButton` | Task rows |
| `homeNextUpCard` (`homeNextUpText`) | Next event → Calendar tab |
| `homeBellButton` | Placeholder (toast); inside a FrameLayout since u13b (same id) |

Removed in u13 (moved to Focus in u14, see below): `homeCancelButton`, `homeSkipRestButton`, `homeBlockModeButton`,
`homeFailsChip`, `homeTimerSettingsButton`, `homeFocusBanner`, `homeStreakCount`, `homeTodayTimeText`.

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

## Progress screen (`fragment_progress.xml`, u17 + u18)

- `progressTitle`, `progressShieldButton`, `progressShareButton`, `progressScroll`, `progressGranularity`, `progressPeriodNavigator` (hijos `periodNavTitle/Subtitle/Previous/Next`).
- Tiles: `progressTileFocus`, `progressTileSessions`, `progressTileTasks`.
- Tendencia: `progressTrendValue`, `progressTrendDelta`, `progressTrendChartHolder`; `progressSeeAll`.
- Bloqueo: `progressBlockingCard`, `progressBlockingSubtitle`, `progressBlockingBar`, `progressBlockingChips`, `progressManageBlocking`; proyectos: `progressProjectsCard`, `progressProjectsRows`.
- Retirados en u17: `granularity_day/week/month`, `range_prev/next/label` (el viejo `range_label` ya no existe: la aserción de rango es `periodNavTitle`), `projects_band`, `projects_list`.
- Retirados en u18 (Progress ya no tiene carrusel ni controles de uso): `progressLegacyCharts`, `chart_dots`, `chart_pager`, `usage_add_apps`, `usage_enforce_master`, `usage_block_toggle(_chip)`.
- Banda de uso (solo resume, u18): `usage_band`, `usage_screen_time`, `usage_list`, `usage_loading`, `usage_state_card`.

## Focus (u14)

Focus (`fragment_focus.xml`, destino `navigation_focus`, raíz `focusScreen`):

| id | Qué |
|---|---|
| `topBar` | Barra superior (flecha «Back» = navigateUp, sin cancelar) |
| `focusBlockModeButton` | Acción 🚫 de la barra; content-desc «Block mode: off/on/needs permission. …» |
| `focusModeChip` | «CONCENTRATION · x OF N» / «REST · x OF N» |
| `focusCycleDots` | Puntos de ciclo (content-desc «Cycle x of N») |
| `focusRing` | `CircularProgressIndicator` (restante) |
| `focusTimerText` | mm:ss |
| `focusEndsAtText` (`focusEndsAtRow`, `focusEndsAtIcon`) | «Ends at …» / «Paused» / «Ready when you are» |
| `focusStopButton` | Stop (desc «Stop focus session»; deshabilitado sin sesión) |
| `focusPlayButton` | desc «Start focus» / «Pause focus timer» / «Resume focus timer» |
| `focusSkipButton` | desc «Skip rest» en descanso, «Skip rest (only during a rest)» si no |
| `focusStatsCard` (`focusTodayColumn`, `focusStreakColumn`) | Tira Today / Streak |
| `focusTodayText`, `focusFailsText`, `focusStreakText` | Tiempo de hoy, «N missed sessions», «N days» |
| `focusSocialBlock`, `focusStudyingHeader` (`sectionHeaderMeta` = «5 / 8»), `focusFriendsGrid` | Maqueta social |
| celdas: `focusFriendAvatar`, `focusFriendName`, `focusFriendTime` | Una por amigo (`item_focus_friend.xml`) |
| `focusInviteButton` | «Invite friends to study together» (pie; toast) |

Notas para los flujos: con el temporizador corriendo `uiautomator dump` falla a menudo con «could not
get idle state» y puede devolver un árbol viejo; repetir, o tocar por coordenadas (los controles no se
mueven). El quirk de la 2.ª `MainActivity` con `am start -n …SplashActivity` sigue existiendo (ese
intent no lleva `MAIN`/`LAUNCHER`): sigue haciendo falta `am force-stop` antes de la entrada debug.

## Cycle complete (u15)

Cycle complete (`fragment_cycle_complete.xml`, destino `navigation_cycle_complete`, raíz `cycleCompleteScreen`):

| id | Qué |
|---|---|
| `cycleCloseButton` | Pastilla X (igual que Continue) |
| `cycleTitle`, `cycleSummary`, `cycleTaskChip` | Título, "You finished N Pomodoros", chip de tarea |
| `cycleFocusTile` (`cycleFocusValue`, `cycleFocusSub`) | Tile Focus |
| `cycleStreakTile` (`cycleStreakValue`, `cycleStreakSub`, `cycleStreakBadge`) | Tile Streak |
| `cycleBestTile` (`cycleBestValue`, `cycleBestSub`, `cycleBestGap`) | Tile Best (social) |
| `cycleFriendsStrip` (`cycleAvatar1..3`, `cycleFriendsText`) | Franja de amigos (social) |
| `cycleContinueButton`, `cycleShareButton` | Acciones |

Salto debug: `am start -n ...SplashActivity --es bb_debug_nav cycle_complete`.

## Projects (u16)

- Lista: `projectsList`, `projectsAddButton`, `projectsSummaryText`, `projectsStreakChip`, `projectsRhythmCard`.
- Tarjeta: `projectCard`, `projectHeader` (tap = desplegar, long-press = detalle), `projectNameText`, `projectDeadlineChip`,
  `projectTasksText`, `projectPercentText`, `projectCardProgress`, `projectPomodorosToday`, `projectEstRemaining`,
  `projectSubtasksContainer`, `projectAddSubtask`, `projectOpenDetail`; subtarea: `projectSubtaskCheck/Title/Meta`.
- Cambiados: `projectProgressBar`/`projectProgressText` ya no existen en `item_project.xml`.

## All progress (u18)

- All progress: `allProgressScreen`, `allProgressGranularity`, `allProgressPeriodNavigator` (`periodNavTitle/Subtitle/Previous/Next`), `chart_dots`, `chart_pager`.

## Screen time (u19)

- `screenTimeScreen`, `topBar`, `screenTimeScroll`, `screenTimeTileTracked`, `screenTimeTileLimit`, `screenTimeTileBlocked`, `screenTimeBlockedCount`.
- `screenTimeHourCard`, `screenTimeHourChartHolder`; `screenTimeManageCard`, `screenTimeProtected`, `screenTimeBlockNew`, `screenTimeAppRows` (filas `appUsageName/Text/Bar/Lock/Chevron`), `screenTimeAppsEmpty`.
- `screenTimeShieldCard`, `screenTimeShieldSwitch`, `screenTimeFooter`, `screenTimeFooterText`.

## App detail (u20)

- `appDetailScreen`, `appDetailHeader`, `appDetailBack`, `appDetailIcon`, `appDetailName`, `appDetailSubtitle`, `appDetailScroll`, `appDetailPeriod`.
- `appDetailTileToday`, `appDetailTileAvg`, `appDetailTileBlocked`, `appDetailBlockedCount`.
- `appDetailChartCard`, `appDetailChartTitle`, `appDetailChartHolder`.
- `appDetailLimitCard`, `appDetailLimitSubtitle`, `appDetailLimitBadge`, `appDetailStepper` (`stepperMinus/stepperPlus/stepperValue`), `appDetailPresets`, `appDetailPreset30/45/60/None`.
- `appDetailProtectCard`, `appDetailWarnRow`, `appDetailWarnValue`, `appDetailBlockSwitch`, `appDetailA11yStatus/Dot/Text`, `appDetailStopTracking`.

## Popups (u21)

`okButton`, `popup_description_edit_text`, `streakCloseButton`, `firstFocusButton`, `btn_save`, `btn_cancel`; abrir: `homeCycleChip` (timer), `homeStreakButton` (streak), `quickAddRepeatButton`/`task_repetition_layout`, `task_notifications_layout`, `task_description_layout` (tras `quickAddMoreOptionsButton`).

## Calendar month/week and forms (u22)

- Mes: `calendarModeMonth`, `calendarModeWeek`, `monthGridCard`, `monthPrevButton`, `monthNextButton`, `monthTitleText`, `monthCalendarView`, `legendDay0..6`, `dayDetailTitle`, `dayDetailEmptyText`, `dayDetailRecycler`, `calendarAddEventButton`; celda: `cellDayText`, `cellTodayIndicator`, `cellEventBar1..3`; item: `eventColorSwatch`, `eventTitle`, `eventDescription`, `eventTime`.
- Semana: `calendarWeekRoot`, `weekMonthTitleText`, `weekView` (canvas, sin hijos accesibles por id), `calendarAddEventButton2`.
- Formularios: `btnClose`, `btnSaveEvent`, `switch_to_event_button`, `switch_to_task_button`, `notificationsButton`, `event_title`, `event_start_date/hour`, `event_end_date/hour`, `task_title`, `task_start_date/hour`, `task_duration`.
- Hoja rapida (abre `homeAddTaskButton`): `quickAddTitleInput`, `quickAddTimeButton`, `quickAddRepeatButton`, `quickAddTargetInput`, `quickAddMoreOptionsButton`, `quickAddSaveButton`.

## Splash, AppPicker, block cover (u23)

- Sin ids nuevos. Existentes: `splash_logo`, `app_picker_close`, `app_picker_save`, `app_picker_list`, `app_pick_icon/label/check`, `block_cover_app_label/title/subtitle/close`.

## Home additions (u13b)

- Sin ids nuevos. `homeBellButton` ahora esta dentro de un FrameLayout (mismo id).

## Assertion guidance

- **Verify the *effect*, not the visual.** After tapping a Week/Month/Year segment or
  `periodNavPrevious`, assert `periodNavTitle` **text** changed — don't try to read which segmented
  button looks selected (it's a background-drawable swap, not a `checked` attribute).
- **Chart correctness is out of band.** `chart_container` / chart data points are
  canvas-drawn and absent from the XML. Cover chart *data* with JUnit on
  `ProgressViewModel`; this UI layer only confirms the screen builds without crashing.
  To eyeball that a chart actually *rendered*, grab a PNG with
  `scripts\capture-screen.ps1 -Label <name>` and Read it (visual escape hatch — charts only).
- **Crashes** surface in `adb -s emulator-5554 logcat -d -b crash` as `FATAL EXCEPTION`.
