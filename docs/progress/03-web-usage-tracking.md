# 03 — Per-website usage ("even webpages")

> The sketch and brief mention tracking **websites** (e.g. time on youtube.com), not just apps.
> This is the single **least reliable** request. Here's exactly why, and what we can honestly
> deliver.

## The core problem

Android gives us **no API for "time spent on a website."** Browser history is private to the
browser. There is no `WebStatsManager`. The OS only knows you spent X minutes in *Chrome* — not
that 25 of those were on youtube.com and 5 on wikipedia.org.

So the only way to attribute time to a domain is to **watch what the browser is showing in real
time** and infer it. That means `AccessibilityService`.

## The only viable technique: read the URL bar via AccessibilityService

When a known browser fires `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOW_CONTENT_CHANGED`, we walk
the `AccessibilityNodeInfo` tree and read the text of the **address-bar node**, identified by a
hardcoded resource ID per browser:

| Browser | URL-bar resource ID |
|---|---|
| Chrome | `com.android.chrome:id/url_bar` |
| Firefox | `org.mozilla.firefox:id/mozac_browser_toolbar_url_view` |
| Edge | `com.microsoft.emmx:id/url_bar` |
| Opera | `com.opera.browser:id/url_field` |
| DuckDuckGo | `com.duckduckgo.mobile.android:id/omnibarTextInput` |
| Brave / Vivaldi / Kiwi | Chromium-based, usually `…:id/url_bar` |

```java
AccessibilityNodeInfo root = getRootInActiveWindow();
List<AccessibilityNodeInfo> hits =
    root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar");
if (!hits.isEmpty()) {
    String shownUrl = String.valueOf(hits.get(0).getText());  // e.g. "youtube.com"
}
```

To turn URL sightings into **time per domain**, combine this with the foreground-interval logic
from [`01-usage-tracking.md`](01-usage-tracking.md): while the browser is foreground *and* the
last-seen URL bar is `youtube.com`, attribute that wall-clock to `youtube.com`. It's a manual
state machine we maintain ourselves.

## Why it's fragile (set expectations honestly)

- **Resource IDs break.** They're internal to each browser and can change on any update. We'd be
  maintaining a per-browser, per-version lookup table forever.
- **Known browsers only.** Anything not in our table → no data. Long tail of browsers unsupported.
- **The bar isn't always the real URL.** It may show a search term, a shortened/"pretty" URL, be
  empty while scrolled away, or hidden in fullscreen video — exactly the youtube.com case the
  user cares about.
- **In-app webviews don't count.** Links opened inside Instagram/Twitter's built-in browser have
  no standard URL-bar node.
- **It rides on the AccessibilityService**, inheriting all of its Play-policy weight
  ([`05`](05-permissions-and-play-policy.md)) and battery considerations.

## Honest recommendation

- **Don't promise per-website time in the MVP.** It's the feature most likely to feel broken.
- **Phase it in (Tier 3) only if** we already have the AccessibilityService for blocking — then
  reading the URL bar is "free" incremental work and we can offer **domain blocking** (redirect
  away from blocked domains) which is more valuable than domain *timing*.
- **Reframe the user's intent:** what they really want is "stop me doom-scrolling youtube.com."
  - **Blocking** a domain (bounce/cover when the URL bar matches) is achievable and useful.
  - A local **`VpnService`** DNS filter (see [`02`](02-blocking-and-reminders.md), Path C) blocks
    a domain *more reliably than the accessibility approach* because it works at the network
    layer for all browsers and even in-app webviews — at the cost of the VPN slot. For **website
    blocking specifically, VpnService is the better tool**; for website *timing*, accessibility
    is the only tool and it's shaky.

| Goal | Best mechanism | Reliability |
|---|---|---|
| Time spent per website | AccessibilityService URL-bar read | 🟡 shaky, known-browsers only |
| Block a website | `VpnService` DNS filter (preferred) or accessibility URL match | 🟢 / 🟡 |
| Block a website inside in-app browsers | `VpnService` only | 🟢 |

## Sources

- [Track web browser usage in Android using Accessibility Service — Medium](https://midagepro.medium.com/track-web-browser-usage-in-android-using-accessibility-service-800bfa2745d2)
- [Accessibility-Service-Browser-URL-Filter — GitHub](https://github.com/Amir-yazdanmanesh/Accessibility-Service-Browser-URL-Filter)
- [NetGuard — GitHub](https://github.com/M66B/NetGuard)
</content>
