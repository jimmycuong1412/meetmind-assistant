package com.meetmind.assistant.data.datasource

import kotlinx.coroutines.flow.Flow

/**
 * Data source interface for Speech-To-Text operations.
 *
 * Provides low-level audio recording and recognition functionality.
 */
interface SttDataSource {
    /**
     * Initialize the STT engine with a model and language.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @param languageCode BCP-47 language code (e.g. "en", "vi")
     * @return Result indicating success or failure
     */
    suspend fun initialize(modelPath: String, languageCode: String): Result<Unit>

    /**
     * Configure audio retention for the next [startRecording] call. When non-null,
     * the data source mirrors the PCM stream to a 16 kHz mono PCM-16 WAV file at
     * the given path so end-of-session diarization can run later. Pass null
     * (the default) to disable audio retention for the next session.
     *
     * Must be called BEFORE [startRecording]. Calling during an active session
     * has no effect on that session.
     */
    fun setAudioOutputFile(path: String?)

    /**
     * Configure audio input routing for subsequent [startRecording] calls.
     *
     * @param prefer When false (the default), capture is pinned to the built-in
     *   microphone even if a Bluetooth headset is connected. When true, a connected
     *   BT headset mic is used instead.
     *
     * Defaults to false because routing to a BT headset forces the audio stack into
     * communication (HFP/SCO) mode — a narrowband, codec-compressed, HAL-processed
     * telephony path that measurably degrades transcription accuracy relative to the
     * built-in mic's full-band 16 kHz raw PCM. See
     * [com.meetmind.assistant.domain.model.AppSettings.preferBluetoothMic].
     *
     * Must be called BEFORE [startRecording]; changing it during an active session
     * has no effect on that session.
     */
    fun setPreferBluetoothMic(prefer: Boolean)

    /**
     * Start audio recording and real-time recognition.
     *
     * @return Flow of recognition results
     */
    fun startRecording(): Flow<RecognitionResult>

    /**
     * Stop audio recording and release resources.
     */
    suspend fun stopRecording()

    /**
     * Absolute path of the WAV file written during the most recently completed
     * recording session, or null if audio retention was disabled, the recorder
     * failed to open, or the session captured zero samples. Valid only after
     * [stopRecording] has returned.
     */
    fun lastRecordedAudioFilePath(): String?

    /**
     * Release the native STT model from memory (~670 MB footprint).
     *
     * Safe to call after stopRecording() completes. The model will be lazily
     * recreated on the next initialize() call.
     */
    suspend fun releaseModel()
}

/**
 * Result from speech recognition.
 *
 * @property text Recognized text
 * @property isComplete Whether this represents a complete utterance (speech segment ended)
 * @property startOffsetMs Audio offset of segment start, measured from the beginning
 *   of the current recording session in milliseconds. Derived from total samples
 *   consumed; not wall-clock-correlated. Null only if the data source could not
 *   compute it (legacy/non-segment-aware paths).
 * @property endOffsetMs Audio offset of segment end, in ms since recording start.
 *   For partial results this is the offset of the latest sample seen so far.
 */
data class RecognitionResult(
    val text: String,
    val isComplete: Boolean,
    val startOffsetMs: Long? = null,
    val endOffsetMs: Long? = null
)
