// T029-T030: Network audit tests — Contract 5 (no audio, data minimisation)
// Pure JUnit4 + MockWebServer — no Robolectric needed
package com.meetmind.assistant.inference

import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.SessionMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Verifies that [ClaudeInferenceClient] transmits ONLY the structured JSON payload
 * (question text + system prompt) — no audio bytes, no user identity, no binary blobs.
 *
 * Uses MockWebServer to intercept the outgoing HTTP request before it reaches the network.
 * A canned SSE response (empty stream) is returned so the client doesn't hang.
 */
class NetworkAuditTest {

    private lateinit var mockWebServer: MockWebServer

    /** Minimal SSE response that Anthropic SDK can parse: message_start → message_stop */
    private val cannedSseResponse = """
        event: message_start
        data: {"type":"message_start","message":{"id":"msg_test","type":"message","role":"assistant","content":[],"model":"claude-haiku-4-5-20251001","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":0}}}

        event: message_delta
        data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}

        event: message_stop
        data: {"type":"message_stop"}

    """.trimIndent()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun makeClient(baseUrl: String) =
        ClaudeInferenceClient(apiKey = "sk-ant-mock-test-key", baseUrl = baseUrl)

    private fun makeRequest(
        questionText: String = "What is memoization?",
        systemPrompt: String = "You are a helpful assistant."
    ) = CloudInferenceRequest(
        requestId = UUID.randomUUID().toString(),
        questionText = questionText,
        systemPrompt = systemPrompt,
        provider = CloudProvider.CLAUDE,
        sessionMode = SessionMode.INTERVIEW
    )

    private fun enqueueSseResponse() {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(cannedSseResponse)
                .setResponseCode(200)
        )
    }

    /** Contract 5.1: Request body contains no audio bytes or binary blobs */
    @Test
    fun noAudioBytes_inClaudeRequest() = runTest {
        enqueueSseResponse()
        val client = makeClient(mockWebServer.url("/").toString())

        // Collect and ignore errors (SDK may reject our mock response; we only care about the request)
        client.streamSuggestion(makeRequest())
            .catch { /* ignore parse errors from mock server */ }
            .toList()

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        // Must not contain audio MIME types or obvious audio markers
        assertFalse("Body contains audio/ MIME type", body.contains("audio/"))
        assertFalse("Body contains .wav reference", body.contains(".wav"))
        assertFalse("Body contains .m4a reference", body.contains(".m4a"))
        assertFalse("Body contains .mp3 reference", body.contains(".mp3"))

        // Body must look like JSON (starts with '{')
        val trimmed = body.trim()
        assertTrue("Body should be a JSON object, got: ${trimmed.take(80)}", trimmed.startsWith("{"))
    }

    /** Contract 5.2: Question text is truncated to ≤ 600 chars before transmission */
    @Test
    fun questionText_isTruncated_to600Chars() = runTest {
        enqueueSseResponse()
        val client = makeClient(mockWebServer.url("/").toString())
        val longQuestion = "Q".repeat(800)

        client.streamSuggestion(makeRequest(questionText = longQuestion))
            .catch { /* ignore parse errors from mock server */ }
            .toList()

        val body = mockWebServer.takeRequest().body.readUtf8()
        // The "content" array in the Anthropic request wraps the user message
        // We assert the body doesn't contain the full 800-char string
        assertFalse(
            "Full 800-char question must not appear in request body",
            body.contains("Q".repeat(801))
        )
        // And the truncated (600-char) version should not be exceeded
        val qIndex = body.indexOf("QQQQ") // start of the padded string
        if (qIndex >= 0) {
            val qRun = body.substring(qIndex).takeWhile { it == 'Q' }
            assertTrue(
                "Question run in body (${qRun.length} chars) must be ≤ 600",
                qRun.length <= 600
            )
        }
    }

    /** Contract 5.3: System prompt is truncated to ≤ 320 chars before transmission */
    @Test
    fun systemPrompt_isTruncated_to320Chars() = runTest {
        enqueueSseResponse()
        val client = makeClient(mockWebServer.url("/").toString())
        val longPrompt = "S".repeat(400)

        client.streamSuggestion(makeRequest(systemPrompt = longPrompt))
            .catch { /* ignore parse errors from mock server */ }
            .toList()

        val body = mockWebServer.takeRequest().body.readUtf8()

        // Full 400-char prompt must not appear
        assertFalse(
            "Full 400-char system prompt must not appear in request body",
            body.contains("S".repeat(321))
        )
    }

    /** Contract 5.4: Request body does not contain user identity fields */
    @Test
    fun noUserIdentity_inRequest() = runTest {
        enqueueSseResponse()
        val client = makeClient(mockWebServer.url("/").toString())

        client.streamSuggestion(makeRequest())
            .catch { /* ignore parse errors from mock server */ }
            .toList()

        val body = mockWebServer.takeRequest().body.readUtf8().lowercase()

        assertFalse("Body must not contain 'deviceid' field", body.contains("\"deviceid\""))
        assertFalse("Body must not contain 'userid' field", body.contains("\"userid\""))
        assertFalse("Body must not contain 'email' field", body.contains("\"email\""))
        assertFalse("Body must not contain 'phone' field", body.contains("\"phone\""))
    }
}
