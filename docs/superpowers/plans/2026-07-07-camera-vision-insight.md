# Camera Vision Insight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Camera button in the recording top bar that captures a photo, analyzes it on-device with a vision-capable LLM (Gemma 3 4B multimodal via llama.cpp mtmd), and merges the description into the next AI insight.

**Architecture:** The vendored llama.cpp checkout (`F:\Git\llama.cpp-master`, sibling of the repo) already contains `tools/mtmd` (clip/multimodal); we compile it into the native lib and add two JNI functions (`loadMmprojNative`, `processImagePrompt`). An optional `imagePath` parameter is threaded through every layer (`InferenceEngine` → `LlmDataSource` → `LlmRepository`) with default `null` so existing call sites compile unchanged. `DefaultModelConfig` (variant `Q8_0`) is replaced with Gemma 3 4B Q4_K_M + its `mmproj` vision-adapter GGUF; the other 5 variants stay text-only. Photo descriptions queue in-memory and are spliced into the next periodic insight prompt; photos + descriptions persist in a new Room table.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, llama.cpp (mtmd/clip), CMake/NDK r27, JUnit4.

**Spec:** `docs/superpowers/specs/2026-07-07-camera-vision-insight-design.md`

## Global Constraints

- Windows dev box: build with `./gradlew.bat` (Git Bash invokes the `.bat`), never `./gradlew`.
- Compile gate for Kotlin/UI changes: `./gradlew.bat :app:compileDebugKotlin`. Native/CMake changes require full `./gradlew.bat :app:assembleDebug` (cold native build can take ~10 min).
- Domain tests: `./gradlew.bat :domain:testDebugUnitTest`.
- Conventional Commits (`feat(...)`, `fix(...)`, `docs(...)`); branch is `develop`-based feature work.
- All user-facing strings go in `app/src/main/res/values/strings.xml` (+ 24 locale variants, e.g. `values-vi/`). Never inline English in composables.
- Every icon goes through `AppIcons` (`app/src/main/java/com/meetmind/assistant/ui/icons/AppIcons.kt`), never direct `Icons.*` in screens.
- Compose resources resolve via `com.meetmind.assistant.ui.R` (the `.ui.R` is correct). Theme imports use the doubled `com.meetmind.assistant.ui.ui.theme.*`.
- llama.cpp is NOT thread-safe: all inference stays on the existing single-thread dispatchers. Do not add parallelism.
- Dependency rule: UI (app) → Presentation → Domain ← Data. Domain stays pure Kotlin (no Android imports).
- On-device verification is human-only (no emulator in agent environment). Never claim UX is "verified" from a green build.
- minSdk = 35, JDK 17, AGP 8.13.2, Kotlin 2.0.21, NDK r27.

**Known accepted behavior changes** (from the approved spec, restated so no implementer "fixes" them):
- `DefaultModelConfig` / variant `Q8_0` stops meaning "Gemma 3 1B Q8_0" and becomes "Gemma 3 4B Q4_K_M multimodal + mmproj" (~2.5 GB + ~850 MB). The enum constant name `Q8_0` is intentionally kept to avoid breaking the persisted `AppSettings.llmModelVariant` value; only its docs change.
- Existing users with `gemma-3-1b-it-Q8_0.gguf` on disk will see "Model not downloaded" after this update (the old filename is no longer referenced by any variant). This is accepted migration behavior.
- No `CAMERA` permission is declared or requested: `ActivityResultContracts.TakePicture()` delegates to the system camera app, which needs no permission **as long as the app does not declare CAMERA in its manifest**. This deviates from the spec's "request lazily" note in the simpler, strictly-better direction.

---

### Task 1: Domain — vision capability flag + PhotoContextQueue

**Files:**
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/model/LlmModelVariant.kt`
- Create: `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueue.kt`
- Test: `domain/src/test/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueueTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `LlmModelVariant.supportsVision: Boolean` (true only for `Q8_0`); `PhotoContextQueue` with `fun add(description: String)`, `fun drain(): List<String>`, and `companion object { fun formatBlock(descriptions: List<String>): String; const val MAX_PENDING = 10 }`. Task 4 wires the queue into `SyncSttLlmUseCase`; Task 7 reads `supportsVision`.

- [ ] **Step 1: Write the failing test**

Create `domain/src/test/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueueTest.kt`:

```kotlin
package com.meetmind.assistant.domain.usecase.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PhotoContextQueue] — the pending photo-description buffer consumed
 * by SyncSttLlmUseCase at each insight tick.
 */
class PhotoContextQueueTest {

    @Test
    fun `drain returns queued descriptions in capture order and clears the queue`() {
        val queue = PhotoContextQueue()
        queue.add("whiteboard with Q3 roadmap")
        queue.add("slide showing budget table")

        val drained = queue.drain()

        assertEquals(listOf("whiteboard with Q3 roadmap", "slide showing budget table"), drained)
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `drain on empty queue returns empty list`() {
        assertEquals(emptyList<String>(), PhotoContextQueue().drain())
    }

    @Test
    fun `blank descriptions are ignored`() {
        val queue = PhotoContextQueue()
        queue.add("   ")
        queue.add("")
        assertTrue(queue.drain().isEmpty())
    }

    @Test
    fun `queue caps at MAX_PENDING keeping the newest entries`() {
        val queue = PhotoContextQueue()
        repeat(PhotoContextQueue.MAX_PENDING + 3) { i -> queue.add("photo $i") }

        val drained = queue.drain()

        assertEquals(PhotoContextQueue.MAX_PENDING, drained.size)
        // Oldest overflow entries (photo 0..2) were dropped.
        assertEquals("photo 3", drained.first())
        assertEquals("photo ${PhotoContextQueue.MAX_PENDING + 2}", drained.last())
    }

    @Test
    fun `formatBlock renders numbered photo lines`() {
        val block = PhotoContextQueue.formatBlock(listOf("a whiteboard", "a slide"))
        assertEquals(
            "Photos captured during this period:\nPhoto 1: a whiteboard\nPhoto 2: a slide",
            block
        )
    }

    @Test
    fun `formatBlock of empty list is empty string`() {
        assertEquals("", PhotoContextQueue.formatBlock(emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :domain:testDebugUnitTest --tests "com.meetmind.assistant.domain.usecase.sync.PhotoContextQueueTest"`
Expected: FAIL (compilation error — `PhotoContextQueue` unresolved).

- [ ] **Step 3: Implement PhotoContextQueue**

Create `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueue.kt`:

```kotlin
package com.meetmind.assistant.domain.usecase.sync

/**
 * Thread-safe FIFO buffer of photo descriptions produced by AnalyzePhotoUseCase and
 * consumed by SyncSttLlmUseCase at the next insight tick.
 *
 * Producer: MainViewModel (after vision analysis completes).
 * Consumer: SyncSttLlmUseCase.buildUserPrompt (drains once per tick).
 *
 * Bounded at [MAX_PENDING]: if the user captures faster than insight ticks consume
 * (e.g. REAL_TIME_TRANSLATION mode never drains), the oldest entries are dropped.
 * Descriptions are also persisted per-photo in the session_photos table, so a
 * dropped queue entry never loses data — it only stops influencing the next insight.
 */
class PhotoContextQueue {

    private val pending = ArrayDeque<String>()
    private val lock = Any()

    fun add(description: String) {
        if (description.isBlank()) return
        synchronized(lock) {
            pending.addLast(description)
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    fun drain(): List<String> = synchronized(lock) {
        val all = pending.toList()
        pending.clear()
        all
    }

    companion object {
        const val MAX_PENDING = 10

        /** Renders drained descriptions as a numbered block for the user prompt. */
        fun formatBlock(descriptions: List<String>): String {
            if (descriptions.isEmpty()) return ""
            return buildString {
                append("Photos captured during this period:")
                descriptions.forEachIndexed { i, desc ->
                    append("\nPhoto ${i + 1}: $desc")
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :domain:testDebugUnitTest --tests "com.meetmind.assistant.domain.usecase.sync.PhotoContextQueueTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Add supportsVision to LlmModelVariant**

In `domain/src/main/java/com/meetmind/assistant/domain/model/LlmModelVariant.kt`, replace the `Q8_0` doc comment and add the property. The enum becomes:

```kotlin
enum class LlmModelVariant {
    /**
     * Default vision-capable variant — Gemma 3 4B Q4_K_M (~2.5 GB) plus a SigLIP
     * mmproj vision adapter (~850 MB). The only variant that supports photo analysis.
     * The constant name Q8_0 is historical (formerly Gemma 3 1B Q8_0) and is kept
     * because the selected variant is persisted by name in AppSettings.
     */
    Q8_0,

