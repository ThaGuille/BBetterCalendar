# Deferred features

> Things we consciously decided **not** to build right now, but might revisit. One row per item:
> what it is, why it was deferred, and the trigger that would make us pick it up. Keep this current
> when a phase defers something — it's the memory for "why isn't X in here yet."
>
> This is distinct from **dropped** items (bottom section), which we do not intend to revisit.

## Deferred — may revisit

| Feature | Deferred in | Why deferred | Revisit when |
|---|---|---|---|
| **Battery-optimisation exemption prompt + foreground-service keep-alive** for the blocking AccessibilityService | Phase 4a (2026-07-04) | The exemption prompt is explicitly *optional* per [`07-legal-and-compliance.md`](progress/07-legal-and-compliance.md); bound accessibility services are OS-managed and reasonably resilient. Adds scary friction (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + a Play justification) for uncertain benefit. | On-device evidence that OEM battery-killers (Xiaomi / Samsung / etc.) are actually killing the service and letting blocked apps through. |
| **Cover "continue anyway" / snooze escape hatch** (soft-limit *one-sec* pattern) | Phase 4a (2026-07-04) | We chose a **hard cover + Close** (bounce to home), reset only at the daily boundary. Simpler state, firmer block. | If the hard cover proves too rigid in daily use and we want a gentler friction tier. |
| **Website / network blocking via `VpnService`** | Roadmap (2026-06-28) | Websites descoped — apps only. Local VPN conflicts with a user's real VPN, shows the persistent "VPN active" icon, and is more engineering than the overlay. Kept as reference in [`02-blocking-and-reminders.md`](progress/02-blocking-and-reminders.md) Path C / [`03`](progress/03-web-usage-tracking.md). | If we decide to bring back website limits; the Phase-4 accessibility service would make URL-bar reading incremental. |

## Dropped — not planned to revisit

| Feature | Dropped in | Why |
|---|---|---|
| **Instant-block toggle** ("block this app right now for the rest of today", independent of any limit) | Phase 4 (2026-07-04) | User decision: blocking is driven **only** by the per-app daily time limit. No separate insta-block path. The staged `AppRule.instantBlock` column becomes unused (or repurposed as the per-app "enforce at limit" flag — decided in the Phase 4a spec). |
| **Hard OS lock via `DevicePolicyManager` device-owner** | Roadmap (2026-06-28) | Requires device-owner / profile-owner provisioning (ADB or MDM) — not obtainable by a Play-Store install. Non-starter for a consumer app. See [`02`](progress/02-blocking-and-reminders.md) Path B. |
