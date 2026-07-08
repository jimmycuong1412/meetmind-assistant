# Photo Upload From Device Files Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick an existing image from device files during recording and feed it through the same vision-analysis pipeline as camera captures.

**Architecture:** Photo Picker (`PickVisualMedia`) → JPEG re-encode into `filesDir/photos/` (new `PhotoImport` helper in `:app`) → existing `MainViewModel.onPhotoCaptured` → `PhotoAnalysisQueue`. One thin error setter added to `MainViewModel`.

**Tech Stack:** Jetpack Compose, `androidx.activity` Photo Picker, `ImageDecoder`, Kotlin coroutines.

**Spec:** `docs/superpowers/specs/2026-07-08-photo-upload-from-files-design.md`

## Global Constraints

- Windows dev box: run Gradle via `.\gradlew.bat` from the repo root.
- Verification gates: `.\gradlew.bat :app:compileDebugKotlin` and `.\gradlew.bat :domain:test` (regression only — approved TDD exception, no new unit tests; helper is Android-framework-bound).
- Conventional Commits; branch `feat/photo-upload-from-files` **stacked on
  `feat/concurrent-photo-capture`** (PR #7) — uploading while a photo is analyzing
  requires the analysis queue; on plain `develop` the old guard would delete the file.
- Strings in `values/strings.xml` AND `values-vi/strings.xml`; icons via `AppIcons`.

---

### Task 1: Import helper + ViewModel error setter + icon + strings

**Files:**
- Create: `app/src/main/java/com/meetmind/assistant/ui/util/PhotoImport.kt`
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt` (add `onPhotoImportFailed` next to `onPhotoCaptured`)
- Modify: `app/src/main/java/com/meetmind/assistant/ui/icons/AppIcons.kt` (camera section, ~line 101)
- Modify: `app/src/main/res/values/strings.xml` (camera string group) and `app/src/main/res/values-vi/strings.xml` (same group)

**Interfaces:**
- Produces: `suspend fun importPhotoForAnalysis(context: Context, uri: Uri): Result<String>` (absolute JPEG path), `MainViewModel.onPhotoImportFailed(message: String)`, `AppIcons.PhotoLibrary`, strings `photo_pick_from_files` / `photo_import_failed`.

- [ ] **Step 1: Create `PhotoImport.kt`**

```kotlin
package com.meetmind.assistant.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Longest-side cap for imported images. The vision encoder resizes far below this;
 * capping here bounds decode memory and the size of the stored JPEG copy.
 */
private const val MAX_DIMENSION_PX = 2048
private const val JPEG_QUALITY = 90

/**
 * Copies a user-picked image into the session photo directory as a JPEG.
 *
 * Gallery images are often HEIC/WebP, which the native vision loader (stb_image in
 * mtmd) cannot decode — so the image is always decoded via [ImageDecoder] and
 * re-encoded as JPEG into `filesDir/photos/` using the same `IMG_<timestamp>.jpg`
 * naming as camera captures.
 *
 * @return Absolute path of the written JPEG, or failure on any decode/write error
 *   (no partial file is left behind).
 */
suspend fun importPhotoForAnalysis(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val outFile = File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software bitmap: Bitmap.compress cannot read hardware bitmaps.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DIMENSION_PX) {
                    val scale = MAX_DIMENSION_PX.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
            try {
                outFile.outputStream().use { out ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        "JPEG encode failed"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            Result.success(outFile.absolutePath)
        } catch (e: Exception) {
            outFile.delete()
            Result.failure(e)
        }
    }
```

- [ ] **Step 2: Add `onPhotoImportFailed` to `MainViewModel`** (directly after `onPhotoCaptured`)

```kotlin
    /**
     * Called when a picked device image could not be imported (decode/copy failed
     * before any file path existed, so there is nothing to persist or queue).
     */
    fun onPhotoImportFailed(message: String) {
        _uiState.update { it.copy(error = message) }
    }
```

- [ ] **Step 3: Add icon to `AppIcons.kt`**

```kotlin
    // Camera capture (vision insight)
    val Camera: ImageVector get() = Icons.Outlined.PhotoCamera
    val PhotoLibrary: ImageVector get() = Icons.Outlined.PhotoLibrary
```

- [ ] **Step 4: Add strings**

`values/strings.xml`, after `photo_analyzing_queued`:

```xml
    <string name="photo_pick_from_files">Choose a photo to include in the AI insight</string>
    <string name="photo_import_failed">Couldn\'t load the selected image.</string>
```

`values-vi/strings.xml`, after `photo_analyzing_queued`:

```xml
    <string name="photo_pick_from_files">Chọn ảnh có sẵn để đưa vào phân tích AI</string>
    <string name="photo_import_failed">Không thể tải ảnh đã chọn.</string>
```

- [ ] **Step 5: Compile gate**

Run: `.\gradlew.bat :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/util/PhotoImport.kt presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt app/src/main/java/com/meetmind/assistant/ui/icons/AppIcons.kt app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(ui): photo import helper, error setter, icon and strings"
```

---

### Task 2: Picker launcher + gallery button in `MainScreen`

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt` (imports; picker launcher next to `takePictureLauncher` ~line 118-129; top-bar actions ~line 313-335)

**Interfaces:**
- Consumes: everything from Task 1.

- [ ] **Step 1: Add imports**

```kotlin
import androidx.activity.result.PickVisualMediaRequest
import com.meetmind.assistant.ui.util.importPhotoForAnalysis
import kotlinx.coroutines.launch
```

- [ ] **Step 2: Add the picker launcher** (directly after the `takePictureLauncher` block)

```kotlin
    // Photo upload: the system Photo Picker returns a temporary-access content URI;
    // importPhotoForAnalysis copies it as JPEG into filesDir/photos/ (HEIC/WebP get
    // transcoded — the native vision loader only reads formats stb_image supports).
    val coroutineScope = rememberCoroutineScope()
    val photoImportFailedMessage = stringResource(R.string.photo_import_failed)
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                importPhotoForAnalysis(context, uri)
                    .onSuccess { path ->
                        viewModel.onPhotoCaptured(path, photoAnalysisPrompt, photoFailureMessage)
                    }
                    .onFailure {
                        viewModel.onPhotoImportFailed(photoImportFailedMessage)
                    }
            }
        }
    }
```

Note: `context` and the two prompt strings already exist in scope for the camera path.

- [ ] **Step 3: Add the gallery button** (in `actions`, directly after the camera `IconButton`, inside the same `if (uiState.isRecording && uiState.isVisionCapable)` block)

```kotlin
                            IconButton(
                                onClick = {
                                    pickPhotoLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = AppIcons.PhotoLibrary,
                                    contentDescription = stringResource(R.string.photo_pick_from_files),
                                    tint = Color.White
                                )
                            }
```

- [ ] **Step 4: Verification**

Run: `.\gradlew.bat :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.
Run: `.\gradlew.bat :domain:test` — Expected: BUILD SUCCESSFUL (regression).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt
git commit -m "feat(ui): upload picture from device files for vision insight"
```

---

### Task 3: Handoff doc

**Files:**
- Modify: `docs/AI_AGENT_HANDOFF.md` (§8)

- [ ] **Step 1: Extend the `PhotoAnalysisQueue` bullet** with one sentence: uploads from
device files (Photo Picker → `importPhotoForAnalysis` JPEG re-encode in
`ui/util/PhotoImport.kt`) feed the same queue via `onPhotoCaptured`; HEIC/WebP are
transcoded because stb_image can't read them.

- [ ] **Step 2: Final fresh verification**

Run: `.\gradlew.bat :domain:test --rerun-tasks` and `.\gradlew.bat :app:compileDebugKotlin` — Expected: both BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add docs/AI_AGENT_HANDOFF.md
git commit -m "docs(handoff): record photo upload path"
```
