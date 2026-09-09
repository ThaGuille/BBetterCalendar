# Project deadlines & Progress integration (roadmap Phase 5)

**Slug:** project-deadlines-progress
**Status:** archived
**Created:** 2026-09-01
**Last updated:** 2026-09-01

## Why

`Project.softDeadlineMillis` exists since the Projects MVP but is a **dead-end field**: it only
tints a chip inside the Projects tab. Nothing reminds you a deadline is coming, the Calendar (the
screen whose whole question is *"when does what happen?"*) can't see it, and the attributed focus
minutes Phase 4 started logging (`FocusEvent.entryId`) are never aggregated per project, so
Progress can't answer *"where did my time actually go?"*.

This is the last phase of [`projects-tasks-roadmap.md`](../../../plans/projects-tasks-roadmap.md);
closing it makes the four screens answer their four questions with a single editing surface per
fact (deadline is edited only in Projects, painted read-only in Calendar).

## Decisions locked (2026-09-01, with user)

1. **Alarm-scheduled at deadline-minus-N** — not a periodic "is it approaching?" poll. Reuses
   `AlarmReminderCore` ([AlarmReminderCore.java:12-17](../../../../app/src/main/java/com/example/bbettercalendar/notifications/event/AlarmReminderCore.java#L12-L17)
   literally anticipates this client).
2. **New notification channel**, not a reuse of `CHANNEL_EVENT_REMINDERS`.
3. **Calendar deadlines come from a new data source** merged into `CalendarViewModel` — no mirror
   `CalendarEntry` rows, no sync-drift risk.
4. **Progress gets a basic per-project chart** — deliberately plain now; per-project options,
   filters and drill-down come later.
5. **Progress also gets the project-progress band** (the roadmap's "possibly") — a third band under
   the carousel, above the phone-usage band.
6. **The deadline notification deep-links into the project's detail screen**, not just "opens the
   app" — this pulls `MainActivity` intent plumbing into scope that no existing notifier has.

### Chosen by Claude (unanswered questions)

7. **Offsets = 3 days and 1 day before the deadline**, both always on (no per-project offset UI in
   this phase). 3d is not arbitrary: it is exactly `ProjectDeadlineState.APPROACHING_WINDOW_MILLIS`
   ([ProjectDeadlineState.java:11](../../../../app/src/main/java/com/example/bbettercalendar/projects/ProjectDeadlineState.java#L11)),
   so the notification fires on the same instant the chip turns amber — one threshold, two surfaces.
8. **One-shot per offset, never repeating** — a daily nag while amber is a notification-fatigue
   design; two alarms total per project is the whole budget.
9. **Calendar deadline tap navigates to that project's detail screen.** The day-detail list has an
   `OnItemClickListener` that **nothing currently sets** — `CalendarFragmentMonth` never calls
   `setOnItemClickListener` — so wiring it for `DEADLINE` items only is a pure addition; other item
   types keep today's no-op behaviour.
10. **One global nav action** (`action_global_project_detail`) serves all three new entry points —
    notification deep link, calendar marker tap, progress-band row tap — instead of three
    per-destination actions. The Projects tab keeps its existing action untouched.
11. **Only `STATUS_ACTIVE` projects get deadline alarms and calendar markers.** A completed project
    whose deadline is still in the future must not nag; its row is retained for stats either way.
12. **Band membership rule: a project appears if it is active, OR it has attributed minutes inside
    the selected range.** Keeps the band retrospective (a project you finished last month still
    shows for the week you worked on it) without growing unbounded as completed projects pile up.
13. **Band sorts by minutes-in-range desc**, ties by name — same ordering as the chart, since both
    answer "where did the time go".

## What changes (deltas vs current behavior)

### A. Deadline reminders (new `notifications/project/` package)

- ADDED: `ProjectDeadlineScheduler` — thin `AlarmReminderCore` client (mirrors
  `EventReminderScheduler`'s shape exactly). Fixed offset table `{3d, 1d}`; **disjoint request-code
  namespace** `500_000 + projectId * 10 + offsetIndex` (entry reminders use the bare
  `entityId * 10 + offsetIndex` space — they would collide otherwise, since project and entry ids
  both start at 1).
- ADDED: `ProjectDeadlineReceiver` (`exported=false`, `@AndroidEntryPoint`) — re-reads the project
  on the IO executor via `goAsync()`, drops the notification if the project is gone, archived,
  completed, or its deadline changed since the alarm was set (a stale alarm must not fire).
- ADDED: `NotificationChannels.CHANNEL_PROJECT_DEADLINES` (`bb_project_deadlines`,
  `IMPORTANCE_HIGH`) + created in `createAll()`.
- ADDED: notification-id range **70_000 + projectId * 10 + offsetIndex** — free between focus
  (50_001), usage (60_000/61_000) and event (100_000+); documented in `notifications.md`'s
  partition list.
- ADDED: `NotificationSpec.Builder.openProjectDetail(context, projectId)` — sibling of the existing
  `openMainActivity()`, same `MainActivity` intent + flags plus an `EXTRA_OPEN_PROJECT_ID` extra.
  Intent construction stays inside `NotificationSpec` where it already lives.
- CHANGED: `MainActivity` handles that extra in **both** `onCreate()` (the notification intent
  carries `CLEAR_TOP`, and with the default `standard` launch mode that recreates the activity) and
  a new `onNewIntent()` (`setIntent()` first), navigating via the global action. The extra is
  **removed from the intent once consumed** so a rotation doesn't re-navigate.
- CHANGED: `ProjectsRepository.createProject()` schedules after the insert (a project can be
  created with a deadline already set — [CreateProjectDialog.java:53](../../../../app/src/main/java/com/example/bbettercalendar/ui/projects/CreateProjectDialog.java#L53)).
- CHANGED: `ProjectDetailViewModel.updateDeadline()` cancels then re-schedules;
  `completeProject()` and `deleteProject()` cancel. All inside the existing `@DbWriteExecutor`
  blocks — same convention as `AddEventActivity` calling `EventReminderScheduler` directly.
- CHANGED: `BootReceiver` also re-arms project-deadline alarms (it already bundles calendar
  reminders + `UsageLimitScheduler`; this is the third tenant, same `goAsync()` block).

### B. Deadlines painted on the Calendar

- ADDED: `ProjectDAO.observeDeadlinesBetween(start, end)` — active projects with
  `softDeadlineMillis` inside the visible range.
- ADDED: `CalendarItem.Type.DEADLINE` + `ColorResolver` case + `calendar_item_deadline` colour
  (aliases the amber `bb_accent_reward` — decision #9 of the roadmap: deadlines reuse the
  app-limits visual language, no new token invented).
- ADDED: `ProjectDeadlineItemMapper` (`ui/calendar/domain/`) — `Project` → `CalendarItem` with
  `start = end = softDeadlineMillis` (zero-length, exactly like an existing reminder row after
  `CalendarItemMapper`'s `end < start` clamp) and **`id = -project.id`**: a negative id marks
  "not a `CalendarEntry`", so nothing can mistake it for one, and the click handler decodes the
  project id by negating it back.
- CHANGED: `CalendarViewModel.items` merges two Room `LiveData`s (entries + deadlines) through a
  `MediatorLiveData` instead of mapping one. Both sources auto-invalidate, so editing a deadline in
  Projects repaints the Calendar with no refresh poke (see `data-model.md`'s Room invalidation
  invariant — we are *not* reintroducing a `refresh()`).
- CHANGED: `CalendarFragmentMonth` sets the day-detail click listener: `DEADLINE` items navigate to
  the project detail; everything else stays a no-op.
- ADDED: **global** nav action `action_global_project_detail` (root-level in
  `mobile_navigation.xml`) — reachable from any destination, so the calendar tap, the progress-band
  tap and the notification deep link all share one action.

### C. Progress — time per project (chart)

- ADDED: `FocusEventDAO.getMinutesByProject(start, end)` — `focus_event ⨝ calendarEntry ⨝ project`,
  `type = 0` (fails contribute 0), `entryId != 0`, grouped by project, ordered by minutes desc.
  Note this is a **different query shape** from the existing `observeAttributedMinutesByEntry()`,
  which is unbounded in time by design (lifetime per-item progress) — the chart needs the
  `TimeRange` bound.
- ADDED: `ProjectMinutes` projection POJO (`stats/`): `projectId`, `projectName`, `minutes`. The id
  is carried so this **one query feeds both** the chart (name → minutes) and the band (id → minutes).
- CHANGED: `ChartBundle` gains `projectLabels` / `projectMinutes` (plain arrays, still zero
  MPAndroidChart types — its existing contract).
- CHANGED: `ProgressViewModel.buildBundle()` fills them (already on the executor).
- CHANGED: `ChartCarouselAdapter` gains a projects bar-chart page. Page indices are positional and
  DAY mode already remaps them, so: non-DAY `PAGE_COUNT 3→4` (projects = 3), DAY
  `PAGE_COUNT_DAY 2→3` (projects = 2). Empty range → empty chart (MPAndroidChart's own
  "no data" state); no bespoke empty view in this phase.

### D. Progress — project-progress band (decision #5)

A third band in `fragment_progress.xml`, **between the carousel and the phone-usage band**: the
screen then reads charts (how did focus go) → projects (where did the work go) → phone usage
(where did the time leak). Unlike the usage band it is **not permission-gated** — same as the
charts, it renders from local DB rows only.

- ADDED: `ui/progress/ProjectProgressRow` — plain row data (projectId, name, colorIndex, done/total,
  `percent()`, minutesInRange, `ProjectDeadlineState`). No entity types, matching `AppUsageRow`.
- ADDED: `ui/progress/ProjectProgressAdapter` + `res/layout/item_project_progress.xml` — name,
  % bar, `X/Y items`, minutes-in-range, deadline chip. Accent colour comes from
  `ProjectListAdapter.ACCENT_COLORS` via `colorIndex` so a project looks the same on both screens.
- CHANGED: `ProgressViewModel` injects `ProjectsRepository` and exposes
  `LiveData<List<ProjectProgressRow>>` built by a `MediatorLiveData` merging **two live sources**:
  `observeAllWithCounts()` (Room LiveData — auto-invalidates on writes to *either* `project` or
  `calendarEntry`, per T2) and a `MutableLiveData<Map<Integer,Integer>>` of range-scoped minutes
  posted from the executor by the same `getMinutesByProject` call the chart uses. The merge itself
  is a pure in-memory composition, so it stays on the main thread (rule #3 is about the DB read,
  which is already off it) — the same shape as `ProjectsViewModel`'s `Transformations.map`.
- CHANGED: `ProgressFragment` hosts the band; empty list → heading + list `GONE` (no state enum —
  unlike `UsageBandState` there is no permission to be locked on). Row tap → global action to the
  project detail.
- The band re-queries from the same `applyRange()` call as the other two, preserving the screen's
  "one `TimeRange` drives every band" invariant — now three bands, not two.

## Impact

- **Files / packages touched:**
  - `notifications/project/` — **new**: `ProjectDeadlineScheduler`, `ProjectDeadlineReceiver`
  - `notifications/NotificationChannels.java`, `notifications/NotificationSpec.java`
  - `MainActivity.java` (deep-link extra: `onCreate` + new `onNewIntent`)
  - `projects/ProjectDAO.java`, `projects/ProjectsRepository.java`
  - `notifications/event/BootReceiver.java`
  - `ui/projects/ProjectDetailViewModel.java`
  - `ui/calendar/CalendarViewModel.java`, `ui/calendar/CalendarFragmentMonth.java`
  - `ui/calendar/domain/`: `CalendarItem.java`, `ColorResolver.java`, **new** `ProjectDeadlineItemMapper.java`
  - `stats/FocusEventDAO.java`, **new** `stats/ProjectMinutes.java`
  - `ui/progress/ChartBundle.java`, `ProgressViewModel.java`, `ChartCarouselAdapter.java`,
    `ProgressFragment.java`, **new** `ProjectProgressRow.java`, `ProjectProgressAdapter.java`
  - `res/layout/fragment_progress.xml`, **new** `res/layout/item_project_progress.xml`
  - `res/values/colors.xml` (`calendar_item_deadline`), `res/values/strings.xml`,
    `res/navigation/mobile_navigation.xml`, `AndroidManifest.xml` (receiver)
  - `app/src/test/java/.../` — unit tests for the mapper + id/request-code disjointness
  - **Añadidos durante el apply** (no previstos en la propuesta original, todos de una línea y al
    servicio de una tarea ya declarada):
    - `notifications/event/EventReminderScheduler.java` + `notifications/event/EventReminderReceiver.java`
      — la fórmula de request code y la de id de notificación pasan de lambda/privadas a
      `public static requestCodeFor` / `notificationIdFor`, para que el test de disjunción de la
      tarea E compare contra las fórmulas **reales** en vez de contra copias suyas (una copia no
      detectaría que alguien cambie la base del espacio de entrada).
    - `ui/projects/ProjectListAdapter.java` — `ACCENT_COLORS` pasa a pública y el ciclado de la
      paleta (con su módulo negativo) se extrae a `accentColorResFor(int)`, que es lo que la tarea D
      pedía reutilizar desde la banda; el adapter de la lista pasa a usarlo también, así que la
      lógica no queda duplicada.
    - `notifications/event/BootReceiver.java` — además del tercer inquilino previsto, el
      `if (all == null) return;` previo se convierte en un `if (all != null)` alrededor del bucle:
      con el early-return, un `getAllEvents()` nulo se saltaba también el re-armado de deadlines
      (y ya se saltaba el `usageLimitScheduler.arm()` existente).
- **DB schema:** **none.** Stays at v13 — only new `@Query` methods and one projection POJO. Rule #6
  untouched, no migration, no destructive-fallback risk.
- **UI tokens:** `bb_*` only (rule #2) — `calendar_item_deadline` aliases `bb_accent_reward`; the
  chart page reuses `bb_primary` / `bb_on_surface_muted` / `bb_divider` like the existing pages;
  the band row uses `TextAppearance.BBetter.*` + the shared `ACCENT_COLORS` cycle.
- **Layout risk:** `fragment_progress.xml` is a `NestedScrollView` with `wrap_content` lists and
  `nestedScrollingEnabled=false` — a third band there is the known-fragile edit on this screen
  (`progress-screen.md` gotcha). If the band is added via `<include>`, do **not** put an
  `android:id` on the include tag — it shadows the included root's id and yields a null
  `findViewById` ([[include-id-overrides-root-id]]).
- **Threading:** all DAO reads/writes on the injected executors, published via `postValue`
  (rule #3); the receiver uses `goAsync()` + `@IoExecutor` like `EventReminderReceiver`.

## Out of scope

- Per-project configurable reminder offsets / a "remind me" toggle in the detail screen.
- Repeating or escalating deadline reminders, and any notification for an already-passed deadline.
- Per-project drill-down, chart filters/options, selectable metrics (explicitly deferred by the
  user: "basic one, we'll improve it later"). The band and the chart both ship in their plain form.
- Retro-linking the deep link into the other notifiers (focus/usage/event keep `openMainActivity`).
- Deadline editing anywhere outside the Projects tab (Calendar stays read-only — the roadmap's
  one-fact-one-owner principle).
- Hard deadlines, overdue escalation, or project-level time targets.
- T3 schema hygiene (indexes, dropping `fallbackToDestructiveMigration()`) — the roadmap parked it
  here "if a migration is needed"; there is none, so it stays parked.

## Verify

**Veredicto: PASS** (2026-09-01) — `code-reviewer`: *ship*, **0 hallazgos High**. Se archiva.

**1. Completitud** — las 33 casillas de A–E marcadas. Las únicas abiertas al entrar a verify eran
los dos seguimientos del `ui-tester` (ambos declarados explícitamente como *no* defectos de este
cambio: el orden del selector de rango en `fragment_progress.xml` y una referencia obsoleta a
`text_dashboard` en la hoja de selectores de `adb-ui-test`) y la propia línea de `/spec verify`.

**2. Corrección (diff vs. "Files / packages touched")** — sin scope creep no declarado y sin nada
declarado que quedara sin tocar. Los cuatro añadidos del apply (`EventReminderScheduler`,
`EventReminderReceiver`, `ProjectListAdapter`, el `if (all != null)` de `BootReceiver`) ya estaban
documentados arriba. Los 11 ficheros de `repository-layer-consolidation` que ensucian el árbol
están pre-declarados en la nota de abajo. Los diffs de `.claude/docs/systems/*.md` y del roadmap
son el paso de *archive* de ese spec anterior, y `STATUS.md` lo regenera el hook
`update-status.ps1` — ninguno pertenece a este cambio.

**3. Coherencia (`code-reviewer`)** — limpio en las cuatro reglas: threading (rule #3) correcto,
incluido `goAsync()` → `try/catch/finally { pendingResult.finish() }` en `ProjectDeadlineReceiver`
(el `finish()` se alcanza también por la vía de excepción) y los dos `MediatorLiveData` nuevos, que
sólo componen en memoria sobre `LiveData` de Room ya cargadas y quedan dentro del `switchMap`, que
desengancha las fuentes al cambiar de rango; paleta `bb_*` + `TextAppearance.BBetter.*` exclusivas
en `item_project_progress.xml` (rule #2); rule #4 no implicada (el cambio no crea `CalendarEntry`
— ése es justo el punto de la decisión #3); `@Database(version = 13)` sin tocar (rule #6). La
completitud de cancel/reschedule se confirmó exhaustiva: no queda ninguna vía de escritura a
`Project.status` / `softDeadlineMillis` que deje una alarma huérfana.

**`/check` re-ejecutado en verify** (no heredado del apply): `assembleDebug` ✅, `lintDebug` ✅,
`test` ✅ **44/44** (7 `ProjectDeadlineItemMapperTest` + 5 `ReminderNamespaceTest` +
12 `ProjectDeadlineStaleAlarmTest` nuevos; los dos últimos de esa clase salen del arreglo del
hallazgo 2, abajo).

**Hallazgos Medium** — se doblaron hacia `tasks.md` en vez de arreglarlos en silencio (política del
skill); el usuario pidió después cerrar el 2, así que ése sí se corrigió antes de archivar:

1. *Techo de ids de notificación asimétrico* — `70_000` colisiona con el espacio de eventos
   (`100_000+`) a partir de `projectId >= 3000`, frente a los ~50_000 que aguanta el de request
   codes; sólo lo guarda un `ID_CEILING = 2_000` de test, sin comprobación en runtime.
2. *`shouldNotify` sin cota superior de retraso* — **corregido antes de archivar** (no queda como
   seguimiento). Sólo rechazaba alarmas que llegaban pronto, así que una alarma de 3 días diferida
   por el SO hasta faltar 2 horas notificaba con el cuerpo "faltan 3 días". La ventana válida pasa a
   ser un intervalo: la alarma del índice `i` manda entre `OFFSET_MILLIS[i + 1]` (suelo, excluido) y
   `OFFSET_MILLIS[i] + GRACE` (techo); el último offset no tiene suelo. Se descarta en vez de
   degradar el cuerpo, porque la alarma del offset siguiente ya cubre esa banda con el texto
   correcto. `ProjectDeadlineStaleAlarmTest.lateFiringAlarm_stillNotifies` fijaba el comportamiento
   malo y se reescribió, con dos tests nuevos alrededor del suelo (44/44 verde,
   `assembleDebug`/`lintDebug` ✅ re-ejecutados tras el arreglo).

El hallazgo 1 es un caso límite estrecho (exige >3000 proyectos) y queda anotado como seguimiento;
el 2 se arregló en el propio verify, así que no llega al archivo como deuda.
