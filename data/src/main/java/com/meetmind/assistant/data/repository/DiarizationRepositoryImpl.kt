package com.meetmind.assistant.data.repository

import android.util.Log
import com.meetmind.assistant.domain.model.DiarizationResult
import com.meetmind.assistant.domain.repository.DiarizationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder [DiarizationRepository] implementation that fails fast.
 *
 * The on-device diarization pipeline depends on sherpa-onnx's
 * `OfflineSpeakerDiarization` Kotlin/JNI bindings, which are not present in
 * the project's local sherpa-onnx fork yet. Until those bindings are pulled
 * in (or replaced with a direct ONNX Runtime + clustering implementation),
 * this stub returns:
 *   - `isModelAvailable() = false` so the UI surfaces a "model unavailable"
 *     state rather than a half-functional download flow,
 *   - `diarize() = Result.failure(...)` with an explanatory message so the
 *     SessionDetailsViewModel error path is exercised by integration tests
 *     and visible during manual QA.
 *
 * Replacing this with the real impl is mechanical: keep the interface,
 * wire up the sherpa native session, and update the DI binding in
 * [com.meetmind.assistant.di.DataModule].
 */
@Singleton
class DiarizationRepositoryImpl @Inject constructor() : DiarizationRepository {

    private companion object {
        private const val TAG = "DiarizationRepo"
    }

    override fun isModelAvailable(): Boolean {
        // Hard-coded false until the real bindings exist. We don't even check
        // the model directory yet — there is no reader for the file format.
        return false
    }

    override suspend fun diarize(audioFilePath: String): Result<DiarizationResult> {
        Log.w(TAG, "diarize() called but the on-device pipeline is not yet implemented (path=$audioFilePath)")
        return Result.failure(
            UnsupportedOperationException(
                "On-device speaker diarization is not yet enabled in this build. " +
                    "Audio has been retained for this session and will be diarized " +
                    "automatically once the model is supported."
            )
        )
    }
}
