# Responsive Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MeetMind Assistant fit more device screen resolutions — nothing clips on small/short phones, content does not over-stretch on tablets/foldables, and text scales with system font settings.

**Architecture:** Add one reusable `ResponsiveContent` wrapper composable that caps and centers content width by tier (`Reading` = 600dp, `Wide` = full-bleed). Apply it to reading screens. Fix the one confirmed scroll gap (`SetupScreen`). Verify font scaling. No navigation restructuring, no design-token refactor, no manifest changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3. Module `:app`, package root `com.meetmind.assistant.ui`.

## Global Constraints

- New code lives in module `:app`, package `com.meetmind.assistant.ui.components`.
- Theme/color imports come from `com.meetmind.assistant.ui.ui.theme.*`.
- Compose resource id namespace is `com.meetmind.assistant.ui.R` (note the `.ui.R`).
- Do NOT change navigation structure, the AndroidManifest, or any ViewModel/domain/data code.
- Reading-screen content max width: **600.dp**. Full-bleed tier applies **no** width cap.
- Recording/transcription view and Insights view stay **full-bleed** (never capped).
- Text sizes use `.sp` (never `.dp`) so system font-scale is respected.
- Verification builds use: `./gradlew.bat :app:assembleDebug` (Windows). The agent CANNOT run an Android emulator; on-device visual verification is the user's step.

---

### Task 1: `ResponsiveContent` wrapper component

**Files:**
- Create: `app/src/main/java/com/meetmind/assistant/ui/components/ResponsiveContent.kt`

**Interfaces:**
- Produces:
  - `enum class ContentWidth(val max: Dp)` with members `Reading` (600.dp) and `Wide` (Dp.Infinity).
  - `@Composable fun ResponsiveContent(modifier: Modifier = Modifier, width: ContentWidth = ContentWidth.Reading, content: @Composable () -> Unit)` — fills max width, centers a column whose width is capped at `width.max`, and runs `content` inside that column.

- [ ] **Step 1: Create the component file**

Create `app/src/main/java/com/meetmind/assistant/ui/components/ResponsiveContent.kt`:

```kotlin
package com.meetmind.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width tiers for [ResponsiveContent].
 *
 * - [Reading]: caps content at a comfortable reading width on large screens
 *   (tablets/foldables) so text columns and controls do not over-stretch.
 * - [Wide]: no cap — content uses the full available width. Used for immersive
 *   surfaces like the live transcription and insights views.
 */
enum class ContentWidth(val max: Dp) {
    Reading(600.dp),
    Wide(Dp.Infinity),
}

/**
 * Constrains [content] to a maximum width and centers it horizontally.
 *
 * On phones (narrower than [width].max) this is effectively a no-op: the inner
 * column fills the available width. On wide screens it caps the column at
 * [ContentWidth.max] and centers it, leaving symmetric margins.
 *
 * This is the single entry point for the app's large-screen width behavior so
 * the policy is consistent and reversible.
 */
@Composable
fun ResponsiveContent(
    modifier: Modifier = Modifier,
    width: ContentWidth = ContentWidth.Reading,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = width.max),
        ) {
            content()
        }
    }
}

@Preview(name = "Phone", widthDp = 360, heightDp = 640)
@Preview(name = "Large phone", widthDp = 412, heightDp = 915)
@Preview(name = "Tablet", widthDp = 800, heightDp = 1280)
@Composable
private fun ResponsiveContentPreview() {
    ResponsiveContent {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Placeholder content for preview rendering.
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No unresolved references for `ResponsiveContent`, `ContentWidth`, `widthIn`, `Dp.Infinity`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/components/ResponsiveContent.kt
git commit -m "feat(ui): add ResponsiveContent width-tier wrapper"
```

---

### Task 2: Fix the `SetupScreen` scroll gap

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SetupScreen.kt:42-91`

**Interfaces:**
- Consumes: nothing new (self-contained Compose change).
- Produces: nothing consumed by later tasks.

**Why:** `SetupScreen`'s root `Column` uses `fillMaxSize()` + `verticalArrangement = Arrangement.Center` with **no scroll**. On short screens the logo + subtitle + download card (4 feature bullets + 2 buttons) + model-info card overflow and clip. Adding vertical scroll fixes this without changing the visual design on tall screens.

- [ ] **Step 1: Add scroll imports**

In `SetupScreen.kt`, the existing import block uses `androidx.compose.foundation.layout.*`. Add these two imports directly below line 3 (`import androidx.compose.foundation.layout.*`):

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 2: Wrap the root column content in a scroll**

In `SetupScreen.kt`, change the root `Column` modifier (currently lines 42-48):

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
```

to:

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
```

Note: `verticalScroll` is applied BEFORE `padding` so the padding scrolls with the content. `Arrangement.Center` is preserved — on tall screens content still centers; on short screens it becomes scrollable.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/SetupScreen.kt
git commit -m "fix(ui): make SetupScreen scroll so content never clips on short screens"
```

