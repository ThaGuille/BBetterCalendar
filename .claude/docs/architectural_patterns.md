# Architectural Patterns

Patterns that appear across multiple files in this codebase.

## Threading Model: ExecutorService + LiveData.postValue()

All database work runs on a background `ExecutorService`; results reach the UI via `postValue()`.

- `database/ThreadingModule.java` — Hilt-provided shared executors (spec di-threading-consolidation,
  T1): `@IoExecutor` (`@Singleton` fixed pool of 4 — general-purpose background work, the target
  for virtually every existing call site) and `@DbWriteExecutor` (`@Singleton` single thread —
  provisioned for T2's global write-ordering migration; no consumers yet). Inject with
  `@Inject @IoExecutor ExecutorService executor;` — never construct a `Executors.new*` locally.
  Both are process-lifetime singletons: nothing calls `.shutdown()` on them (unlike the old
  per-instance executors, which leaked threads on every ViewModel recreation).
- `ConfigurationManager.java:15` is the one deliberate holdout — still owns its own
  `Executors.newFixedThreadPool(2)` (out of T1's scope; not in the tranche's touched-files list).
- UI observation always uses `observe(getViewLifecycleOwner(), ...)` — e.g. `HomeFragment.java:118`

Never call DAO methods on the main thread. Never use `setValue()` from a background thread.

## Async Synchronization: CountDownLatch

When one background task must complete before the next starts (e.g., read-then-write during startup), a `CountDownLatch(1)` gates the sequence.

- `InitialConfiguration.java:30,62,68` — latch created, `countDown()` inside executor, `await()` before next step

## Singleton Initialization: Double-Checked Locking

Singletons use a `synchronized` static `getInstance()` with a null check.

- `ConfigurationManager.java:20-25`
- `AppDatabase.java` (package-private `getDatabase()` — see below)

`AppDatabase.getDatabase()` is package-private (spec di-threading-consolidation, T1): only
`database/DatabaseModule.java` (same package) may call it. Every other class gets `AppDatabase`
or a DAO through Hilt injection — the compiler enforces "Hilt is the only door".

## Dependency Injection: Hilt Wiring

- All Activities and Fragments are annotated `@AndroidEntryPoint`.
- `database/DatabaseModule.java` — single source of truth for `AppDatabase` + all 8 DAOs
  (`@Provides @Singleton`, one method per DAO). Absorbed the old `ConfigurationDatabaseModule`
  (deleted) and the DAO providers that used to live scattered in `ConfigurationModule` /
  `NotificationsModule`.
- `database/ThreadingModule.java` — `@IoExecutor` / `@DbWriteExecutor` qualified `ExecutorService`
  singletons (see Threading Model above).
- `ConfigurationModule.java` — now only `ConfigurationManager` (`ConfigurationDAO` comes from
  `DatabaseModule`).
- `NotificationsModule.java` — now only `AlarmManager` (`CalendarEntryDAO`/`AppRuleDAO` come from
  `DatabaseModule`).
- The 5 ViewModels (`HomeViewModel`, `CalendarViewModel`, `ProgressViewModel`,
  `ProjectsViewModel`, `ProjectDetailViewModel`) are `@HiltViewModel` with `@Inject` constructors
  (DAOs + `@IoExecutor`); still `AndroidViewModel` where they need `Application` — Hilt injects it
  directly (no qualifier needed). Their fragments are `@AndroidEntryPoint` so
  `new ViewModelProvider(this)` resolves the Hilt factory automatically.
- `HomeFragment.java:82-83` — `@Inject ConfigurationManager configManager` then passed to
  `HomeViewModel` via `setConfigManager()` (kept for compatibility; same singleton either way —
  `HomeViewModel`'s own constructor also receives `ConfigurationManager` directly).
- `@HiltViewModel` requires `androidx.hilt:hilt-navigation-fragment` (implementation) +
  `androidx.hilt:hilt-compiler` (annotationProcessor) in `app/build.gradle` on top of
  `hilt-android` — without them the annotation doesn't compile.

Modules live in `configuration/` and `notifications/` alongside the non-DAO classes they still
provide; all DAO + executor plumbing lives in `database/`.

## DAO Conventions (Room)

All three DAOs follow the same conventions:

- `CalendarEntryDAO.java`, `StatsDAO.java`, `ConfigurationDAO.java`
- Single-entity updates use `@Query("UPDATE T SET col = :param WHERE id = :id")`.
- Increment operations use `col = col + :amount` in the SQL — e.g. `StatsDAO.java:49,54`.
- First-run initialization checks `if (dao.get() == null)` before inserting — `InitialConfiguration.java:86-91`.
- `Stats` and `Configuration` are single-row tables (always id = 0 or 1).

## LiveData Exposure from ViewModels

ViewModels hold `MutableLiveData` fields and expose them as immutable `LiveData` via getters.

- `HomeViewModel.java:28-46` — fields declared as `MutableLiveData<T>`, getters return `LiveData<T>`
- Updates from background threads always go through `postValue()`, never `setValue()`

## Popup / Dialog Fragment Pattern

Each popup is a `DialogFragment` subclass. Construction follows the same flow:

1. Create instance as a field in the Fragment — e.g. `HomeFragment.java:58-60`
2. Call `popup.setConfiguration(config)` or `popup.setOnPopupListener(this)` before showing
3. Show via `popup.show(getChildFragmentManager(), "tag")`
4. Results return through `OnPopupListener.OnClosePopup(int popupType, Object result)` — `PopupHelper.java`

`PopupHelper` defines the popup-type constants (`TIMER_POPUP`, etc.) used in the callback.

## Type Conversion: Gson in Room

`DBConverter.java` handles all non-primitive Room fields using Gson:

- `Calendar` ↔ JSON string (stores `timeInMillis` as a map)
- `boolean[]` ↔ JSON string (notification flags)

Annotated with `@TypeConverter`; registered on `AppDatabase.java:17-18` via `@TypeConverters({DBConverter.class})`.

When adding a new complex field to an entity, add a converter pair here — do not store raw objects.

## Builder Pattern for Entities

`CalendarEntry` uses an inner `EventBuilder` class for construction.

- `CalendarEntry.java:68-176` — fluent setters, terminal `build()` returns a `CalendarEntry`
- `type` field: `1` = event, `2` = task, `3` = reminder

Use `EventBuilder` whenever creating a new `CalendarEntry`; direct field assignment bypasses required defaults.
