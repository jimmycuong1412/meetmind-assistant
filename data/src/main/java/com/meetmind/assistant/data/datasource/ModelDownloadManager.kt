package com.meetmind.assistant.data.datasource

import android.content.Context
import android.util.Log
import com.meetmind.assistant.data.config.ModelConfig
import com.meetmind.assistant.data.config.DefaultModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages downloading and verification of on-device model files.
 *
 * Model URLs and filenames are supplied via [ModelConfig] so that they can be changed
 * (e.g. for different build variants or A/B tests) without touching this class.
 */
class ModelDownloadManager(
    private val context: Context,
    private val modelConfig: ModelConfig = DefaultModelConfig.INSTANCE
) {
    // App-specific external storage: no special permissions needed, deleted on uninstall (Android 4.4+)
    private val modelsDir = File(context.getExternalFilesDir(null), "models")

    companion object {
        private const val TAG = "ModelDownloadManager"
    }

    init {
        modelsDir.mkdirs()
    }

    /**
     * Download progress state.
     */
    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percentage: Int
    )

    /**
     * Get the path to the LLM model file.
     *
     * @param filename The model filename to check; defaults to the injected [ModelConfig] filename.
     *                 Pass a variant-specific filename to check a non-default variant.
     */
    fun getLlmModelPath(filename: String = modelConfig.llmFilename): String? {
        val modelFile = File(modelsDir, filename)
        return if (modelFile.exists() && modelFile.length() > 0) {
            modelFile.absolutePath
        } else {
            null
        }
    }

    /**
     * Check if the LLM model file is already downloaded.
     *
     * @param filename The model filename to check; defaults to the injected [ModelConfig] filename.
     */
    fun isLlmModelDownloaded(filename: String = modelConfig.llmFilename): Boolean {
        return getLlmModelPath(filename) != null
    }

    /**
     * Download LLM model with progress updates and resume capability.
     */
    fun downloadLlmModel(
        url: String = modelConfig.llmUrl,
        filename: String = modelConfig.llmFilename
    ): Flow<DownloadProgress> = flow {
        val outputFile = File(modelsDir, filename)
        val partialFile = File(modelsDir, "$filename.partial")

        // If already exists and is valid, don't re-download
        if (outputFile.exists() && outputFile.length() > 100_000_000) { // At least 100MB
            Log.i(TAG, "Model already exists: ${outputFile.absolutePath}")
            emit(DownloadProgress(outputFile.length(), outputFile.length(), 100))
            return@flow
        }

        // First, get the actual file size via HEAD request
        Log.i(TAG, "Fetching actual file size...")
        var totalBytes = 0L
        var headConnection: HttpURLConnection? = null
        try {
            headConnection = URL(url).openConnection() as HttpURLConnection
            headConnection.requestMethod = "HEAD"
            headConnection.connectTimeout = 15000
            headConnection.instanceFollowRedirects = true
            headConnection.connect()

            totalBytes = headConnection.contentLengthLong
            if (totalBytes <= 0) {
                Log.w(TAG, "Could not get file size from HEAD request, will try during download")
            } else {
                Log.i(TAG, "LLM model size: ${totalBytes / 1_000_000}MB")
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD request failed, will get size during download", e)
        } finally {
            headConnection?.disconnect()
        }

        // Check for partial download
        val startByte = if (partialFile.exists()) {
            val existingSize = partialFile.length()
            Log.i(TAG, "Found partial download: ${existingSize / 1_000_000}MB, resuming...")
            existingSize
        } else {
            0L
        }

        var connection: HttpURLConnection? = null
        try {
            Log.i(TAG, "Starting download from: $url (from byte $startByte)")
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            // Follow redirects (important for HuggingFace)
            connection.instanceFollowRedirects = true

            // Request range if resuming
            if (startByte > 0) {
                connection.setRequestProperty("Range", "bytes=$startByte-")
            }

            connection.connect()

            val responseCode = connection.responseCode

            // If we didn't get totalBytes from HEAD, get it from download response
            if (totalBytes <= 0) {
                totalBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL || responseCode == HttpURLConnection.HTTP_OK) {
                    if (startByte > 0 && responseCode == HttpURLConnection.HTTP_OK) {
                        // Server doesn't support resume, start from beginning
                        Log.w(TAG, "Server doesn't support resume, restarting download")
                        partialFile.delete()
                        connection.contentLengthLong
                    } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        // Resume supported, get total from Content-Range header
                        val contentRange = connection.getHeaderField("Content-Range")
                        if (contentRange != null && contentRange.contains("/")) {
                            contentRange.substringAfterLast("/").toLongOrNull() ?: (startByte + connection.contentLengthLong)
                        } else {
                            startByte + connection.contentLengthLong
                        }
                    } else {
                        connection.contentLengthLong
                    }
                } else {
                    throw Exception("Server returned error code: $responseCode")
                }
            }

            Log.i(TAG, "Total size: ${totalBytes / 1_000_000}MB, starting from: ${startByte / 1_000_000}MB")

            val isResuming = startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL

            connection.inputStream.use { input ->
                FileOutputStream(partialFile, isResuming).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = startByte

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val percentage = if (totalBytes > 0) {
                            ((totalBytesRead * 100) / totalBytes).toInt()
                        } else {
                            0
                        }

                        // Emit progress every 5%
                        if (percentage % 5 == 0) {
                            emit(DownloadProgress(totalBytesRead, totalBytes, percentage))
                        }
                    }

                    // Final progress
                    emit(DownloadProgress(totalBytesRead, totalBytes, 100))

                    // Move partial file to final location
                    if (partialFile.renameTo(outputFile)) {
                        Log.i(TAG, "Download completed: ${outputFile.absolutePath}")
                    } else {
                        // If rename fails, copy and delete
                        partialFile.copyTo(outputFile, overwrite = true)
                        partialFile.delete()
                        Log.i(TAG, "Download completed (via copy): ${outputFile.absolutePath}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed - partial file kept for resume", e)
            // Don't delete partial file - keep it for resume
            throw Exception("Download failed. You can retry to resume from ${startByte / 1_000_000}MB")
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Download multiple LLM-related files (base GGUF + optional mmproj) sequentially
     * with per-file Range-header resume and combined progress. Mirrors the STT
     * multi-file strategy: completed files are skipped on retry, the interrupted
     * file resumes from its .partial.
     *
     * @param files Ordered list of (url, filename) pairs.
     */
    fun downloadLlmFiles(files: List<Pair<String, String>>): Flow<DownloadProgress> = flow {
        // Best-effort HEAD for real sizes; fall back to on-disk partial size (never 0-div).
        val actualSizes = files.associate { (url, filename) ->
            filename to (headSize(url)
                ?: File(modelsDir, "$filename.partial").takeIf { it.exists() }?.length()
                ?: 0L)
        }
        val totalBytes = actualSizes.values.sum()

        // Seed with completed + partial bytes so the first emission reflects resume state.
        var totalBytesDownloaded = files.sumOf { (_, filename) ->
            val out = File(modelsDir, filename)
            val partial = File(modelsDir, "$filename.partial")
            when {
                out.exists() && out.length() > 0 -> out.length()
                partial.exists() && partial.length() > 0 -> partial.length()
                else -> 0L
            }
        }
        Log.i(TAG, "LLM files total: ${totalBytes / 1_000_000}MB, on disk: ${totalBytesDownloaded / 1_000_000}MB")

        files.forEachIndexed { index, (url, filename) ->
            val outputFile = File(modelsDir, filename)
            val partialFile = File(modelsDir, "$filename.partial")

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "LLM file $filename already complete, skipping")
                return@forEachIndexed
            }

            val startByte = partialFile.takeIf { it.exists() }?.length() ?: 0L
            Log.i(TAG, "Downloading LLM file ${index + 1}/${files.size}: $filename" +
                if (startByte > 0) " (resuming from ${startByte / 1_000_000}MB)" else "")
            // NOTE: totalBytesDownloaded already includes startByte from the seed scan.

            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                if (startByte > 0) connection.setRequestProperty("Range", "bytes=$startByte-")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode for $filename")
                }
                val isResuming = startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (startByte > 0 && !isResuming) {
                    Log.w(TAG, "Server did not honour Range for $filename, restarting this file")
                    totalBytesDownloaded -= startByte
                }

                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, isResuming).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesDownloaded += bytesRead
                            val pct = if (totalBytes > 0)
                                ((totalBytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                            else 0
                            if (pct % 2 == 0) {
                                emit(DownloadProgress(totalBytesDownloaded, totalBytes, pct))
                            }
                        }
                    }
                }

                // Promote .partial → final file
                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
            } catch (e: Exception) {
                connection?.disconnect()
                // Keep completed files and the .partial so retry resumes precisely.
                throw Exception("LLM download failed at $filename: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        val missing = files.map { it.second }.filter { !File(modelsDir, it).exists() }
        if (missing.isNotEmpty()) {
            throw Exception("Download incomplete: missing $missing")
        }
        emit(DownloadProgress(totalBytes, totalBytes, 100))
        Log.i(TAG, "LLM files download completed")
    }.flowOn(Dispatchers.IO)

    /**
     * Check if STT model is already downloaded for a specific language.
     */
    fun isSttModelDownloaded(languageCode: String = "en"): Boolean {
        val config = com.meetmind.assistant.data.config.modelConfigForLanguage(languageCode)
        val sttDir = File(modelsDir, "stt/${languageCode.take(2)}")
        return config.sttFiles.all { filename ->
            val file = File(sttDir, filename)
            file.exists() && file.length() > 0
        }
    }

    /**
     * Get the path to the STT model directory for a specific language.
     * Returns the path if all files exist, null otherwise.
     */
    fun getSttModelPath(languageCode: String = "en"): String? {
        return if (isSttModelDownloaded(languageCode)) {
            File(modelsDir, "stt/${languageCode.take(2)}").absolutePath
        } else {
            null
        }
    }

    /**
     * Download all STT model files sequentially with resume capability for a specific language.
     *
     * Each file is downloaded to a .partial temp file and renamed to its final name
     * only on successful completion. On failure:
     * - Already-completed files (final name) are kept → next retry skips them
     * - The .partial file of the file currently being downloaded is kept → Range
     *   header resume is attempted on the next retry if the server supports it
     *
     * This mirrors the LLM resume strategy so that any network interruption, even
     * on the first download attempt, does not force a full restart on retry.
     */
    fun downloadSttModel(languageCode: String = "en"): Flow<DownloadProgress> = flow {
        val config = com.meetmind.assistant.data.config.modelConfigForLanguage(languageCode)
        val sttDir = File(modelsDir, "stt/${languageCode.take(2)}")
        sttDir.mkdirs()

        if (isSttModelDownloaded(languageCode)) {
            Log.i(TAG, "STT model ($languageCode) already downloaded: ${sttDir.absolutePath}")
            emit(DownloadProgress(100_000_000, 100_000_000, 100))
            return@flow
        }

        // Best-effort HEAD requests for real file sizes; fall back to estimates on failure
        // so that a missing network at retry time does not block the size lookup.
        Log.i(TAG, "Fetching actual file sizes for $languageCode...")
        val actualFileSizes = mutableMapOf<String, Long>()
        config.sttFiles.forEach { filename ->
            try {
                val connection = URL("${config.sttBaseUrl}/$filename").openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()
                val size = connection.contentLengthLong
                actualFileSizes[filename] = if (size > 0) size else estimatedSttFileSize(filename)
                connection.disconnect()
                Log.i(TAG, "File $filename: ${size / 1_000_000}MB")
            } catch (e: Exception) {
                Log.w(TAG, "Could not get size for $filename, using estimate")
                // Use on-disk size if a .partial is already present (more accurate than hardcoded estimate)
                val partialSize = File(sttDir, "$filename.partial").takeIf { it.exists() }?.length() ?: 0L
                actualFileSizes[filename] = partialSize.coerceAtLeast(estimatedSttFileSize(filename))
            }
        }

        val totalBytes = actualFileSizes.values.sum()

        // Seed with completed files only. Partial-file bytes are added per-file
        // in the loop (startByte) so they are counted exactly once.
        var totalBytesDownloaded = config.sttFiles.sumOf { filename ->
            val f = File(sttDir, filename)
            if (f.exists() && f.length() > 0) f.length() else 0L
        }

        Log.i(TAG, "Total download size ($languageCode): ${totalBytes / 1_000_000}MB, already completed: ${totalBytesDownloaded / 1_000_000}MB")

        config.sttFiles.forEachIndexed { index, filename ->
            val outputFile = File(sttDir, filename)
            val partialFile = File(sttDir, "$filename.partial")

            // Skip files that are already fully downloaded
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "STT file $filename already complete, skipping")
                return@forEachIndexed
            }

            // Resume from .partial if available (Range request)
            val startByte = partialFile.takeIf { it.exists() }?.length() ?: 0L

            Log.i(TAG, "Downloading STT file ${index + 1}/${config.sttFiles.size}: $filename" +
                if (startByte > 0) " (resuming from ${startByte / 1_000_000}MB)" else "")

            // Count already-downloaded partial bytes so progress is correct from the start
            totalBytesDownloaded += startByte

            var connection: HttpURLConnection? = null
            try {
                connection = URL("${config.sttBaseUrl}/$filename").openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                if (startByte > 0) {
                    connection.setRequestProperty("Range", "bytes=$startByte-")
                }
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode for $filename")
                }

                // If server ignores Range and returns 200, restart this file from 0
                val isResuming = startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (startByte > 0 && !isResuming) {
                    Log.w(TAG, "Server did not honour Range for $filename, restarting this file")
                    totalBytesDownloaded -= startByte // undo the partial-bytes pre-count
                }

                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, isResuming).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var fileBytesWritten = if (isResuming) startByte else 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            fileBytesWritten += bytesRead
                            totalBytesDownloaded += bytesRead

                            val percentage = if (totalBytes > 0)
                                ((totalBytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                            else 0
                            if (percentage % 2 == 0) {
                                emit(DownloadProgress(totalBytesDownloaded, totalBytes, percentage))
                            }
                        }

                        Log.i(TAG, "STT file $filename complete (${fileBytesWritten / 1_000_000}MB)")
                    }
                }

                // Atomically promote .partial → final file
                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
            } catch (e: Exception) {
                connection?.disconnect()
                // .partial and all already-completed files are intentionally kept so the
                // next retry can skip completed files and resume the interrupted one.
                throw Exception("STT model download failed: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        if (!isSttModelDownloaded(languageCode)) {
            throw Exception("Download incomplete: some files are missing or empty")
        }

        emit(DownloadProgress(totalBytes, totalBytes, 100))
        Log.i(TAG, "STT model download completed: ${sttDir.absolutePath}")
    }.flowOn(Dispatchers.IO)

    /** Hardcoded size estimates used as fallback when HEAD requests fail. */
    private fun estimatedSttFileSize(filename: String): Long = when (filename) {
        // English (Parakeet)
        "encoder.int8.onnx" -> 68_000_000L
        "decoder.int8.onnx" -> 10_000_000L
        "joiner.int8.onnx"  -> 15_000_000L
        // Vietnamese (Zipformer)
        "encoder-epoch-12-avg-8.int8.onnx" -> 85_000_000L
        "decoder-epoch-12-avg-8.onnx"      -> 5_000_000L
        "joiner-epoch-12-avg-8.int8.onnx"   -> 5_000_000L
        else                -> 100_000L
    }

    // ── Diarization ────────────────────────────────────────────────────────

    /**
     * Where on-device diarization models live. Must agree with
     * `DiarizationRepositoryImpl.MODEL_DIR_NAME` (`filesDir/models/diarization/`).
     *
     * Note: this is `filesDir`, NOT `externalFilesDir`, because diarization
     * models are bound to native sherpa-onnx and we want them in fully
     * private storage (no external read).
     */
    private val diarizationDir = File(context.filesDir, "models/diarization")

    /**
     * Direct .onnx URLs — both files are hosted unmodified by csukuangfj's
     * sherpa-onnx Hugging Face mirror. We avoid the .tar.bz2 release archives
     * because Android has no built-in bzip2 decoder and we don't want to pull
     * in Apache Commons Compress just for two file downloads.
     */
    private val diarizationFiles = listOf(
        DiarizationFile(
            name = "segmentation.onnx",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/" +
                "resolve/main/model.onnx",
            estimatedSize = 6_000_000L
        ),
        DiarizationFile(
            name = "embedding.onnx",
            // 3D-Speaker eres2net base — 16 kHz, language-agnostic, ~28 MB.
            url = "https://huggingface.co/csukuangfj/speaker-embedding-models/" +
                "resolve/main/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx",
            estimatedSize = 28_000_000L
        )
    )

    private data class DiarizationFile(
        val name: String,
        val url: String,
        val estimatedSize: Long
    )

    /**
     * Pre-network estimate of the total diarization model size in bytes. Used
     * to render an immediate "~X MB" label in the download dialog before the
     * real HEAD-resolved size arrives via [diarizationTotalRemoteSize]. Sum
     * of the two file estimates declared in [diarizationFiles].
     */
    val diarizationEstimatedTotalSize: Long
        get() = diarizationFiles.sumOf { it.estimatedSize }

    /**
     * Issue HEAD requests for every diarization file and return the precise
     * total in bytes. Falls back to the declared estimate for any file whose
     * HEAD fails (offline, server gives no Content-Length, etc.) so this
     * never returns 0 unless the network is fully unreachable.
     *
     * Should be called off the main thread — it performs blocking I/O.
     */
    fun diarizationTotalRemoteSize(): Long {
        return diarizationFiles.sumOf { f ->
            headSize(f.url) ?: f.estimatedSize
        }
    }

    /** True iff both diarization model files are fully present on disk. */
    fun isDiarizationModelDownloaded(): Boolean {
        diarizationDir.mkdirs()
        return diarizationFiles.all { f ->
            val out = File(diarizationDir, f.name)
            out.exists() && out.length() > 0
        }
    }

    /**
     * Sum of bytes already on disk for the diarization model — completed
     * files plus any in-progress .partial files. Used by callers to seed a
     * "Resuming from X MB" UI before [downloadDiarizationModel] emits its
     * first progress event. Returns 0 if no partial state exists.
     */
    fun diarizationPartialBytes(): Long {
        if (!diarizationDir.exists()) return 0L
        return diarizationFiles.sumOf { f ->
            val out = File(diarizationDir, f.name)
            val partial = File(diarizationDir, "${f.name}.partial")
            when {
                out.exists() && out.length() > 0 -> out.length()
                partial.exists() && partial.length() > 0 -> partial.length()
                else -> 0L
            }
        }
    }

    /**
     * Download the diarization models (segmentation + embedding) sequentially
     * with Range-header resume per file. Mirrors the STT download pattern.
     */
    fun downloadDiarizationModel(): Flow<DownloadProgress> = flow {
        diarizationDir.mkdirs()

        if (isDiarizationModelDownloaded()) {
            Log.i(TAG, "Diarization models already downloaded")
            val totalSize = diarizationFiles.sumOf { File(diarizationDir, it.name).length() }
            emit(DownloadProgress(totalSize, totalSize, 100))
            return@flow
        }

        // Best-effort HEAD requests for real sizes; fall back to estimates.
        val actualSizes = mutableMapOf<String, Long>()
        diarizationFiles.forEach { f ->
            actualSizes[f.name] = headSize(f.url) ?: f.estimatedSize
        }
        val totalBytes = actualSizes.values.sum()
        Log.i(TAG, "Diarization total download size: ${totalBytes / 1_000_000}MB")

        // Seed the running total with bytes already on disk: completed files
        // PLUS any .partial bytes from a previous interrupted run. This way the
        // first emitted progress immediately reflects the resumed position
        // instead of jumping from 0 → 50 % the moment the first byte arrives.
        var totalBytesDownloaded = diarizationFiles.sumOf { f ->
            val out = File(diarizationDir, f.name)
            val partial = File(diarizationDir, "${f.name}.partial")
            when {
                out.exists() && out.length() > 0 -> out.length()
                partial.exists() && partial.length() > 0 -> partial.length()
                else -> 0L
            }
        }

        diarizationFiles.forEachIndexed { index, file ->
            val outputFile = File(diarizationDir, file.name)
            val partialFile = File(diarizationDir, "${file.name}.partial")

            if (outputFile.exists() && outputFile.length() > 0) {
                Log.i(TAG, "Diarization file ${file.name} already complete, skipping")
                return@forEachIndexed
            }

            val startByte = partialFile.takeIf { it.exists() }?.length() ?: 0L
            Log.i(TAG, "Downloading diarization file ${index + 1}/${diarizationFiles.size}: ${file.name}" +
                if (startByte > 0) " (resuming from ${startByte / 1_000_000}MB)" else "")
            // NOTE: totalBytesDownloaded already includes `startByte` from the
            // partial-file scan above — DO NOT add it again here.

            var connection: HttpURLConnection? = null
            try {
                connection = URL(file.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                if (startByte > 0) connection.setRequestProperty("Range", "bytes=$startByte-")
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode for ${file.name}")
                }
                val isResuming = startByte > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
                if (startByte > 0 && !isResuming) {
                    Log.w(TAG, "Server did not honour Range for ${file.name}, restarting this file")
                    totalBytesDownloaded -= startByte
                }

                connection.inputStream.use { input ->
                    FileOutputStream(partialFile, isResuming).use { output ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            totalBytesDownloaded += read
                            val pct = if (totalBytes > 0)
                                ((totalBytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                            else 0
                            if (pct % 2 == 0) {
                                emit(DownloadProgress(totalBytesDownloaded, totalBytes, pct))
                            }
                        }
                    }
                }

                // Promote .partial → final file
                if (!partialFile.renameTo(outputFile)) {
                    partialFile.copyTo(outputFile, overwrite = true)
                    partialFile.delete()
                }
            } catch (e: Exception) {
                connection?.disconnect()
                throw Exception("Diarization model download failed: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }

        if (!isDiarizationModelDownloaded()) {
            throw Exception("Diarization download incomplete — missing files in $diarizationDir")
        }

        emit(DownloadProgress(totalBytes, totalBytes, 100))
        Log.i(TAG, "Diarization model download completed")
    }.flowOn(Dispatchers.IO)

    /** Issue a HEAD request and return Content-Length, or null on failure. */
    private fun headSize(url: String): Long? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.connect()
            conn.contentLengthLong.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
