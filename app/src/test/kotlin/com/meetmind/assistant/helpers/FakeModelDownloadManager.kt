package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.DownloadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test double for ModelDownloadManager.
 *
 * Emits a configurable sequence of [DownloadProgress] events and tracks how many
 * download calls were made (for contract C2.1 / C2.2 tests).
 *
 * spec 009 — T010
 */
class FakeModelDownloadManager(
    /** Emitted in order when [downloadFile] is called. If empty, emits a single complete event. */
    private val progressSequence: List<DownloadProgress> = listOf(
        DownloadProgress("file.bin", 0L, 1024L, false),
        DownloadProgress("file.bin", 512L, 1024L, false),
        DownloadProgress("file.bin", 1024L, 1024L, true)
    )
) {
    val downloadCallCount = AtomicInteger(0)

    /** Last URL passed to [downloadFile]. */
    var lastUrl: String? = null
        private set

    /** Last destFile passed to [downloadFile]. */
    var lastDestFile: File? = null
        private set

    fun downloadFile(url: String, destFile: File): Flow<DownloadProgress> {
        downloadCallCount.incrementAndGet()
        lastUrl = url
        lastDestFile = destFile
        return flow {
            progressSequence.forEach { emit(it) }
        }
    }

    /** Convenience: returns true if all expected files are present in [modelDir]. */
    fun sttModelsReady(modelDir: File, requiredFiles: List<String>): Boolean =
        requiredFiles.all { File(modelDir, it).exists() }
}
