# 05 — Permissions, onboarding & Google Play policy

> Every special permission this screen could need, how the user grants it, and the **distribution
> risk** that comes attached. This is where "technically possible" meets "will Google let me ship
> it."

## Permission inventory

| Permission / access | Needed for | How granted | Risk |
|---|---|---|---|
| `PACKAGE_USAGE_STATS` | all usage stats (per-app time, screen time, per-hour) | User toggles in **Settings → Special app access → Usage access** (deep-link via `Settings.ACTION_USAGE_ACCESS_SETTINGS`) | Low — must declare + disclose, but normal for wellbeing apps |
| `POST_NOTIFICATIONS` | usage-limit reminders | Runtime dialog (Android 13+) | None — **already in manifest** |
| `FOREGROUND_SERVICE` (+ `_SPECIAL_USE`) | keep monitoring alive | Manifest | None — **already present** ([service exists](../../app/src/main/AndroidManifest.xml)) |
| `BIND_ACCESSIBILITY_SERVICE` (enable our a11y service) | foreground-app detection, soft blocking, URL reading | User enables in **Settings → Accessibility** | **High** — see policy section |
| `SYSTEM_ALERT_WINDOW` (overlay) | block/friction screens (if not using `TYPE_ACCESSIBILITY_OVERLAY`) | **Settings → Display over other apps** (`ACTION_MANAGE_OVERLAY_PERMISSION`) | Medium |
| `BIND_VPN_SERVICE` | website/network blocking via local VPN | System consent dialog on first start | Medium — single-VPN conflict, "VPN active" icon |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | reliable background monitoring on OEM ROMs | Settings prompt | Medium — Play restricts; justify use |
| `DevicePolicyManager` device-owner | hard OS lock | ADB / MDM only | 🔴 not viable (see [`02`](02-blocking-and-reminders.md)) |

**Key takeaway:** there's a clean **permission cliff**. Everything up to and including *reminders*
needs only `PACKAGE_USAGE_STATS` + notifications — both low-risk. The moment we add *blocking*,
we cross into `AccessibilityService` / overlay / VPN territory, which is where Play policy and
OEM battery problems start. **Design the screen so that cliff is a deliberate, opt-in step.**

## Onboarding UX (the "locked state")

The Progress screen must handle "permission not yet granted" gracefully, per band:

- **Charts band:** our own focus/fail charts render with **no special permission**. Always show
  these.
- **App-usage band:** if `PACKAGE_USAGE_STATS` is off, replace the app list with a single card:
  *"Turn on Usage Access to see where your time goes →"* deep-linking to Settings. Re-check
  `onResume()` (the user comes back from Settings, not via a callback).
- **Blocking toggles:** greyed until the AccessibilityService is enabled; tapping one walks the
  user through enabling it, with the required plain-language disclosure first.

Pattern: a small `PermissionState` helper that exposes `hasUsageAccess()`, `hasOverlay()`,
`isAccessibilityEnabled()`, each re-evaluated in `onResume()`, and the ViewModel emits a sealed
UI state (Locked / Loading / Ready) per band.

## Google Play policy — the real constraint

### `PACKAGE_USAGE_STATS` — manageable

Allowed for legitimate wellbeing/parental use. You must complete the **Permissions declaration**
in Play Console and show an in-app disclosure of what you collect and why. Not a blocker.

### `AccessibilityService` — the sharp edge ⚠️

This is the policy that sinks app-blocker apps:

- Since **Nov 2021**, apps targeting API 31+ that ship an `AccessibilityService` must complete a
  **Play Console declaration**.
- Google's stance: the **accessibility-tool** classification is *only* for apps that genuinely
  help users with disabilities. A blocker is **not** that. So we must declare a non-accessibility
  use, add a **prominent in-app disclosure**, get **affirmative consent**, and possibly submit a
  **video** justifying the use.
- **"Deceptive or non-declared use … may result in suspension of your app and/or termination of
  your developer account."** Screen-time apps *do* exist on Play using accessibility, but they
  live under ongoing scrutiny and some have been pulled. This is a real, recurring risk — not
  hypothetical.

### Implications for distribution

| Build | Accessibility blocking OK? | Notes |
|---|---|---|
| Personal / sideload (debug APK) | ✅ Freely | We control the device; no policy. Great for v1 + dogfooding. |
| **F-Droid** | ✅ Generally | No accessibility-tool restriction like Play's |
| **Google Play** | ⚠️ With declaration + disclosure + consent, ongoing risk | Fine for measurement; risky for accessibility blocking |

**Recommendation:** build **measurement + reminders** (low-risk, Play-safe) as the shippable
core, and gate **accessibility blocking** behind an explicit opt-in that's safe to ship as a
sideload/F-Droid feature and only carefully on Play. Given this is currently a personal project
(`com.example.bbettercalendar`), the policy risk is **deferred, not blocking** — but bake the
disclosure/consent flow in from the start so a future Play release isn't a rewrite.

## Sources

- [Use of the AccessibilityService API — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Permissions and APIs that Access Sensitive Information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241)
- [Device and Network Abuse — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16559646)
- [Impact of Accessibility Permission in Android Apps — BrowserStack](https://www.browserstack.com/guide/accessibility-permission-in-android)
</content>
