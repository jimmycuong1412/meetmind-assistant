package com.meetmind.assistant.data.repository

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.meetmind.assistant.domain.model.DiarizationResult
import com.meetmind.assistant.domain.model.SpeakerSpan
import com.meetmind.assistant.domain.repository.DiarizationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * On-device speaker diarization backed by sherpa-onnx's
 * [OfflineSpeakerDiarization] (Pyannote segmentation + 3D-Speaker embedding +
 * agglomerative clustering).
 *
 * Model files are expected under [Context.getFilesDir]/[MODEL_DIR_NAME]:
 *   - [SEGMENTATION_MODEL_FILE] — Pyannote segmentation ONNX
 *     (e.g. `sherpa-onnx-pyannote-segmentation-3-0/model.onnx`)
 *   - [EMBEDDING_MODEL_FILE] — 3D-Speaker embedding ONNX
 *     (e.g. `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx`)
 *
 * Until the in-app download UX ships, the user side-loads via
 * `adb push <local>.onnx /data/data/com.meetmind.assistant/files/models/diarization/<name>.onnx`.
 *
 * Native processing is single-threaded; we serialize on a single IO
 * dispatcher slot per call. Cooperative cancellation isn't supported by the
 * underlying JNI yet — once started, a run completes (or crashes).
 */
@Singleton
class DiarizationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DiarizationRepository {

    private companion object {
        private const val TAG = "DiarizationRepo"

        /** Subdirectory of `filesDir` where the user (or future downloader) places models. */
        private const val MODEL_DIR_NAME = "models/diarization"

        /** Fixed filenames under [MODEL_DIR_NAME]. Renamed to these by the download flow. */
        private const val SEGMENTATION_MODEL_FILE = "segmentation.onnx"
        private const val EMBEDDING_MODEL_FILE = "embedding.onnx"

        /** Number of CPU threads for both segmentation and embedding inference. */
        private const val NUM_THREADS = 2

        /** Required input format for both models. */
        private const val EXPECTED_SAMPLE_RATE = 16_000
    }

    private val modelDir: File get() = File(context.filesDir, MODEL_DIR_NAME)
    private val segmentationModel: File get() = File(modelDir, SEGMENTATION_MODEL_FILE)
    private val embeddingModel: File get() = File(modelDir, EMBEDDING_MODEL_FILE)

    override fun isModelAvailable(): Boolean {
        val ok = segmentationModel.isFile && segmentationModel.length() > 0 &&
                 embeddingModel.isFile && embeddingModel.length() > 0
        if (!ok) {
            Log.d(
                TAG,
                "Diarization models missing — looked for $segmentationModel " +
                    "(${if (segmentationModel.exists()) segmentationModel.length() else "absent"}) and " +
                    "$embeddingModel (${if (embeddingModel.exists()) embeddingModel.length() else "absent"})"
            )
        }
        return ok
    }

