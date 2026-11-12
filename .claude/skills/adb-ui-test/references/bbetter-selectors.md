# BBetter selector cheatsheet

The id/selector facts the agent needs so it never re-derives them each run.
Resource-ids below are the **suffix** after `:id/` — pass them bare to
`find-node.ps1 -Id <suffix>` (it matches `com.example.bbettercalendar:id/<suffix>`).

**Last verified:** 2026-11-11 (mejora-visual u17: ids of u6-u16 screens walked by the mv-tour recording).

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
Calendar week). On every secondary destination it is hidden → `find-node -Id nav_view` exits 1.
Since mejora-visual u15 it overlays the navigation host and fades out to INVISIBLE (it stays laid out, so hiding it
never resizes the screens mid-transition); the tabs reserve its height with a bottom margin on their root. Every tab
keeps its scroll position (saved back stacks): a flow that needs the top of a tab swipes down first (see
`recordings/mv-u15-tour.log`).

## Home (u13, rehecha en mejora-visual u6, `fragment_home.xml`)

| Id | What |
|---|---|
| `homePlayButton` | `RingButtonView` (canvas: no text in the dump). Idle: start session + open Focus (desc "Start timer"). Session alive: opens Focus (desc "Open focus session") |
| `homeTimerCard` | Clickable only while a session is alive → Focus (`homeTimerHalo` = amber glow inside) |
| `homeTimerText` / `homeTimerTypeText` | Countdown / chip text «Concentración · 1 de 3» |
| `homeCycleChip` | The chip above the countdown (dot + `homeTimerTypeText`) → timer settings popup |
| `homeCycleText` | Under the ring: «Ahora: <tarea>» / «Siguiente: <tarea>»; GONE without a task |
| `homeStreakButton` (`homeStreakValue`, `homeStreakStrip`, `homeStreakGoal`) | Streak card «Racha / 2 semanas / 7 pills / 2 de 3 días» → `FocusStreakPopup` |
| `homeFriendsCard` (`homeFriendsLabel`, `homeFriendsAvatars`, `homeFriendsTitle`, `homeFriendsNote`, `homeFriendsJoin`) | Friends card; signed out = join call |
| `homeTasksCount`, `homeTasksProgress`, `homeTasksSkeleton`, `homeTasksEmpty` | «2 de 5 tareas», bar, skeleton until the first emission, `EmptyStateView` (action = quick add) |
| `homeAddTaskButton`, `homeTodayTaskList`, `homeShowOverdueButton`, `homeOverdueTaskList` | Tasks card |
| row ids: `taskRow`, `taskDoneCheckbox`, `taskTitleText`, `taskTimeText`, `taskFocusButton`, `taskRemoveButton` | Task rows; `taskTimeText` = «25 / 60 min · Proyecto», «18:00 · Proyecto» or «Hecha»; `taskFocusButton` = small `RingButtonView` |
| `homeNextUpCard` (`homeNextUpLabel`, `homeNextUpText`) | «A continuación · 16:30» + title → Calendar tab |
| `homeBellButton` | Placeholder (toast); inside a FrameLayout since u13b (same id) |

Removed in u13 (moved to Focus in u14, see below): `homeCancelButton`, `homeSkipRestButton`, `homeBlockModeButton`,
`homeFailsChip`, `homeTimerSettingsButton`, `homeFocusBanner`, `homeStreakCount`, `homeTodayTimeText`.
Removed in mejora-visual u6: `homeModeChip` (the chip is `homeCycleChip`), `homeStreakWeek`, `homeFriendsDot`,
`homeFriendsNames` (names now in the avatars row content-desc), `homeTasksEmptyText` (now `homeTasksEmpty`),
`taskDivider`, `taskMetaRow`, `taskProgressText`, `taskProjectChip` (all in `taskTimeText`).
Recording: `mv-u6-home` (rest, tasks card, start, Focus, Home running, pause, resume, stop).

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
- Resumen (u7): `progressSummaryCard`, `progressSummarySkeleton`, `progressSummaryContent`, `progressSummaryFocus`, `progressSparkBars`, `progressSummarySessions`, `progressSummaryTasks` (replace `progressTileFocus/Sessions/Tasks`).
- `progressSeeAll`. The "Past 12 weeks" card (`progressTrend*`) moved to All progress (u8).
- Tus apps (u7): `progressAppsCard` (whole card opens `navigation_screen_time`), `progressAppsSubtitle`, `progressAppsSkeleton`, `progressAppsRows` (rows from `item_progress_app_row.xml`: `progressAppIcon`, `progressAppName`, `progressAppLock`, `progressAppSuggested`, `progressAppValue`, `progressAppBar`), `progressAppsHint`; replaces `progressBlocking*` and `progressManageBlocking`. Projects: `progressProjectsCard`, `progressProjectsRows`.
- Retirados en u17: `granularity_day/week/month`, `range_prev/next/label` (el viejo `range_label` ya no existe: la aserción de rango es `periodNavTitle`), `projects_band`, `projects_list`.
- Retirados en u18 (Progress ya no tiene carrusel ni controles de uso): `progressLegacyCharts`, `chart_dots`, `chart_pager`, `usage_add_apps`, `usage_enforce_master`, `usage_block_toggle(_chip)`.
- Banda de uso (solo resume, u18): `usage_band`, `usage_screen_time`, `usage_list`, `usage_loading`, `usage_state_card`.

