package com.meetmind.assistant.domain.usecase.transcription

import com.meetmind.assistant.domain.model.SpeakerChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpeakerAttributionHeuristic].
 *
 * This heuristic is the **single-mic fallback** for Interview Mode: it runs only
 * when true channel separation is unavailable (playback-capture consent denied, the
 * conferencing app opted out of capture, or an in-person interview). Pure Kotlin,
 * no Android deps — runs on plain JVM.
 *
 * The governing design principle (see the design doc §3.1.1) is that **a confident
 * wrong label is worse than an honest unknown**, because the LLM prompt is built
 * from these labels. So the tests below assert as much about what the heuristic
 * *refuses* to claim as about what it detects.
 */
class SpeakerAttributionHeuristicTest {

    private val heuristic = SpeakerAttributionHeuristic()

    /** A pause long enough to read as a turn change. */
    private val turnGapMs = 2_000L

    /** Continuation of the same speaker's turn. */
    private val noGapMs = 200L

    // ─── Interviewer detection ──────────────────────────────────────────────────

    @Test
    fun `interrogative after a pause is attributed to the interviewer`() {
        val result = heuristic.attribute(
            text = "How do you handle Terraform state locking across teams?",
            silenceBeforeMs = turnGapMs,
            previous = null
        )

        assertEquals(SpeakerChannel.INTERVIEWER, result.channel)
        assertTrue(
            "expected high confidence, was ${result.confidence}",
            result.confidence >= 0.8f
        )
    }

    @Test
    fun `imperative probe without a question mark is still an interviewer turn`() {
        // Senior interviews lean heavily on these; they carry no '?' at all.
        val probes = listOf(
            "Walk me through a production incident you owned.",
            "Tell me about a time you had to roll back a deploy.",
            "Describe your approach to secret rotation.",
            "Explain how you'd design a multi-region failover."
        )

        probes.forEach { probe ->
            val result = heuristic.attribute(probe, turnGapMs, previous = null)
            assertEquals(
                "failed to attribute imperative probe: $probe",
                SpeakerChannel.INTERVIEWER,
                result.channel
            )
        }
    }

    // ─── Candidate detection ────────────────────────────────────────────────────

    @Test
    fun `long answer following an interviewer turn is attributed to the candidate`() {
        val result = heuristic.attribute(
            text = "So we use an S3 backend with a DynamoDB lock table, and when we hit " +
                "contention during the multi-region migration the state actually got " +
                "corrupted, which is why we moved everything to Atlantis for serialized applies.",
            silenceBeforeMs = noGapMs,
            previous = SpeakerChannel.INTERVIEWER
        )

        assertEquals(SpeakerChannel.CANDIDATE, result.channel)
        assertTrue(
            "expected high confidence, was ${result.confidence}",
            result.confidence >= 0.8f
        )
    }

    @Test
    fun `candidate keeps the turn across a short pause mid-answer`() {
        // Thinking pauses inside an answer must not flip attribution.
        val result = heuristic.attribute(
            text = "and then we cut the deploy time from forty-five minutes down to eight " +
                "by fixing the layer caching in the build stage",
            silenceBeforeMs = noGapMs,
            previous = SpeakerChannel.CANDIDATE
        )

        assertEquals(SpeakerChannel.CANDIDATE, result.channel)
    }

    // ─── Honest uncertainty ─────────────────────────────────────────────────────

    @Test
    fun `short acknowledgement is left unknown rather than guessed`() {
        // "Right." could be either party. Guessing here poisons the prompt.
        val result = heuristic.attribute("Right.", noGapMs, previous = SpeakerChannel.CANDIDATE)

        assertEquals(SpeakerChannel.UNKNOWN, result.channel)
        assertTrue(
            "ambiguous input must fall below the confidence floor, was ${result.confidence}",
            result.confidence < SpeakerAttributionHeuristic.CONFIDENCE_FLOOR
        )
    }

    @Test
    fun `candidate asking a clarifying question is not confidently the interviewer`() {
        // A real failure mode: candidates ask questions too ("Do you mean at the
        // cluster level?"). Mid-turn, with no pause, this must not read as a
        // confident interviewer turn.
        val result = heuristic.attribute(
            text = "Do you mean at the cluster level?",
            silenceBeforeMs = noGapMs,
            previous = SpeakerChannel.CANDIDATE
        )

        assertTrue(
            "must not confidently claim INTERVIEWER for a candidate's clarifier",
            result.channel != SpeakerChannel.INTERVIEWER ||
                result.confidence < SpeakerAttributionHeuristic.CONFIDENCE_FLOOR
        )
    }

    @Test
    fun `blank text is unknown with zero confidence`() {
        val result = heuristic.attribute("   ", turnGapMs, previous = null)

        assertEquals(SpeakerChannel.UNKNOWN, result.channel)
        assertEquals(0f, result.confidence, 0.001f)
    }

    // ─── Contract guards ────────────────────────────────────────────────────────

    @Test
    fun `confidence floor is exported and within a sane range`() {
        // Call sites must reference the constant, never a magic number.
        assertTrue(
            SpeakerAttributionHeuristic.CONFIDENCE_FLOOR > 0f &&
                SpeakerAttributionHeuristic.CONFIDENCE_FLOOR < 1f
        )
    }

    @Test
    fun `confidence is always a valid probability`() {
        val samples = listOf(
            "How would you scale this?" to 3_000L,
            "Uh." to 100L,
            "We ran a canary deploy behind a feature flag and watched the SLO burn rate." to 500L,
            "" to 0L
        )

        samples.forEach { (text, gap) ->
            val c = heuristic.attribute(text, gap, previous = null).confidence
            assertTrue("confidence out of range for '$text': $c", c in 0f..1f)
        }
    }
}
