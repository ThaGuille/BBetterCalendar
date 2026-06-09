# Tasks — persist-pomodoro-session-state

- [ ] Add `onSaveInstanceState(Bundle)` to `HomeFragment` writing `timer_state`,
      `timeLeftInMillis`, `lastTimerTime`, `cyclesCompleted` under namespaced keys.
- [ ] In `onViewCreated` (after views + config are ready), if `savedInstanceState != null`
      read the four keys back into the instance fields.
- [ ] Add `renderRestoredState()`: set timer text from `timeLeftInMillis`, set the
      work/rest mode chip color/label for the restored `timer_state`, and update the
      play/pause button glyph.
- [ ] If restored state is `TIMER_RUNNING` / `TIMER_RUNNING_REST`, re-create the
      `CountDownTimer` from `timeLeftInMillis` (reuse `startTimer(...)`); for
      `TIMER_PAUSED*` / `TIMER_STOPPED*` only render, do not start.
- [ ] Confirm `cyclesCompleted` restore keeps the cycle-limit / auto-cycle logic in
      `completeTimer` / `completeRest` correct after rotation.
- [ ] Verify: rotate the device while a work timer and a rest timer are each running
      and paused; cycle count and remaining time persist. Run `/check`.
