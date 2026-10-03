# Design System: BBetter — Calm Focus, Earned Energy

Android study companion for students aged ~16–25: Pomodoro focus timer, study streaks, projects with
subtasks, calendar, and a light social layer (friends studying, small study challenges).
Source references: `.claude/references/inspiration/refs.md`.

## 1. Visual Theme & Atmosphere

A **deep forest-green night**: calm, quiet and focused while the student studies, then warm and
celebratory only when something is earned. The background is a dark, saturated pine green rather than
black or grey, so it feels alive and natural, like a forest at dusk. The interface stays near-monochrome
green at rest, and a single honey-amber reward colour lights up for streaks, completed sessions and
achievements.

Personality: **calm · focused · rewarding**. Never childish, never corporate, never noisy.
Density: balanced daily-use app (5/10). Variance: mostly orderly, with one hero element per screen
(4/10). Motion: restrained; celebration moments only (3/10).

**Key characteristics:**
- Dark pine-green canvas, with lighter green tonal cards (tonal elevation, not drop shadows)
- One hero element per screen: the timer number, the weekly total, the celebration headline
- Big, confident numbers with tabular figures, and small quiet labels above them
- Honey amber is rare and meaningful: streak flame, "today" chart point, celebration, rank highlight
- Material 3 Android patterns: bottom navigation bar, top app bar, a single FAB where needed

## 2. Color Palette & Roles

### Foundation (dark-first)
- **Deep Pine Night** (#12201C) – App background. Dark saturated green, never pure black.
- **Pine Card** (#1B2D27) – Cards, sheets and the bottom navigation bar surface.
- **Raised Moss** (#24392F) – Raised or selected surfaces: expanded project card, active chip, input fields.
- **Hairline Moss** (#2E453B) – Dividers, chart gridlines, outlines of unselected chips.

### Brand & Interactive
- **Sage Leaf** (#7FB69A) – Primary colour: primary buttons, active navigation icon, chart line and
  area fill (at ~25% opacity), progress rings, checkboxes. Text on Sage Leaf uses Deep Pine Night.
- **Forest Deep** (#2F4F4A) – Tinted containers, such as the chip background behind "Concentration".

### Reward (use sparingly)
- **Honey Amber** (#F2B544) – Earned moments only: streak flame and count, the current or best point on
  a chart, the celebration headline, a friend's rank badge, "new record" tags. Text on it uses Deep Pine Night.

### Text
- **Mist White** (#EAF2EC) – Primary text and big numbers.
- **Lichen Grey** (#9DB3A8) – Secondary labels, axis labels, timestamps, inactive navigation labels.

### Data & State
- **Dusk Blue** (#8FA8D8) – The comparison series in charts (the previous period). Never a primary action.
- **Ember Red** (#E8796B) – Destructive actions and errors only.

All text pairs meet WCAG AA on Pine Card (the lowest is Ember Red at 5.1:1).

## 3. Typography Rules

**Font family:** Plus Jakarta Sans for everything (400 / 500 / 600 / 700 / 800). No serif.
Numbers use tabular figures.

- **Timer / hero number:** 72–96sp, weight 700, tight tracking (−1%). Mist White.
- **Big stat numbers** ("5h 52m", "26 weeks"): 26–32sp, weight 700.
- **Screen title:** 22sp, weight 700.
- **Section / card title:** 17sp, weight 700.
- **Small label above a number** ("Focus time", "Your streak"): 13sp, weight 500, Lichen Grey.
- **Body:** 15sp, weight 400, line height 1.45.
- **Chip / caption:** 12sp, weight 600; uppercase only for tiny status chips.

## 4. Component Stylings

- **Cards:** Pine Card fill, 20dp corner radius, 16–20dp padding, no shadow and no border.
  Separation comes from the tonal step against the background.
- **Primary button:** a full-width pill (fully rounded), Sage Leaf fill with Deep Pine Night text, 52dp tall.
  On celebration screens the button can be Honey Amber.
- **Secondary button:** a pill with a Hairline Moss outline and Mist White text.
- **Chips / segmented control:** pills. Selected chip is Sage Leaf fill with dark text; unselected is a
  Hairline Moss outline.
- **Bottom navigation:** 4 destinations (Home, Progress, Calendar, Projects), outline icons with labels.
  The active one is Sage Leaf.
- **Play / pause button:** a large circle (72dp), Sage Leaf fill with a dark icon.
- **Charts (Strava style):** ONE hero series drawn as a 2.5dp Sage Leaf line with a soft Sage area
  fill below it. Small hollow circle markers. The latest point is filled Honey Amber with a soft halo.
  An optional comparison series in Dusk Blue, line only, with no fill. Faint horizontal gridlines
  (Hairline Moss) with only 2–3 value labels on the right edge. Month or day labels below. Big totals sit
  ABOVE the chart, never inside it.
- **Streak calendar:** a week row of circles. Days with a session are filled with a small leaf/check
  icon; others are outlined with the date number. A tall pill on the right shows a flame and the
  streak count in Honey Amber.
- **Avatars (friends):** 40dp circles with initials or a simple illustration on a tonal background.
  When studying now, the avatar has a Sage ring and the name is bright. When away, it is dimmed to 40%.

## 5. Layout Principles

- An 8dp grid. 16dp side margins. 12–16dp between cards.
- One hero per screen, placed high; supporting cards stack below it in a single column.
- Lists are scannable: a title on the left, meta on the right, a progress bar or count under the title.
- Touch targets are at least 48dp.
- Generous breathing room around the hero number; density increases further down the screen.

## 6. Design System Notes for Stitch Generation

- Platform: **Android phone**, Material 3 conventions, dark theme. Never iOS idioms.
- Use the real app copy given in each prompt. No lorem ipsum.
- Illustrations: at most one small, simple, flat leaf/tree motif. No mascots and no cartoon characters.

### Anti-patterns (banned)
- Pure black backgrounds, grey "dead" dark mode, purple or neon gradients
- More than one accent colour on a screen (Honey Amber is the only warm colour)
- Multi-colour dashboards with several chart types side by side (the Oura/Metriport look)
- Confetti and XP badges everywhere: celebrate only real milestones
- Cream background with a serif display font and terracotta (the old BBetter look)
- Emoji as icons; drop shadows on cards
