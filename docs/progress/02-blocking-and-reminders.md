# 02 — Blocking apps & "remind me to close it"

> The sketch's left-column toggle (▣ / 🚫) and the idea of *"set a timer on Instagram → remind
> me to close it, or block it for the rest of the day."* This is the hardest, most
> permission-heavy, most policy-sensitive part. Read the verdict first, then the mechanics.

## Verdict up front

| What the user imagined | What's actually possible | How |
|---|---|---|
| Hard, un-bypassable "the app won't open" | 🔴 **Not for a normally installed app** | `DevicePolicyManager.setPackagesSuspended` needs **device-owner / profile-owner** |
| "Block app for the rest of the day" | 🟡 **Soft block** — detect it opening and bounce the user out / cover it | `AccessibilityService` + overlay |
| "After 30 min of Instagram, stop me" | 🟡 **Soft** usage-limit nudge | Monitor usage, then nudge/cover |
| "Add friction / a breathing pause" | 🟢 Yes (this is what *one sec* does) | Same overlay, gentler |
| "Remind me to close this app" | 🟢 Yes | A notification or overlay after N minutes |
| Block a **website** for the day | 🟡 Soft (accessibility) **or** local `VpnService` DNS filter | See [`03`](03-web-usage-tracking.md) |

The crucial mental model: **no normal Android app can prevent another app from launching.**
*one sec*, *ScreenZen*, *Stay Free*, *Opal*, *minimalist phone* — none of them do. They all
**detect** the distracting app coming to the foreground and immediately **draw something over
it** (a pause screen, a "you're blocked until tomorrow" card) and/or **send the user to the home
screen**. The block is psychological + friction, not a kernel-level lock. That's the bar we'd be
matching, and it's a perfectly good bar.

## Path A — the realistic one: AccessibilityService + overlay ("soft block")

This is the architecture behind essentially every Play-Store screen-time app.

### How it works

1. The user enables our **AccessibilityService** (one-time, in Settings → Accessibility).
2. The service receives `TYPE_WINDOW_STATE_CHANGED` events. Each carries the **package name** of
   whatever just came to the foreground — instantly, no polling.
3. We check that package against the user's block/limit rules.
4. If it's blocked (or over its limit), we **react**:
   - **Bounce:** `performGlobalAction(GLOBAL_ACTION_HOME)` → user is thrown to the launcher.
   - **Cover:** draw a full-screen overlay ("Instagram is blocked until tomorrow / breathe for
     10 s / 3h30 used today") on top of the app.
   - **Pause/friction:** show the overlay with a countdown and a "continue anyway / close" choice.

### Overlay specifics

- An `AccessibilityService` can draw overlays of type `TYPE_ACCESSIBILITY_OVERLAY` **without**
  the `SYSTEM_ALERT_WINDOW` permission — the accessibility grant covers it. This is the clean
  route and avoids a second scary permission.
- Alternatively, a plain `WindowManager` overlay using `SYSTEM_ALERT_WINDOW`
  (`Settings.canDrawOverlays()`, requested via `ACTION_MANAGE_OVERLAY_PERMISSION`) works too,
  but then you also need *something* to detect the foreground app (accessibility or usage-poll).
- Android 12+ lets a *target* app call `setHideOverlayWindows(true)` to hide non-system overlays
  over sensitive screens; some apps may dodge our overlay on certain screens. Bouncing to home
  is the robust fallback.

### Skeleton

```java
public class BlockerAccessibilityService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        Rule rule = rules.get(pkg.toString());
        if (rule != null && rule.isCurrentlyBlocked()) {
            performGlobalAction(GLOBAL_ACTION_HOME);   // bounce
            showBlockOverlay(rule);                      // and/or cover
        }
    }
    @Override public void onInterrupt() {}
}
```

```xml
<!-- res/xml/blocker_accessibility_config.xml -->
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:description="@string/blocker_accessibility_description"
    android:canPerformGestures="false" />
```

```xml
<!-- manifest -->
<service android:name=".blocking.BlockerAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/blocker_accessibility_config" />
</service>
```

### Limits & gotchas

- **The user can disable our accessibility service** in Settings at any time — that's by design
  and there's nothing (legitimate) we can do about it. A determined user always wins. Wellbeing
  is for the *willing*, not the adversarial.
- **OEM battery killers** (Xiaomi, Samsung, etc.) may kill the service. Mitigate with a
  foreground service (we already have [`HomeForegroundService`](../../app/src/main/java/com/example/bbettercalendar/ui/home/HomeForegroundService.java)
  to build on) + a battery-optimisation-exemption prompt.
- **Latency:** there's a brief moment where the blocked app is visible before we bounce/cover.
  Acceptable and normal.
- **Play policy:** an `AccessibilityService` used for blocking is **not** an accessibility-tool
  use; Google requires a prominent disclosure + consent + a Console declaration, and historically
  scrutinises/removes such apps. See [`05`](05-permissions-and-play-policy.md). For a personal /
  sideloaded / F-Droid build this is a non-issue.

## Path B — the "real lock": `DevicePolicyManager` (🔴 not for us)

`DevicePolicyManager.setPackagesSuspended(admin, packages, true)` genuinely freezes apps: a
suspended app can't start activities, its notifications hide, it vanishes from recents.
**But** it can only be called by a **device owner** or **profile owner**:

- **Device owner** is set only on a *fresh / unprovisioned* device via
  `adb shell dpm set-device-owner ...` or enterprise (MDM) enrollment — impossible to obtain by
  a user just installing us from the Play Store.
- **Profile owner** requires creating a managed **work profile** to put the apps in — heavy,
  intrusive, and not how a consumer wellbeing app behaves.
- Some packages can never be suspended (launcher, dialer, package installer, etc.).

So this gives a *true* lock but the provisioning cost makes it a non-starter for our audience.
Worth knowing it exists; not worth building for v1. (A power-user "advanced mode" that walks
through the ADB device-owner setup is theoretically possible but niche.)

## Path C — `VpnService` local filter (for network/website blocking)

A local-only `VpnService` (no remote server) can intercept the device's traffic and **drop
connections** to chosen hostnames/IPs — this is how NetGuard and several blockers cut off apps'
or websites' **network access** without accessibility. Good for **website blocking** (more
robust than reading the URL bar) and for "kill Instagram's network so it's useless." Trade-offs:

