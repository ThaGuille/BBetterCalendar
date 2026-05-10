# Architectural Patterns

Patterns that appear across multiple files in this codebase.

## Threading Model: ExecutorService + LiveData.postValue()

All database work runs on a background `ExecutorService`; results reach the UI via `postValue()`.

- `ConfigurationManager.java:15` — `Executors.newFixedThreadPool(2)` field
- `InitialConfiguration.java:46` — same setup for startup DB writes
- `HomeViewModel.java:51` — same setup for stats/config updates
- UI observation always uses `observe(getViewLifecycleOwner(), ...)` — e.g. `HomeFragment.java:118`

Never call DAO methods on the main thread. Never use `setValue()` from a background thread.

## Async Synchronization: CountDownLatch

When one background task must complete before the next starts (e.g., read-then-write during startup), a `CountDownLatch(1)` gates the sequence.

- `InitialConfiguration.java:30,62,68` — latch created, `countDown()` inside executor, `await()` before next step

## Singleton Initialization: Double-Checked Locking

Singletons use a `synchronized` static `getInstance()` with a null check.

- `ConfigurationManager.java:20-25`
- `AppDatabase.java:27-39`

`AppDatabase` is also provided by Hilt via `ConfigurationDatabaseModule` — prefer injection over direct `getInstance()` calls.

## Dependency Injection: Hilt Wiring

- All Activities and Fragments are annotated `@AndroidEntryPoint`.
- `ConfigurationModule.java:7-31` — provides `ConfigurationDAO` and `ConfigurationManager` as `@Singleton` via `@Provides`.
- `ConfigurationDatabaseModule.java:9-25` — provides the `AppDatabase` instance.
- `HomeFragment.java:82-83` — `@Inject ConfigurationManager configManager` then passed to ViewModel via setter.

Modules live in `configuration/` alongside the classes they provide.

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
