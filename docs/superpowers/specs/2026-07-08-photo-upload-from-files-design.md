# Photo Upload From Device Files — Design

**Date:** 2026-07-08
**Status:** Approved

## Problem

The camera-vision feature only accepts photos taken live via the system camera app.
Users cannot include an existing image (screenshot, earlier photo, received file) in
the AI insight.

## Design

### Entry point

A gallery icon button (`AppIcons.PhotoLibrary` = `Icons.Outlined.PhotoLibrary`) next to
the camera button in the recording top bar. Same visibility gate (`isRecording &&
isVisionCapable`), never disabled during analysis — uploads join the same
`PhotoAnalysisQueue` FIFO as camera captures. (Chosen over a tap-menu on the camera
icon and over allowing uploads outside recording.)

### Picker

Android Photo Picker via `ActivityResultContracts.PickVisualMedia` launched with
`ImageOnly`. No storage permission needed (consistent with the no-CAMERA-permission
design). minSdk 35 means the native picker is always available.

### Import step (`app/src/main/java/com/meetmind/assistant/ui/util/PhotoImport.kt`)

`suspend fun importPhotoForAnalysis(context: Context, uri: Uri): Result<String>`,
runs on `Dispatchers.IO`:

1. Decode the content URI with `ImageDecoder` — handles HEIC/PNG/WebP, which gallery
   photos often are and which the native vision loader (stb_image in mtmd) cannot read.
2. Downsample during decode so the longest side is ≤ 2048 px (the vision encoder
   shrinks far below that anyway; this bounds memory).
3. Re-encode as JPEG (quality 90) to `filesDir/photos/IMG_<timestamp>.jpg` — the same
   directory and naming scheme the camera path uses.
4. Return the absolute path, or `Result.failure` on any decode/write error (partial
   output file deleted).

### Downstream (unchanged pipeline)

On success the UI calls the existing `MainViewModel.onPhotoCaptured(path,
analysisPrompt, failureMessage)`: row persisted to `session_photos` immediately,
analysis queued FIFO, banner + queued count, description folded into the next insight.

On import failure (before any file path exists) the UI calls a new thin setter
`MainViewModel.onPhotoImportFailed(message: String)` that sets `uiState.error` —
same banner mechanism as analysis failure.

### Strings (en + vi)

- `photo_pick_from_files` — "Choose a photo to include in the AI insight" /
  "Chọn ảnh có sẵn để đưa vào phân tích AI"
- `photo_import_failed` — "Couldn't load the selected image." /
  "Không thể tải ảnh đã chọn."

## Testing

**Approved TDD exception:** no new domain logic (queue and use case reused as-is);
the import helper is bound to Android framework APIs (`ImageDecoder`,
`ContentResolver`) and this repo has no instrumented-test infrastructure.
Verification = `:app:compileDebugKotlin` + `:domain:test` (regression) + human
on-device check: pick a HEIC gallery photo while recording → analyzing banner →
photo + description in Session Details → description folded into a later insight.
