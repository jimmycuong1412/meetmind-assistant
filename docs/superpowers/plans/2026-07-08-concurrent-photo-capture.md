# Concurrent Photo Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user capture a new photo while the previous one is still being analyzed by the vision model — captures queue up and are analyzed strictly one at a time.

**Architecture:** New domain `PhotoAnalysisQueue` (FIFO serial worker, pending-count `StateFlow`), owned by `MainViewModel`, which mirrors the count into `MainUiState`. The camera button loses its `isAnalyzingPhoto` gate; the analysis banner gains a queued-count variant. Inference stays serialized (llama.cpp is not thread-safe).

**Tech Stack:** Kotlin, kotlinx-coroutines (`Channel`), JUnit4 + kotlinx-coroutines-test in `:domain`, Jetpack Compose in `:app`.

**Spec:** `docs/superpowers/specs/2026-07-08-concurrent-photo-capture-design.md`

## Global Constraints

- Windows dev box: run Gradle via `.\gradlew.bat` (PowerShell) from the repo root.
- Domain tests: `.\gradlew.bat :domain:test`; UI compile gate: `.\gradlew.bat :app:compileDebugKotlin`.
- Conventional Commits; branch `feat/concurrent-photo-capture` (already created off `develop`).
- User-facing strings go in `app/src/main/res/values/strings.xml` AND `values-vi/strings.xml` (the camera string group is vi-localized).
- On-device behavior cannot be verified by the agent — compile gate + domain tests + human device check.

---

### Task 1: `PhotoAnalysisQueue` (domain, TDD)

**Files:**
- Create: `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/PhotoAnalysisQueue.kt`
- Test: `domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/PhotoAnalysisQueueTest.kt`

**Interfaces:**
- Consumes: nothing (pure Kotlin + coroutines).
- Produces: `class PhotoAnalysisQueue` with `fun submit(job: suspend () -> Unit)`, `val pending: StateFlow<Int>`, `suspend fun process()` (worker loop; owner launches it once).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.meetmind.assistant.domain.usecase.llm

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PhotoAnalysisQueue] — the FIFO serial worker that lets the user keep
 * capturing photos while earlier ones are still being analyzed. Jobs must run one
 * at a time in submission order; a failing job must not kill the worker.
 */
class PhotoAnalysisQueueTest {

    @Test
    fun `jobs run one at a time in submission order`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        val firstJobGate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        queue.submit {
            events.add("first:start")
            firstJobGate.await()
            events.add("first:end")
        }
        queue.submit { events.add("second:start") }

        testScheduler.advanceUntilIdle()
        // Second job must not start while the first is suspended.
        assertEquals(listOf("first:start"), events)

        firstJobGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("first:start", "first:end", "second:start"), events)

        worker.cancelAndJoin()
    }

    @Test
    fun `pending counts submitted jobs and drops to zero when done`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        val gate = CompletableDeferred<Unit>()

        assertEquals(0, queue.pending.value)
        queue.submit { gate.await() }
        queue.submit { }
        assertEquals(2, queue.pending.value)

        testScheduler.advanceUntilIdle()
        assertEquals(2, queue.pending.value) // first still running, second waiting

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(0, queue.pending.value)

        worker.cancelAndJoin()
    }

    @Test
    fun `a throwing job does not stop later jobs and still decrements pending`() = runTest {
        val queue = PhotoAnalysisQueue()
        val worker = launch { queue.process() }
        var secondRan = false

        queue.submit { throw IllegalStateException("boom") }
        queue.submit { secondRan = true }
        testScheduler.advanceUntilIdle()

        assertTrue(secondRan)
        assertEquals(0, queue.pending.value)
        assertFalse(worker.isCancelled)

        worker.cancelAndJoin()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :domain:test --tests "com.meetmind.assistant.domain.usecase.llm.PhotoAnalysisQueueTest"`
Expected: FAIL to compile with `Unresolved reference 'PhotoAnalysisQueue'`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.meetmind.assistant.domain.usecase.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * FIFO serial worker for photo-analysis jobs.
 *
 * llama.cpp is not thread-safe, so photos captured while an earlier one is still
 * being analyzed cannot run in parallel — they queue here and run strictly one at
 * a time in capture order. The owner launches [process] once in its scope
 * (MainViewModel uses viewModelScope) and mirrors [pending] into UI state.
 *
 * A job that throws is dropped (the job itself is expected to surface its own
 * error state); the worker keeps consuming subsequent jobs.
 */
class PhotoAnalysisQueue {

    private val jobs = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    private val _pending = MutableStateFlow(0)

    /** Jobs submitted but not yet finished, including the one currently running. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** Appends [job] to the queue; never blocks. */
    fun submit(job: suspend () -> Unit) {
        _pending.update { it + 1 }
        // UNLIMITED channel: trySend cannot fail while the channel is open.
        jobs.trySend(job)
    }

    /** Worker loop — runs queued jobs one at a time in submission order. */
    suspend fun process() {
        for (job in jobs) {
            try {
                job()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Job already surfaced its own failure; keep the worker alive.
            } finally {
                _pending.update { it - 1 }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :domain:test --tests "com.meetmind.assistant.domain.usecase.llm.PhotoAnalysisQueueTest"`
Expected: BUILD SUCCESSFUL, 3/3 pass. Then run the full suite: `.\gradlew.bat :domain:test` — all green.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/PhotoAnalysisQueue.kt domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/PhotoAnalysisQueueTest.kt
git commit -m "feat(domain): add PhotoAnalysisQueue FIFO serial worker"
```

---

### Task 2: Wire the queue into `MainUiState` + `MainViewModel`

**Files:**
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt` (the `isAnalyzingPhoto` property, ~line 69-73)
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt` (`onPhotoCaptured`, ~line 459-500, plus `init`)

**Interfaces:**
- Consumes: `PhotoAnalysisQueue` from Task 1 (`submit`, `pending`, `process`).
- Produces: `MainUiState.pendingPhotoAnalysisCount: Int` (new) and `MainUiState.isAnalyzingPhoto` as a derived `val` — Task 3's UI reads both.

- [ ] **Step 1: Replace the `isAnalyzingPhoto` constructor property in `MainUiState`**

Replace:

```kotlin
    /**
     * True while a captured photo is being analyzed by the vision model.
     * The camera button is disabled and an "Analyzing photo…" banner is shown.
     */
    val isAnalyzingPhoto: Boolean = false
) {
```

with:

```kotlin
    /**
     * Number of captured photos whose vision analysis has not finished yet
     * (the one currently running + any queued behind it). Capture is never
     * blocked; photos are analyzed one at a time in capture order.
     */
    val pendingPhotoAnalysisCount: Int = 0
) {
    /** True while at least one captured photo is being (or waiting to be) analyzed. */
    val isAnalyzingPhoto: Boolean
        get() = pendingPhotoAnalysisCount > 0
```

- [ ] **Step 2: Update `MainViewModel`**

Add the import and field, launch the worker + count mirror in `init`, and rewrite `onPhotoCaptured`.

Import: `import com.meetmind.assistant.domain.usecase.llm.PhotoAnalysisQueue`

Field (near the other private state at the top of the class):

```kotlin
    // FIFO worker so the user can keep capturing photos while earlier ones are
    // still being analyzed. llama.cpp is single-threaded for inference, so jobs
    // run strictly one at a time in capture order.
    private val photoAnalysisQueue = PhotoAnalysisQueue()
```

In `init` (alongside the existing collectors):

```kotlin
        viewModelScope.launch { photoAnalysisQueue.process() }
        viewModelScope.launch {
            photoAnalysisQueue.pending.collect { count ->
                _uiState.update { it.copy(pendingPhotoAnalysisCount = count) }
            }
        }
```

Replace the body of `onPhotoCaptured` (drop the early-return guard and the two
`isAnalyzingPhoto` updates — the count mirror handles state now):

```kotlin
    fun onPhotoCaptured(photoPath: String, analysisPrompt: String, failureMessage: String) {
        val photo = SessionPhoto(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            filePath = photoPath,
            description = null,
            timestamp = System.currentTimeMillis()
        )
        // Persist immediately so the photo shows in Session Details even while
        // its analysis is still queued behind earlier captures.
        val insertJob = viewModelScope.launch {
            transcriptionRepository.insertSessionPhoto(photo)
        }
        photoAnalysisQueue.submit {
            insertJob.join() // description update below needs the row to exist
            analyzePhotoUseCase(photoPath, analysisPrompt)
                .onSuccess { description ->
                    transcriptionRepository.updateSessionPhotoDescription(photo.id, description)
                    syncSttLlmUseCase.queuePhotoDescription(description)
                    Log.i(TAG, "Photo analyzed (${description.length} chars), queued for next insight")
                }
                .onFailure { e ->
                    Log.e(TAG, "Photo analysis failed", e)
                    _uiState.update { it.copy(error = failureMessage) }
                }
        }
    }
```

Also update the function's KDoc: capture is no longer rejected while analyzing;
photos queue FIFO.

- [ ] **Step 3: Compile gate**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `MainScreen.kt` reads `uiState.isAnalyzingPhoto`, which still exists as a derived val. If it fails, fix before committing.

- [ ] **Step 4: Commit**

```bash
git add presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt
git commit -m "feat(presentation): queue photo analyses instead of rejecting captures"
```

---

### Task 3: UI — always-enabled camera button + queued-count banner + strings

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt` (camera button ~line 314-333, banner ~line 582-605)
- Modify: `app/src/main/res/values/strings.xml` (camera string group, ~line 550)
- Modify: `app/src/main/res/values-vi/strings.xml` (camera string group, ~line 76)

**Interfaces:**
- Consumes: `uiState.isAnalyzingPhoto` (derived) and `uiState.pendingPhotoAnalysisCount` from Task 2.
- Produces: string `photo_analyzing_queued` (one `%1$d` int arg).

- [ ] **Step 1: Add strings**

`values/strings.xml`, after `photo_analyzing`:

```xml
    <string name="photo_analyzing_queued">Analyzing photo… %1$d more in queue</string>
```

`values-vi/strings.xml`, after `photo_analyzing`:

```xml
    <string name="photo_analyzing_queued">Đang phân tích ảnh… còn %1$d ảnh trong hàng đợi</string>
```

- [ ] **Step 2: Camera button — remove the analyzing gate**

In `MainScreen.kt` (~line 314-334), the IconButton currently has
`enabled = !uiState.isAnalyzingPhoto` and a dimmed tint. Change to always enabled
with full tint (also update the section comment):

```kotlin
                        // Camera capture — vision-capable model only, while recording.
                        // Never disabled by analysis: captures queue FIFO in the ViewModel.
                        if (uiState.isRecording && uiState.isVisionCapable) {
                            IconButton(
                                onClick = {
                                    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
                                    val photoFile = File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", photoFile
                                    )
                                    pendingPhotoFile = photoFile
                                    takePictureLauncher.launch(uri)
                                }
                            ) {
                                Icon(
                                    imageVector = AppIcons.Camera,
                                    contentDescription = stringResource(R.string.camera_take_photo),
                                    tint = Color.White
                                )
                            }
                        }
```

- [ ] **Step 3: Banner — show queued count**

In the photo-analysis banner (~line 598-602), replace the `Text` content:

```kotlin
                        Text(
                            text = if (uiState.pendingPhotoAnalysisCount > 1) {
                                stringResource(
                                    R.string.photo_analyzing_queued,
                                    uiState.pendingPhotoAnalysisCount - 1
                                )
                            } else {
                                stringResource(R.string.photo_analyzing)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
```

- [ ] **Step 4: Compile gate + full domain suite**

Run: `.\gradlew.bat :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.
Run: `.\gradlew.bat :domain:test` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(ui): allow photo capture while previous photo is analyzing"
```

---

### Task 4: Handoff doc + final verification

**Files:**
- Modify: `docs/AI_AGENT_HANDOFF.md` (§8 camera vision section + Last updated date)

**Interfaces:**
- Consumes: everything above.
- Produces: nothing (docs).

- [ ] **Step 1: Update §8**

Update the `PhotoContextQueue` bullet's neighborhood: add one bullet noting
`PhotoAnalysisQueue` (domain, `usecase/llm/`) serializes photo analyses FIFO so
capture is never blocked; `MainUiState.pendingPhotoAnalysisCount` drives the
banner; tests in `PhotoAnalysisQueueTest`. Bump "Last updated" to 2026-07-08.

- [ ] **Step 2: Final verification (fresh runs)**

Run: `.\gradlew.bat :domain:test --rerun-tasks` — Expected: BUILD SUCCESSFUL.
Run: `.\gradlew.bat :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add docs/AI_AGENT_HANDOFF.md
git commit -m "docs(handoff): record PhotoAnalysisQueue and concurrent capture"
```
