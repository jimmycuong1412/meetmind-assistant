# MeetMind Design System

> **Status:** Implemented. All six migration steps are done — see
> [§10](#10-migration-state). `ColorContrastTest` and `SpacingGridTest` enforce the contrast,
> spacing and no-raw-color rules on every build; see [§11 Enforcement](#enforcement).
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

### 2.5 Gradient surfaces

The full-bleed download / welcome screens, brand headers and the record button sit on a
gradient rather than a scheme surface. M3 has no role meaning "on top of a brand
gradient", so the stops and their content colors live alongside the scheme and are read
through `MaterialTheme.semanticColors`.

| Token | Value | Note |
|---|---|---|
| `GradientTop` | `#8D3A1E` | Lightest stop — the worst case for contrast |
| `GradientMid` | `#6B3320` | Header bands |
| `GradientBottom` | `#241A16` | Immersive screens |
| `OnGradient` | `#FFFFFF` | 7.62 on top, 17.01 on bottom |
| `OnGradientVariant` | white @ 80% | 5.47 composited on top, 6.96 on mid |
| `OnGradientDivider` | white @ 18% | Non-text |
| `OnGradientScrim` | white @ 14% | Glass pill fills |

**Why the top stop is not the brand terracotta.** Secondary text on these screens is
translucent white. Over `#B35334` at 80% alpha that composites to 3.78:1 — below AA for
the `bodySmall`/`bodyMedium` it is actually used on. Over `#8D3A1E` it is 5.47:1.

The previous violet build had the same defect, drawing secondary text at 70–75% alpha for
roughly 3.3:1. It was fixed during the migration rather than carried across, and
`ColorContrastTest` composites the translucent value against the ground so it cannot
return.

These content colors are **identical in both themes** — a brand gradient is a dark ground
whichever scheme is active, so they do not flip.

---

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

Helpers live in `Containment.kt` — `flatCardElevation()`, `containmentRing()` and
`containedCardColors()` — so ring weight and surface tone stay consistent rather than being
re-derived per screen.

The original audit counted 31 elevation settings, but 27 of those were already `0.dp`: the
codebase was mostly following this rule and only four places actually raised elevation (two
shimmer skeletons, the setup download card, and the task-row `tonalElevation`). All four now
use containment.

---

## 6. Recording mode colors

> **Decided: Option C (two-tier), 2026-09-06.** The options considered are kept in
> [§6.4](#64-options-considered-and-rejected) — the rejection reasons matter if this is revisited.

### 6.1 The tier split

`RecordingMode` already divides cleanly along what the mode *does*, and the color follows that
rather than inventing a new grouping:

| Tier | Modes | What they have in common |
|---|---|---|
| **Capture** | Simple Listening, Short Meeting, Long Meeting | Record a meeting, produce a summary afterwards. Differ only in length and insight cadence. |
| **Live assist** | Real-Time Translation, Interview, English Coach | Act on speech *during* the session. Each is a distinct activity. |

**Capture modes share one color — terracotta — and are told apart by icon and label.**
**Live-assist modes each keep their own hue.**

This is the core of the decision: the three capture modes are variations of one activity, so
spending three colors on them was never buying real separation (see §6.3). The three assist modes
are genuinely different things and keep colour-coding where it earns its place.

### 6.2 Values

Every value is verified ≥4.5:1 against **both** the surface and the background in its own theme,
because these tints are used as small label text (`SessionsScreen.kt:734`,
`SearchScreen.kt:261`) — not just as icons.

| Mode | Light | on surface | on bg | Dark | on surface | on bg |
|---|---|---|---|---|---|---|
| Simple Listening | `#B35334` | 4.74 | 4.53 | `#E08A68` | 6.42 | 7.03 |
| Short Meeting | `#B35334` | 4.74 | 4.53 | `#E08A68` | 6.42 | 7.03 |
| Long Meeting | `#B35334` | 4.74 | 4.53 | `#E08A68` | 6.42 | 7.03 |
| Real-Time Translation | `#4F6F82` Slate Blue | 5.07 | 4.85 | `#8FB0C4` | 7.36 | 8.05 |
| Interview | `#734765` Plum | 7.07 | 6.76 | `#C495B4` | 6.66 | 7.29 |
| English Coach | `#547449` Moss | 5.02 | 4.80 | `#9BBE90` | 8.15 | 8.92 |

All four colors are mutually distinguishable — every pair differs by ≥1.5:1 in contrast or ≥25° in
hue, in both themes.

Note the capture tier uses the same `#B35334` as the `primary` role. That is deliberate: capture is
the app's default activity, so it takes the brand color rather than a color of its own.

### 6.3 Why the capture modes are not three shades of terracotta

The first attempt at Option C gave each capture mode its own terracotta-family shade — taupe
`#6E6257`, terracotta `#B35334`, umber `#8C4A2F`. Each passed AA against the background
individually, so it looked fine on paper. Measured against *each other* they were:

| Pair | Contrast |
|---|---|
| Simple vs Short | 1.18 |
| Simple vs Long | 1.13 |
| Short vs Long | 1.34 |

Anything under ~1.5:1 reads as the same colour at label size. Their hues sat 3–14° apart, so hue
could not separate them either. A tier of three near-identical colours is worse than one honest
colour, because it implies a distinction the eye cannot resolve — and it triples the palette for
nothing. Hence: one colour, three icons.

### 6.4 Options considered and rejected

**Option A — rewarm all six.** Keeps one hue per mode, pulled to the earth range. Rejected: it
preserves a six-colour palette that contradicts §1 rule 2, and the rewarmed hues crowd each other
(Clay vs Terracotta, Sage vs Moss). It also failed the text-contrast bar — four of the six proposed
tints landed between 3.13 and 4.10 against parchment, below the 4.5 these labels need.

**Option B — single accent, icon-led throughout.** Purest reading of §1 and the biggest
simplification. Rejected as too blunt for now: it removes glanceable identity from Translation,
Interview and Coach, which are genuinely different activities a user picks deliberately. Option C
gets most of B's simplification while keeping that. B stays the fallback if the assist hues prove
noisy in practice.

### 6.5 Implementation notes

- Three near-duplicate mode→color mappings currently exist — `SessionsScreen.kt:1134`,
  `SearchScreen.kt:288`, `NewSessionDialog.kt:461`. Consolidate into one
  `RecordingMode.accentColor()` in the theme package, scheme-aware, and delete the other two.
- Likewise `recordingModeGradient()` — with capture collapsed to one colour, six gradients become
  four.
- `ModeShortMeetingGradient`/`Tint` currently alias `PrimaryGradient`/`BrandPrimary`, so
  `SearchScreen.kt:290` (the last `BrandPrimary` reference in the app) resolves as part of this work.
- `InsightsSection.kt:539` uses `ModeInterviewTint` for a "question detected" accent and `:861`
  uses `ModeEnglishCoachTint` for coaching — both stay valid under the new values.
- `InsightsSection.kt:537` hardcodes `Color(0xFFD97706)` for coaching notes; fold it into the
  warning semantic role (§7.1) rather than the mode palette.

> `DaytimeSkyBanner` was reinterpreted in step 5 — see [§7.3](#73-daytimeskybanner).

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

Replacement — six warm hues, each verified ≥4.5:1 against **both** the surface and the background
in its own theme:

| # | Light | on surface | on bg | Dark | on surface | on bg |
|---|---|---|---|---|---|---|
| 1 | `#B35334` Terracotta Deep | 4.74 | 4.53 | `#E08A68` | 6.42 | 7.03 |
| 2 | `#4F6F82` Slate Blue | 5.07 | 4.85 | `#8FB0C4` | 7.36 | 8.05 |
| 3 | `#547449` Moss | 5.02 | 4.80 | `#9BBE90` | 8.15 | 8.92 |
| 4 | `#8A6A2F` Ochre | 4.77 | 4.55 | `#D4B36A` | 8.39 | 9.18 |
| 5 | `#734765` Plum | 7.07 | 6.76 | `#C495B4` | 6.66 | 7.29 |
| 6 | `#5E5D59` Olive Gray | 6.26 | 5.98 | `#B0AEA5` | 7.57 | 8.29 |

Speakers 1–3 and 5 share their values with the mode palette (§6.2) deliberately — one set of
warm hues serves both, so the app has fewer colors to hold, not more.

Keep the deterministic hash so a speaker keeps their color across a session, but index into the
scheme-appropriate list.

---

### 7.3 DaytimeSkyBanner

Five time-of-day gradients, rewarmed. Hue carries the time; legibility is handled
separately so the two never trade off against each other.

| State | Window | Top | Bottom |
|---|---|---|---|
| Night | 21:00–05:00 | `#1C1A2E` | `#0E0D14` |
| Dawn | 05:00–08:00 | `#8A4526` | `#C4763F` |
| Morning | 08:00–12:00 | `#A8794C` | `#D4A574` |
| Afternoon | 12:00–17:00 | `#B07A43` | `#E0A868` |
| Dusk | 17:00–21:00 | `#8D3A1E` | `#4A2418` |

**The bug this fixed.** The banner carries white header text — the date and the session
stat row — in its top third. The old implementation drew a scrim that started at 45%
height and faded to 30% black at the bottom, so it never reached the text. Measured
against the actual text position, four of the five states failed AA:

| State | White on top stop | White on bottom stop |
|---|---|---|
| Night | 17.42 | 19.53 |
| Dawn | 7.72 | **2.97** |
| Morning | **2.54** | **1.33** |
| Afternoon | **4.10** | **2.14** |
| Dusk | **3.19** | 7.72 |

Morning at 1.33:1 is effectively invisible text.

The fix is structural: a **uniform `scrim` at `SkyScrimAlpha` (0.50) across the full
banner height**, replacing the bottom fade. Because contrast no longer depends on the sky
hue, the palette is free to be as light as the time of day wants. Worst case across all
five states is now **5.14:1**.

`ColorContrastTest` composites the scrim over each stop and checks both the primary and
the translucent secondary text, and it was verified to fail when the old morning sky is
restored.

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
| 3 | Decide the mode-color question (§6) | F-02 | ✅ **Done — Option C, implemented** |
| 4 | Swap palette in `Color.kt` / `Theme.kt`; audit 104 `Color.White` sites | F-01 | ✅ **Done** |
| 5 | Reinterpret bespoke components (sky banner, speaker chips, download heroes) | F-04, F-05 | ✅ **Done** |
| 6 | Rings replace elevation; normalise spacing to the 4dp grid | F-07, F-08 | ✅ **Done** |

Steps 1–2 changed nothing visually; step 3 moved the mode accents; step 4 was the full switch;
steps 5–6 finished the bespoke components and the containment/spacing rules.

**Not yet done:** none of this has been reviewed on a device. Every claim above is verified by
measurement and compilation, which is not the same as looking right. The warm sky gradients
(§7.3) and the dark scheme are the two places most worth a human eye.

### Known blockers

None. The remaining steps (5, 6) are independent of each other.

Resolved in step 4: the 104 `Color.White` literals turned out to sit almost entirely on
gradient or dark grounds, so they mapped to the four `onGradient*` tokens (§2.5) by alpha
rather than needing 104 individual judgements. One remains, a star in `DaytimeSkyBanner`,
which step 5 owns.

---

## 11. Rules for writing UI code

1. **Never** hardcode a color. No `Color(0xFF…)`, no `Color.White`, no `Color.Gray`.
   Everything comes from `MaterialTheme.colorScheme`.
2. If a color you need has no role, **add a role** — do not inline a literal.
3. Structural spacing — `padding`, `spacedBy`, `Spacer` gaps — is a multiple of **4dp**, with
   1–2dp allowed as an optical half-step for tight work (badge insets, label/value gaps).
   Enforced by `SpacingGridTest`.

   This does **not** apply to icon `size()` (14/18/22dp are standard optical steps) or to
   stroke widths, corner radii and Canvas geometry, where odd and fractional values are
   deliberate.
4. Check both schemes before committing. `@Preview(uiMode = UI_MODE_NIGHT_YES)`.
5. Body text clears **4.5:1**; icons, strokes and large text clear **3.0:1**. When in doubt,
   compute it — see the terracotta trap in §2.4 for why assuming is not safe.
6. No raw `Color(0x…)` outside the theme package. Enforced by `SpacingGridTest`.

### Enforcement

Three of these rules are tests rather than conventions, because all three had already been
violated in ways nobody noticed:

| Test | Guards | Count |
|---|---|---|
| `ColorContrastTest` | Every AA ratio in §2.4, §2.5, §6.2, §7.1–7.3 | 11 |
| `SpacingGridTest` | The 4dp grid and the no-raw-color rule | 3 |

Each was verified to fail when the defect it guards is reintroduced — a green test that has
never been seen red is not evidence.
