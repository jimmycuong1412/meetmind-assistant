package com.meetmind.assistant.domain.repository

import com.meetmind.assistant.domain.model.TranscriptionSegment
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Speech-To-Text operations.
 *
 * Provides streaming transcription from audio input using Sherpa-ONNX models.
 */
interface SttRepository {
    /**
     * Initialize the STT engine with a model and language.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @param languageCode BCP-47 language code (e.g. "en", "vi")
     * @return Result indicating success or failure
     */
    suspend fun initialize(modelPath: String, languageCode: String): Result<Unit>

    /**
     * Configure audio retention for the next [startStreaming] call. When
     * non-null, the underlying engine mirrors the live PCM stream to a WAV
     * file at [path] so end-of-session diarization can run later. Pass null
     * (the default) to disable retention for the next session.
     *
     * Has no effect on a session already in progress.
     */
    fun setAudioOutputFile(path: String?)

    /**
     * Configure audio input routing for the next [startStreaming] call.
     *
     * @param prefer When false (the default), capture is pinned to the built-in
     *   microphone even if a Bluetooth headset is connected; when true, the headset
     *   mic is used.
     *
     * The default is false for transcription accuracy: using a BT headset mic forces
     * the audio stack into communication (HFP/SCO) mode — a narrowband, codec-
     * compressed, HAL-processed telephony path — whereas the built-in mic delivers the
     * full-band 16 kHz raw PCM the STT model expects. See
     * [com.meetmind.assistant.domain.model.AppSettings.preferBluetoothMic].
     *
     * Has no effect on a session already in progress.
     */
    fun setPreferBluetoothMic(prefer: Boolean)

    /**
     * Start streaming audio recording and transcription.
     *
     * @return Flow of transcription segments. Flow never completes until stopStreaming is called.
     */
    fun startStreaming(): Flow<TranscriptionSegment>

    /**
     * Stop streaming and release audio resources.
     */
    suspend fun stopStreaming()

    /**
     * Absolute path of the WAV file written during the most recently completed
     * recording session, or null when audio retention was disabled / failed /
     * captured zero samples. Valid only after [stopStreaming] returns.
     */
    fun lastRecordedAudioFilePath(): String?

    /**
     * Release the native STT model from memory (~670 MB footprint).
     *
     * Safe to call after stopStreaming() completes. The model will be lazily
     * recreated on the next initialize() call.
     */
    suspend fun releaseModel()
}
