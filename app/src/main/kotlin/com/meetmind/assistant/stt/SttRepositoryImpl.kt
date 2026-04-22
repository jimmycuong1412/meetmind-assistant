// spec 009 — T040: SttRepositoryImpl — delegates to SherpaOnnxDataSource
package com.meetmind.assistant.stt

import com.meetmind.assistant.data.model.RecognitionResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pass-through implementation of [SttRepository] backed by [SherpaOnnxDataSource].
 *
 * The indirection layer exists to allow test doubles to be injected via Hilt
 * without needing the native Sherpa-ONNX JNI library available in unit tests.
 *
 * spec 009 — T040
 */
@Singleton
class SttRepositoryImpl @Inject constructor(
    private val dataSource: SherpaOnnxDataSource
) : SttRepository {

    override suspend fun initialize(sttModelPath: String) =
        dataSource.initialize(sttModelPath)

    override fun startRecording(): Flow<RecognitionResult> =
        dataSource.startRecording()

    override suspend fun stopRecording() =
        dataSource.stopRecording()

    override suspend fun releaseModel() =
        dataSource.releaseModel()
}
