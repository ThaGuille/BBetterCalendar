# Skills — BBetterCalendar

Guide to every skill available in this repo: what each one is *for*, everything it can do, and
which one fires for which kind of request.

**Platform reality check.** BBetter is **Java + Android XML Views** on Material Components
1.9.0 (min 21 / target 34). It is not Compose and not web. Most design skills in the ecosystem
were written for the web; the ones below have a **PROJECT OVERRIDE block** at the top of their
`SKILL.md` that redirects them to Android XML, the `bb_*` token system, and emulator
verification. Those blocks are local edits — see [Maintenance](#maintenance) before updating a
vendored skill.

---

## 1. Routing — which skill answers which request

There is no central router: **a skill's `description` field is the routing logic.** The
descriptions below have been rewritten so the lanes don't overlap. This table is the
human-readable version of that.

| You ask for… | Skill | Why it and not the neighbour |
|---|---|---|
| "make this screen better", "redesign X", "it looks generic", "add polish/motion/personality" | **impeccable** | Design authority. It *decides* and *edits*. |
| "what palette suits a calm productivity app?", "give me a font pairing", "which chart for this data?" | **ui-ux-pro-max** | You need a *value*, not a decision. |
| "what does MD3 say about elevation / type scale / colour roles?" | **material-3** | Spec authority. |
| *(attaches a screenshot)* "make it look like this" | **image-to-code** | A reference image is present. |
| "show me 3 directions for the timer screen" | **mobile-mockup** | You want to *look* at options first. Images only. |
| "design a logo", "app icon concepts", "brand board" | **brandkit** | Identity artefact, not app UI. |
| "does the app still run?", "click through Progress" | **adb-ui-test** (via `ui-tester`) | Runtime verification. |
| "build / lint / test" | **bb-build**, **check** | Gradle. |
| "propose a spec", "apply the spec" | **spec** | Change lifecycle. |
| "save this plan" | **save-plan** | Persistence. |

**Precedence when two could fire:**

1. A **reference image** in the request → `image-to-code` (it hands values back to `impeccable`).
2. The request asks to **see** options rather than change code → `mobile-mockup` / `brandkit`.
3. The request is a **narrow factual lookup** → `ui-ux-pro-max` or `material-3`.
4. **Everything else visual → `impeccable`.** It pulls the other three in as it needs them.

### Conflicts that were resolved

| Conflict | Resolution |
|---|---|
| `impeccable` ↔ `ui-ux-pro-max` — both claimed "designing, reviewing, fixing interfaces" | `ui-ux-pro-max` demoted to **lookup library**; its description now says explicitly it does not decide direction, critique, audit or edit. |
| `impeccable` ↔ `material-3` — both claimed components/theming/tokens/motion | `material-3` narrowed to **"what does the MD3 spec say"**. |
| `material-3` led with "Jetpack Compose" | Rewritten: spec is authoritative, **its Compose code samples must not be pasted in**. |
| `mobile-mockup` had `name: imagegen-frontend-mobile` ≠ its folder | Renamed to `mobile-mockup` — it was uninvokable before. |
| `mobile-mockup` defaulted to **iPhone** frames and iOS patterns | Project override: Android/Pixel frame, MD3 patterns, `bb_*` palette. |
| `brandkit` fired on any "design" request | Narrowed to identity artefacts only. |
| `impeccable` ↔ user-scope `frontend-design` plugin — two competing "design directors" | `impeccable` declared **design authority for this repo** in its override block. `frontend-design` is left installed (it is user-scope and affects your other projects) but yields here. |
| `impeccable` wanted to run `adb screencap` itself | Override delegates to the **`ui-tester` subagent** per project rule #7, keeping uiautomator dumps and logcat out of the main conversation. |
| `impeccable` wanted `PRODUCT.md` / `DESIGN.md` | Pointed at the real equivalents: `CLAUDE.md`, `.claude/docs/style_guide.md`, `.claude/docs/systems/`. |

---

## 2. Design skills

### `impeccable` — design authority
*Apache-2.0 · 67k ★ · [pbakaus/impeccable](https://github.com/pbakaus/impeccable) · v4.3.1*

The default for any visual request. Genuinely native-aware: ships `reference/android.md`
covering **Android Views** (not only Compose) with auditable rule IDs.

**23 commands**, invoked as `/impeccable <verb> [target]`:

| Category | Commands | What they do |
|---|---|---|
| **Build** | `shape` · `init` · `document` · `extract` | Plan UX/UI before code · capture product context · derive a design doc from existing code · pull reusable tokens/components into the system |
| **Evaluate** | `critique` · `audit` | UX review with heuristic scoring · technical checks (a11y, performance, responsive) — both have **native** variants |
| **Refine** | `polish` · `bolder` · `quieter` · `distill` · `harden` · `onboard` | Final pass before shipping · amplify bland UI · tone down loud UI · strip to essence · production-ready errors/i18n/edge cases · first-run flows, **empty states**, activation |
| **Enhance** | `animate` · `colorize` · `typeset` · `layout` · `delight` · `overdrive` | Purposeful motion · strategic colour in monochrome UI · type hierarchy and fonts · spacing, rhythm, hierarchy · personality and memorable touches · push past conventional limits |
| **Fix** | `clarify` · `adapt` · `optimize` | UX copy, labels, error messages · device/screen-size adaptation (**native** variant) · UI performance |
| *Inapplicable here* | `live` | Browser-only variant picking — there is no browser in this project |

**Also carries:** a *craft floor* (absolute bans and quality minimums loaded before any UI
edit), four modes (Persuade / **Operate** ← ours / Read / Experience), saturated-pattern
warnings, and `reference/ios.md` for comparison.

**Android rules it enforces** — adaptive Material navigation (bar / rail / drawer by width),
system Back and predictive Back, **edge-to-edge with window insets**, top app bar + single FAB,
**48×48dp touch targets** with 8dp separation, MD3 type scale in `sp`, Material colour roles,
dynamic colour, dark theme as first-class, **tonal elevation over arbitrary shadows**, Material
motion patterns (container transform, shared-axis, fade-through) honouring "remove animations".

> **Engine binary not installed.** `scripts/impeccable` would download it on first run and the
> author notes the Windows launcher is untested on real Windows. The skill has a documented
> fallback and the `reference/*.md` playbooks — the actual substance — work without it.

### `ui-ux-pro-max` — lookup library
*MIT · 126k ★ · [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)*

Offline, queryable design data. Run it, don't guess values.

```powershell
python .claude\skills\ui-ux-pro-max\scripts\search.py "calm productivity app" --design-system
python .claude\skills\ui-ux-pro-max\scripts\search.py "streak reward" --domain color
python .claude\skills\ui-ux-pro-max\scripts\search.py "weekly focus minutes" --domain chart
```

**Searchable domains:** `style` · `color` · `chart` · `landing` · `product` · `ux` ·
`typography` · `icons` · `gsap` · `react` · `web` · `google-fonts`

**Data it ships:** 79 styles (50 active) · **192 product palettes with contrast-corrected
pairs** · 74 font pairings · 119 UX guidelines · 105 icons · 25 chart types · 17 GSAP presets ·
22 stacks · plus `motion.csv`, `app-interface.csv` (mobile patterns), `ui-reasoning.csv`.

**Useful flags:** `--design-system` (complete recommendation in one shot) · `--variance 1-10`,
`--motion 1-10`, `--density 1-10` (taste dials) · `--persist --output-dir` (writes a MASTER.md
design system, with `--page` overrides) · `--stack jetpack-compose` (closest Android profile;
translate to XML) · `--json`, `--full`, `--format markdown`.

It auto-corrects failing contrast and tells you when it did (*"Accent adjusted from #F97316"*).

### `material-3` — MD3 spec authority
*MIT · 1.4k ★ · [hamen/material-3-skill](https://github.com/hamen/material-3-skill)*

Six reference documents: `color-system.md` · `typography-and-shape.md` ·
`component-catalog.md` (30+ components) · `theming-and-dynamic-color.md` ·
`layout-and-responsive.md` · `navigation-patterns.md`.

**Covers:** colour roles and tonal palettes, the full type scale, shape scale, elevation levels,
motion tokens with easing and durations, component anatomy and states, adaptive breakpoints,
dynamic colour, M3 Expressive, and an **audit mode** scoring 10 categories.

> Read the spec, translate to XML themes and `bb_*` tokens. **Do not paste its Compose samples.**
> Material Components is pinned at 1.9.0 — check availability before proposing a component.

### `image-to-code` — reference image → design system
*MIT · 996 ★ · [plugin87/ux-ui-agent-skills](https://github.com/plugin87/ux-ui-agent-skills) · adapted for this project*

Give it a screenshot of an app you like. It infers **palette, type scale, spacing rhythm,
radius/depth language and layout archetype**, maps them onto `bb_*` tokens and
`TextAppearance.BBetter.*`, checks WCAG AA in light *and* dark, then rebuilds in Android XML and
verifies on the emulator.

Matches the **system**, not pixels — and never reproduces the reference's logos, photos or copy.
Carries `reference/adapter-protocol.md`, `aesthetic-systems.md`, `design-taste.md`.

### `mobile-mockup` — screen concept images
*MIT · from [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) (86k ★)*

Generates **images only** of Android screen concepts and multi-screen flows, for choosing a
direction before writing XML. Covers onboarding, auth, home, detail, settings, empty and
success states, and multi-screen consistency. Android/Pixel framing enforced by project override.

### `brandkit` — identity artefact images
*MIT · same source*

Generates **images only**: logo concepts and lockups, brand-guideline boards, colour and type
specimen sheets, app-icon directions, identity decks, moodboards.

---

## 3. Reference libraries — `.claude/references/`

Knowledge, not invokable skills. Read the file that matches the problem.

### `interaction-design/` — 22 documents
*MIT · [Owl-Listener/designer-skills](https://github.com/Owl-Listener/designer-skills) (2.6k ★)*

**Motion & feedback:** `animation-principles` · `micro-interaction-spec` · `feedback-patterns` ·
`loading-states` · `interfaces-that-feel`
**Input & flow:** `gesture-patterns` · `form-design` · `navigation-patterns` · `search-ux` ·
`state-machine` · `onboarding-design` · `error-handling-ux` · `conversational-ux`
**UX laws** (the *why* behind the rules): `fitts-law` (target size/distance) ·
`hicks-law` (choice count) · `millers-law` (working memory) · `jakobs-law` (familiarity) ·
`doherty-threshold` (400ms response) · `teslers-law` (irreducible complexity) ·
`peak-end-rule` · `serial-position-effect` · `zeigarnik-effect` (unfinished tasks — directly
relevant to streaks and Pomodoro)

### `visual-critique/` — 7 lenses
`critique-visual-hierarchy` · `critique-composition` · `critique-color` ·
`critique-typography` · `critique-information-density` · `critique-affordance` ·
`critique-brand-consistency`

### `design-systems/` — 74 real brand design systems
*MIT · [VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md) (115k ★)*

`DESIGN.md` files reverse-engineered from shipped products — Apple, Linear, Stripe, Figma,
Notion, Vercel, Airbnb, Spotify, Claude, Cursor, Framer, Coinbase, IBM, and more, including
non-software systems (Ferrari, BMW, Bugatti) worth raiding for colour and type discipline.

Each has 9 sections: visual theme, palette, typography, component styling, layout principles,
depth/elevation, do's and don'ts, responsive behaviour, agent prompt guide.

> **Same format Impeccable consumes.** Point `/impeccable` at one to adopt its language —
> but tokens are CSS variables: the *values and roles* transfer, the CSS does not.

---

## 4. Project skills (pre-existing)

| Skill | Covers |
|---|---|
| **adb-ui-test** | Drive the emulator over adb — navigate by resource-id via uiautomator XML, scan `logcat -b crash` for `FATAL EXCEPTION`, record/replay flows, `capture-screen.ps1` for a real PNG when a visual judgement is needed. Reach it through the **`ui-tester` subagent**, not inline. |
| **bb-build** | `gradlew.bat` on Windows: assemble, test, lint, clean; handles stuck daemon and OneDrive lock. |
| **check** | On-demand build + lint verification. |
| **spec** | Change lifecycle: propose → apply → verify → archive under `.claude/specs/`. |
| **save-plan** | Persist a design/implementation plan to `.claude/plans/<slug>.md`. |

---

## 5. Known gaps

Verified against the ecosystem — **no skill exists for these**, because
[`android/skills`](https://github.com/android/skills) (Google, official) explicitly excludes
design, and every design skill targets web or Compose:

1. **Android XML implementation** — nothing emits `res/layout` XML against a token system.
2. **Android motion** — every motion skill is Framer Motion / CSS. This repo has **no `res/anim`
   directory at all**. MotionLayout, Transition API, `ObjectAnimator` and MD3 easing are uncovered.
3. **Icons** — icon skills emit web SVG/React. Android needs vector drawables from Material Symbols.
4. **Charts** — `MPAndroidChart` styling has no skill; `ui-ux-pro-max --domain chart` gives the
   chart *choice*, not the Android implementation.
5. **Sound** — nothing in the ecosystem; the app already has `SoundFeedback` / `HapticFeedback`.

These want project-local skills.

---

## Maintenance

Skills in `impeccable/`, `ui-ux-pro-max/`, `material-3/`, `image-to-code/`, `mobile-mockup/` and
`brandkit/` are **vendored copies** with local edits — the PROJECT OVERRIDE blocks and the
rewritten `description` fields that make routing work.

**Re-cloning upstream will silently destroy the routing and the Android overrides.** After any
update, re-apply the override block and re-check the description lanes against §1.

Impeccable's plugin hooks (`PostToolUse`, `Stop`) were deliberately **not** installed: they are
POSIX shell and would collide with the existing `verify-ui-reminder.ps1` Stop hook.