## Focus (u14)

Focus (`fragment_focus.xml`, destino `navigation_focus`, raíz `focusScreen`):

| id | Qué |
|---|---|
| `topBar` | Barra superior (flecha «Back» = navigateUp, sin cancelar) |
| `focusBlockModeButton` | Acción 🚫 de la barra; content-desc «Block mode: off/on/needs permission. …» |
| `focusModeChip` (`focusModeChipRow`, `focusModeDot`) | «CONCENTRATION · x OF N» / «REST · x OF N» / «PAUSED · x OF N» |
| `focusCycleDots` | Puntos de ciclo (content-desc «Cycle x of N») |
| `focusRingBox` | Caja del anillo (anillo + restante + «Termina a las»); elemento compartido con `homePlayButton` en la transición Home → Focus (mejora-visual u15) |
| `focusRing` | `FocusRingView` (restante; canvas, invisible en el dump) |
| `focusTimerText` | mm:ss |
| `focusEndsAtText` (`focusEndsAtRow`, `focusEndsAtIcon`) | «Ends at …» / «Paused» / «Ready when you are» |
| `focusStopButton` | Stop (desc «Stop focus session»; deshabilitado sin sesión) |
| `focusPlayButton` | `RingButtonView` since mejora-visual u6 (canvas, no text); desc «Start focus» / «Pause focus timer» / «Resume focus timer» |
| `focusSkipButton` | desc «Skip rest» en descanso, «Skip rest (only during a rest)» si no |
| `focusStatsCard` (`focusTodayColumn`, `focusStreakColumn`) | Tira Today / Streak |
| `focusTodayText`, `focusFailsText`, `focusStreakText` | Tiempo de hoy, «N missed sessions» (GONE con 0), «N days» |
| `focusRestHint` | «Stretch, drink some water.» (solo en descanso) |
| `focusSocialBlock`, `focusStudyingHeader` (`focusStudyingMeta` = «5 / 8»), `focusFriendsGrid` | Maqueta social |
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
| `cycleCelebration` | `CelebrationView` (anillo + brote + destello, una vez por entrada) |
| `cycleTitle`, `cycleSummary`, `cycleTaskChip` | Título, "4 pomodoros · 1 h 40 m de enfoque", chip de tarea |
| `cycleFocusTile` (`cycleFocusValue`, `cycleFocusSub`) | Celda «Hoy» (valor + «Tu mejor marca») |
| `cycleStreakTile` (`cycleStreakValue`, `cycleStreakSub`) | Celda «Racha» (u13: sin `cycleStreakBadge`) |
| `cycleFriendsStrip` (`cycleAvatar1..3`, `cycleFriendsText`) | Franja de amigos (social) |
| `cycleContinueButton`, `cycleShareButton` | Acciones |

u13: hoja breve `celebrationSheetMark/Title/Message/Button` (`CelebrationSheet`) al cumplir el objetivo semanal (al pulsar Continuar, una vez por semana ISO) y al completar un proyecto (una vez por id).

Salto debug: `am start -n ...SplashActivity --es bb_debug_nav cycle_complete`.

## Projects (u11, mejora-visual)

- Lista: `projectsList`, `projectsAddButton` (FAB extendido "Nuevo proyecto"), `projectsSummaryText`, `projectsStreakChip`,
  `projectsEmptyState` (EmptyStateView), `projectsCompletedHeader` (+ `projectsCompletedTitle`, `projectsCompletedChevron`) que despliega `projectsCompletedList`.
