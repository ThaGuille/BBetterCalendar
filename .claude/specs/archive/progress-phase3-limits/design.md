# Design notes — progress-phase3-limits

Deeper rationale for the choices in [`proposal.md`](proposal.md). The big one: **how to detect
"about to hit the limit" without an always-on service, and without an accessibility permission.**

## 1. Why a periodic AlarmManager check, not a foreground service

The `02-blocking-and-reminders.md` doc sketches a "foreground monitor service." For a **warn-only**
phase that is more than we need:

- A foreground service requires a **persistent, undismissable notification** all day — a real
  annoyance for a feature whose whole point is to reduce phone friction.
- It's heavier on battery, and on Android 14+ a `specialUse` FGS needs a **Play justification**.
  We'd be taking on Phase-4-sized cost for a Phase-3 feature.

Instead, reuse the infra the app already has for event reminders: an **`AlarmManager` alarm that
fires a `BroadcastReceiver`, does a quick check, and reschedules itself** (`EventReminderScheduler`
→ `EventReminderReceiver`, re-armed by `BootReceiver`). `USE_EXACT_ALARM` / `RECEIVE_BOOT_COMPLETED`
are already declared.

**Why polling is the only option here (not a one-shot predictive alarm):** app usage is not
linear or predictable — you cannot know in advance *when* someone will reach 55 min of Instagram.
So the warning trigger is inherently *sampled*: wake periodically, read today's usage, compare.

**Why periodic polling is cheap anyway:** usage only accrues while the **screen is on and the app
is foreground**. When the device is idle/Doze (screen off), no usage accrues — so the OS deferring
our alarm during Doze is harmless (there's nothing to miss). During active use the device isn't in
deep Doze, so a ~5-min repeating check fires roughly on time. We further bound cost by **arming the
alarm only when ≥1 tracked app has a limit that hasn't already fired both notifications today**, and
disarming otherwise.

Cadence is a constant (default 5 min). Trade-off: a coarser cadence saves battery but makes the
"~5 min left" warning less precise (it could arrive after the limit). 5 min is the balance; it's a
single constant to tune. The notification copy says "~5 min" / "about to" rather than an exact
figure so slight sampling jitter reads fine.

If Phase 4's enforcement later needs sub-minute reaction, that's the `AccessibilityService`'s job
(instant `TYPE_WINDOW_STATE_CHANGED`), not this poller — another reason to keep the two phases'
mechanisms separate.

## 2. No schema bump — reuse the dormant columns

`app_rule` was created in `MIGRATION_9_10` (DB v10) already carrying
`dailyLimitMinutes INTEGER NOT NULL DEFAULT 0` and `warnBeforeMinutes INTEGER NOT NULL DEFAULT 5`
(plus the Phase-4 `instantBlock` / `blockedToday` / `blockStyle`). Phase 2 deliberately shipped
these dormant. Phase 3 just starts **writing/reading** `dailyLimitMinutes` and reading
`warnBeforeMinutes`. **No `@Database` version change → no migration → no rule #6 data-wipe risk.**

## 3. De-dup without a new column

We must fire each notification **once per app per day**. Adding a `warnedDate` column would mean a
schema bump. Instead, a tiny `SharedPreferences` store keys markers by `yyyy-MM-dd + packageName`;
a marker counts only if its date equals today, and we clear stale markers at the daily-reset hook
the stats already use. Cheap, no migration, self-expiring.

`blockedToday` is **not** reused for this — it belongs to Phase 4 enforcement and writing it now
would blur the phase boundary.

## 4. Two thresholds, one mechanism

The same per-app check evaluates two thresholds against today's foreground ms:

- `used >= limit − warnBefore` → **pre-limit warning** (once).
- `used >= limit` → **limit-reached** (once).

Both are plain notifications via the existing `BBetterNotifier` / `NotificationSpec` (tap →
`openMainActivity`). The limit-reached one is *informational only* — it does not set `blockedToday`
or cover anything. That line is exactly where Phase 4 picks up.

## 5. UI: tap-the-row to set a limit

The usage list already renders `[block-stub][icon][label][time]`. Adding limits:

- The row becomes tappable → `AppLimitDialog` (PopupHelper, like `UsageDisclosureDialog`): a minutes
  input + **Save** / **Clear**. `Clear` sets `dailyLimitMinutes = 0`.
- The time cell shows progress against the limit (`32m / 1h`), tinted with a `bb_*` warning color at/
  over the limit. No limit → just the time, as today.
- The **block toggle stays a disabled stub** — visually reinforcing to the user (and to us) that
  *limits warn, they don't block yet*. It flips to functional in Phase 4.

`warnBeforeMinutes` keeps its stored default (5) with no UI this phase — one less control to design,
and easy to expose later in the same dialog.

## 6. Threading & lifecycle recap

- DB writes (`setDailyLimit`) and the usage read in the check run on an `ExecutorService`; the
  receiver wraps work in `goAsync()` + executor; UI updates via `postValue` (rule #3).
- Arm points: app start (alongside `NotificationChannels.createAll`), on any limit change, and after
  boot (extend `BootReceiver`). Disarm when no limited apps remain.

## Alternatives considered

- **WorkManager periodic** — min interval 15 min is too coarse for a "few minutes before" warning,
  and it's not a current dependency. Rejected.
- **Foreground service polling on a Handler loop** — see §1; rejected for a warn-only phase.
- **One-shot predictive alarm at the exact warn time** — impossible; usage isn't predictable (§1).
- **New `warnedDate` column for de-dup** — would force a schema bump; SharedPreferences avoids it (§3).