- Only **one** VPN can be active at a time → conflicts with a user's real VPN.
- Blocks the *network*, not the *time on screen* — the app still opens, just can't load.
- Shows the persistent "VPN active" key icon; needs the `BIND_VPN_SERVICE` consent dialog.
- More engineering than an overlay.

Best treated as an **optional, advanced website/network blocker**, complementary to Path A.

## "Remind me to close the app" (the timer idea) 🟢

This one is friendly and low-risk. Two flavours:

1. **Usage-limit reminder (no accessibility needed):** a foreground service periodically reads
   `UsageStatsManager` for today's time on the chosen app; when it crosses the user's limit, post
   a notification ("You've spent 30 min on TikTok — time to close it?"). Pure measurement +
   notification. We already hold `POST_NOTIFICATIONS`.
2. **Open-app reminder (needs foreground detection):** when the app is detected open (accessibility
   or usage-poll), start a countdown; after N minutes still-open, nudge with a notification or a
   gentle overlay. This is the *one sec* "you've been here a while" pattern.

For a first version, **(1) is the sweet spot**: it delivers the user's core wish ("set a timer
on Instagram, remind me") using **only the Usage Access permission they already granted for the
stats**, with **zero** accessibility/overlay/Play-policy exposure.

## Recommended layering

```
Tier 0 (MVP)      Measure usage  ............... PACKAGE_USAGE_STATS only
Tier 1 (easy win) Usage-limit reminders ........ + nothing (notifications we have)
Tier 2 (opt-in)   Soft block / friction screens  + AccessibilityService + overlay
Tier 3 (advanced) Website/network blocking ..... + VpnService   (or accessibility URL match)
Tier X (skip)     Hard OS lock ................. DevicePolicyManager device-owner — not viable
```

Each tier is independently shippable and independently gated by its own permission, so the
screen degrades gracefully when a permission is absent.

## Sources

- [DevicePolicyManager.setPackagesSuspended — Android Developers](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPackagesSuspended(android.content.ComponentName,%20java.lang.String[],%20boolean))
- [AccessibilityService — Android Developers](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Behavior changes: all apps (overlays, Android 12) — Android Developers](https://developer.android.com/about/versions/12/behavior-changes-all)
- [one sec — how the pause works](https://one-sec.app/) · [ScreenZen](https://screenzen.co/)
- [NetGuard (local VpnService firewall) — GitHub](https://github.com/M66B/NetGuard)
</content>
