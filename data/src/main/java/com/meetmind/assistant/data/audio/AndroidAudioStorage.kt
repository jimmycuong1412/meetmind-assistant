package com.meetmind.assistant.data.audio

import android.content.Context
import android.util.Log
import com.meetmind.assistant.domain.audio.AudioStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-backed [AudioStorage] that writes session WAV files into
 * `<context.filesDir>/audio/<sessionId>.wav`.
 *
 * filesDir is app-private, survives reboot, and is cleared on uninstall —
 * matches the semantics we want for transient diarization audio. The
 * directory is created lazily on first call so we don't pollute filesDir
 * for installs that never opt into audio retention.
 */
@Singleton
class AndroidAudioStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioStorage {

    private companion object {
        private const val TAG = "AudioStorage"
        private const val AUDIO_DIR = "audio"
        private const val EXTENSION = "wav"
    }

    private val audioDir: File
        get() = File(context.filesDir, AUDIO_DIR)

    override fun audioFilePathForSession(sessionId: String): String {
        // Sanitize defensively even though session ids are UUIDs we control —
        // if a future migration changes the id format, this avoids path traversal.
        val safeId = sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(audioDir, "$safeId.$EXTENSION").absolutePath
    }

    override fun deleteAudioForSession(sessionId: String): Boolean {
        val path = audioFilePathForSession(sessionId)
        return try {
            val deleted = File(path).delete()
            if (deleted) {
                Log.i(TAG, "Deleted audio file for session $sessionId")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete audio for session $sessionId", e)
            false
        }
    }
}
