# Tasks — project-deadlines-progress

## A. Deadline reminders

- [x] `NotificationChannels`: add `CHANNEL_PROJECT_DEADLINES = "bb_project_deadlines"`
      (`IMPORTANCE_HIGH`) + create it in `createAll()`; strings for name/desc.
- [x] New `notifications/project/ProjectDeadlineScheduler` (`@Singleton`, `@Inject` ctor with
      `@ApplicationContext` + `AlarmManager`): offsets `{3d, 1d}`, namespace
      `500_000 + projectId * 10 + offsetIndex`, extras `EXTRA_PROJECT_ID` / `EXTRA_OFFSET_INDEX`.
      `scheduleFor(Project)` no-ops when `softDeadlineMillis <= 0` or status != ACTIVE;
      `cancelFor(int projectId)`.
- [x] New `notifications/project/ProjectDeadlineReceiver` (`@AndroidEntryPoint`, `goAsync()` +
      `@IoExecutor`): re-read the project, drop if missing / not ACTIVE / deadline changed or
      already passed; else notify via `BBetterNotifier` with id
      `70_000 + projectId * 10 + offsetIndex`.
      *(La fórmula del id vive en `ProjectDeadlineScheduler.notificationIdFor` junto a la de request
      codes: los dos espacios de ids se leen de un vistazo y el test no carga el receiver.)*
- [x] Register the receiver in `AndroidManifest.xml` (`exported="false"`).
- [x] `NotificationSpec.Builder.openProjectDetail(context, projectId)` — sibling of
      `openMainActivity()`, same flags plus `MainActivity.EXTRA_OPEN_PROJECT_ID`.
- [x] `MainActivity`: `EXTRA_OPEN_PROJECT_ID` constant; consume it in `onCreate()` (after the
      NavController is resolved) **and** in a new `onNewIntent()` (`setIntent()` first); navigate
      via `action_global_project_detail`; `removeExtra` once consumed so rotation doesn't re-navigate.