- Tarjeta (`item_project.xml`, dos zonas): `projectCard`; **`projectCardTop`** (tap = abre el detalle; ya no hay pulsacion larga) con `projectGlow`,
  `projectRing` (ProgressRingView), `projectNameText`, `projectTasksText` ("4 de 9 tareas · 6 h 20 m"), `projectDeadlineChip`, `projectChevron`,
  `projectNextRow` (`projectNextTitle`, `projectFocusButton` "Enfocar"); **`projectCardStrip`** (ExpandStripView: tap = solo despliega, no navega;
  `expandStripTitle` = "5 tareas pendientes", `expandStripAction` = Ver/Ocultar) cuyo contenido son filas `item_project_subtask.xml`
  (`projectSubtaskCheck/Title/Meta`, `projectSubtaskFocus` = play pequeno) y `projectAddSubtask` ("Anadir tarea").
- Retirados en u11: `projectHeader`, `projectColorAccent`, `projectDotRing`, `projectPercentText`, `projectCardProgress`, `projectPomodoroRow`,
  `projectPomodorosToday`, `projectEstRemaining`, `projectExpandIcon`, `projectExpandedBody`, `projectSubtasksContainer`, `projectOpenDetail`,
  `projectsRhythmCard`, `projectsEmptyText`.
- Detalle (`fragment_project_detail.xml`): `projectDetailBackButton`, `projectDetailMenuButton` (menu: completar, eliminar), `projectDetailRing`,
  `projectDetailNameInput`, `projectDetailDeadlineChip` (siempre visible; abre el selector de fecha), `projectDetailFocusTile` ("Enfocado"),
  `projectDetailTasksTile` ("4 de 9" + "Quedan unas 5 h"), `projectDetailNotesInput`, `projectDetailItemsList` (filas `projectItemDoneCheckbox`,
  `projectItemTitleText`, `projectItemProgressText`, `projectItemFocusButton`), `projectDetailItemsEmptyText`, alta rapida fija
  `projectDetailQuickAddInput` + `projectDetailAddItemButton` ("+", pulsacion larga = hoja completa).
- Retirados en u11: `projectDetailDeadlineButton`, `projectDetailCompleteButton`, `projectDetailDeleteButton`, `projectItemDateText`.

## All progress (u18)

- All progress: `allProgressScreen`, `allProgressGranularity`, `allProgressPeriodNavigator` (`periodNavTitle/Subtitle/Previous/Next`), `allProgressScroll`, and the 8 cards `allProgressCardFocusByDay`, `allProgressCardBestHours`, `allProgressCard12Weeks`, `allProgressCardConsistency`, `allProgressCardSessions`, `allProgressCardProjects`, `allProgressCardDurations`, `allProgressCardFocusVsPhone` (hidden without usage access); inside each card (same ids in all 8, find them under the card) `chartCardTitle/Subtitle/Figure/Unit/Delta/Chart/Legend/Sentence`. Removed in mejora-visual u8: `chart_dots`, `chart_pager`.

## Screen time (u19)

- `screenTimeScreen`, `topBar`, `screenTimeScroll`.
- (u9) `screenTimeShieldCard`, `screenTimeShieldSwitch`, `screenTimeAllowedRow` (`screenTimeAllowedCount`, `screenTimeAllowedEdit`); `screenTimePeriod` (Semana / Mes); `screenTimeUsageCard` (`screenTimeUsageSubtitle`, `screenTimeAvgValue`, `screenTimeAvgDelta`, `screenTimeAppRows` with rows `appUsageIcon/Name/LockIcon/Suggested/Text/Bar`, `screenTimeAppsEmpty`, `screenTimeUsageLegend`, `screenTimeBlockNew` = "Limitar otra app"); `screenTimeDailyCard` (`screenTimeDailyChart`, `screenTimeDailyLegend`, `screenTimeDailySentence`); `screenTimeBeforeAfterCard` (`screenTimeBeforeAfterRows`, `screenTimeBeforeAfterEmpty`, `screenTimeBeforeAfterSentence`); `screenTimeAllowedCard` (`screenTimeAllowedChips`, `screenTimeAllowedAdd`). (u10) `screenTimeAllowedRow` and `screenTimeAllowedAdd` open the picker in allowlist mode; `screenTimeAllowedChips` holds one Chip per allowed app (no id; text = app name, desc "<app>, siempre permitida..."; close icon or long press removes it).
- `screenTimeFooter`, `screenTimeFooterIcon`, `screenTimeFooterText`.

## App detail (u20)