    override suspend fun diarize(audioFilePath: String): Result<DiarizationResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isModelAvailable()) {
                    "Diarization models are not installed at $modelDir. Place " +
                        "$SEGMENTATION_MODEL_FILE and $EMBEDDING_MODEL_FILE there to enable."
                }
                val wav = File(audioFilePath)
                require(wav.isFile) { "Audio file not found: $audioFilePath" }

                val (samples, sampleRate) = readMonoFloatPcm(wav)
                require(sampleRate == EXPECTED_SAMPLE_RATE) {
                    "Diarization expects $EXPECTED_SAMPLE_RATE Hz audio; got $sampleRate Hz"
                }

                Log.i(TAG, "Diarizing ${samples.size} samples (${samples.size * 1000L / EXPECTED_SAMPLE_RATE} ms)")

                val config = OfflineSpeakerDiarizationConfig(
                    segmentation = OfflineSpeakerSegmentationModelConfig(
                        pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                            model = segmentationModel.absolutePath
                        ),
                        numThreads = NUM_THREADS,
                        provider = "cpu",
                        debug = false,
                    ),
                    embedding = SpeakerEmbeddingExtractorConfig(
                        model = embeddingModel.absolutePath,
                        numThreads = NUM_THREADS,
                        provider = "cpu",
                        debug = false,
                    ),
                    // numClusters = -1 lets the model auto-detect the number of speakers
                    // using the threshold. 0.5 is sherpa-onnx's recommended default for
                    // 3D-Speaker embeddings; lower = more clusters / more sensitive splits.
                    clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
                    minDurationOn = 0.2f,
                    minDurationOff = 0.5f,
                )

                val diarizer = OfflineSpeakerDiarization(assetManager = null, config = config)
                val rawSegments = try {
                    diarizer.process(samples)
                } finally {
                    diarizer.release()
                }

                val spans = rawSegments.map { seg ->
                    SpeakerSpan(
                        startMs = (seg.start * 1000.0).roundToLong(),
                        endMs = (seg.end * 1000.0).roundToLong(),
                        cluster = seg.speaker,
                    )
                }
                val clusterCount = if (spans.isEmpty()) 0
                                   else spans.maxOf { it.cluster } + 1

                Log.i(TAG, "Diarization done: ${spans.size} spans, $clusterCount clusters")
                DiarizationResult(spans = spans, clusterCount = clusterCount)
            }
        }

    /**
     * Read a 16-bit PCM mono WAV file and return its samples as float32 in
     * [-1.0, 1.0] alongside the file's sample rate.
     *
     * Tailored to the format our [com.meetmind.assistant.feature.stt] pipeline
     * writes (16 kHz, mono, 16-bit PCM, little-endian). Tolerates additional
     * RIFF chunks (LIST/INFO etc.) by scanning for the `data` chunk.
     */
    private fun readMonoFloatPcm(wav: File): Pair<FloatArray, Int> {
        RandomAccessFile(wav, "r").use { raf ->
            val header = ByteArray(12)
            require(raf.read(header) == 12) { "WAV too small" }
            require(header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF") { "Not a RIFF file" }
            require(header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WAVE") { "Not a WAVE file" }

            var sampleRate = -1
            var numChannels = -1
            var bitsPerSample = -1
            var dataOffset = -1L
            var dataSize = -1L

            // Walk the chunks: each is `id (4)` + `size (4 LE)` + `payload (size)`.
            val chunkHeader = ByteArray(8)
            while (raf.filePointer < raf.length()) {
                if (raf.read(chunkHeader) != 8) break
                val id = chunkHeader.copyOfRange(0, 4).toString(Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size.toInt())
                        raf.readFully(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        val audioFormat = bb.short.toInt() and 0xFFFF
                        numChannels = bb.short.toInt() and 0xFFFF
                        sampleRate = bb.int
                        bb.int  // byteRate
                        bb.short  // blockAlign
                        bitsPerSample = bb.short.toInt() and 0xFFFF
                        require(audioFormat == 1) { "Only PCM WAV supported (got format=$audioFormat)" }
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                        // Skip past the data so we can keep walking in case extra chunks follow.
                        raf.seek(raf.filePointer + size)
                    }
                    else -> raf.seek(raf.filePointer + size)
                }
                // Pad byte for odd-sized chunks
                if (size % 2 == 1L) raf.seek(raf.filePointer + 1)
            }

            require(sampleRate > 0 && numChannels > 0 && bitsPerSample > 0) { "Invalid WAV header" }
            require(dataOffset >= 0 && dataSize > 0) { "WAV has no data chunk" }
            require(numChannels == 1) { "Diarization expects mono audio (got $numChannels channels)" }
            require(bitsPerSample == 16) { "Diarization expects 16-bit PCM (got $bitsPerSample bits)" }

            // Read PCM data and convert to float32 in [-1, 1]
            val sampleCount = (dataSize / 2).toInt()
            val pcmBytes = ByteArray(max(0, dataSize.toInt()))
            raf.seek(dataOffset)
            raf.readFully(pcmBytes)
            val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                out[i] = bb.short / 32768f
            }
            return out to sampleRate
        }
    }
}
