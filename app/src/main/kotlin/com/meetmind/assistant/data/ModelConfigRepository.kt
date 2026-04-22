// T018 (spec 007): DataStore repository for on-device model configuration
// spec 009 — T029: Added GemmaModelVariant persistence (observeVariant, setModelVariant, resolvedModelPath)
package com.meetmind.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meetmind.assistant.data.model.GemmaModelVariant
import com.meetmind.assistant.data.model.ModelConfig
import com.meetmind.assistant.inference.LlamaBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.onDeviceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "on_device_config"
)

/**
 * Persists and exposes [ModelConfig] for the on-device Gemma 4 E4B inference engine.
 *
 * Uses a dedicated DataStore file (`on_device_config`) distinct from the cloud config
 * DataStore (`cloud_provider_config`) to keep concerns separated.
 *
 * All suspend functions use DataStore's internal threading — no explicit Dispatchers needed.
 */
class ModelConfigRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.onDeviceDataStore
) {

    companion object {
        val KEY_MODEL_PATH      = stringPreferencesKey("on_device_model_path")
        val KEY_N_THREADS       = intPreferencesKey("on_device_n_threads")
        val KEY_N_GPU_LAYERS    = intPreferencesKey("on_device_n_gpu_layers")
        val KEY_CONTEXT_SIZE    = intPreferencesKey("on_device_context_size")
        /** spec 009 — T029: Selected GemmaModelVariant name (default Q4_K_M). */
        val KEY_MODEL_VARIANT   = stringPreferencesKey("on_device_model_variant")
    }

    /** Observe the current [ModelConfig], emitting updates whenever any field changes. */
    fun observe(): Flow<ModelConfig> = dataStore.data.map { prefs ->
        ModelConfig(
            modelPath   = prefs[KEY_MODEL_PATH]   ?: ModelConfig.DEFAULT_PATH,
            nThreads    = (prefs[KEY_N_THREADS]   ?: ModelConfig.DEFAULT_THREADS).coerceIn(1, 16),
            nGpuLayers  = prefs[KEY_N_GPU_LAYERS]  ?: ModelConfig.DEFAULT_GPU_LAYERS,
            contextSize = prefs[KEY_CONTEXT_SIZE]  ?: ModelConfig.DEFAULT_CONTEXT_SIZE,
        )
    }

    /** Persist a new model file path. */
    suspend fun setModelPath(path: String) {
        dataStore.edit { it[KEY_MODEL_PATH] = path }
    }

    /**
     * Persist the number of CPU threads. Value is clamped to [1..16].
     * Contract C2.3: setNThreads(0) → stores 1; setNThreads(99) → stores 16.
     */
    suspend fun setNThreads(n: Int) {
        dataStore.edit { it[KEY_N_THREADS] = n.coerceIn(1, 16) }
    }

    /** Persist the number of GPU layers to offload (-1 = all). */
    suspend fun setNGpuLayers(n: Int) {
        dataStore.edit { it[KEY_N_GPU_LAYERS] = n }
    }

    /** Persist the KV cache context size in tokens. */
    suspend fun setContextSize(n: Int) {
        dataStore.edit { it[KEY_CONTEXT_SIZE] = n }
    }

    /**
     * Returns true iff the model file at the configured path exists on disk AND the
     * [backend] reports it as a valid GGUF file (header check only, no full load).
     *
     * Contract C2.2.
     */
    suspend fun isModelReady(backend: LlamaBackend): Boolean {
        val path = dataStore.data.map { it[KEY_MODEL_PATH] ?: ModelConfig.DEFAULT_PATH }.first()
        return File(path).exists() && backend.canLoad(path)
    }

    // ── spec 009 T029: GemmaModelVariant persistence ──────────────────────────

    /**
     * Observe the current [GemmaModelVariant] selection.
     * Defaults to [GemmaModelVariant.Q4_K_M] on fresh DataStore.
     */
    fun observeVariant(): Flow<GemmaModelVariant> = dataStore.data.map { prefs ->
        val name = prefs[KEY_MODEL_VARIANT] ?: GemmaModelVariant.Q4_K_M.name
        try {
            GemmaModelVariant.valueOf(name)
        } catch (_: IllegalArgumentException) {
            GemmaModelVariant.Q4_K_M  // graceful fallback if stored value is corrupted
        }
    }

    /** Persist the selected [GemmaModelVariant]. */
    suspend fun setModelVariant(variant: GemmaModelVariant) {
        dataStore.edit { it[KEY_MODEL_VARIANT] = variant.name }
    }

    /**
     * Returns the absolute path where the GGUF file for [variant] should reside.
     * Convention: `<externalFilesDir>/models/<llmFilename>`.
     */
    fun resolvedModelPath(variant: GemmaModelVariant, filesDir: File): String =
        File(filesDir, "models/${variant.llmFilename}").absolutePath
}
