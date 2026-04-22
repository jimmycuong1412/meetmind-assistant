// T019 (spec 007): On-device inference provider backed by llama.cpp + Gemma 4 E4B
// spec 009 — T033: Path-change detection — unload before loading a different model
package com.meetmind.assistant.inference

import android.util.Log
import com.meetmind.assistant.data.ModelConfigRepository
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.ModelLoadState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Implements [CloudInferenceEngine.OnDeviceFallback] using the llama.cpp JNI bridge.
 *
 * Responsibilities:
 * - Lazily loads the Gemma 4 E4B Q4_K_M GGUF on first [loadModel] call
 * - Streams [InferenceEvent.Token] fragments via [callbackFlow] as the model generates
 * - Enforces data minimisation: questionText ≤ 600 chars, systemPrompt ≤ 320 chars
 * - Thread-safe model lifecycle via [ReentrantReadWriteLock]
 *
 * @param configRepository  Source of truth for model path + runtime parameters
 * @param backend           llama.cpp JNI implementation; defaults to [LlamaJni] in production,
 *                          injectable for testing via [FakeLlamaJni]
 */
class OnDeviceLlamaProvider(
    private val configRepository: ModelConfigRepository,
    internal val backend: LlamaBackend = LlamaJni,
    /** Overridable in tests to avoid dispatching to a real thread pool. */
    private val loadDispatcher: CoroutineDispatcher = Dispatchers.Default
) : CloudInferenceEngine.OnDeviceFallback {

    companion object {
        private const val TAG = "OnDeviceLlamaProvider"
        private const val MAX_QUESTION_CHARS = 600   // Principle I data minimisation — mirrors CloudInferenceEngine
        private const val MAX_SYSTEM_CHARS   = 320   // Principle I — ≈80 tokens

        /** System prompt prepended to every on-device inference call. ≤ 320 chars. */
        internal fun buildSystemPrompt(): String =
            "You are a real-time meeting assistant. Answer the question concisely in 1-3 sentences."
        // length = 89 chars < 320 — verified by contract test C5.3
    }

    private val lock = ReentrantReadWriteLock()
    private var modelHandle: Long = -1L

    /**
     * spec 009 T033: Tracks the file path of the currently-loaded model.
     * If [loadModel] is called with a different path, [unload] is triggered first.
     * Protected by [lock] (write lock on mutation, read lock on read).
     */
    private var currentModelPath: String? = null

    // ── Model lifecycle ────────────────────────────────────────────────────────

    /**
     * Load the model into RAM using the current [ModelConfig].
     * Transitions: NotLoaded → Loading → Ready | Error
     *
     * spec 009 T033: If a different model path is requested, the currently loaded model
     * is unloaded first (C5.1). If the same path is requested and the model is already
     * loaded, this call is a no-op (C5.2).
     *
     * Should be called from the UI thread (via ViewModel); suspends on IO dispatcher.
     *
     * @param overridePath Optional path override; defaults to the path in [ModelConfigRepository].
     */
    suspend fun loadModel(overridePath: String? = null): ModelLoadState = withContext(loadDispatcher) {
        val config = configRepository.observe().first()
        val requestedPath = overridePath ?: config.modelPath
        Log.i(TAG, "Loading model from $requestedPath (threads=${config.nThreads}, gpu=${config.nGpuLayers})")
        val t0 = System.currentTimeMillis()

        lock.write {
            // C5.2: Same path + already loaded → idempotent, skip reload
            if (modelHandle > 0 && currentModelPath == requestedPath) {
                Log.i(TAG, "Model already loaded at same path (handle=$modelHandle) — skipping reload")
                return@withContext ModelLoadState.Ready(modelHandle)
            }

            // C5.1: Different path + model loaded → unload first
            if (modelHandle > 0 && currentModelPath != requestedPath) {
                Log.i(TAG, "Path change detected ($currentModelPath → $requestedPath) — unloading first")
                backend.unloadModel(modelHandle)
                modelHandle = -1L
                currentModelPath = null
            }

            val handle = backend.loadModel(
                path        = requestedPath,
                nThreads    = config.nThreads,
                nGpuLayers  = config.nGpuLayers,
                contextSize = config.contextSize
            )
            modelHandle = handle
            val elapsed = System.currentTimeMillis() - t0
            return@withContext if (handle > 0) {
                currentModelPath = requestedPath
                Log.i(TAG, "Model loaded in ${elapsed}ms (handle=$handle)")
                ModelLoadState.Ready(handle)
            } else {
                Log.e(TAG, "loadModel() returned $handle after ${elapsed}ms")
                ModelLoadState.Error("Failed to load model from '$requestedPath'. " +
                    "Ensure the file exists and is a valid GGUF. (result=$handle)")
            }
        }
    }

    /**
     * Release native model resources. Safe to call multiple times (idempotent).
     * Call from [MeetMindApplication.onTrimMemory] when TRIM_MEMORY_RUNNING_CRITICAL.
     */
    fun unload() {
        lock.write {
            if (modelHandle > 0) {
                Log.i(TAG, "Unloading model (handle=$modelHandle, path=$currentModelPath)")
                backend.unloadModel(modelHandle)
                modelHandle = -1L
                currentModelPath = null  // spec 009 T033: clear path on explicit unload
            }
        }
    }

    /**
     * Returns true if the model is currently loaded and ready for inference.
     * Used by [DefaultConversationAnalyzer] to decide between full inference and label-only cards.
     */
    fun isLoaded(): Boolean = lock.read { modelHandle > 0 }

    // ── Inference ──────────────────────────────────────────────────────────────

    /**
     * Stream a suggestion for [questionText] via the loaded on-device model.
     *
     * Emits [InferenceEvent.Token] for each generated token, then
     * [InferenceEvent.Complete] when done. Emits [InferenceEvent.Error] if the
     * model is not loaded or [questionText] is blank.
     *
     * Constitution Principle I: [questionText] is truncated to [MAX_QUESTION_CHARS]
     * before reaching the native layer. No audio bytes are accepted.
     */
    override fun generate(questionText: String): Flow<InferenceEvent> = callbackFlow {
        val requestId = UUID.randomUUID().toString()

        // Validate inputs before touching native layer
        if (questionText.isBlank()) {
            send(InferenceEvent.Error(requestId, "empty question"))
            close()
            return@callbackFlow
        }

        val handle = lock.read { modelHandle }
        if (handle <= 0) {
            send(InferenceEvent.Error(requestId, "Model not loaded. Open Settings → On-Device Model and tap Load."))
            close()
            return@callbackFlow
        }

        // Data minimisation — Principle I
        val truncatedText = questionText.take(MAX_QUESTION_CHARS)
        val systemPrompt  = buildSystemPrompt().take(MAX_SYSTEM_CHARS)

        val fullText = StringBuilder()
        val t0 = System.currentTimeMillis()
        var firstTokenMs = -1L

        val callback = object : TokenCallback {
            override fun onToken(token: String) {
                fullText.append(token)
                if (firstTokenMs < 0) {
                    firstTokenMs = System.currentTimeMillis() - t0
                    Log.d(TAG, "First token latency: ${firstTokenMs}ms (requestId=$requestId)")
                }
                val result = trySend(InferenceEvent.Token(requestId, token))
                if (result.isFailure) {
                    Log.w(TAG, "Channel closed before token could be sent")
                }
            }

            override fun onComplete(assembledText: String) {
                val elapsed = System.currentTimeMillis() - t0
                val tokenCount = fullText.length  // rough char-level estimate
                Log.i(TAG, "Inference complete: ${elapsed}ms, ~${tokenCount} chars (requestId=$requestId)")
                // On-device provider uses GEMINI as a sentinel provider (no real provider concept)
                val result = trySend(
                    InferenceEvent.Complete(
                        requestId = requestId,
                        fullText = assembledText,
                        provider = com.meetmind.assistant.data.model.CloudProvider.GEMINI
                    )
                )
                if (result.isFailure) {
                    Log.w(TAG, "Channel closed before Complete could be sent")
                }
                close()
            }

            override fun onError(message: String) {
                Log.e(TAG, "Native inference error: $message (requestId=$requestId)")
                close(Exception("On-device inference error: $message"))
            }
        }

        backend.inferenceAsync(handle, systemPrompt, truncatedText, callback)

        awaitClose {
            // Native cancellation could be hooked here in a future iteration
            Log.d(TAG, "callbackFlow collector cancelled (requestId=$requestId)")
        }
    }
}
