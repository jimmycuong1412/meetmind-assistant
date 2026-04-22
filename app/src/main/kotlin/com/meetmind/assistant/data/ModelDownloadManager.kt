// spec 009 — T031: HTTP download with Range-header resume capability
package com.meetmind.assistant.data

import android.content.Context
import android.util.Log
import com.meetmind.assistant.data.model.DownloadProgress
import com.meetmind.assistant.data.model.SttModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads model files over HTTP(S) with Range-header resume support.
 *
 * Resume protocol (contracts.md C2.2):
 *  1. On first call: download to `<destFile>.partial`
 *  2. On subsequent calls: read existing `.partial` size → add `Range: bytes=N-` header
 *  3. On completion: rename `.partial` → final `destFile`
 *
 * All disk I/O runs on [Dispatchers.IO].
 *
 * spec 009 — T031
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val BUFFER_SIZE = 8 * 1024  // 8 KB
    }

    /**
     * Downloads a file from [url] to [destFile].
     * Emits [DownloadProgress] as bytes are written.
     * Resumes from byte offset if a `.partial` file exists.
     *
     * @param url      Full HTTPS URL of the model file
     * @param destFile Destination file (the final path; `.partial` suffix is managed internally)
     */
    fun downloadFile(url: String, destFile: File): Flow<DownloadProgress> = flow {
        destFile.parentFile?.mkdirs()
        val partialFile = File("${destFile.absolutePath}.partial")
        val resumeFrom = if (partialFile.exists()) partialFile.length() else 0L

        if (resumeFrom > 0) {
            Log.d(TAG, "Resuming download from byte $resumeFrom (Range: bytes=$resumeFrom-) → ${destFile.name}")
        } else {
            Log.d(TAG, "Starting download: ${destFile.name} from $url")
        }

        val requestBuilder = Request.Builder().url(url)
        if (resumeFrom > 0) {
            requestBuilder.addHeader("Range", "bytes=$resumeFrom-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw IOException("HTTP ${response.code}: ${response.message} — $url")
        }

        val contentLength = response.body?.contentLength() ?: -1L
        val totalBytes = if (resumeFrom > 0 && contentLength > 0) {
            resumeFrom + contentLength
        } else {
            contentLength
        }

        val filename = destFile.name
        var bytesDownloaded = resumeFrom

        val outputStream = FileOutputStream(partialFile, /* append= */ resumeFrom > 0)
        outputStream.use { out ->
            val inputStream = response.body?.byteStream()
                ?: throw IOException("Empty response body for $url")
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                emit(
                    DownloadProgress(
                        filename = filename,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        isComplete = false
                    )
                )
            }
        }

        // Rename .partial → final file on success
        partialFile.renameTo(destFile)
        Log.d(TAG, "Download complete: ${destFile.name} (${bytesDownloaded / 1_000_000}MB)")
        emit(
            DownloadProgress(
                filename = filename,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                isComplete = true
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Returns true if all 4 Parakeet TDT STT model files are present in [sttModelDir].
     * Used by [AudioProcessingForegroundService] and route guards.
     */
    fun sttModelsReady(sttModelDir: File): Boolean =
        SttModelConfig.STT_FILES.all { filename -> File(sttModelDir, filename).exists() }

    /**
     * Emits [DownloadProgress] for each of the 4 STT model files in sequence.
     * Files are downloaded to [sttModelDir] using [SttModelConfig] constants.
     */
    fun downloadSttModel(sttModelDir: File): Flow<DownloadProgress> = flow {
        sttModelDir.mkdirs()
        val config = SttModelConfig()
        config.files.forEach { filename ->
            val fileUrl = "${config.baseUrl}/$filename"
            val destFile = File(sttModelDir, filename)
            if (!destFile.exists()) {
                downloadFile(fileUrl, destFile).collect { emit(it) }
            } else {
                Log.d(TAG, "STT file already present, skipping: $filename")
                emit(
                    DownloadProgress(
                        filename = filename,
                        bytesDownloaded = destFile.length(),
                        totalBytes = destFile.length(),
                        isComplete = true
                    )
                )
            }
        }
    }
}