    // ... IQ4_NL, QWEN3_5_Q8_0, GEMMA3_4B_Q4, QWEN3_4B_Q4, PHI4_MINI_Q4 unchanged ...
    ;

    /** True when this variant's ModelConfig ships an mmproj vision adapter. */
    val supportsVision: Boolean
        get() = this == Q8_0
}
```

Keep the other five enum entries and their doc comments exactly as they are; only add the trailing `;` and the `supportsVision` property, and replace the `Q8_0` KDoc.

- [ ] **Step 6: Compile check + commit**

Run: `./gradlew.bat :domain:testDebugUnitTest`
Expected: PASS (all domain tests, including pre-existing parser tests).

```bash
git add domain/src/main/java/com/meetmind/assistant/domain/model/LlmModelVariant.kt domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueue.kt domain/src/test/java/com/meetmind/assistant/domain/usecase/sync/PhotoContextQueueTest.kt
git commit -m "feat(domain): add PhotoContextQueue and LlmModelVariant.supportsVision"
```

---

### Task 2: Native — build mtmd and add vision JNI functions

**Files:**
- Modify: `lib-llama-android/src/main/cpp/CMakeLists.txt`
- Modify: `lib-llama-android/src/main/cpp/ai_chat.cpp`

**Interfaces:**
- Consumes: vendored llama.cpp at `${CMAKE_CURRENT_LIST_DIR}/../../../../../llama.cpp-master` (already referenced as `LLAMA_SRC`), which contains `tools/mtmd/` (mtmd.h, mtmd-helper.h, clip.cpp, models/*.cpp).
- Produces: JNI symbols `Java_com_arm_aichat_internal_InferenceEngineImpl_loadMmprojNative(jstring): jint` and `Java_com_arm_aichat_internal_InferenceEngineImpl_processImagePrompt(jstring imagePath, jstring userPrompt, jint nPredict): jint`. Return 0 on success, non-zero error codes otherwise. Task 3's Kotlin `external fun`s bind to these exact names.

- [ ] **Step 1: Compile mtmd into the native build**

In `lib-llama-android/src/main/cpp/CMakeLists.txt`, after the existing `add_subdirectory(${LLAMA_SRC} build-llama)` (line 34), add an `mtmd` static library target. We compile the sources directly instead of `add_subdirectory(${LLAMA_SRC}/tools/mtmd)` because upstream's tools CMakeLists also defines CLI executables and version/install machinery we don't want in an Android build:

```cmake
# --------------------------------------------------------------------------
# mtmd — llama.cpp multimodal (vision) support, compiled from the vendored
# tree. Built here directly (not via add_subdirectory) because upstream's
# tools/mtmd/CMakeLists.txt also defines CLI executables and install rules
# that do not apply to an Android library build.
# --------------------------------------------------------------------------
file(GLOB MTMD_MODEL_SOURCES ${LLAMA_SRC}/tools/mtmd/models/*.cpp)
add_library(mtmd STATIC
        ${LLAMA_SRC}/tools/mtmd/mtmd.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-audio.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-image.cpp
        ${LLAMA_SRC}/tools/mtmd/mtmd-helper.cpp
        ${LLAMA_SRC}/tools/mtmd/clip.cpp
        ${MTMD_MODEL_SOURCES})
target_include_directories(mtmd PUBLIC ${LLAMA_SRC}/tools/mtmd)
target_include_directories(mtmd PRIVATE ${LLAMA_SRC} ${LLAMA_SRC}/vendor)
target_link_libraries(mtmd PUBLIC ggml llama)
target_compile_features(mtmd PRIVATE cxx_std_17)
# stb_image.h needs -Wno-cast-qual; miniaudio.h needs -Wno-missing-prototypes (upstream does the same)
target_compile_options(mtmd PRIVATE -Wno-cast-qual -Wno-missing-prototypes)
```

Then add `mtmd` to the existing `target_link_libraries(${CMAKE_PROJECT_NAME} ...)` block (line 53), before `android`:

```cmake
target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        mtmd
        android
        log)
```

- [ ] **Step 2: Add mtmd globals and includes to ai_chat.cpp**

In `lib-llama-android/src/main/cpp/ai_chat.cpp`, add includes after `#include "llama.h"` (line 13):

```cpp
#include "mtmd.h"
#include "mtmd-helper.h"
```

Add a global next to the other statics (after `g_model_has_mrope`, line 50):

```cpp
// Multimodal (vision) context — non-null only after loadMmprojNative() succeeds.
static mtmd_context                     * g_mtmd_ctx = nullptr;
```

- [ ] **Step 3: Add loadMmprojNative JNI function**

Add after `Java_..._prepare` (after line 180):

```cpp
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_loadMmprojNative(
        JNIEnv *env, jobject /*unused*/, jstring jmmproj_path) {
    if (!g_model) {
        LOGe("%s: base model must be loaded before the mmproj", __func__);
        return 1;
    }
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }

    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu       = false;         // CPU-only, consistent with the text pipeline
    params.print_timings = false;
    params.n_threads     = N_THREADS_MAX; // image encode is a one-shot burst; cap like text decode
    params.warmup        = false;         // skip warmup pass to keep model-load latency low

    const auto *mmproj_path = env->GetStringUTFChars(jmmproj_path, nullptr);
    LOGi("%s: loading mmproj from %s", __func__, mmproj_path);
    g_mtmd_ctx = mtmd_init_from_file(mmproj_path, g_model, params);
    env->ReleaseStringUTFChars(jmmproj_path, mmproj_path);

    if (!g_mtmd_ctx) {
        LOGe("%s: mtmd_init_from_file failed", __func__);
        return 2;
    }
    if (!mtmd_support_vision(g_mtmd_ctx)) {
        LOGe("%s: mmproj loaded but does not support vision", __func__);
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
        return 3;
    }
    LOGi("%s: mmproj loaded, vision ready", __func__);
    return 0;
}
```

- [ ] **Step 4: Add processImagePrompt JNI function**

Add after `Java_..._processUserPrompt` (after line 598). It mirrors `processUserPrompt`'s structure (reset short-term state → format via chat template → decode → set positions), but tokenizes via mtmd (text + image chunks) and prefills via `mtmd_helper_eval_chunks`, which internally handles image encode + non-causal attention for Gemma-3-style models:

```cpp
extern "C"
JNIEXPORT jint JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_processImagePrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jimage_path,
        jstring juser_prompt,
        jint n_predict
) {
    if (!g_mtmd_ctx) {
        LOGe("%s: mmproj not loaded — vision unavailable", __func__);
        return 1;
    }

    reset_short_term_states();

    // Decode the image file (jpg/png/etc. via stb_image inside the helper).
    const auto *image_path = env->GetStringUTFChars(jimage_path, nullptr);
    LOGd("%s: loading image %s", __func__, image_path);
    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_file(g_mtmd_ctx, image_path);
    env->ReleaseStringUTFChars(jimage_path, image_path);
    if (!bitmap) {
        LOGe("%s: failed to load/decode image file", __func__);
        return 2;
    }

    // Build the user content: media marker (replaced by image tokens) + instruction text.
    const auto *user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    std::string content = std::string(mtmd_default_marker()) + "\n" + user_prompt;
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted = has_chat_template
        ? chat_add_and_format(ROLE_USER, content)
        : content;

    // Tokenize into text + image chunks. parse_special must be true so the marker
    // and any template control tokens are parsed; add_special mirrors the text path.
    mtmd_input_text input_text {
        formatted.c_str(),
        /* add_special   */ has_chat_template,
        /* parse_special */ true
    };
    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    const mtmd_bitmap *bitmaps[] = { bitmap };
    const int32_t tok_result = mtmd_tokenize(g_mtmd_ctx, chunks, &input_text, bitmaps, 1);
    mtmd_bitmap_free(bitmap);
    if (tok_result != 0) {
        LOGe("%s: mtmd_tokenize failed w/ %d", __func__, tok_result);
        mtmd_input_chunks_free(chunks);
        return 3;
    }

    // Guard against context overflow: image (~256 tok for Gemma 3) + text must fit.
    const llama_pos chunk_pos = mtmd_helper_get_n_pos(chunks);
    if (current_position + chunk_pos >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
        LOGe("%s: image+text (%d pos) won't fit at position %d", __func__,
             (int) chunk_pos, current_position);
        mtmd_input_chunks_free(chunks);
        return 4;
    }

    // Prefill: text chunks via llama_decode, image chunk via mtmd encode + embd decode.
    llama_pos new_n_past = current_position;
    const int32_t eval_result = mtmd_helper_eval_chunks(
            g_mtmd_ctx, g_context, chunks,
            /* n_past      */ current_position,
            /* seq_id      */ 0,
            /* n_batch     */ BATCH_SIZE,
            /* logits_last */ true,
            &new_n_past);
    mtmd_input_chunks_free(chunks);
    if (eval_result != 0) {
        LOGe("%s: mtmd_helper_eval_chunks failed w/ %d", __func__, eval_result);
        return 5;
    }

    current_position = new_n_past;
    stop_generation_position = current_position + n_predict;
    LOGi("%s: image prefill done, position=%d", __func__, current_position);
    return 0;
}
```

- [ ] **Step 5: Free the mtmd context in unload()**

In `Java_..._unload` (line 696), before `common_sampler_free(g_sampler);`:

```cpp
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }
```

- [ ] **Step 6: Full native build to verify compile + link**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL. This is the gate that mtmd sources compile under NDK r27 and the two new JNI symbols link. If clip/mtmd compilation fails on warnings-as-errors, add the offending `-Wno-...` flag to the `target_compile_options(mtmd PRIVATE ...)` line rather than editing vendored sources.

- [ ] **Step 7: Commit**

```bash
git add lib-llama-android/src/main/cpp/CMakeLists.txt lib-llama-android/src/main/cpp/ai_chat.cpp
git commit -m "feat(native): build mtmd and add vision JNI functions (loadMmprojNative, processImagePrompt)"
```

---

### Task 3: Vision plumbing — InferenceEngine → LlmDataSource → LlmRepository

All signature changes land in one commit so the build stays green. Every new parameter defaults to `null`, so existing call sites compile unchanged.

**Files:**
- Modify: `lib-llama-android/src/main/java/com/arm/aichat/InferenceEngine.kt`
- Modify: `lib-llama-android/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/datasource/LlmDataSource.kt`
- Modify: `feature-llm/src/main/java/com/meetmind/assistant/feature/llm/datasource/LlamaAndroidDataSource.kt`
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/repository/LlmRepository.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/repository/LlmRepositoryImpl.kt`
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/InitializeLlmUseCase.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/di/MockInferenceEngine.kt`

**Interfaces:**
- Consumes: JNI symbols from Task 2 (`loadMmprojNative(String): Int`, `processImagePrompt(String, String, Int): Int`).
- Produces (used by Tasks 4 and 7):
  - `InferenceEngine.loadMmproj(pathToMmproj: String)` (suspend), `InferenceEngine.sendUserPrompt(message: String, predictLength: Int, imagePath: String? = null): Flow<String>`
  - `LlmDataSource.loadModel(modelPath, systemPrompt, nThreadsHint, mmprojPath: String? = null)`, `LlmDataSource.sendPrompt(prompt, maxTokens, imagePath: String? = null)`
  - `LlmRepository.initialize(modelPath, systemPrompt, loadImmediately, mmprojPath: String? = null)`, `LlmRepository.generateInsight(text, systemPrompt, maxTokens, imagePath: String? = null)`
  - `InitializeLlmUseCase.invoke(modelPath, loadImmediately, mmprojPath: String? = null)`

- [ ] **Step 1: InferenceEngine interface**

In `InferenceEngine.kt`: change `sendUserPrompt` (line 52) and add `loadMmproj` after `loadModel` (line 24):

```kotlin
    /**
     * Load a multimodal projector (mmproj GGUF) enabling image input.
     * Must be called after [loadModel]. No-op requirement for text-only models: simply never call it.
     *
     * @throws java.io.IOException if the mmproj fails to load or lacks vision support
     */
    suspend fun loadMmproj(pathToMmproj: String)