---

### Task 3: Apply `Reading` cap to `SetupScreen`

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SetupScreen.kt`

**Interfaces:**
- Consumes: `ResponsiveContent`, `ContentWidth` from Task 1.

**Why:** `SetupScreen`'s card and model-info content stretch full-width on tablets. Cap to `Reading`. This task is separate from Task 2 because scroll (correctness) and width-cap (polish) are independently reviewable.

- [ ] **Step 1: Add the import**

In `SetupScreen.kt`, add below the existing `import com.meetmind.assistant.ui.ui.theme.*` line:

```kotlin
import com.meetmind.assistant.ui.components.ResponsiveContent
```

- [ ] **Step 2: Wrap the scrolling column body**

In `SetupScreen.kt`, wrap the inner content of the root `Column` (the children: app-name `Text` through `ModelInfoCard()`) in a `ResponsiveContent`. Concretely, immediately after the root `Column(...) {` opening brace, insert `ResponsiveContent {` and add its closing `}` immediately before the root `Column`'s closing brace.

Resulting structure:

```kotlin
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ResponsiveContent {
                // App logo/title
                Text(
                    text = stringResource(R.string.app_name),
                    // ... unchanged children through ModelInfoCard() ...
                )
                // ...
                ModelInfoCard()
            }
        }
```

Inner children keep `horizontalAlignment` behavior because `ResponsiveContent`'s inner column is `fillMaxWidth`; child `Text`/`Card` that previously centered via the outer column should set their own `Modifier.align`/`fillMaxWidth` as already present (they use `fillMaxWidth()`, so they remain centered within the capped column). Do not otherwise alter the children.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/SetupScreen.kt
git commit -m "feat(ui): cap SetupScreen content width on large screens"
```

---

