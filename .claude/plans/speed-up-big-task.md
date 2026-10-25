# Speed up the social big-task phases (F3–F5)

**Status:** in progress
**Created:** 2026-10-25
**Last updated:** 2026-10-25

## Summary

F2 took 94.6 h wall for ≈ 5.6 h of real work. The Windows power log shows that most of the idle time was the laptop
sleeping, not the causes first assumed: the "7 h 18 min planner stall" (Modern Standby 2026-10-21 21:32:41 → 04:50:12
local, the exact second the turn started and one second before the "cut off" notice), the "u4 Firebase hang" (standby
21:27 → 23:19:19, the second the 600 s Bash timeout fired; mocha's "2h" and the 409 lock timeout are the emulator's
deadlines expiring during sleep) and the "wave 3 died with its chat" (suspend 2026-10-22T22:43:50Z, 15 min after u7
started, wake 2026-10-24T11:20:01Z). Idle sleep is 20 min on AC / 15 on battery, and standby disconnects the network.
Together ≈ 45.7 h. This plan fixes that root cause (big-task keeps the machine awake while a run is live) and every
other cause on the list: cold builds, the Firebase suite's latent trigger race, AVD cold boots, serialised units,
missing hard timeouts, unfixable checks, the misfiring `pool-underfilled` signal and the human idle time.

## Plan

### BBetterCalendar (branch `speed-up-big-task`)

1. `gradle.properties`: `org.gradle.caching=true`, `org.gradle.parallel=true`; measure a fresh worktree before/after;
   try the configuration cache.
2. Firebase suite: `friends-rules.test.js` waits for the `onFriendRequestAccepted` trigger to settle before the next
   `clearFirestore` (only when the Functions emulator runs); mocha `--exit`; stress run ×10.
3. `firebase/scripts/run-tests.sh`: JDK 21 for its own process, hard timeout (default 300 s) that kills the whole
   process tree (`taskkill /T /F`), one retry for the known flaky signatures, `--port-offset`/`--block auto` that
   generates a `firebase.<block>.json` with shifted ports so worktrees run side by side. Docs: `firebase/README.md`,
   `.claude/docs/systems/social.md`.
4. AVD: reuse a running `api36`, else boot from the snapshot; `-no-snapshot` only for a clean boot
   (`adb-ui-test` skill, `windows_commands.md`).
5. `docs/social/README.md`: per-unit notes in `.claude/plans/social-fN/notes/uN.md`; only the closing unit folds them.
6. `.agents/profile.yml`: `bigTask.resources` (`fbemu: 3`, `emulator: 1`) and planner guidance.

### AI-kit (branch `big-task-speed`)

1. Keep-awake while a run holds the lock (`bt watch` spawns it; Windows `SetThreadExecutionState`, macOS `caffeinate`).
2. Liveness: `bt watch` and `orch-next` read run activity from the workflow transcripts, report a dead run within
   10 min and a silent agent after 15 min (stop-run → abandon → relaunch once, then escalate); sleep-aware.
3. `bt-run.sh` hard timeout with process-tree kill (exit 124, `timedOut` in telemetry); per-check `timeout` in
   `run-check` (recorded `timedOut` in the ledger); briefs forbid backgrounded tool calls.
4. `amend` / `plan-check` refuse a check amend past the unit's checks array; `bt equivalent` lets an implementer
   declare an equivalent mutant and the verifier rule on it inside the wave.
5. Parallelism: telemetry `width` counts `touches`/resource conflicts (the F2 wave 2 signal was a false positive);
   multi-instance resources (`bigTask.resources`) in the scheduler and the pool; planner guidance on bookkeeping docs,
   per-unit notes and `workMode`.
6. PushNotification on escalate, plan ready and final stop; document that a run dies with its chat.
7. Telemetry: stall gaps and bt-run timeouts; `node telemetry/export.mjs --all`.

## Open questions

- Lid close / power button still sleep the laptop: a power request only blocks idle sleep. The README says so.

## Verify

Numbers for cold build, Firebase stress run and AVD reuse; demos of a timed-out command with no leftover process or
port, an out-of-range amend refused, a dead run reported within 10 min, two Firebase suites on separate port blocks at
once; AI-kit suites green; both branches merged to `main`.