```

```kotlin
    /**
     * Sends a user prompt to the loaded model and returns a Flow of generated tokens.
     *
     * @param imagePath Optional absolute path to an image file (jpg/png). When non-null,
     *   the prompt is processed multimodally — requires a prior [loadMmproj] call.
     */
    fun sendUserPrompt(
        message: String,
        predictLength: Int = DEFAULT_PREDICT_LENGTH,
        imagePath: String? = null
    ): Flow<String>
```

- [ ] **Step 2: InferenceEngineImpl**

In `InferenceEngineImpl.kt`, add the two external functions next to `processUserPrompt` (line 102):

```kotlin
    private external fun loadMmprojNative(mmprojPath: String): Int

    private external fun processImagePrompt(imagePath: String, userPrompt: String, predictLength: Int): Int
```

Add the `loadMmproj` implementation after `loadModel` (after line 189):

```kotlin
    override suspend fun loadMmproj(pathToMmproj: String) =
        withContext(llamaDispatcher) {
            check(_state.value is InferenceEngine.State.ModelReady) {
                "Cannot load mmproj in ${_state.value.javaClass.simpleName}!"
            }
            File(pathToMmproj).let {
                require(it.exists()) { "mmproj file not found" }
                require(it.canRead()) { "Cannot read mmproj file" }
            }
            Log.i(TAG, "Loading mmproj... \n$pathToMmproj")
            loadMmprojNative(pathToMmproj).let {
                if (it != 0) throw IOException("Failed to load mmproj (code $it)")
            }
            Log.i(TAG, "mmproj loaded — vision enabled")
        }
