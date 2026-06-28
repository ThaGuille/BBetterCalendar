# Tasks — persist-pomodoro-session-state

- [x] Add `onSaveInstanceState(Bundle)` to `HomeFragment` writing `timer_state`,
      `timeLeftInMillis`, `lastTimerTime`, `cyclesCompleted` under namespaced keys.
- [x] In `onCreateView` (after views + config are ready), if `savedInstanceState != null`
      read the four keys back into the instance fields.
- [x] Add `renderRestoredState()`: set timer text from `timeLeftInMillis`, set the
      work/rest mode chip color for the restored `timer_state`.
      (Play/pause button glyph is never swapped in current code — nothing to restore.)
- [x] If restored state is `TIMER_RUNNING` / `TIMER_RUNNING_REST`, re-create the
      `CountDownTimer` from `timeLeftInMillis` (reuse `startTimer(...)`); for
      `TIMER_PAUSED*` / `TIMER_STOPPED*` only render, do not start.
      Render deferred via `root.post(...)` so the LiveData time posted by
      `setConfigManager()` doesn't clobber the restored countdown text.
- [x] Confirm `cyclesCompleted` restore keeps the cycle-limit / auto-cycle logic in
      `completeTimer` / `completeRest` correct after rotation (field restored as-is).
- [x] Verify: rotated while a work timer was running — remaining time + mode chip
      persisted (continued from 19:52 → 19:40 → 19:20 across two rotations), no crash.
      `assembleDebug` passed (/check). Verified via ui-tester on emulator-5554.
