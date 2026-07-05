# System — Startup & configuration (`configuration/`)

**Last verified:** 2026-07-05 (DB v10) · Code wins on conflict — if you find drift, fix this doc and bump the date.

App entry point and the timer/rest/cycle settings that back the Pomodoro screen. `SplashActivity`
is the real `LAUNCHER` activity: it does first-run DB seeding, the daily-stats reset, and streak
math before handing off to `MainActivity`. `ConfigurationManager` is a plain (non-Hilt-scoped)
singleton that caches `Configuration` in memory.

## Surface (manifest + entry points)
| Kind | Entry |
|---|---|
| Activity | `.configuration.SplashActivity` (`LAUNCHER` intent-filter, `SplashTheme`) — **the actual app entry point**, not `MainActivity` |
| Hilt module | `ConfigurationDatabaseModule` — provides the `AppDatabase` singleton |
| Hilt module | `ConfigurationModule` — provides `ConfigurationDAO` + `ConfigurationManager` |

## Files
| Class | Path | Role |
|---|---|---|
| `Configuration` | `configuration/Configuration.java` | Room `@Entity`; timer/rest time, cycle count, toggle flags, country, notification-permission ask tracking (added `MIGRATION_7_8`) |
| `ConfigurationDAO` | `configuration/ConfigurationDAO.java` | Insert/update/delete + `getConfiguration()` (single row, `LIMIT 1`) |
| `ConfigurationManager` | `configuration/ConfigurationManager.java` | Plain thread-safe singleton (own `getInstance()`, not Hilt-scoped directly); loads config async on construction, caches in memory, writes back via its own `ExecutorService` |
| `ConfigurationDatabaseModule` | `configuration/ConfigurationDatabaseModule.java` | Hilt `@Module` — `@Provides AppDatabase` via `AppDatabase.getDatabase()` |
| `ConfigurationModule` | `configuration/ConfigurationModule.java` | Hilt `@Module` — `@Provides ConfigurationDAO`, `@Provides ConfigurationManager` (delegates to `ConfigurationManager.getInstance()`) |
| `SplashActivity` | `configuration/SplashActivity.java` | Real entry point; seeds `Stats`+`Configuration` rows, persists yesterday's `DailyStat`, resets today's counters, updates streak, launches `MainActivity` |
| `InitialConfiguration` | `configuration/InitialConfiguration.java` | **Legacy**, superseded by `SplashActivity` — extends `AppCompatActivity` but is used as a singleton (`getInstance()`); duplicates the same seed/reset/streak logic behind a `LiveData<Boolean>` + `CountDownLatch` init signal. `MainActivity`'s call to it is commented out |
| `MainActivity` | `MainActivity.java` | Root activity `SplashActivity` launches into; hosts the bottom-nav `NavHostFragment` for all 4 tabs, creates the foreground-service notification channel |

## Flow — non-obvious hops only

1. **`SplashActivity.onCreate()` runs its whole sequence on one `ExecutorService`, then jumps back to the main thread to launch `MainActivity`**: seed rows → `resetDailyStats` → `checkAndUpdateStreak` → `runOnUiThread { startActivity(MainActivity) }`. Nothing here uses `postValue`/`LiveData` — it's a direct executor-then-UI-thread handoff, not the LiveData pattern used elsewhere.
2. **`resetDailyStats` persists history *before* zeroing counters**: it upserts a `DailyStat` row keyed by `lastDayStreak` (the last day the user was actually active, not necessarily yesterday) via `persistDailyStat()`, then calls `statsDao.resetDailyStats()`. Reordering this would silently lose a day of chart history.
3. **`ConfigurationManager` is a singleton constructed once via `getInstance(dao)`**, not re-created per Hilt scope — `ConfigurationModule.provideConfigurationManager()` just forwards to it, so injecting it in a test or a second Hilt component still returns the same instance app-wide.
4. **`InitialConfiguration` is dead code in practice** (the wiring call in `MainActivity` is commented out) but still compiles and duplicates `SplashActivity`'s logic almost line-for-line — don't fix a bug in one without checking whether it's mirrored in the other, since some future re-enable could resurrect it.

## Contracts
- Reads/Writes: `Stats`, `DailyStat`, `Configuration` (owner: `data-model.md#per-entity-readerswriters-contract-table`) · Shared with: `pomodoro-timer.md` (`Configuration` values), `progress-screen.md`/`app-limits.md` (`DailyStat` history, `AppRule` scheduler arm happens in `database.md`'s `DBMigration`, not here)

## Invariants & gotchas

- **`SplashActivity`, not `MainActivity`, is the `LAUNCHER` activity** — anything that must run before any UI shows (seeding, streak, daily reset) belongs here, not in `MainActivity.onCreate()`.
- **`ConfigurationManager.getConfiguration()` can return null** if called before the async `loadConfiguration()` finishes — it lazily inserts a default `Configuration` row only on that specific null-and-still-null-after-DAO-read path, so a caller during the race window before the background load truly needs to handle null.
- **The streak/reset logic is duplicated verbatim in `InitialConfiguration`** — since it's legacy and its call site is commented out, don't spend time keeping it in sync unless you're the one re-enabling it; consider it a candidate for deletion if touching this area.
- **Daily reset keys off `lastDayStreak`, not "yesterday"** — a user who skipped several days still gets exactly one `DailyStat` row persisted (for their last active day), not one per skipped day; gap days simply have no `DailyStat` row at all (Progress charts show them as zero via gap-filling, not from a persisted row).

## History

| Date | Change | Spec |
|---|---|---|
| 2026-06-28 | `SplashActivity`/`InitialConfiguration` both gained `persistDailyStat()` before daily reset | `.claude/specs/archive/progress-charts-mvp/proposal.md` |