```

Change `sendUserPrompt` (line 243) to accept and route the image path — only the signature and the `processUserPrompt` call change:

```kotlin
    override fun sendUserPrompt(
        message: String,
        predictLength: Int,
        imagePath: String?,
    ): Flow<String> = flow {
```

and replace the `processUserPrompt(message, predictLength).let { ... }` block (lines 257-262) with:

```kotlin
            val prefillResult = if (imagePath != null) {
                processImagePrompt(imagePath, message, predictLength)
            } else {
                processUserPrompt(message, predictLength)
            }
            prefillResult.let { result ->
                if (result != 0) {
                    Log.e(TAG, "Failed to process user prompt (image=${imagePath != null}): $result")
                    return@flow
                }
            }
```

- [ ] **Step 3: LlmDataSource interface + LlamaAndroidDataSource**

`LlmDataSource.kt` — change both signatures:

```kotlin
    suspend fun loadModel(
        modelPath: String,
        systemPrompt: String? = null,
        nThreadsHint: Int = -1,
        mmprojPath: String? = null
    ): Result<Unit>

    fun sendPrompt(prompt: String, maxTokens: Int, imagePath: String? = null): Flow<String>
```

`LlamaAndroidDataSource.kt` — update the overrides. In `loadModel`, load the mmproj right after the model is ready, **before** the system prompt (the system-prompt call transitions engine state):

```kotlin
    override suspend fun loadModel(
        modelPath: String,
        systemPrompt: String?,
        nThreadsHint: Int,
        mmprojPath: String?
    ): Result<Unit> = runCatching {
```

and inside the `is InferenceEngine.State.ModelReady ->` branch (line 58), before `inferenceEngine.setSystemPrompt(promptToUse)`:

```kotlin
                // Load the vision adapter first (if this variant ships one) —
                // must happen in ModelReady state, before the system prompt is processed.
                if (mmprojPath != null) {
                    inferenceEngine.loadMmproj(mmprojPath)
                }
```

`sendPrompt` becomes:

```kotlin
    override fun sendPrompt(prompt: String, maxTokens: Int, imagePath: String?): Flow<String> {
        return inferenceEngine.sendUserPrompt(
            message = prompt,
            predictLength = maxTokens,
            imagePath = imagePath
        )
    }
```

- [ ] **Step 4: LlmRepository interface + impl**

`domain/.../repository/LlmRepository.kt` — update the two signatures (lines 22-26 and 52):

```kotlin
    suspend fun initialize(
        modelPath: String,
        systemPrompt: String? = null,
        loadImmediately: Boolean = true,
        mmprojPath: String? = null
    ): Result<Unit>
```

```kotlin
    /**
     * Generate insight/response based on input text, optionally grounded in an image.
     *
     * @param imagePath Optional absolute path to a photo; when non-null the model
     *   receives the image plus [text] as the instruction (vision-capable models only).
     */
    fun generateInsight(
        text: String,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        imagePath: String? = null
    ): Flow<String>
```

`data/.../repository/LlmRepositoryImpl.kt`:

1. Add `private var lastMmprojPath: String? = null` next to `lastModelPath` (line 121).
2. `initialize` gains `mmprojPath: String?` (after `loadImmediately`); store `lastMmprojPath = mmprojPath` next to `lastModelPath = modelPath`, and pass it through: `llmDataSource.loadModel(modelPath, systemPrompt, nThreadsHint, mmprojPath)`.
3. `reloadModel` passes it too: `llmDataSource.loadModel(path, lastSystemPrompt, nThreadsHint, lastMmprojPath)`.
4. `generateInsight` gains `imagePath: String?` as the 4th parameter. Two body changes:
   - Prompt selection (line 254): the `Transcription: "..."` wrapper is for transcript analysis only — an image instruction must pass through verbatim:
     ```kotlin
     val prompt = if (imagePath != null) text else buildPrompt(text)
     ```
   - Pass through (line 267): `llmDataSource.sendPrompt(prompt, maxTokens, imagePath)`.

- [ ] **Step 5: InitializeLlmUseCase + MockInferenceEngine**

`InitializeLlmUseCase.kt`:

```kotlin
    suspend operator fun invoke(
        modelPath: String,
        loadImmediately: Boolean = true,
        mmprojPath: String? = null
    ): Result<Unit> {
        val settings = settingsRepository.getSettings().first()
        return llmRepository.initialize(
            modelPath, settings.simpleListeningSystemPrompt, loadImmediately, mmprojPath
        )
    }
```

`app/src/main/java/com/meetmind/assistant/di/MockInferenceEngine.kt` implements `InferenceEngine` — add matching overrides (read the file first; follow its existing stub style):

```kotlin
    override suspend fun loadMmproj(pathToMmproj: String) { /* no-op in mock */ }
```

and add the `imagePath: String?` parameter to its `sendUserPrompt` override (behavior unchanged — ignore the image).

- [ ] **Step 6: Compile check + commit**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If any other `InferenceEngine`/`LlmDataSource` implementor or call site fails to compile, update it to the new signature (default-null params mean only overrides need edits, never callers).

```bash
git add lib-llama-android/src/main/java/com/arm/aichat/InferenceEngine.kt lib-llama-android/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt data/src/main/java/com/meetmind/assistant/data/datasource/LlmDataSource.kt feature-llm/src/main/java/com/meetmind/assistant/feature/llm/datasource/LlamaAndroidDataSource.kt domain/src/main/java/com/meetmind/assistant/domain/repository/LlmRepository.kt data/src/main/java/com/meetmind/assistant/data/repository/LlmRepositoryImpl.kt domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/InitializeLlmUseCase.kt app/src/main/java/com/meetmind/assistant/di/MockInferenceEngine.kt
git commit -m "feat(llm): thread optional mmproj/imagePath through engine, data source and repository"
```

---

### Task 4: Domain — AnalyzePhotoUseCase + photo context in SyncSttLlmUseCase

**Files:**
- Create: `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCase.kt`
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/di/DomainModule.kt`
- Test: `domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCaseTest.kt`

**Interfaces:**
- Consumes: `LlmRepository.generateInsight(text, systemPrompt, maxTokens, imagePath)` and `beginInference`/`endInference`/`reloadModel` (Task 3); `PhotoContextQueue` (Task 1).
- Produces (used by Task 7):
  - `AnalyzePhotoUseCase` with `suspend operator fun invoke(imagePath: String, analysisPrompt: String): Result<String>`
  - `SyncSttLlmUseCase.queuePhotoDescription(description: String)`

- [ ] **Step 1: Write the failing test**

Create `domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCaseTest.kt`. A hand-rolled fake keeps the domain module dependency-free (no mocking library):

```kotlin
package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.model.LlmSamplerConfig
import com.meetmind.assistant.domain.model.ThermalThrottle
import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzePhotoUseCaseTest {

    private class FakeLlmRepository(
        private val tokens: List<String> = listOf("A ", "whiteboard."),
        private val throwOnGenerate: Boolean = false
    ) : LlmRepository {
        var receivedImagePath: String? = null
        var receivedText: String? = null
        var beginCalled = false
        var endCalled = false
        var reloadCalled = false

        override suspend fun initialize(
            modelPath: String, systemPrompt: String?, loadImmediately: Boolean, mmprojPath: String?
        ) = Result.success(Unit)

        override suspend fun reloadModel(): Result<Unit> {
            reloadCalled = true
            return Result.success(Unit)
        }

        override fun isMemoryConstrained() = false

        override fun generateInsight(
            text: String, systemPrompt: String?, maxTokens: Int, imagePath: String?
        ): Flow<String> {
            receivedText = text
            receivedImagePath = imagePath
            return flow {
                if (throwOnGenerate) throw RuntimeException("native OOM")
                tokens.forEach { emit(it) }
            }
        }

        override suspend fun updateSystemPrompt(systemPrompt: String) = Result.success(Unit)
        override fun useConservativeThreads() {}
        override suspend fun checkAndCacheMemoryConstraint() {}
        override suspend fun recordConstrainedInference(inputChars: Int) {}
        override fun isLargeContext(inputChars: Int) = false
        override val isGenerating: Boolean get() = false
        override fun beginInference() { beginCalled = true }
        override fun endInference() { endCalled = true }
        override suspend fun cleanup() {}
        override suspend fun updateSamplerConfig(config: LlmSamplerConfig) = Result.success(Unit)
        override val thermalThrottleFlow: Flow<ThermalThrottle> = emptyFlow()
    }

    @Test
    fun `returns concatenated tokens and passes image path through`() = runBlocking {
        val repo = FakeLlmRepository()
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertEquals("A whiteboard.", result.getOrThrow())
        assertEquals("/data/photos/img.jpg", repo.receivedImagePath)
        assertEquals("Describe this image.", repo.receivedText)
        assertTrue(repo.reloadCalled)
        assertTrue(repo.beginCalled)
        assertTrue(repo.endCalled)
    }

    @Test
    fun `returns failure and still ends inference when generation throws`() = runBlocking {
        val repo = FakeLlmRepository(throwOnGenerate = true)
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertTrue(result.isFailure)
        assertTrue(repo.endCalled)
    }

    @Test
    fun `blank model output is a failure`() = runBlocking {
        val repo = FakeLlmRepository(tokens = listOf("  ", ""))
        val useCase = AnalyzePhotoUseCase(repo)

        val result = useCase("/data/photos/img.jpg", "Describe this image.")

        assertFalse(result.isSuccess)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :domain:testDebugUnitTest --tests "com.meetmind.assistant.domain.usecase.llm.AnalyzePhotoUseCaseTest"`
Expected: FAIL (compilation error — `AnalyzePhotoUseCase` unresolved).

- [ ] **Step 3: Implement AnalyzePhotoUseCase**

Create `domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCase.kt`:

```kotlin
package com.meetmind.assistant.domain.usecase.llm

import com.meetmind.assistant.domain.repository.LlmRepository
import kotlinx.coroutines.CancellationException

/**
 * Runs on-device vision analysis of a captured photo and returns the text description.
 *
 * Runs immediately after capture (independent of the recording mode's insight cadence).
 * Serialization with the periodic insight loop is two-layered:
 *  - [LlmRepository.beginInference]/[endInference] make [LlmRepository.isGenerating] cover
 *    this call, so SyncSttLlmUseCase skips its tick while a photo is being analyzed.
 *  - The repository's single-thread dispatcher queues any overlap at the native boundary.
 *
 * @property llmRepository Repository for LLM operations (must be vision-initialized:
 *   the active model config supplied an mmprojPath at initialize time)
 */
class AnalyzePhotoUseCase(
    private val llmRepository: LlmRepository
) {
    companion object {
        // Descriptions are context for the next insight, not a deliverable — keep them short.
        private const val MAX_TOKENS_PHOTO_DESCRIPTION = 256

        // English on purpose: descriptions are intermediate LLM-to-LLM context (like the
        // JSON field names, which also stay English across all 25 locales). The next
        // insight re-renders the information in the session's output language.
        private const val PHOTO_SYSTEM_PROMPT =
            "You are a visual assistant. Describe the image factually and concisely " +
            "for meeting notes. Report visible text, diagrams, charts and key items. " +
            "Plain sentences only — no JSON, no markdown."
    }

    /**
     * @param imagePath Absolute path to the captured photo file
     * @param analysisPrompt Localized instruction text shown to the model as the user prompt
     * @return The generated description, or failure (never throws except for cancellation)
     */
    suspend operator fun invoke(imagePath: String, analysisPrompt: String): Result<String> {
        return try {
            llmRepository.beginInference()
            llmRepository.reloadModel().getOrThrow()

            val builder = StringBuilder()
            llmRepository.generateInsight(
                text = analysisPrompt,
                systemPrompt = PHOTO_SYSTEM_PROMPT,
                maxTokens = MAX_TOKENS_PHOTO_DESCRIPTION,
                imagePath = imagePath
            ).collect { token -> builder.append(token) }

            val description = builder.toString().trim()
            if (description.isBlank()) {
                Result.failure(IllegalStateException("Vision model returned empty description"))
            } else {
                Result.success(description)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            llmRepository.endInference()
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :domain:testDebugUnitTest --tests "com.meetmind.assistant.domain.usecase.llm.AnalyzePhotoUseCaseTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Wire PhotoContextQueue into SyncSttLlmUseCase**

In `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt`:

1. Add a queue field and public producer method inside the class (after the constructor, before `companion object`):

```kotlin
    // Pending photo descriptions to merge into the next insight prompt.
    // Producer: MainViewModel via queuePhotoDescription(). Consumer: the tick below.
    private val photoContextQueue = PhotoContextQueue()

    /** Queue a vision-generated photo description for inclusion in the next insight. */
    fun queuePhotoDescription(description: String) {
        photoContextQueue.add(description)
    }
```

2. Extend the busy-skip check (line 235) so a running photo analysis defers the tick:

```kotlin
                    // Skip if a previous LLM call is still running (periodic insight OR an
                    // in-flight photo analysis, which also sets isGenerating).
                    if (isLlmBusy.get() || llmRepository.isGenerating) return@collect
```

3. At the prompt-build site (lines 269-273), drain the queue for analysis modes and pass it through:

```kotlin
                        val finalPrompt = if (mode == RecordingMode.INTERVIEW && interviewRole != null) {
                            InterviewPromptBuilder.build(interviewRole, newContent)
                        } else {
                            // Photos are merged only into analysis-mode prompts. Translation
                            // sends raw text (no wrapper) and Interview prompts are
                            // self-contained; their descriptions remain in session_photos.
                            val photoDescriptions =
                                if (mode == RecordingMode.REAL_TIME_TRANSLATION) emptyList()
                                else photoContextQueue.drain()
                            buildUserPrompt(mode, contextBuffer, newContent, photoDescriptions)
                        }
```

4. Extend `buildUserPrompt` (line 477) with the new parameter and splice the block ahead of the Context/Analyze section (replace the final `return` block, lines 501-506):

```kotlin
    private fun buildUserPrompt(
        mode: RecordingMode,
        contextBuffer: List<String>,
        newContent: String,
        photoDescriptions: List<String> = emptyList()
    ): String {
```

```kotlin
        val photoBlock = PhotoContextQueue.formatBlock(photoDescriptions)
        val contextText = contextBuffer.joinToString(" ")
        val prompt = if (contextText.isNotBlank()) {
            "Context: $contextText\n\nAnalyze: $cappedContent"
        } else {
            cappedContent
        }
        return if (photoBlock.isNotEmpty()) "$photoBlock\n\n$prompt" else prompt
```

(`PhotoContextQueue` is in the same package as `SyncSttLlmUseCase` — no import needed.)

- [ ] **Step 6: Provide AnalyzePhotoUseCase in DomainModule**

In `app/src/main/java/com/meetmind/assistant/di/DomainModule.kt`, add after `provideInitializeLlmUseCase`:

```kotlin
    @Provides
    fun provideAnalyzePhotoUseCase(
        llmRepository: LlmRepository
    ): AnalyzePhotoUseCase {
        return AnalyzePhotoUseCase(llmRepository)
    }
```

with import `com.meetmind.assistant.domain.usecase.llm.AnalyzePhotoUseCase`.

- [ ] **Step 7: Run all domain tests + compile, then commit**

Run: `./gradlew.bat :domain:testDebugUnitTest && ./gradlew.bat :app:compileDebugKotlin`
Expected: PASS / BUILD SUCCESSFUL.

```bash
git add domain/src/main/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCase.kt domain/src/test/java/com/meetmind/assistant/domain/usecase/llm/AnalyzePhotoUseCaseTest.kt domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt app/src/main/java/com/meetmind/assistant/di/DomainModule.kt
git commit -m "feat(domain): AnalyzePhotoUseCase and photo-context merge into insight prompts"
```

---

### Task 5: Data — vision model config + multi-file LLM download

**Files:**
- Modify: `data/src/main/java/com/meetmind/assistant/data/config/ModelConfig.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/datasource/ModelDownloadManager.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/service/AndroidDownloadManager.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Task 7): `ModelConfig.mmprojUrl: String?` / `ModelConfig.mmprojFilename: String?`; `ModelDownloadManager.downloadLlmFiles(files: List<Pair<String, String>>): Flow<DownloadProgress>` (url → filename pairs, combined progress). `getLlmModelPath(filename)` already accepts any filename in the models dir, so mmproj presence is checked with `getLlmModelPath(config.mmprojFilename!!)`.

- [ ] **Step 1: Extend ModelConfig and swap DefaultModelConfig**

In `ModelConfig.kt`, add the two fields to the data class (line 12):

```kotlin
data class ModelConfig(
    val llmUrl: String,
    val llmFilename: String,
    val sttBaseUrl: String,
    val sttFiles: List<String>,
    val sttModelType: Int = 40, // Default to English Parakeet
    /** Optional multimodal projector (vision adapter) downloaded alongside the LLM GGUF. */
    val mmprojUrl: String? = null,
    val mmprojFilename: String? = null
)
```

Replace `DefaultModelConfig` (lines 40-60) with:

```kotlin
/**
 * Default vision-capable configuration — Gemma 3 4B Q4_K_M (~2.5 GB) plus the
 * official SigLIP mmproj vision adapter (~850 MB). Recommended for flagship devices.
 * The only config with photo-analysis (camera) support.
 *
 * LLM    : Gemma 3 4B IT Q4_K_M (ggml-org, HuggingFace)
 * mmproj : mmproj-model-f16.gguf from the same ggml-org release
 * STT    : Sherpa-ONNX Nemo Parakeet TDT 0.6B Int8 (csukuangfj, HuggingFace)
 */
object DefaultModelConfig {

    private const val LLM_URL =
        "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf?download=true"
    private const val LLM_FILENAME = "gemma-3-4b-it-Q4_K_M.gguf"
    private const val MMPROJ_URL =
        "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/mmproj-model-f16.gguf?download=true"
    private const val MMPROJ_FILENAME = "gemma-3-4b-mmproj-f16.gguf"

    val INSTANCE = ModelConfig(
        llmUrl = LLM_URL,
        llmFilename = LLM_FILENAME,
        sttBaseUrl = STT_BASE_URL,
        sttFiles = STT_FILES,
        sttModelType = 40,
        mmprojUrl = MMPROJ_URL,
        mmprojFilename = MMPROJ_FILENAME
    )
}
```

Notes:
- `LLM_FILENAME` intentionally matches `Gemma3_4B_Q4Config`'s filename — a user who already downloaded that variant reuses the base GGUF and only fetches the mmproj.
- The local mmproj filename is prefixed `gemma-3-4b-` (upstream's generic `mmproj-model-f16.gguf` would collide with other models' mmproj files later).
- All other config objects are untouched (their constructors still compile — new fields default to null).

- [ ] **Step 2: Add multi-file LLM download to ModelDownloadManager**

In `ModelDownloadManager.kt`, add after `downloadLlmModel` (after line 209). This mirrors the per-file resume loop of `downloadSttModel` (lines 247-378) but takes explicit url/filename pairs and emits combined progress:

```kotlin
    /**
     * Download multiple LLM-related files (base GGUF + optional mmproj) sequentially
     * with per-file Range-header resume and combined progress. Mirrors the STT
     * multi-file strategy: completed files are skipped on retry, the interrupted
     * file resumes from its .partial.
     *
     * @param files Ordered list of (url, filename) pairs.
     */
    fun downloadLlmFiles(files: List<Pair<String, String>>): Flow<DownloadProgress> = flow {
        // Best-effort HEAD for real sizes; fall back to on-disk partial size (never 0-div).
        val actualSizes = files.associate { (url, filename) ->
            filename to (headSize(url)
                ?: File(modelsDir, "$filename.partial").takeIf { it.exists() }?.length()
                ?: 0L)
        }
        val totalBytes = actualSizes.values.sum()

        // Seed with completed + partial bytes so the first emission reflects resume state.
        var totalBytesDownloaded = files.sumOf { (_, filename) ->
            val out = File(modelsDir, filename)
            val partial = File(modelsDir, "$filename.partial")
            when {
                out.exists() && out.length() > 0 -> out.length()
                partial.exists() && partial.length() > 0 -> partial.length()
                else -> 0L
            }
        }
        Log.i(TAG, "LLM files total: ${totalBytes / 1_000_000}MB, on disk: ${totalBytesDownloaded / 1_000_000}MB")

        files.forEachIndexed { index, (url, filename) ->
            val outputFile = File(modelsDir, filename)
            val partialFile = File(modelsDir, "$filename.partial")

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "LLM file $filename already complete, skipping")
                return@forEachIndexed
            }

            val startByte = partialFile.takeIf { it.exists() }?.length() ?: 0L
            Log.i(TAG, "Downloading LLM file ${index + 1}/${files.size}: $filename" +
                if (startByte > 0) " (resuming from ${startByte / 1_000_000}MB)" else "")
            // NOTE: totalBytesDownloaded already includes startByte from the seed scan.

            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                if (startByte > 0) connection.setRequestProperty("Range", "bytes=$startByte-")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode for $filename")
                }
                val isResuming = startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (startByte > 0 && !isResuming) {
                    Log.w(TAG, "Server did not honour Range for $filename, restarting this file")
                    totalBytesDownloaded -= startByte
                }

                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, isResuming).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesDownloaded += bytesRead
                            val pct = if (totalBytes > 0)
                                ((totalBytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                            else 0
                            if (pct % 2 == 0) {
                                emit(DownloadProgress(totalBytesDownloaded, totalBytes, pct))
                            }
                        }
                    }
                }

                // Promote .partial → final file
                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
            } catch (e: Exception) {
                connection?.disconnect()
                // Keep completed files and the .partial so retry resumes precisely.
                throw Exception("LLM download failed at $filename: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        val missing = files.map { it.second }.filter { !File(modelsDir, it).exists() }
        if (missing.isNotEmpty()) {
            throw Exception("Download incomplete: missing $missing")
        }
        emit(DownloadProgress(totalBytes, totalBytes, 100))
        Log.i(TAG, "LLM files download completed")
    }.flowOn(Dispatchers.IO)
```

- [ ] **Step 3: Route AndroidDownloadManager through downloadLlmFiles**

In `AndroidDownloadManager.kt`:

1. Add a private helper near `startLlmDownload` (line 222):

```kotlin
    /** Files for a variant's LLM download: base GGUF plus mmproj when the config has one. */
    private fun llmFilesFor(config: com.meetmind.assistant.data.config.ModelConfig): List<Pair<String, String>> =
        buildList {
            add(config.llmUrl to config.llmFilename)
            val mmUrl = config.mmprojUrl
            val mmFile = config.mmprojFilename
            if (mmUrl != null && mmFile != null) add(mmUrl to mmFile)
        }
```

2. In `startLlmDownload` (line 236) replace the `modelDownloadManager.downloadLlmModel(url = config.llmUrl, filename = config.llmFilename)` call with:

```kotlin
                modelDownloadManager.downloadLlmFiles(llmFilesFor(config))
```

3. In `resumeLlmDownload` (line 313), same replacement:

```kotlin
                modelDownloadManager.downloadLlmFiles(llmFilesFor(config))
```

(The `.partial`-rename legacy handling above it stays as-is — it operates on the base GGUF filename, and `downloadLlmFiles` picks up any `.partial` automatically.)

- [ ] **Step 4: Compile check + commit**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add data/src/main/java/com/meetmind/assistant/data/config/ModelConfig.kt data/src/main/java/com/meetmind/assistant/data/datasource/ModelDownloadManager.kt data/src/main/java/com/meetmind/assistant/data/service/AndroidDownloadManager.kt
git commit -m "feat(data): vision-capable default model config with mmproj multi-file download"
```

---

### Task 6: Data — session photos persistence (Room v10)

**Files:**
- Create: `data/src/main/java/com/meetmind/assistant/data/database/entity/SessionPhotoEntity.kt`
- Create: `data/src/main/java/com/meetmind/assistant/data/database/dao/SessionPhotoDao.kt`
- Create: `domain/src/main/java/com/meetmind/assistant/domain/model/SessionPhoto.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/database/AppDatabase.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/di/DatabaseModule.kt`
- Modify: `domain/src/main/java/com/meetmind/assistant/domain/repository/TranscriptionRepository.kt`
- Modify: `data/src/main/java/com/meetmind/assistant/data/repository/TranscriptionRepositoryImpl.kt`

**Interfaces:**
- Consumes: existing Room/DAO patterns.
- Produces (used by Tasks 7-8): domain model `SessionPhoto(id: String, sessionId: String, filePath: String, description: String?, timestamp: Long)`; `TranscriptionRepository.insertSessionPhoto(photo: SessionPhoto)`, `updateSessionPhotoDescription(photoId: String, description: String)`, `getPhotosForSession(sessionId: String): Flow<List<SessionPhoto>>`.

- [ ] **Step 1: Domain model**

Create `domain/src/main/java/com/meetmind/assistant/domain/model/SessionPhoto.kt`:

```kotlin
package com.meetmind.assistant.domain.model

/**
 * A photo captured during a recording session.
 *
 * @property description Vision-model-generated description; null until analysis completes
 *   (or permanently null if analysis failed — the photo itself is still kept).
 */
data class SessionPhoto(
    val id: String,
    val sessionId: String,
    val filePath: String,
    val description: String?,
    val timestamp: Long
)
```

- [ ] **Step 2: Entity + DAO**

Create `data/src/main/java/com/meetmind/assistant/data/database/entity/SessionPhotoEntity.kt` (mirrors `LlmInsightEntity`'s FK/index pattern):

```kotlin
package com.meetmind.assistant.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a photo captured during a recording session.
 * Deleted automatically when the parent session is deleted (CASCADE).
 */
@Entity(
    tableName = "session_photos",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class SessionPhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
```

Create `data/src/main/java/com/meetmind/assistant/data/database/dao/SessionPhotoDao.kt`:

```kotlin
package com.meetmind.assistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meetmind.assistant.data.database.entity.SessionPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionPhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: SessionPhotoEntity)

    @Query("UPDATE session_photos SET description = :description WHERE id = :photoId")
    suspend fun updateDescription(photoId: String, description: String)

    @Query("SELECT * FROM session_photos WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getPhotosForSession(sessionId: String): Flow<List<SessionPhotoEntity>>
}
```

- [ ] **Step 3: Database version 10 + migration**

In `AppDatabase.kt`:
1. Add `SessionPhotoEntity::class` to the `entities` list and bump `version = 9` → `version = 10`.
2. Add `abstract fun sessionPhotoDao(): SessionPhotoDao` next to the other DAOs (with imports for both new classes).
3. Add the migration to the companion:

```kotlin
        /**
         * Database version 10:
         * - Added session_photos table for camera captures during recording
         *   (vision-analyzed photos merged into AI insights).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS session_photos (
                        id TEXT NOT NULL PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        description TEXT,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (session_id) REFERENCES transcription_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_photos_session_id ON session_photos(session_id)"
                )
            }
        }
```

In `DatabaseModule.kt`: add `AppDatabase.MIGRATION_9_10` to the `.addMigrations(...)` list and a provider:

```kotlin
    @Provides
    @Singleton
    fun provideSessionPhotoDao(database: AppDatabase): SessionPhotoDao {
        return database.sessionPhotoDao()
    }
```

(import `com.meetmind.assistant.data.database.dao.SessionPhotoDao`).

- [ ] **Step 4: Repository methods**

In `domain/.../repository/TranscriptionRepository.kt`, add (with `import com.meetmind.assistant.domain.model.SessionPhoto`):

```kotlin
    /** Persist a photo captured during a recording session (description may be null initially). */
    suspend fun insertSessionPhoto(photo: SessionPhoto)

    /** Attach the vision-generated description to a previously inserted photo. */
    suspend fun updateSessionPhotoDescription(photoId: String, description: String)

    /** Observe all photos of a session, oldest first. */
    fun getPhotosForSession(sessionId: String): Flow<List<SessionPhoto>>
```

In `TranscriptionRepositoryImpl.kt`: add `private val sessionPhotoDao: SessionPhotoDao` as a constructor parameter (and pass it in `DatabaseModule.provideTranscriptionRepository` — add the DAO parameter there and forward it), then implement, following the file's existing entity↔domain mapping style:

```kotlin
    override suspend fun insertSessionPhoto(photo: SessionPhoto) {
        sessionPhotoDao.insert(
            SessionPhotoEntity(
                id = photo.id,
                sessionId = photo.sessionId,
                filePath = photo.filePath,
                description = photo.description,
                timestamp = photo.timestamp
            )
        )
    }

    override suspend fun updateSessionPhotoDescription(photoId: String, description: String) {
        sessionPhotoDao.updateDescription(photoId, description)
    }

    override fun getPhotosForSession(sessionId: String): Flow<List<SessionPhoto>> {
        return sessionPhotoDao.getPhotosForSession(sessionId).map { entities ->
            entities.map { e ->
                SessionPhoto(
                    id = e.id,
                    sessionId = e.sessionId,
                    filePath = e.filePath,
                    description = e.description,
                    timestamp = e.timestamp
                )
            }
        }
    }
```

(imports: `SessionPhotoDao`, `SessionPhotoEntity`, `SessionPhoto`, `kotlinx.coroutines.flow.map` — check which are present.)

- [ ] **Step 5: Compile check + commit**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room's annotation processor validates the schema; fix any KSP errors it reports).

```bash
git add data/src/main/java/com/meetmind/assistant/data/database/entity/SessionPhotoEntity.kt data/src/main/java/com/meetmind/assistant/data/database/dao/SessionPhotoDao.kt domain/src/main/java/com/meetmind/assistant/domain/model/SessionPhoto.kt data/src/main/java/com/meetmind/assistant/data/database/AppDatabase.kt data/src/main/java/com/meetmind/assistant/data/di/DatabaseModule.kt domain/src/main/java/com/meetmind/assistant/domain/repository/TranscriptionRepository.kt data/src/main/java/com/meetmind/assistant/data/repository/TranscriptionRepositoryImpl.kt data/schemas
git commit -m "feat(data): session_photos table (Room v10) with repository access"
```

(`data/schemas` — `exportSchema = true` generates a new JSON schema file; include it if the build produced one.)

---

### Task 7: UI + ViewModel — camera button, capture flow, analyzing banner

**Files:**
- Modify: `app/src/main/java/com/meetmind/assistant/ui/icons/AppIcons.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/xml/file_paths.xml`
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt`
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt`

**Interfaces:**
- Consumes: `AnalyzePhotoUseCase`, `SyncSttLlmUseCase.queuePhotoDescription` (Task 4); `SessionPhoto` + repository methods (Task 6); `ModelConfig.mmprojFilename` (Task 5); `LlmModelVariant.supportsVision` (Task 1); `InitializeLlmUseCase(modelPath, loadImmediately, mmprojPath)` (Task 3).
- Produces: `MainViewModel.onPhotoCaptured(photoPath: String, analysisPrompt: String, failureMessage: String)`; `MainUiState.isVisionCapable`, `MainUiState.isAnalyzingPhoto`.

- [ ] **Step 1: Icon + strings + FileProvider path**

`AppIcons.kt` — add next to the other vals (follow the existing `Icons.Outlined.*` style):

```kotlin
    val Camera: ImageVector get() = Icons.Outlined.PhotoCamera
```

`app/src/main/res/values/strings.xml` — add (near other recording-screen strings):

```xml
    <!-- Camera photo capture during recording (vision insight) -->
    <string name="camera_take_photo">Take a photo to include in the AI insight</string>
    <string name="photo_analyzing">Analyzing photo…</string>
    <string name="photo_analysis_failed">Photo analysis failed. The photo was kept with the session.</string>
    <string name="prompt_photo_analysis">Describe this image concisely for meeting notes. Focus on any visible text, diagrams, charts and key information.</string>
```

`app/src/main/res/xml/file_paths.xml` — add inside `<paths>`:

```xml
    <!--
        Exposes files written to Context.filesDir/photos/ via FileProvider.
        Used as the output target for ACTION_IMAGE_CAPTURE (camera during recording).
    -->
    <files-path name="photos" path="photos/" />
```

- [ ] **Step 2: MainUiState fields**

In `MainUiState.kt`, add after `batteryWhitelistPromptVisible`:

```kotlin
    /**
     * True when the active LLM variant supports vision AND both its base model and
     * mmproj adapter are on disk. Gates the camera button in the recording top bar.
     */
    val isVisionCapable: Boolean = false,
    /**
     * True while a captured photo is being analyzed by the vision model.
     * The camera button is disabled and an "Analyzing photo…" banner is shown.
     */
    val isAnalyzingPhoto: Boolean = false
```

- [ ] **Step 3: MainViewModel — vision resolution + onPhotoCaptured**

In `MainViewModel.kt`:

1. Constructor: add `private val analyzePhotoUseCase: AnalyzePhotoUseCase,` after `initializeLlmUseCase` (import `com.meetmind.assistant.domain.usecase.llm.AnalyzePhotoUseCase`). `transcriptionRepository` is already injected.

2. In `initialize()`'s model-resolution block (lines 287-341): after `llmModelPath` is resolved non-null (the `else` branch at line 303), resolve the mmproj and set `isVisionCapable`:

```kotlin
                    // Vision capability: active variant must ship an mmproj, the mmproj must be
                    // on disk, and the resolved model must actually be the vision variant's file
                    // (not a text-only fallback picked because the preferred file was missing).
                    val activeConfig = modelConfigForVariant(settings.llmModelVariant)
                    val mmprojPath = if (settings.llmModelVariant.supportsVision) {
                        activeConfig.mmprojFilename?.let { modelDownloadManager.getLlmModelPath(it) }
                    } else null
                    val visionReady = mmprojPath != null &&
                        llmModelPath == modelDownloadManager.getLlmModelPath(activeConfig.llmFilename)
                    _uiState.update { it.copy(isVisionCapable = visionReady) }
```

and change the init call (line 326) to pass it:

```kotlin
                    initializeLlmUseCase(llmModelPath, loadImmediately, if (visionReady) mmprojPath else null)
```

Also in the `llmModelPath == null` branch (line 297), add `isVisionCapable = false` to the `copy(...)`.

3. Add the capture handler (near `downloadLlmModel()`, line 437):

```kotlin
    /**
     * Called after the system camera app wrote a photo to [photoPath].
     * Persists the photo row immediately, then runs vision analysis; on success the
     * description is attached to the row and queued for the next insight tick.
     *
     * @param analysisPrompt Localized instruction from string resources (passed from UI,
     *   same pattern as regenerateInsight's error message)
     * @param failureMessage Localized error banner text for analysis failure
     */
    fun onPhotoCaptured(photoPath: String, analysisPrompt: String, failureMessage: String) {
        if (_uiState.value.isAnalyzingPhoto) return
        _uiState.update { it.copy(isAnalyzingPhoto = true) }
        viewModelScope.launch {
            val photo = SessionPhoto(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                filePath = photoPath,
                description = null,
                timestamp = System.currentTimeMillis()
            )
            try {
                transcriptionRepository.insertSessionPhoto(photo)
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
            } finally {
                _uiState.update { it.copy(isAnalyzingPhoto = false) }
            }
        }
    }
```

(imports: `com.meetmind.assistant.domain.model.SessionPhoto`, `java.util.UUID` — check which exist.)

- [ ] **Step 4: MainScreen — camera button + capture launcher + banner**

In `MainScreen.kt`:

1. Imports to add: `java.io.File`, `androidx.core.content.FileProvider`.

2. Inside the `MainScreen` composable, before `Scaffold` (near the other `remember` state, ~line 110):

```kotlin
    // Camera capture: the system camera app writes to a FileProvider URI in
    // filesDir/photos/. No CAMERA permission is needed because the app does not
    // declare it in the manifest (ACTION_IMAGE_CAPTURE delegates to the camera app).
    var pendingPhotoFile by remember { mutableStateOf<java.io.File?>(null) }
    val photoAnalysisPrompt = stringResource(R.string.prompt_photo_analysis)
    val photoFailureMessage = stringResource(R.string.photo_analysis_failed)
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingPhotoFile
        pendingPhotoFile = null
        if (success && file != null && file.exists()) {
            viewModel.onPhotoCaptured(file.absolutePath, photoAnalysisPrompt, photoFailureMessage)
        } else {
            // Cancelled or failed capture — remove the empty placeholder file.
            file?.delete()
        }
    }
```

3. In the `TopAppBar` `actions` block, **before** the existing screen-off toggle (line 287), add:

```kotlin
                        // Camera capture — vision-capable model only, while recording.
                        // Disabled while a previous photo is still being analyzed.
                        if (uiState.isRecording && uiState.isVisionCapable) {
                            IconButton(
                                onClick = {
                                    val photosDir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
                                    val photoFile = java.io.File(photosDir, "IMG_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", photoFile
                                    )
                                    pendingPhotoFile = photoFile
                                    takePictureLauncher.launch(uri)
                                },
                                enabled = !uiState.isAnalyzingPhoto
                            ) {
                                Icon(
                                    imageVector = AppIcons.Camera,
                                    contentDescription = stringResource(R.string.camera_take_photo),
                                    tint = Color.White.copy(alpha = if (uiState.isAnalyzingPhoto) 0.4f else 1f)
                                )
                            }
                        }
```

(Use plain `File` with the import instead of fully-qualified names if preferred — match one style.)

4. Analyzing banner — after the thermal-downgrade banner block (line 528), add:

```kotlin
            // Photo-analysis banner: shown while the vision model describes a captured photo.
            if (uiState.isAnalyzingPhoto) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.photo_analyzing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
```

- [ ] **Step 5: Compile check + commit**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/meetmind/assistant/ui/icons/AppIcons.kt app/src/main/res/values/strings.xml app/src/main/res/xml/file_paths.xml presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt app/src/main/java/com/meetmind/assistant/ui/screens/MainScreen.kt
git commit -m "feat(ui): camera capture button with on-device photo analysis during recording"
```

---

### Task 8: Session details — show captured photos

**Files:**
- Modify: `presentation/src/main/java/com/meetmind/assistant/presentation/sessiondetails/SessionDetailsViewModel.kt` (and its UiState class — same package, likely `SessionDetailsUiState.kt` or nested; locate with Grep for `data class SessionDetailsUiState`)
- Modify: `app/src/main/java/com/meetmind/assistant/ui/screens/SessionDetailsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `TranscriptionRepository.getPhotosForSession(sessionId)` (Task 6).
- Produces: `SessionDetailsUiState.photos: List<SessionPhoto>` rendered as a "Photos" section.

- [ ] **Step 1: String**

`app/src/main/res/values/strings.xml`:

```xml
    <string name="session_photos_title">Photos</string>
```

- [ ] **Step 2: ViewModel — observe photos**

In `SessionDetailsViewModel.kt`:
1. Add `private val transcriptionRepository: TranscriptionRepository` to the constructor **only if not already injected** (check first — MainViewModel has it; this VM may too). Import `com.meetmind.assistant.domain.repository.TranscriptionRepository` and `com.meetmind.assistant.domain.model.SessionPhoto`.
2. Add to the UiState data class:

```kotlin
    /** Photos captured during this session, oldest first (empty for pre-feature sessions). */
    val photos: List<SessionPhoto> = emptyList(),
```

3. In `init`, alongside `loadActionItems()`, add a collector following the exact pattern of `loadActionItems` (lines 198-205):

```kotlin
    private fun loadPhotos() {
        viewModelScope.launch {
            transcriptionRepository.getPhotosForSession(sessionId)
                .catch { /* non-fatal */ }
                .collect { photos ->
                    _uiState.update { it.copy(photos = photos) }
                }
        }
    }
```

and call `loadPhotos()` in `init`.

- [ ] **Step 3: Screen — photos section**

In `SessionDetailsScreen.kt`, add a private composable at the end of the file (no image-loading library exists in this project; decode with a bounded sample size — do NOT add a Coil/Glide dependency):

```kotlin
/**
 * Photos captured during the session: thumbnail + vision description per row.
 * Bitmaps are decoded off the main thread with inSampleSize to bound memory.
 */
@Composable
private fun SessionPhotosSection(photos: List<com.meetmind.assistant.domain.model.SessionPhoto>) {
    if (photos.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.session_photos_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        photos.forEach { photo ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                var bitmap by remember(photo.filePath) {
                    mutableStateOf<android.graphics.Bitmap?>(null)
                }
                LaunchedEffect(photo.filePath) {
                    bitmap = withContext(Dispatchers.IO) {
                        try {
                            val bounds = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            android.graphics.BitmapFactory.decodeFile(photo.filePath, bounds)
                            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 256)
                            android.graphics.BitmapFactory.decodeFile(
                                photo.filePath,
                                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = photo.description ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

Required imports (add the missing ones): `androidx.compose.foundation.Image`, `androidx.compose.ui.graphics.asImageBitmap`, `androidx.compose.ui.layout.ContentScale`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`.

Then place `SessionPhotosSection(uiState.photos)` in the screen's main content column, after the insights section and before the transcript/segments section (read the screen's layout to find the analogous section boundaries — insert as a sibling of the existing titled sections).

- [ ] **Step 4: Compile check + commit**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add presentation/src/main/java/com/meetmind/assistant/presentation/sessiondetails app/src/main/java/com/meetmind/assistant/ui/screens/SessionDetailsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(ui): show captured photos with descriptions in session details"
```

---

### Task 9: Locale strings, docs, final verification

**Files:**
- Modify: `app/src/main/res/values-*/strings.xml` (24 locale variants)
- Modify: `docs/AI_AGENT_HANDOFF.md`
- Modify: `README.md`

- [ ] **Step 1: Translate the 5 new strings into all 24 locale variants**

The new keys: `camera_take_photo`, `photo_analyzing`, `photo_analysis_failed`, `prompt_photo_analysis`, `session_photos_title`. List the variants with `ls app/src/main/res | grep values-`. For each `values-<locale>/strings.xml`, add the five keys translated into that locale (short UI strings; `prompt_photo_analysis` is the vision instruction and, like the system prompts, should be translated so the description language matches user expectations — but keep instructions semantically identical). Missing translations fall back to English, so if a locale's translation is uncertain, prefer a faithful simple translation over omission.

- [ ] **Step 2: Update docs**

`docs/AI_AGENT_HANDOFF.md`: add a sibling section after §7 titled "8. Camera vision insight (2026-07-07)" covering, in the doc's established terse style: the mtmd native build (where it's wired), the `imagePath`-threading pattern (all params default null), `DefaultModelConfig` = Gemma 3 4B + mmproj (Q8_0 enum name is historical), `PhotoContextQueue` + `queuePhotoDescription` flow, `session_photos` table (DB v10), and the "no CAMERA permission by design" gotcha. Renumber subsequent sections (current §8 "Gotchas" → §9, §9 → §10, §10 → §11) and bump the "Last updated" date. Update the §5 testing note to mention the two new domain test classes.

`README.md`: in the Features list, add a line for on-device photo analysis (camera capture during recording, vision-capable default model); update the model description where Gemma 3 1B is named as the default LLM (Features bullet ~line 56-57 and any "Recording Modes"/LLM-pipeline mention of the default model — search for `1B` and reconcile each hit that describes the *default* variant).

- [ ] **Step 3: Full verification**

Run: `./gradlew.bat :domain:testDebugUnitTest && ./gradlew.bat :app:assembleDebug`
Expected: all domain tests PASS; BUILD SUCCESSFUL (full APK including native mtmd link).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res docs/AI_AGENT_HANDOFF.md README.md
git commit -m "docs: camera vision insight — locale strings, handoff and README updates"
```

- [ ] **Step 5: Human on-device verification (hand off to user)**

The agent environment cannot run an emulator, and vision quality/latency can only be judged on hardware. Ask the user to verify on a physical device (needs ~3.4 GB free for the vision model pair + ~1.7 GB STT):
1. Download the default (vision) model via onboarding or Settings — confirm combined progress covers both files.
2. Start a Short Meeting recording — camera button appears next to the lock button.
3. Take a photo of a whiteboard/document — "Analyzing photo…" banner shows, button disabled meanwhile.
4. Wait for the next insight — it should reference photo content.
5. Stop the session, open session details — photo thumbnail + description visible.
6. Switch to a text-only variant (e.g. IQ4_NL) in Settings — camera button disappears.

---

## Execution notes for the implementer

- Task order matters: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. Tasks 2 and 5-6 are independent of each other but all are prerequisites of 7.
- Every commit must leave the build green (`:app:compileDebugKotlin` minimum; `assembleDebug` for Tasks 2 and 9).
- The vendored llama.cpp tree at `F:\Git\llama.cpp-master` is OUTSIDE this repo — never edit it. If an mtmd source fails to compile, suppress the warning via CMake flags in our CMakeLists.
- If HuggingFace URLs change or the mmproj filename differs upstream, verify at https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/tree/main before adjusting `DefaultModelConfig`.
