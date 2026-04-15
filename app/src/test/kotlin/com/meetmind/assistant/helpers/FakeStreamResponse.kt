// T028: Fake StreamResponse<T> for ClaudeInferenceClient unit tests
package com.meetmind.assistant.helpers

import com.anthropic.core.http.StreamResponse

/**
 * Test double for [StreamResponse]<T>.
 *
 * Wraps a [List]<T> and exposes it as a Java [java.util.stream.Stream] via [stream].
 * Used when testing [ClaudeInferenceClient] directly with pre-built event lists.
 *
 * For higher-level tests (e.g., [CloudInferenceEngineTest]), use [FakeCloudStreamingProvider]
 * instead — it doesn't require constructing [RawMessageStreamEvent] objects.
 */
class FakeStreamResponse<T>(private val items: List<T>) : StreamResponse<T> {
    override fun stream(): java.util.stream.Stream<T> = items.stream()
    override fun close() { /* no-op */ }
}