### Task 4: Apply `Reading` cap to `WelcomeScreen`

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/WelcomeScreen.kt:47-127`

**Interfaces:**
- Consumes: `ResponsiveContent` from Task 1.

**Why:** `WelcomeScreen` already scrolls and pads insets correctly; it only needs a width cap so the feature rows + CTA don't stretch on tablets.

- [ ] **Step 1: Add the import**

In `WelcomeScreen.kt`, add below the existing `import com.meetmind.assistant.ui.ui.theme.*` line:

```kotlin
import com.meetmind.assistant.ui.components.ResponsiveContent
```

- [ ] **Step 2: Wrap the scrolling column body**

In `WelcomeScreen.kt`, the root `Column` (lines 47-55) contains children from `Spacer(height = 48.dp)` through the final `Spacer(height = 32.dp)`. Wrap those children in `ResponsiveContent { ... }` — insert `ResponsiveContent {` right after the root `Column(...) {` opening, and its closing `}` right before the root `Column` closing brace. Leave the root `Column`'s `verticalScroll` / `statusBarsPadding` / `navigationBarsPadding` / `padding(horizontal = 32.dp)` and `horizontalAlignment = Alignment.CenterHorizontally` unchanged.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/WelcomeScreen.kt
git commit -m "feat(ui): cap WelcomeScreen content width on large screens"
```

---

### Task 5: Apply `Reading` cap to `SettingsScreen`

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SettingsScreen.kt`

**Interfaces:**
- Consumes: `ResponsiveContent` from Task 1.

**Why:** Settings is a long reading/list screen that already scrolls; cap it so rows don't span an entire tablet width.

- [ ] **Step 1: Read the screen to locate the scroll container**

Run: open `SettingsScreen.kt` and find the top-level scroll container inside the `Scaffold` content lambda. It is either a `Column(... .verticalScroll(...))` or a `LazyColumn`.

- [ ] **Step 2: Wrap or constrain based on container type**

If the container is a `Column` with `verticalScroll`: add the import `import com.meetmind.assistant.ui.components.ResponsiveContent`, then wrap the column's children in `ResponsiveContent { ... }` (same pattern as Task 4).

If the container is a `LazyColumn`: do NOT wrap items individually. Instead add `import androidx.compose.foundation.layout.widthIn`, `import androidx.compose.foundation.layout.fillMaxWidth`, `import androidx.compose.ui.Alignment`, and `import androidx.compose.ui.unit.dp` if missing, and apply to the `LazyColumn` modifier:

```kotlin
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .align(Alignment.CenterHorizontally)  // requires the LazyColumn's parent to be a Column
```

If the `LazyColumn`'s parent is not a `Column` (so `.align` is unavailable), instead wrap the `LazyColumn` in `Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { ... }` and put `.widthIn(max = 600.dp)` on the `LazyColumn`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/SettingsScreen.kt
git commit -m "feat(ui): cap SettingsScreen content width on large screens"
```

---

### Task 6: Apply `Reading` cap to the remaining reading screens

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SttDownloadScreen.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/LlmDownloadScreen.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SupportedLanguagesScreen.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/LicensesScreen.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SessionsScreen.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SearchScreen.kt`

**Interfaces:**
- Consumes: `ResponsiveContent` from Task 1.

**Why:** These are all reading/list screens that already scroll (confirmed: each contains `verticalScroll` or `LazyColumn`). Apply the same cap using the per-container rule from Task 5. Done as one task because each file is the identical, low-risk transformation and a reviewer would accept/reject them as a set.

- [ ] **Step 1: For each file, read it and identify the top-level scroll container**

For each file listed above, find the top-level `Column(...verticalScroll...)` or `LazyColumn` inside the screen's content area.

- [ ] **Step 2: Apply the cap per the Task 5 rule**

For a `verticalScroll` `Column`: add `import com.meetmind.assistant.ui.components.ResponsiveContent` and wrap the children in `ResponsiveContent { ... }`.

For a `LazyColumn`: apply `.widthIn(max = 600.dp)` with center alignment exactly as described in Task 5 Step 2 (add `import androidx.compose.foundation.layout.widthIn` and `import androidx.compose.ui.unit.dp` if missing; use the `Box(... contentAlignment = Alignment.TopCenter)` fallback when `.align` is unavailable).

Apply to all six files.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/SttDownloadScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/LlmDownloadScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/SupportedLanguagesScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/LicensesScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/SessionsScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/SearchScreen.kt
git commit -m "feat(ui): cap remaining reading screens' width on large screens"
```

---

### Task 7: Confirm recording/transcription + insights views stay full-bleed

**Files:**
- Read only: `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt:582-635`
- Read only: `app/src/main/java/com/meetmind/assistant/ui/screens/TranscriptionSection.kt`
- Read only: `app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Why:** Per the design, these stay full-bleed. This task is a deliberate **verification + documentation** step: confirm no width cap was accidentally introduced and that the content area in `MainScreen` still uses `fillMaxSize`. If any cap exists, remove it.

- [ ] **Step 1: Verify the MainScreen content area is full-width**

Confirm `MainScreen.kt` content `Box` (around lines 582-586) still uses `Modifier.weight(1f)` + `fillMaxSize` on the section composables and contains NO `ResponsiveContent` / `widthIn`. If a cap is present, remove it.

- [ ] **Step 2: Verify the sections are full-width**

Confirm `TranscriptionSection` and `InsightsSection` roots use `fillMaxSize`/`fillMaxWidth` and contain NO `widthIn(max = ...)` cap. If present, remove it.

- [ ] **Step 3: Build to verify nothing broke**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit only if a change was made**

If Steps 1-2 required removing a cap:

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/TranscriptionSection.kt \
        app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt
git commit -m "fix(ui): keep recording/transcription and insights views full-bleed"
```

If no change was needed, record that the views were verified full-bleed and skip the commit.

---

### Task 8: Font-scaling verification & final debug build

**Files:**
- Read only: all files under `app/src/main/java/com/meetmind/assistant/ui/`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

**Why:** Acceptance criterion 4 — text must scale with the system font-size setting without clipping. `.sp` already scales; the risk is (a) any text size declared in `.dp`, or (b) a fixed-height container around text that would clip at large font scale.

- [ ] **Step 1: Search for `.dp` text sizes**

Run: `grep -rn "fontSize = [0-9].*\.dp" app/src/main/java/com/meetmind/assistant/ui/`
Expected: no matches. If any are found, change `.dp` to `.sp` for that `fontSize`.

- [ ] **Step 2: Search for fixed-height text containers**

Run: `grep -rn "\.height(" app/src/main/java/com/meetmind/assistant/ui/screens/`
Review each hit: if a fixed `.height(...)` wraps a `Text` whose content could grow with font scale, change it to `.heightIn(min = ...)`. Spacers, progress bars, dividers, and icon sizes are fine — leave them. Make changes only where a `Text` would clip.

- [ ] **Step 3: Run the full debug build**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. APK produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit any fixes**

If Steps 1-2 made changes:

```bash
git add -A
git commit -m "fix(ui): ensure text uses sp and does not clip at large font scale"
```

- [ ] **Step 5: Report verification status to the user**

State plainly: the debug build passed and previews render, but final visual confirmation on small-phone / large-phone / tablet form factors is the user's on-device/emulator step (the agent cannot launch an Android emulator). List which screens were capped and which were intentionally left full-bleed.

---

## Notes for the implementer

- The `ResponsiveContent` inner column is `fillMaxWidth().widthIn(max = …)`. On phones the content already fits under 600dp, so it renders exactly as before — verify visually that no phone screen changed.
- When wrapping a screen whose root `Column` carries `verticalScroll` and inset paddings, keep those modifiers on the OUTER column and put `ResponsiveContent` INSIDE it, so scrolling and insets still cover the full screen while only the content is capped.
- Never wrap a `LazyColumn`'s individual items; cap the `LazyColumn` itself (it must remain the scroll container for performance).