- [x] Strings: notification title + body format (days-left wording for the 3d / 1d offsets).
- [x] `ProjectsRepository.createProject()` — schedule after insert (re-read the inserted row's id).
- [x] `ProjectDetailViewModel`: `updateDeadline()` cancel+reschedule, `completeProject()` cancel,
      `deleteProject()` cancel — all inside the existing `@DbWriteExecutor` blocks.
- [x] `BootReceiver`: inject `ProjectDAO` + `ProjectDeadlineScheduler`, re-arm alarms for active
      projects with a future deadline (same `goAsync()` block as the entry reminders).

## B. Calendar deadlines

- [x] `ProjectDAO.observeDeadlinesBetween(long start, long end)` — active only, deadline in range.
- [x] `CalendarItem.Type`: add `DEADLINE`; `ColorResolver` case; `calendar_item_deadline` in
      `colors.xml` aliasing `bb_accent_reward`.
- [x] New `ui/calendar/domain/ProjectDeadlineItemMapper`: `Project` → `CalendarItem`,
      `start = end = softDeadlineMillis`, `id = -project.id`; skips `softDeadlineMillis <= 0`.
      Encode y decode (`projectIdOf`) viven juntos ahí.
- [x] `CalendarViewModel`: inject `ProjectDAO`, merge entries + deadlines via `MediatorLiveData`
      inside the existing `switchMap` on `DateRange`, sorted by `startMillis`. No `refresh()`.
- [x] Nav graph: root-level **global** action `action_global_project_detail` → `navigation_project_detail`.
- [x] `CalendarFragmentMonth`: `dayDetailAdapter.setOnItemClickListener(...)` → for
      `Type.DEADLINE`, navigate via the global action with `projectId = -item.getId()`;
      other types stay no-op.
- [x] Check the week view renders the zero-length deadline chip acceptably.
      **Verificado sin cambios**: `WeekViewItemAdapter.onCreateEntity` ya infla a 1 h cualquier item
      con `end <= start` ([WeekViewItemAdapter.java:28-30](../../../../app/src/main/java/com/example/bbettercalendar/ui/calendar/binders/WeekViewItemAdapter.java#L28-L30)),
      que es exactamente el trato que reciben hoy los recordatorios. El id negativo tampoco choca:
      los ids de entrada son positivos.

## C. Progress chart

- [x] `stats/ProjectMinutes` projection (`projectId`, `projectName`, `minutes`) — feeds chart *and* band.
- [x] `FocusEventDAO.getMinutesByProject(start, end)` — join `focus_event ⨝ calendarEntry ⨝
      project`, `type = 0 AND entryId != 0`, grouped by project, ordered desc.
- [x] `ChartBundle`: `projectLabels` + `projectMinutes` arrays (no chart-lib types).
- [x] `ProgressViewModel.buildBundle()`: fill them from the new query (already on the executor).
- [x] `ChartCarouselAdapter`: projects bar page — `PAGE_COUNT 3→4`, `PAGE_COUNT_DAY 2→3`, position
      mapping for both modes, `labelFor()` string, `bb_primary` bars, project names on the X axis.

## D. Progress project band

- [x] `ui/progress/ProjectProgressRow` — projectId, name, colorIndex, done/total, `percent()`,
      minutesInRange, `ProjectDeadlineState`. Guard `totalCount == 0` like `ProjectListItem`.
- [x] `res/layout/item_project_progress.xml` — name, % bar, `X/Y items`, minutes, deadline chip;
      `bb_*` + `TextAppearance.BBetter.*` only (rule #2).
- [x] `ui/progress/ProjectProgressAdapter` — rows + click callback; accent from
      `ProjectListAdapter.ACCENT_COLORS` via `colorIndex` (expuesto como
      `ProjectListAdapter.accentColorResFor(int)` para que el ciclado de la paleta viva en un
      único sitio).
- [x] `ProgressViewModel`: inject `ProjectsRepository`; `MediatorLiveData` merging
      `observeAllWithCounts()` with a `MutableLiveData<Map<Integer,Integer>>` of range minutes
      posted from `applyRange()`'s executor pass. Membership: active **or** has minutes in range;
      sort by minutes desc, then name.
- [x] `fragment_progress.xml`: band section between the carousel and the usage band. **No
      `android:id` on any `<include>`** ([[include-id-overrides-root-id]]); keep the
      `NestedScrollView` + `wrap_content` + `nestedScrollingEnabled=false` pattern intact.
      *(La banda se añadió inline, sin `<include>`, así que la trampa del id ni se roza.)*
- [x] `ProgressFragment`: observe with `getViewLifecycleOwner()`, hide heading + list when empty,
      row tap → global action to project detail.

## E. Tests + verification

- [x] JUnit: `ProjectDeadlineItemMapperTest` — negative-id encode/decode round-trip, zero-deadline
      skipped, zero-length span. (7 tests, verde)
- [x] JUnit: request-code + notification-id namespaces are disjoint from the entry-reminder space
      for a realistic id range. (`ReminderNamespaceTest`, 5 tests, verde — compara contra las
      fórmulas reales, no contra copias: por eso `EventReminderScheduler.requestCodeFor` y
      `EventReminderReceiver.notificationIdFor` pasaron a públicas.)
- [x] **Extra (no previsto):** `ProjectDeadlineStaleAlarmTest` — 10 tests sobre
      `ProjectDeadlineReceiver.shouldNotify`, la guarda que decide si una alarma vieja llega a
      notificar (completado / archivado / borrado / deadline movido / ya pasado / disparo tardío).
      Es la lógica que, si falla, notifica a destiempo en silencio; no tenía cobertura.
- [x] `/check` (build + lint). `assembleDebug` ✅, `lintDebug` ✅ (sólo un aviso informativo de
      overdraw en `item_project_progress.xml`, el mismo que ya emiten `item_day_event.xml` y
      `item_app_usage_row.xml`), `test` ✅ 42/42.
- [x] `ui-tester` pass (rule #7) — **PASS, 0 `FATAL EXCEPTION`** en todo el recorrido. Corrió sobre
      el teléfono físico `84396e95` (Realme RMX3851), no sobre el emulador.
      - Carrusel: 4 páginas en Week (`concent` → `fails` → `when I focus / fail` → **`time per
        project`**) y 3 en Day (la de proyectos también la última). Sin crash en ninguno de los dos.
      - Banda `projects_band` visible entre los puntos y la banda de uso; la de uso sigue pintando
        y el `NestedScrollView` scrollea entero — el riesgo de layout de la propuesta no se
        materializó.
      - Calendario: la fila del deadline aparece en el día 2, **el tap navega al detalle**, se mueve
        el deadline al día 5 desde el detalle y el marcador salta del 2 al 5 **sin refresh manual**
        (confirma que la invalidación de Room a través del `MediatorLiveData` funciona de verdad).
      - Regresión: tocar una fila NO-deadline del día sigue sin hacer nada y sin romper.

### Seguimiento detectado por el `ui-tester` (no es un defecto de este cambio)

- [ ] El selector Day/Week/Month y el stepper de rango viven al **final** de `fragment_progress.xml`,
      debajo de la banda de uso, así que ya antes había que scrollear para alcanzarlos; esta banda
      añade una pantalla más de distancia. No lo toco aquí (la propuesta fija el orden de las bandas
      a propósito), pero mover el navegador de rango arriba del todo es un candidato claro para el
      siguiente spec de Progress.
- [ ] La hoja de selectores de `adb-ui-test` referencia `text_dashboard`, que ya no existe en
      `fragment_progress.xml`. Referencia obsoleta en la skill, no del app.
- [x] `/spec verify` — code-reviewer pass **ship, 0 High**; `/check` re-run verde (`assembleDebug` ✅,
      `lintDebug` ✅, `test` ✅ 42/42). Hallazgos Medium anotados abajo como seguimiento.

### Seguimiento detectado por `/spec verify` (code-reviewer)

- [ ] **Techo del espacio de ids de notificación asimétrico respecto al de request codes.**
      `NOTIFICATION_ID_BASE = 70_000` choca con el espacio de eventos (`100_000+`) a partir de
      `projectId >= 3000`, mientras que el de request codes (`REQUEST_CODE_BASE = 500_000`) aguanta
      ~50_000 ([ProjectDeadlineScheduler.java:59,77-83](../../../../app/src/main/java/com/example/bbettercalendar/notifications/project/ProjectDeadlineScheduler.java#L59)).
      Sólo lo guarda `ReminderNamespaceTest` con `ID_CEILING = 2_000` (garantía de test, no de
      runtime): con >3000 proyectos una notificación de deadline pisaría en silencio a una de
      evento. Improbable (Room reutiliza rowid sin `AUTOINCREMENT`), pero la asimetría entre los dos
      techos parece descuido, no decisión. Arreglo barato: separar más las bases o un `Log.w` de
      runtime, en el próximo spec que toque esta tabla.
- [x] **RESUELTO en verify. La tolerancia a disparo tardío de `shouldNotify` pegaba el texto del
      offset equivocado.** `remaining <= OFFSET_MILLIS[offsetIndex] + GRACE` sólo rechazaba alarmas
      que llegaban *pronto* (deadline movido hacia adelante), nunca una que llegaba *muy tarde*: si
      el SO difería la alarma de 3 días y disparaba cuando faltaban 2 horas, se enviaba el cuerpo
      "faltan 3 días", porque el cuerpo lo elige el índice de la alarma y no el tiempo restante.
      **Arreglo:** la ventana válida pasa a ser un intervalo y no sólo un techo — la alarma del
      índice `i` manda mientras `remaining` caiga entre `OFFSET_MILLIS[i + 1]` (suelo, excluido) y
      `OFFSET_MILLIS[i] + GRACE` (techo, incluido); el último offset no tiene suelo, así que sigue
      cubriendo hasta el propio deadline. Se descarta en vez de degradar el texto: la alarma del
      offset siguiente ya es la dueña de esa banda y avisa con el cuerpo correcto, así que degradar
      duplicaría el aviso. Ojo al índice: `OFFSET_MILLIS` va de mayor a menor, así que la alarma que
      cubre la banda pegada al deadline es la `i + 1`, no la `i - 1` (el `code-reviewer` propuso el
      índice al revés). Ese orden descendente es ahora un contrato documentado en el javadoc de
      `OFFSET_MILLIS`, porque la guarda depende de él.
      `ProjectDeadlineStaleAlarmTest.lateFiringAlarm_stillNotifies` fijaba el comportamiento malo
      (usaba justo el caso límite, `remaining = 1d` para la alarma de 3d): reescrito con un retraso
      realmente moderado, más dos tests nuevos (`grosslyLateAlarm_dropsInsteadOfUsingTheWrongBody`,
      `lastOffsetHasNoFloor_soItStillNotifiesNearTheDeadline`). 12 tests en la clase, 44/44 en total.
- [ ] Archive + update `projects.md`, `calendar.md`, `notifications.md`, `progress-screen.md`,
      `data-model.md` and the roadmap's Status line.
