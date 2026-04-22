package com.meetmind.assistant.data

import com.meetmind.assistant.data.model.DownloadProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * Contract tests for [ModelDownloadManager].
 *
 * C2.1 — download progress is monotonically increasing from 0 % to 100 %
 * C2.2 — resume: when a .partial file exists, the Range header is sent and
 *         already-downloaded bytes are skipped
 *
 * spec 009 — T044
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ModelDownloadManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var manager: ModelDownloadManager
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = Files.createTempDirectory("download_test").toFile()
        client = OkHttpClient()
        manager = ModelDownloadManager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            client = client
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    // ── C2.1: Monotonic progress 0 % → 100 % ─────────────────────────────────

    /**
     * C2.1 — Each emitted [DownloadProgress.bytesDownloaded] must be ≥ the previous value.
     * The final emission must have [DownloadProgress.isComplete] = true.
     */
    @Test
    fun `C2_1 download progress is monotonically increasing and completes`() = runTest {
        // 10 KB of fake GGUF data
        val fakeBody = ByteArray(10_240) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(fakeBody))
                .setHeader("Content-Length", fakeBody.size.toString())
                .setResponseCode(200)
        )

        val destFile = File(tempDir, "model.gguf")
        val progressList: List<DownloadProgress> = manager.downloadFile(
            url = server.url("/model.gguf").toString(),
            destFile = destFile
        ).toList()

        assertTrue("Should emit at least one progress update", progressList.isNotEmpty())

        // Verify monotonically increasing bytesDownloaded
        var prevBytes = -1L
        for (p in progressList) {
            assertTrue(
                "bytesDownloaded must be monotonically increasing: got ${p.bytesDownloaded} after $prevBytes",
                p.bytesDownloaded >= prevBytes
            )
            prevBytes = p.bytesDownloaded
        }

        // Final emission must be complete
        val last = progressList.last()
        assertTrue("Final progress must have isComplete=true", last.isComplete)
        assertEquals("Final bytesDownloaded must equal file size", fakeBody.size.toLong(), last.bytesDownloaded)
        assertTrue("Destination file must exist after download", destFile.exists())
        assertTrue("Partial file must be cleaned up after completion", !File("${destFile.absolutePath}.partial").exists())
    }

    // ── C2.2: Resume with Range header ───────────────────────────────────────

    /**
     * C2.2 — When a `.partial` file exists (simulating a previous partial download),
     * [ModelDownloadManager] must:
     *  1. Include `Range: bytes=N-` in the request header
     *  2. Start [DownloadProgress.bytesDownloaded] from N (already-downloaded bytes)
     *  3. Complete successfully (isComplete = true on final emission)
     */
    @Test
    fun `C2_2 resume sends Range header and skips already-downloaded bytes`() = runTest {
        val alreadyDownloaded = 4_096
        val remainingBytes = ByteArray(6_144) { (it + alreadyDownloaded).toByte() }

        // Server responds 206 Partial Content for the range request
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(remainingBytes))
                .setHeader("Content-Length", remainingBytes.size.toString())
                .setResponseCode(206)
        )

        val destFile = File(tempDir, "model_resume.gguf")
        // Create the partial file with first 4 KB already present
        val partialFile = File("${destFile.absolutePath}.partial")
        partialFile.writeBytes(ByteArray(alreadyDownloaded) { it.toByte() })

        val progressList: List<DownloadProgress> = manager.downloadFile(
            url = server.url("/model_resume.gguf").toString(),
            destFile = destFile
        ).toList()

        // Verify Range header was sent
        val recordedRequest = server.takeRequest()
        val rangeHeader = recordedRequest.getHeader("Range")
        assertEquals(
            "Range header must request bytes starting from already-downloaded offset",
            "bytes=$alreadyDownloaded-",
            rangeHeader
        )

        // Verify first progress starts at (or above) alreadyDownloaded
        val first = progressList.first()
        assertTrue(
            "First progress.bytesDownloaded must be >= $alreadyDownloaded (already have that), got ${first.bytesDownloaded}",
            first.bytesDownloaded >= alreadyDownloaded
        )

        // Verify completion
        val last = progressList.last()
        assertTrue("Final progress must have isComplete=true", last.isComplete)
        assertTrue("Destination file must exist after resume completion", destFile.exists())
    }
}
