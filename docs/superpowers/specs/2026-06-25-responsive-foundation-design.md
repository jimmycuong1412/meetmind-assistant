# Responsive Foundation — Design

**Date:** 2026-06-25
**Scope:** Safe responsive pass so MeetMind Assistant fits more popular device screen
resolutions (small/low-res phones through tablets/foldables) without redesigning layouts
or restructuring navigation.

## Goal

A "general responsiveness pass" at the **safe foundation** level:

1. Nothing clips on small/short phones — every top-level screen scrolls.
2. Content does not stretch ugly on tablets/foldables — reading screens are capped to a
   readable max width; immersive/live screens stay full-bleed.
3. Text respects the user's system font-scale (accessibility) without clipping.

Explicitly **out of scope**: WindowSizeClass two-pane/adaptive layouts, a design-token
refactor of all `.dp` values, and manifest orientation changes.

## Current State (from codebase audit)

The app is a Jetpack Compose codebase in good shape:

- Content screens already use `verticalScroll` / `LazyColumn`
  (`WelcomeScreen`, `SettingsScreen`, `SessionsScreen`, `SearchScreen`, `SessionDetailsScreen`,
  download screens, etc.).
- Inset handling is already correct (`statusBarsPadding`, `navigationBarsPadding`,
  `safeDrawingPadding`).
- Width handling uses `fillMaxWidth`, so phones are fine; the gap is **wide screens**
  (no max-width) and a **few screens without scroll**.
- No `dimens.xml`, no responsive resource qualifiers, no manifest orientation/config handling.
- Text uses hardcoded `.sp` (e.g. `fontSize = 30.sp`) — `.sp` already scales with system font
  settings, so this is mostly a verification step.

## Approach

### 1. Scroll-gap audit & fix

Confirm every top-level screen scrolls so content never clips at ~360×640dp. Most already do.
Wrap content in `verticalScroll(rememberScrollState())` **only where missing**. No layout
redesign. Candidate screens to verify: `SetupScreen`, `ui/asr/screens/Home`, and any dialog
with a fixed height (`NewSessionDialog`, `RenameSessionDialog`).

### 2. Large-screen max-width constraint (the high-impact change)

Add one reusable wrapper composable:

```
ui/components/ResponsiveContent.kt
```

Behavior:

- Centers its content horizontally and caps width at a readable max on wide screens.
- No-op on phones (content narrower than the cap fills available width as before).
- Implemented with `Modifier.widthIn(max = …)` + center alignment; no WindowSizeClass dependency.

**Two width tiers** (driven by the user's full-bleed decision):

| Tier | Max width | Applied to |
|---|---|---|
| `Reading` (default) | 600.dp | Welcome, Settings, Setup, STT/LLM download, SupportedLanguages, Licenses, Sessions list, Search |
| `Wide` / full-bleed | unbounded (`Dp.Infinity`) | Recording / live transcription view, Insights view (MainScreen content area) |

The recording/transcription and insights surfaces stay full-bleed so the live transcript and
insight cards use the whole width on tablets. `ResponsiveContent` still wraps them (for a single
consistent entry point) but with the `Wide` tier, which applies no cap — making the intent
explicit and reversible.

API sketch:

```kotlin
enum class ContentWidth(val max: Dp) {
    Reading(600.dp),
    Wide(Dp.Infinity),
}

@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    width: ContentWidth = ContentWidth.Reading,
    content: @Composable () -> Unit
)
```

### 3. Font-scaling verification

Confirm no text uses `.dp` for size and no fixed-height container would clip large
accessibility fonts. `.sp` usages already scale correctly. Fix any offenders found
(convert `.dp` text sizes to `.sp`; replace fixed text-row heights with intrinsic/min heights).

## Components

- **New:** `ui/components/ResponsiveContent.kt` — the max-width wrapper + `ContentWidth` enum
  (~30–40 lines). Single purpose: constrain/center content width by tier. Depends only on
  Compose foundation/layout. Testable via `@Preview` at multiple device specs.
- **Edited:** ~6–8 screen files — wrap their root content in `ResponsiveContent` and add
  `verticalScroll` where missing. Each edit is local and independent.

## Data Flow

No data/state changes. This is purely a presentation-layer/layout change. ViewModels,
repositories, and domain logic are untouched.

## Error Handling

No new runtime failure modes. `widthIn(max = Dp.Infinity)` is a no-op (safe). Adding
`verticalScroll` to an already-fitting screen is harmless. Risk is purely visual regression,
mitigated by previews + on-device check.

## Testing

- Add/extend Compose `@Preview` for `ResponsiveContent` and the wrapped screens at three
  device specs: small phone (~360×640), large phone (~412×915), tablet (~800×1280).
- Run a debug build to confirm compilation.
- **Final on-device / emulator verification is the user's step** — the agent environment here
  cannot launch an Android emulator. The agent will state this plainly rather than claim the
  layouts were verified on a device.

## Acceptance Criteria

1. Every top-level screen scrolls; no content clipped at 360×640dp.
2. Reading screens are centered and capped at 600dp on tablet-width previews; phones unchanged.
3. Recording/transcription and insights views remain full-bleed on tablet-width previews.
4. Text scales with system font-size setting without clipping.
5. Debug build compiles; new previews render.
