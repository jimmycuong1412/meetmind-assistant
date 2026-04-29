package com.meetmind.assistant.feature.stt.datasource

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes the live PCM audio stream to a 16 kHz mono PCM-16 WAV file alongside
 * the STT pipeline. The output is intended for end-of-session speaker
 * diarization; sherpa-onnx's `OfflineSpeakerDiarization` reads WAV directly,
 * so no decoder is needed at diarization time.
 *
 * Lifecycle:
 *   1. [start] opens the file and writes a placeholder WAV header.
 *   2. [appendSamples] is called from the audio-read loop with each
 *      Float-normalized PCM chunk. Floats are converted back to PCM-16.
 *   3. [stop] flushes, closes the stream, and rewrites the header now that
 *      the final byte length is known. The header is patched in-place via
 *      [RandomAccessFile] so we don't have to rewrite the whole file.
 *
 * Failures during [appendSamples] are logged but never propagated — losing
 * the audio file should never break the STT pipeline. If [start] fails the
 * recorder is left in a disabled state and subsequent calls are no-ops; the
 * caller can detect this via [isActive].
 *
 * Thread-safety: instance methods must be called from a single thread (the
 * audio recording coroutine). Concurrent access is not supported.
 *
 * Format: 16 kHz, mono, 16-bit signed little-endian PCM. Matches the input
 * the STT pipeline expects, so we write the same bytes the AudioRecord
 * delivers without any resampling.
 */
class WavRecorder(
    private val sampleRateHz: Int = 16000
) {
    private companion object {
        private const val TAG = "WavRecorder"
        private const val NUM_CHANNELS: Short = 1
        private const val BITS_PER_SAMPLE: Short = 16
        private const val PCM_FORMAT: Short = 1 // WAVE_FORMAT_PCM
        private const val WAV_HEADER_SIZE = 44
        // Reusable scratch buffer to avoid allocating per chunk; auto-grows when needed.
        private const val INITIAL_SCRATCH_CAPACITY = 8192
    }

    private var stream: BufferedOutputStream? = null
    private var file: File? = null
    private var totalSamplesWritten: Long = 0
    private var disabled: Boolean = false
    private var scratch = ByteArray(INITIAL_SCRATCH_CAPACITY)

    val isActive: Boolean get() = stream != null && !disabled

    /**
     * Opens [outputPath] for writing and lays down a 44-byte placeholder WAV
     * header. The header is rewritten with correct sizes during [stop].
     *
     * Creates parent directories as needed. Returns false if the file cannot
     * be opened (permissions, disk full, etc.) — recorder transitions to a
     * disabled state and subsequent calls are no-ops.
     */
    fun start(outputPath: String): Boolean {
        if (stream != null) {
            Log.w(TAG, "start() called on an already-open recorder; ignoring")
            return false
        }
        return try {
            val target = File(outputPath)
            target.parentFile?.mkdirs()
            val out = BufferedOutputStream(FileOutputStream(target))
            // Reserve 44 bytes for the header; we'll patch sizes in stop().
            out.write(ByteArray(WAV_HEADER_SIZE))
            stream = out
            file = target
            totalSamplesWritten = 0
            disabled = false
            Log.i(TAG, "Started WAV recording: ${target.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WAV file at $outputPath", e)
            disabled = true
            stream = null
            file = null
            false
        }
    }

    /**
     * Append a chunk of PCM samples (Float, range [-1.0, 1.0]) to the file.
     * Converts to little-endian PCM-16. No-op when the recorder is not active.
     */
    fun appendSamples(samples: FloatArray) {
        val out = stream ?: return
        if (samples.isEmpty()) return

        val byteCount = samples.size * 2
        if (scratch.size < byteCount) {
            scratch = ByteArray(byteCount)
        }
        val buf = ByteBuffer.wrap(scratch, 0, byteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            // Clamp before scaling to avoid 32768 wrap-around when input is exactly 1.0.
            val clamped = when {
                s > 1.0f -> 1.0f
                s < -1.0f -> -1.0f
                else -> s
            }
            buf.putShort((clamped * 32767.0f).toInt().toShort())
        }
        try {
            out.write(scratch, 0, byteCount)
            totalSamplesWritten += samples.size
        } catch (e: Exception) {
            // Don't break STT if the disk write fails — disable recorder and move on.
            Log.e(TAG, "WAV append failed; disabling recorder", e)
            disabled = true
            try {
                out.close()
            } catch (_: Exception) { /* ignore */ }
            stream = null
        }
    }

    /**
     * Closes the file and patches the WAV header with the final sizes. Returns
     * the absolute path of the written file, or null if the recorder was never
     * started, was disabled, or produced zero samples (in which case the file,
     * if any, is deleted to keep the audio directory clean).
     */
    fun stop(): String? {
        val out = stream
        val target = file
        stream = null
        file = null
        if (out == null || target == null) {
            return null
        }
        try {
            out.flush()
            out.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close WAV stream", e)
        }
        if (totalSamplesWritten == 0L || disabled) {
            // Don't keep an empty / partially-written file around.
            target.delete()
            return null
        }
        return try {
            patchHeader(target, totalSamplesWritten)
            Log.i(TAG, "Stopped WAV recording: ${target.absolutePath} (${totalSamplesWritten} samples)")
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch WAV header; deleting partial file", e)
            target.delete()
            null
        }
    }

    /**
     * Rewrites the 44-byte WAV header in place. We do this at the end so we
     * don't have to know the final byte count up-front.
     *
     * Layout (little-endian):
     *   00..03  "RIFF"
     *   04..07  chunk size (file size - 8)
     *   08..11  "WAVE"
     *   12..15  "fmt "
     *   16..19  subchunk1 size (16 for PCM)
     *   20..21  audio format (1 = PCM)
     *   22..23  num channels
     *   24..27  sample rate
     *   28..31  byte rate (= sampleRate * channels * bitsPerSample/8)
     *   32..33  block align (= channels * bitsPerSample/8)
     *   34..35  bits per sample
     *   36..39  "data"
     *   40..43  data chunk size (= totalSamples * channels * bitsPerSample/8)
     */
    private fun patchHeader(target: File, sampleCount: Long) {
        val byteRate = sampleRateHz * NUM_CHANNELS.toInt() * (BITS_PER_SAMPLE.toInt() / 8)
        val blockAlign = (NUM_CHANNELS.toInt() * (BITS_PER_SAMPLE.toInt() / 8)).toShort()
        val dataChunkSize = (sampleCount * NUM_CHANNELS.toInt() * (BITS_PER_SAMPLE.toInt() / 8)).toInt()
        val riffChunkSize = 36 + dataChunkSize

        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(riffChunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // subchunk1 size
        header.putShort(PCM_FORMAT)
        header.putShort(NUM_CHANNELS)
        header.putInt(sampleRateHz)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(BITS_PER_SAMPLE)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataChunkSize)

        RandomAccessFile(target, "rw").use { raf ->
            raf.seek(0)
            raf.write(header.array())
        }
    }
}
