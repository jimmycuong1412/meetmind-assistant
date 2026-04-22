package com.meetmind.assistant.data.model

/**
 * A single ASR result emitted by [SherpaOnnxDataSource].
 *
 * [isComplete] = true when the VAD segment has ended and the result is final.
 * Only complete results are appended to [TranscriptWindowBuffer].
 *
 * spec 009 — T007
 */
data class RecognitionResult(
    val text: String,
    val isComplete: Boolean
)