- `appDetailScreen`, `appDetailHeader`, `appDetailBack`, `appDetailIcon`, `appDetailName`, `appDetailSubtitle`, `appDetailScroll`, `appDetailPeriod`.
- `appDetailTileToday`, `appDetailTileAvg` (the blocked-opens tile was removed in u9).
- `appDetailChartCard`, `appDetailChartTitle`, `appDetailChart` (CompareBarsView, replaces `appDetailChartHolder`).
- `appDetailLimitCard`, `appDetailLimitSubtitle`, `appDetailLimitBadge`, `appDetailStepper` (`stepperMinus/stepperPlus/stepperValue`), `appDetailPresets`, `appDetailPreset30/45/60/None`.
- `appDetailProtectCard`, `appDetailWarnRow`, `appDetailWarnValue`, `appDetailBlockSwitch`, `appDetailA11yStatus/Dot/Text`, `appDetailStopTracking`.

## Popups (u21)

`okButton`, `popup_description_edit_text`, `streakCloseButton`, `firstFocusButton`, `btn_save`, `btn_restore`, `repetition_popup_confirm`, `btnSaveNotificationsPopup`; mejora-visual u14: Timer, Streak, Notifications, Repetition and Description are bottom sheets (handle, drag to dismiss, main button at the bottom); the timer has chips `chip_preset_25/50/90` («25 / 5», «50 / 10», «90 / 15»), `slider_timer`, `slider_rest`, `stepper_cycles` (a StepperView: `stepperMinus/stepperPlus/stepperValue`), `toggle_infinite_cycles`, `switch_rest_enabled`, `switch_auto_cycle`; `btn_cancel` and `btn_add_cycles`/`btn_subtract_cycles`/`text_cycles` were removed; abrir: `homeCycleChip` (timer), `homeStreakButton` (streak), `quickAddRepeatButton`/`task_repetition_layout`, `task_notifications_layout`, `task_description_layout` (tras `quickAddMoreOptionsButton`).

## Calendar month/week and forms (u22)

- Mes: `legendTask`, `legendEvent`, `legendDeadline`, `legendFocus` (pairs inside `calendarLegend`), `calendarModeMonth`, `calendarModeWeek`, `monthGridCard`, `monthPrevButton`, `monthNextButton`, `monthTitleText`, `monthCalendarView`, `legendDay0..6`, `calendarLegend`, `calendarAgendaCard`, `dayDetailTitle`, `dayDetailSubtitle`, `dayDetailEmptyText` (EmptyStateView; su botón `emptyStateAction`), `dayDetailRecycler`, `calendarAddEventButton` (el «+» de la tarjeta, ya no es FAB); celda: `cellDayText`, `cellTodayIndicator`, `cellFocusBg`, `cellDots`, `cellDot1..3` (antes `cellEventBar1..3`); item: `eventColorSwatch`, `eventTitle`, `eventMeta` (antes `eventDescription`), `eventTime`.
- Semana: `calendarWeekRoot`, `weekMonthTitleText`, `weekView` (canvas, sin hijos accesibles por id), `calendarAddEventButton2`.
- Formularios: `btnClose`, `btnSaveEvent` (disabled until there is a title and, in an event, the end is not before the start), `form_type_selector` (SegmentedControlView Evento/Tarea/Recordatorio; replaces `switch_to_event_button`/`switch_to_task_button`/`notificationsButton`, tap its items by text), `event_title`, `event_start_label`/`event_start_date`/`event_start_hour`, `event_end_label`/`event_end_date`/`event_end_hour`, `task_title`, `task_start_label`/`task_start_date`/`task_start_hour`, `task_duration`, shortcuts `form_shortcut_today`/`form_shortcut_tomorrow`/`form_shortcut_plus_hour`, inline error `form_error`.
- Hoja rapida (abre `homeAddTaskButton`; u14: handle, shortcuts `quickAddShortcutToday/Tomorrow/PlusHour`, save disabled until a title): `quickAddTitleInput`, `quickAddTimeButton`, `quickAddRepeatButton`, `quickAddTargetInput`, `quickAddMoreOptionsButton`, `quickAddSaveButton`.

## Splash, AppPicker, block cover (u23)

- Sin ids nuevos. Existentes: `splash_logo`, `app_picker_close`, `app_picker_save`, `app_picker_list`, `app_pick_icon/label/check`, `block_cover_app_label/title/subtitle/close`; u14 adds `block_cover_ring` (ProgressRingView usage vs limit, hidden in focus mode), `block_cover_app_icon`, `block_cover_note` («Vuelve a estar disponible mañana» / «Termina tu pomodoro para usarla»).
- (mejora-visual u10) `app_picker_title` ("Añadir apps" / "Apps siempre permitidas" with extra `mode=allowlist`), `app_pick_suggested` ("Sugerida" badge, first allowlist opening only). Block cover: uiautomator does not see the accessibility overlay, and every uiautomator dump rebinds accessibility services (the service clears `FocusBlockState` on reconnect): verify the shield with `screencap` + `input tap` only.

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
