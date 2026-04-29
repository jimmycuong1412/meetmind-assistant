package com.meetmind.assistant.domain.audio

/**
 * Filesystem-aware audio retention helper.
 *
 * Encapsulates where on-device audio recordings are written so the rest of
 * the app can address them by session id without leaking platform paths.
 * Used by the recording pipeline (to write the WAV mirror), by the
 * diarization pipeline (to read it back), and by cleanup paths (to delete
 * audio after diarization completes or a session is deleted).
 *
 * Implementations must be reentrant — concurrent calls for different
 * sessions must not interfere.
 */
interface AudioStorage {
    /**
     * Absolute path where the WAV recording for [sessionId] should live.
     * Idempotent: calling this multiple times with the same id returns the
     * same path. Creating the file is the recorder's responsibility, not
     * this method's; this only resolves the location.
     */
    fun audioFilePathForSession(sessionId: String): String

    /**
     * Delete the audio file for [sessionId] if it exists. Returns true if a
     * file was actually deleted. Safe to call when the file is missing.
     */
    fun deleteAudioForSession(sessionId: String): Boolean
}
