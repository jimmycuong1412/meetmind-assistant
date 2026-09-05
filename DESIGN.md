# MeetMind Design System

> **Status:** Target specification. The code has **not** been migrated yet — it currently ships
> the Teams slate-violet palette (`#6264A7`). This document defines where we are going and is the
> source of truth for the retheme. See [§10 Migration state](#10-migration-state) for what is done.
>
> **Scope:** Android / Jetpack Compose, Material 3. Every color below is expressed as an M3 role
> so it can be dropped into `lightColorScheme()` / `darkColorScheme()` directly.
>
> Last updated: **2026-09-06**

---

## 1. Intent

A warm, paper-toned interface for an offline meeting recorder. The app holds people's private
conversations on their own device; it should feel like a well-made notebook, not a cloud dashboard.

Three rules drive every decision below:

1. **Warm neutrals only.** Every gray carries a yellow-brown undertone. No cool blue-grays anywhere.
   This is the single most important rule — mixing warm and cool neutrals is what makes a partial
   migration look broken.
2. **One accent.** Terracotta. Color is not the primary way we distinguish things; type, spacing
   and iconography are. See [§6](#6-recording-mode-colors) for the one place this is under tension.
3. **Containment over elevation.** Borders and hairline rings, not drop shadows.

### What this replaces

The previous version of this file described Claude's *marketing website* — web `px` type scales up
to 64px, three custom Anthropic typefaces, CSS ring shadows, and no Material 3 role mapping or dark
scheme. It was referenced by nothing in the repo and could not be implemented as written. This
rewrite keeps the palette's intent and discards the web-specific parts.

---

## 2. Color

### 2.1 Raw palette

These are the source values. **Do not reference them directly from UI code** — always go through
`MaterialTheme.colorScheme`. They exist as named constants in `Color.kt` only so the schemes below
can be assembled from them.

| Name | Hex | Role in the system |
|---|---|---|
| Parchment | `#F5F4ED` | Light background — warm cream, the emotional foundation |
| Ivory | `#FAF9F5` | Light card / elevated surface |
| Warm Sand | `#E8E6DC` | Light secondary container, prominent interactive surfaces |
| Border Cream | `#F0EEE6` | Lightest divider |
| Terracotta | `#C96442` | Brand accent — **large text and non-text only**, see §2.4 |
| Terracotta Deep | `#B35334` | Primary role — the AA-safe variant that carries white text |
| Coral | `#D97757` | Dark-scheme accent, text links on dark |
| Near Black | `#141413` | Primary text (light) / background (dark) — warm, olive-tinted |
| Dark Surface | `#1E1D1B` | Dark card surface |
| Dark Elevated | `#30302E` | Dark elevated container, dark dividers |
| Charcoal Warm | `#4D4C48` | Strong secondary text |
| Olive Gray | `#5E5D59` | Standard secondary text |
| Stone Gray | `#87867F` | Tertiary text, outlines |
| Warm Silver | `#B0AEA5` | Secondary text on dark |
| Crimson | `#B53333` | Error (light) |
| Salmon | `#F0A094` | Error (dark) |

### 2.2 Light scheme

```kotlin
primary              = #B35334   onPrimary            = #FFFFFF
primaryContainer     = #F2DDD3   onPrimaryContainer   = #4A1D0C
secondary            = #5E5D59   onSecondary          = #FFFFFF
secondaryContainer   = #E8E6DC   onSecondaryContainer = #33322E
tertiary             = #6B705C   onTertiary           = #FFFFFF
tertiaryContainer    = #E3E5DA   onTertiaryContainer  = #2A2D23
error                = #B53333   onError              = #FFFFFF
errorContainer       = #F7DDD9   onErrorContainer     = #4A1210

background           = #F5F4ED   onBackground         = #141413
surface              = #FAF9F5   onSurface            = #141413
surfaceVariant       = #E8E6DC   onSurfaceVariant     = #5E5D59

surfaceContainerLowest  = #FFFFFF
surfaceContainerLow     = #FAF9F5
surfaceContainer        = #F2F1E9
surfaceContainerHigh    = #ECEBE1
surfaceContainerHighest = #E8E6DC

outline              = #87867F   outlineVariant       = #DEDCD1
scrim                = #141413
```

### 2.3 Dark scheme

Not a naive inversion. Terracotta darkens badly against warm near-black, so the dark scheme uses
the lighter Coral family — this is why `#D97757` exists.

```kotlin
primary              = #E08A68   onPrimary            = #3D1A0B
primaryContainer     = #7A3A22   onPrimaryContainer   = #F7DDD1
secondary            = #C9C6BC   onSecondary          = #33322E
secondaryContainer   = #45443F   onSecondaryContainer = #E8E6DC
tertiary             = #BFC4B0   onTertiary           = #2A2D23
tertiaryContainer    = #414539   onTertiaryContainer  = #E3E5DA
error                = #F0A094   onError              = #3D0F0C
errorContainer       = #7A2320   onErrorContainer     = #F7DDD9

background           = #141413   onBackground         = #ECEAE4
surface              = #1E1D1B   onSurface            = #ECEAE4
surfaceVariant       = #45443F   onSurfaceVariant     = #B0AEA5

surfaceContainerLowest  = #0F0F0E
surfaceContainerLow     = #1A1917
surfaceContainer        = #1E1D1B
surfaceContainerHigh    = #30302E
surfaceContainerHighest = #3A3A37

outline              = #9A978E   outlineVariant       = #45443F
scrim                = #000000
```

### 2.4 Contrast — verified, not assumed

All pairs below were computed against WCAG 2.1. **AA body text requires 4.5:1; non-text and large
text require 3.0:1.**

| Pair | Light | Dark |
|---|---|---|
| `onPrimary` on `primary` | 5.00 | 5.93 |
| `onBackground` on `background` | 16.72 | 15.32 |
| `onSurface` on `surface` | 17.50 | 14.00 |
| `onSurfaceVariant` on `surface` | 6.26 | 7.57 |
| `onPrimaryContainer` on `primaryContainer` | 10.91 | 6.60 |
| `onSecondaryContainer` on `secondaryContainer` | 10.26 | — |
| `error` on `background` | 5.46 | 8.92 |
| `onError` on `error` | 6.02 | 7.99 |
| `outline` on `background` (3.0 target) | 3.31 | 6.31 |

> ### ⚠ The terracotta trap
>
> **Pure Terracotta `#C96442` cannot carry white body text.** White on `#C96442` is **3.90:1** —
> it fails AA. The previous version of this document specified `#C96442` as the primary CTA color,
> which would have shipped a failing contrast on every primary button in the app.
>
> The resolution:
> - **`#B35334` (Terracotta Deep) is the `primary` role.** White on it is 5.00:1. Use it for
>   filled buttons, FABs, and anything with text on top.
> - **`#C96442` stays the brand accent** for icons, focus rings, active-state strokes, chart
>   marks, and large display text (≥24sp) — all of which only need 3.0:1. On parchment it is
>   3.54:1, which clears that bar.
>
> Never put small text on `#C96442`.

---

## 3. Typography

### 3.1 The serif problem

The previous doc specified "Anthropic Serif". **That typeface is not licensable for this app** and
was never a real option. This app is Apache-2.0 and ships fonts as bundled `.ttf` in `res/font/`
(there is no Downloadable Fonts setup), so any face must be redistributable.

**Decision: Source Serif 4** (SIL OFL 1.1) for the display role.

Why this one over the alternatives:

- An Adobe open-source family designed **for screen use**, not a print revival — it holds up at
  20–32sp on a phone, which Georgia-alikes and Playfair do not.
- **OFL-1.1** (confirmed at [adobe-fonts/source-serif](https://github.com/adobe-fonts/source-serif)),
  redistributable in an Apache-2.0 app with attribution.
- Its warm, slightly humanist axis sits naturally on parchment; it does not read as "newspaper".
- Variable-weight `.ttf` available, so one file covers 400–600 like `space_grotesk_bold.ttf`
  already does.

Runner-up if a second opinion is wanted: **Literata** (OFL, Google, designed for Play Books) —
warmer and rounder, slightly more informal.

Add as `app/src/main/res/font/source_serif_4.ttf` and register in the licenses screen (see §9).

### 3.2 Roles

Three families, each with one job:

| Family | Role | Where |
|---|---|---|
| **Source Serif 4** | Display / headline | Screen titles, empty-state headlines, onboarding, section anchors |
| **Inter** | UI / body | Everything else — buttons, labels, list rows, settings, body copy |
| **Space Grotesk** | Wordmark only | The "meetmind" app title in the top bar and welcome screens |

`JetBrains Mono` is **removed** — it currently has zero usages despite shipping two `.ttf` files
(see §9).

### 3.3 Scale

Only the roles that change from the current `Type.kt` are listed; unlisted roles keep their
existing Inter definition.

| M3 role | Family | Size | Weight | Line height | Tracking |
|---|---|---|---|---|---|
| `displayLarge` | Source Serif 4 | 44sp | 500 | 50sp | -0.5sp |
| `displayMedium` | Source Serif 4 | 36sp | 500 | 42sp | -0.25sp |
| `displaySmall` | Source Serif 4 | 30sp | 500 | 38sp | 0 |
| `headlineLarge` | Source Serif 4 | 28sp | 500 | 36sp | 0 |
| `headlineMedium` | Source Serif 4 | 24sp | 500 | 32sp | 0 |
| `headlineSmall` | Source Serif 4 | 21sp | 500 | 28sp | 0 |
| `titleLarge` | Inter | 22sp | 500 | 28sp | 0 |
| *(title/body/label)* | Inter | *unchanged* | | | |

Note the display sizes are pulled well down from the previous doc's 64px/52px web figures — those
were desktop-hero sizes and are unusable on a 360dp-wide phone.

`Typography.transcription` stays **Inter 15sp/24sp**. Transcript text is long-form reading; the
monospace idea was tried and abandoned, and the code already reflects that. The stale header
comment in `Type.kt` describing a "two-font strategy" with JetBrains Mono should be deleted.

---

## 4. Shape

Current `AppShapes` is already correct for this system and does **not** change:

```kotlin
extraSmall = 4.dp    small = 8.dp     medium = 16.dp
large      = 20.dp   extraLarge = 28.dp
```

---

## 5. Elevation and containment

**Rule: containment is drawn, not lit.** M3 elevation is disabled; separation comes from a hairline
border plus a surface-tone step.

| Instead of | Use |
|---|---|
| `Card(elevation = 2.dp)` | `Card(colors = surfaceContainer, border = 1.dp outlineVariant)` |
| `Surface(tonalElevation = 3.dp)` | `Surface(color = surfaceContainerHigh)` |
| `shadowElevation` | nothing — or a 1dp `outlineVariant` ring |

Exceptions where elevation stays: **FAB, modal bottom sheets, menus, and dialogs** — floating
things that genuinely sit above the page and need to read that way.

There are currently 31 elevation settings in the UI (20 `defaultElevation`, 9 `tonalElevation`,
2 `shadowElevation`) to work through.

---

## 6. Recording mode colors

**This is the open design question, and it is the one that determines whether the retheme actually
lands.** It needs a decision before the palette swap.

Six recording modes each currently own a saturated two-stop gradient plus a flat tint — sky blue,
brand purple, amber, emerald, rose, teal — used **44 times** across the UI. They are the most
visually dominant color in the app. They also directly contradict rule 2 in §1, and the "color
restraint" comment already sitting at the top of `Color.kt`.

Dropping six saturated hues onto parchment unchanged produces a warm background with the old
palette still sitting on top of it. Three ways out:

### Option A — Rewarm (lowest risk)

Keep one hue per mode, pulled toward the earth range. Modes stay colour-coded; the palette holds.

| Mode | Now | Proposed | on parchment | Safe for |
|---|---|---|---|---|
| Simple Listening | Sky `#0EA5E9` | Slate Blue `#5B7B8A` | 4.10 | icon / stroke |
| Short Meeting | Purple `#6264A7` | Terracotta `#C96442` | 3.54 | icon / stroke |
| Long Meeting | Amber `#F59E0B` | Ochre `#B3811F` | 3.13 | icon / stroke |
| Translation | Emerald `#10B981` | Sage `#6B8F6B` | 3.30 | icon / stroke |
| Interview | Rose `#F43F5E` | Clay `#A65543` | 4.76 | icon / stroke / **text** |
| English Coach | Teal `#14B8A6` | Moss `#5F7355` | 4.68 | icon / stroke / **text** |

**All six clear 3.0:1, so they are safe as icon fills, gradient stops and active strokes. Only
Clay and Moss clear 4.5:1 for label text.** The current code uses the `Mode*Tint` values for
small text labels in several places — under Option A those labels must switch to
`onSurfaceVariant`, with the mode hue carried by the icon beside them.

Risk: Clay and Terracotta are close enough to be confusable; Sage and Moss likewise. Six earth
tones have less separation than six saturated hues, which weakens the colour-coding exactly where
it is load-bearing.

### Option B — Icon-led, single accent (most faithful to §1)

Modes are distinguished by **icon and label**, not hue. Everything uses terracotta. Colour returns
as a single accent for the active/recording state.

Strongest adherence to the design intent and the biggest simplification — deletes six gradients and
six tints. But it is a real product change: mode identity currently reads at a glance from colour
alone, and this removes that.

### Option C — Two-tier

Terracotta for the two core meeting modes; muted earth tones for the four specialised ones. Keeps
glanceable separation where it matters most and reduces the palette without flattening it.

**Recommendation: Option C**, falling back to A if mode recognition testing shows users rely on the
colour more than expected. Option B is the purest but should not be chosen without checking how
people actually navigate the mode picker.

> Whichever is chosen, `DaytimeSkyBanner` needs its own pass — it hardcodes 13 colors across five
> time-of-day gradients (night indigo, dawn, morning, afternoon, dusk) plus sun, moon and hillside
> fills. It is the most theme-resistant component in the app.

---

## 7. Semantic and data color

### 7.1 Status

| Meaning | Light | on parchment | Dark | on `#141413` |
|---|---|---|---|---|
| Success | `#4F7A4F` | 4.50 | `#8FB88F` | 8.27 |
| Warning | `#8F6415` | 4.76 | `#D9AE5C` | 8.92 |
| Error | `#B53333` | 5.46 | `#F0A094` | 8.92 |

Semantic color is **separate from the accent** and never substitutes for it.

> The obvious warm warning color — ochre `#B3811F` — is only **3.13:1** on parchment and fails AA.
> `#8F6415` is the darkest point that still reads as ochre rather than brown while clearing 4.5:1.
> Same trap as §2.4: warm mid-tones look darker than they measure.

### 7.2 Speaker colors

`speakerColor()` in `SessionDetailsScreen.kt:1696` currently hashes a speaker name into five raw
Material 2 primaries (`#2196F3`, `#4CAF50`, `#FF9800`, `#9C27B0`, `#F44336`). These are unthemed,
cool, and marginal for small text on a light ground.

Replacement — six warm hues, each verified ≥4.5:1 on both `#FAF9F5` and `#1E1D1B` where used as
label text:

| # | Light | Dark |
|---|---|---|
| 1 | `#B35334` Terracotta Deep | `#E08A68` |
| 2 | `#4F6F82` Slate Blue | `#8FB0C4` |
| 3 | `#5A7A50` Moss | `#9BBE90` |
| 4 | `#8A6A2F` Ochre | `#D4B36A` |
| 5 | `#7A4E6B` Plum | `#C495B4` |
| 6 | `#5E5D59` Olive Gray | `#B0AEA5` |

Keep the deterministic hash so a speaker keeps their color across a session, but index into the
scheme-appropriate list.

---

## 8. Motion

Unchanged from Material 3 defaults. This system's character comes from color and type, not motion.
Respect `Settings.Global.ANIMATOR_DURATION_SCALE`.

---

## 9. Asset hygiene

Found during the audit, to be resolved as part of this work:

- **`JetBrainsMonoFamily` has zero usages** — remove `jetbrainsmono_regular.ttf` and
  `jetbrainsmono_medium.ttf`.
- **`coral_pixels.ttf`, `poller_one.ttf`, `puppies_play.ttf`** appear unused — verify and remove.
- **No font is attributed in `LicensesScreen`.** Inter (OFL), Space Grotesk (OFL) and the incoming
  Source Serif 4 (OFL) all require attribution. Add them.
- **`colors.xml` is still the Android Studio template** (`purple_200`, `teal_700`, …). Replace or
  delete.
- **`themes.xml` is stock `android:Theme.Material.Light.NoActionBar`** — it pins the system UI to
  light regardless of the in-app theme. Needs a `DayNight` parent and a `values-night` variant.
- **`surfaceContainer*` roles are defined in `Color.kt` but never wired into either scheme** in
  `Theme.kt`, so M3 silently computes its own. §2.2/§2.3 wire them explicitly.

---

## 10. Migration state

| # | Step | Findings | Status |
|---|---|---|---|
| 1 | Rewrite this spec | F-03, F-09 | ✅ **Done** |
| 2 | Clear 46 legacy token refs; route all color through `colorScheme` | F-06 | ✅ **Done** |
| 3 | Decide the mode-color question (§6) | F-02 | ☐ **Needs a decision** |
| 4 | Swap palette in `Color.kt` / `Theme.kt`; audit 104 `Color.White` sites | F-01 | ☐ Not started |
| 5 | Reinterpret bespoke components (sky banner, speaker chips, download heroes) | F-04, F-05 | ☐ Not started |
| 6 | Rings replace elevation; normalise spacing to the 4dp grid | F-07, F-08 | ☐ Not started |

Steps 1–2 change nothing visually. The visible switch happens once, at step 4.

### Known blockers

- **104 hardcoded `Color.White` calls** across 14 files. These are Compose's `Color.White`
  literal, *not* the theme's `White` token — only 3 sites use the token, so a find-and-replace
  catches nothing. Most sit on gradient surfaces where white-on-violet currently works; on
  parchment many become unreadable. Each site needs a judgement call between `onPrimary`,
  `onSurface`, or a new `onGradient` token.
- **§6 is undecided** and blocks step 4.

---

## 11. Rules for writing UI code

1. **Never** hardcode a color. No `Color(0xFF…)`, no `Color.White`, no `Color.Gray`.
   Everything comes from `MaterialTheme.colorScheme`.
2. If a color you need has no role, **add a role** — do not inline a literal.
3. Spacing is a multiple of **4dp**. The current drift (27× `6.dp`, 27× `10.dp`, 20× `14.dp`,
   10× `5.dp`, 10× `18.dp`, 8× `3.dp`) is a bug, not a style.
4. Check both schemes before committing. `@Preview(uiMode = UI_MODE_NIGHT_YES)`.
5. Body text clears **4.5:1**; icons, strokes and large text clear **3.0:1**. When in doubt,
   compute it — see the terracotta trap in §2.4 for why assuming is not safe.
