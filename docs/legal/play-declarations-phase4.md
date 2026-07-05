# Play Console declarations — Phase 4a (Accessibility-based limit enforcement)

**Status:** drafted (in-repo), **not submitted**
**Last updated:** 2026-07-04
**Applies to:** the `BlockerAccessibilityService` shipped in the `progress-phase4a-blocking` change.

> This file is the **prepared text** for the Play Console steps that are a manual, external task
> (the user's — Phase 4b closes them). The privacy-policy source of truth is
> [`privacy-policy.md`](privacy-policy.md); this file only holds the Console-specific answers plus
> the demo-video shot-list Google requires for accessibility-using apps. Keep both in sync.

The `IsAccessibilityTool` / `AccessibilityServices` policy is the **known high-risk review item**
(see [`docs/progress/07-legal-and-compliance.md`](../progress/07-legal-and-compliance.md) §2). The
mitigations baked into the build are: `android:isAccessibilityTool="false"`, a minimal event mask
(`typeWindowStateChanged` only), `canRetrieveWindowContent="false"`, no gesture capability, a
prominent in-app disclosure + stored affirmative consent, and an on-device-only invariant.

---

## 1. Accessibility API — "Prominent disclosure & use" declaration

**Does your app use the AccessibilityService API?** Yes.

**Is your app an accessibility tool (for users with disabilities)?** **No.** It is a
digital-wellbeing / self-control feature. This is declared honestly in the manifest config
(`android:isAccessibilityTool="false"`).

**What is the functionality that the AccessibilityService enables?**
Enforcing the user's own per-app daily time limits. When a user (a) sets a daily limit on an app,
(b) turns on "Enforce daily limit" for that app, and (c) grants the Accessibility permission, the
service detects when that app comes to the foreground and — only once the app has passed the limit
the user set — covers it with a full-screen reminder for the rest of the day.

**Why does the feature require the AccessibilityService API (vs. an alternative)?**
Reliable detection of *which app is currently in the foreground*, in real time, from the background,
is only available to an Accessibility service on modern Android. `UsageStatsManager` (which the app
already uses for measurement) is a lagging, poll-based aggregate and cannot drive a timely cover at
the moment of foregrounding. No window content is read — only the foreground package name.

**What user data does the service access, and how is it handled?**
Only the foreground app's **package name**, via `TYPE_WINDOW_STATE_CHANGED` events. It does **not**
retrieve window content, read on-screen text, or capture input (`canRetrieveWindowContent="false"`,
no `flagRetrieveInteractiveWindows`, no gesture flags). Nothing is transmitted off the device.

**How does the user consent / how can they turn it off?**
A prominent in-app disclosure (`AccessibilityDisclosureDialog`) is shown and an affirmative "Enable"
is recorded (`ConsentRecord("accessibility_blocking", …)`) **before** the app deep-links to the
system Accessibility settings, where the user makes the actual grant. The user can pause all
enforcement from the Progress screen without revoking, or revoke fully in Settings → Accessibility.

---

## 2. Data safety — deltas vs the Phase 2/3 form

Phase 4a adds **no new collected or shared data type** and **no network transmission**. The existing
[`privacy-policy.md` Appendix B](privacy-policy.md) answers stand, with these clarifications:

- **App activity → App interactions:** the set of foreground-app-package signals the service reacts
  to is processed **ephemerally, on-device**, to make the block decision. It is **not stored**
  (beyond the transient in-memory "blocked today" latch) and **not shared or transmitted**.
- **No new data types** (no screen content, no text, no keystrokes, no location) are accessed.
- **Encrypted in transit?** Not applicable — still no off-device transmission.
- **Deletion:** unchanged — clearing app storage / uninstalling removes the local DB (tracked set,
  consent records); revoking the Accessibility permission stops all foreground signals.

## 3. Foreground service — not applicable

Phase 4a ships **no** foreground service and requests **no** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
A bound AccessibilityService is OS-managed; the battery-exemption prompt + keep-alive service were
**deferred** ([`docs/defered.md`](../defered.md)). No foreground-service type declaration is needed
for the blocking feature. (The unrelated Pomodoro `HomeForegroundService` keeps its existing
`specialUse` declaration.)

---

## 4. Demo-video shot-list (required for accessibility-using apps)

Google asks for a short screen recording showing the accessibility feature in context. Suggested
shots (the user records + uploads — Phase 4b):

1. **Set a limit.** Progress screen → tap a tracked app row → set a short daily limit (e.g. 1 min) in
   the limit dialog → Save.
2. **Turn on enforcement.** Tap the 🚫 "Enforce daily limit" toggle on that row → the prominent
   disclosure dialog appears → read it on camera → tap **Enable**.
3. **Grant the permission.** The app deep-links to Settings → Accessibility → BBetter → toggle on →
   show the system's own accessibility warning being accepted → return to the app.
4. **Trigger the block.** Open the limited app and use it past the limit → the full-screen cover
   appears with the app name, "Daily limit reached", and "Xh Ym used today".
5. **Close / bounce.** Tap **Close** on the cover → it returns to the home screen.
6. **Show the off-switch.** Back in BBetter, tap the master "Enforcing app limits · tap to pause"
   control → show enforcement paused; and Settings → Accessibility → BBetter as the full revoke path.

Keep the clip focused on this single feature; narrate that no screen content is read — only the
foreground app name — to reinforce the `isAccessibilityTool="false"` positioning.
