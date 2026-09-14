package com.meetmind.assistant.domain.usecase.sync

import com.meetmind.assistant.domain.model.SpeakerChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InterviewTurnDetector] — the state machine that decides *when*
 * Interview Mode should ask the LLM for help.
 *
 * The detector replaces a fixed 30 s poll with question-onset firing. Pure Kotlin,
 * no coroutines, so timing is driven by an injected clock value rather than real
 * delays — that keeps these tests instant and deterministic.
 *
 * The behaviours worth protecting, in priority order:
 *  1. **Fire once per question**, not once per clause (multi-part questions are the
 *     norm in senior interviews).
 *  2. **Never fire on the candidate's own speech** — the failure that makes the
 *     assistant answer its own user.
 *  3. **Cancel in-flight generation when the candidate starts talking** — late help
 *     is a distraction, not help.
 */
class InterviewTurnDetectorTest {

    private val detector = InterviewTurnDetector()

    /** Fallback heartbeat interval used by most tests (30 s, the shipped default). */
    private val heartbeatMs = 30_000L

    private fun interviewer(text: String, atMs: Long) = detector.onSegment(
        text = text,
        channel = SpeakerChannel.INTERVIEWER,
        nowMs = atMs,
        heartbeatIntervalMs = heartbeatMs
    )

    private fun candidate(text: String, atMs: Long) = detector.onSegment(
        text = text,
        channel = SpeakerChannel.CANDIDATE,
        nowMs = atMs,
        heartbeatIntervalMs = heartbeatMs
    )

    // ─── Basic question onset ───────────────────────────────────────────────────

    @Test
    fun `single question does not fire before the debounce elapses`() {
        interviewer("How do you handle Terraform state locking?", atMs = 1_000L)

        // Still inside the 900 ms turn-boundary window — the interviewer may continue.
        val early = detector.poll(nowMs = 1_500L, heartbeatIntervalMs = heartbeatMs)

        assertNull("must not fire mid-turn", early)
    }

    @Test
    fun `single question fires once the debounce elapses`() {
        interviewer("How do you handle Terraform state locking?", atMs = 1_000L)

        val fired = detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)

        assertTrue("expected a question trigger", fired is InterviewTrigger.Question)
        assertEquals(
            "How do you handle Terraform state locking?",
            (fired as InterviewTrigger.Question).text
        )
    }

    @Test
    fun `a fired question does not fire again`() {
        interviewer("How do you handle secrets?", atMs = 1_000L)
        detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)

        val second = detector.poll(nowMs = 3_000L, heartbeatIntervalMs = heartbeatMs)

        assertNull("each question must trigger exactly once", second)
    }

    // ─── Multi-part questions — the main reason the debounce exists ─────────────

    @Test
    fun `multi-part question fires once with both clauses`() {
        // Interviewers routinely append a second clause after a short pause. Firing on
        // the first clause wastes the inference and shows the candidate half an answer.
        interviewer("How do you handle secrets?", atMs = 1_000L)
        interviewer("And what about rotation?", atMs = 1_400L) // 400 ms gap — same turn

        // The debounce restarts from the latest clause, so nothing fires at 2_000.
        assertNull(
            "second clause must restart the debounce",
            detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
        )

        val fired = detector.poll(nowMs = 2_400L, heartbeatIntervalMs = heartbeatMs)

        assertTrue(fired is InterviewTrigger.Question)
        val text = (fired as InterviewTrigger.Question).text
        assertTrue("both clauses must reach the LLM, got: $text", "secrets" in text)
        assertTrue("both clauses must reach the LLM, got: $text", "rotation" in text)
    }

    @Test
    fun `non-question interviewer speech preceding a question is carried along`() {
        // Lead-in context matters: "We run about 200 services. How would you scale that?"
        // The pronoun in the question is meaningless without the preceding sentence.
        interviewer("We run about 200 services in production.", atMs = 1_000L)
        interviewer("How would you scale that?", atMs = 1_300L)

        val fired = detector.poll(nowMs = 2_300L, heartbeatIntervalMs = heartbeatMs)

        assertTrue(fired is InterviewTrigger.Question)
        assertTrue("200 services" in (fired as InterviewTrigger.Question).text)
    }

    // ─── Candidate speech must never trigger ────────────────────────────────────

    @Test
    fun `candidate speech never fires a question trigger`() {
        candidate("So we use an S3 backend with a DynamoDB lock table.", atMs = 1_000L)

        assertNull(detector.poll(nowMs = 5_000L, heartbeatIntervalMs = heartbeatMs))
    }

    @Test
    fun `candidate asking a question never fires`() {
        // Candidates ask clarifiers too. Answering the candidate's own question is the
        // single most damaging false positive in this mode.
        candidate("Do you mean at the cluster level?", atMs = 1_000L)

        assertNull(detector.poll(nowMs = 5_000L, heartbeatIntervalMs = heartbeatMs))
    }

    @Test
    fun `interviewer statement without a question does not fire`() {
        interviewer("Great, thanks for walking me through that.", atMs = 1_000L)

        assertNull(detector.poll(nowMs = 5_000L, heartbeatIntervalMs = heartbeatMs))
    }

    // ─── Imperative probes (no question mark) ───────────────────────────────────

    @Test
    fun `imperative probes fire even without a question mark`() {
        val probes = listOf(
            "Walk me through a production incident you owned.",
            "Tell me about a time you rolled back a deploy.",
            "Describe your approach to secret rotation."
        )

        probes.forEachIndexed { i, probe ->
            val d = InterviewTurnDetector()
            d.onSegment(probe, SpeakerChannel.INTERVIEWER, nowMs = 1_000L, heartbeatIntervalMs = heartbeatMs)
            val fired = d.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
            assertTrue("probe #$i should fire: $probe", fired is InterviewTrigger.Question)
        }
    }

    // ─── Barge-in ───────────────────────────────────────────────────────────────

    @Test
    fun `candidate speaking during generation emits a cancel`() {
        interviewer("How do you handle state locking?", atMs = 1_000L)
        detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
        detector.onGenerationStarted()

        // The candidate has started answering — help arriving now is a distraction.
        val trigger = candidate("So we use S3 with a lock table…", atMs = 2_500L)

        assertTrue("expected a cancel trigger", trigger is InterviewTrigger.Cancel)
    }

    @Test
    fun `candidate speech emits no cancel when nothing is generating`() {
        val trigger = candidate("Just thinking out loud here.", atMs = 1_000L)

        assertNull("nothing in flight — nothing to cancel", trigger)
    }

    @Test
    fun `cancel is emitted only once per generation`() {
        interviewer("How do you handle state locking?", atMs = 1_000L)
        detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
        detector.onGenerationStarted()

        candidate("So we use S3…", atMs = 2_500L)
        val second = candidate("…with a DynamoDB lock table.", atMs = 3_000L)

        assertNull("a cancelled generation must not cancel repeatedly", second)
    }

    // ─── Fallback heartbeat ─────────────────────────────────────────────────────

    @Test
    fun `heartbeat fires when no question arrives for a full interval`() {
        // Keeps interviewIntervalSeconds meaningful: if the detector misses a question
        // (unusual phrasing, missed attribution), the user still gets periodic coaching
        // rather than silence for the whole interview.
        candidate("…and that's roughly how we structured the pipeline.", atMs = 1_000L)

        val fired = detector.poll(nowMs = 1_000L + heartbeatMs, heartbeatIntervalMs = heartbeatMs)

        assertTrue("expected a heartbeat trigger", fired is InterviewTrigger.Heartbeat)
    }

    @Test
    fun `a question resets the heartbeat timer`() {
        interviewer("How do you handle rollbacks?", atMs = 1_000L)
        val q = detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
        assertTrue(q is InterviewTrigger.Question)

        // Only ~28 s since the question fired — not yet a full heartbeat interval.
        val tooEarly = detector.poll(nowMs = 30_000L, heartbeatIntervalMs = heartbeatMs)

        assertNull("firing a question must reset the heartbeat window", tooEarly)
    }

    @Test
    fun `heartbeat does not fire while generation is in flight`() {
        detector.onGenerationStarted()

        val fired = detector.poll(nowMs = heartbeatMs * 2, heartbeatIntervalMs = heartbeatMs)

        assertNull("must not queue inference on top of in-flight inference", fired)
    }

    // ─── Guards ─────────────────────────────────────────────────────────────────

    @Test
    fun `blank interviewer segment is ignored`() {
        interviewer("   ", atMs = 1_000L)

        assertNull(detector.poll(nowMs = 5_000L, heartbeatIntervalMs = heartbeatMs))
    }

    @Test
    fun `unknown-channel question does not fire`() {
        // In single-mic mode the heuristic reports UNKNOWN when it is not confident.
        // Firing on that would answer the candidate's own words half the time.
        detector.onSegment(
            text = "How do you handle state locking?",
            channel = SpeakerChannel.UNKNOWN,
            nowMs = 1_000L,
            heartbeatIntervalMs = heartbeatMs
        )

        assertNull(detector.poll(nowMs = 5_000L, heartbeatIntervalMs = heartbeatMs))
    }

    @Test
    fun `generation finishing allows the next question to fire`() {
        interviewer("First question?", atMs = 1_000L)
        detector.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)
        detector.onGenerationStarted()
        detector.onGenerationFinished()

        interviewer("Second question?", atMs = 10_000L)
        val fired = detector.poll(nowMs = 11_000L, heartbeatIntervalMs = heartbeatMs)

        assertTrue("a new question after generation ends must fire", fired is InterviewTrigger.Question)
    }

    // ─── Single-mic mode (no channel separation yet) ────────────────────────────

    @Test
    fun `unattributed question fires when single-mic fallback is enabled`() {
        // Until the capture producer lands (plan Task 3/3b), every segment arrives as
        // UNKNOWN. Refusing all of them would make Interview Mode heartbeat-only —
        // strictly worse than the 30 s poll it replaces. So the detector accepts
        // UNKNOWN *questions* when explicitly told no channel separation is available,
        // trading some false positives for a mode that actually works.
        val d = InterviewTurnDetector(allowUnattributedQuestions = true)
        d.onSegment(
            text = "Walk me through how you'd design a multi-region failover.",
            channel = SpeakerChannel.UNKNOWN,
            nowMs = 1_000L,
            heartbeatIntervalMs = heartbeatMs
        )

        val fired = d.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs)

        assertTrue(fired is InterviewTrigger.Question)
    }

    @Test
    fun `unattributed non-question still does not fire in single-mic mode`() {
        // The question test is the only signal left once attribution is unavailable,
        // so it has to carry the whole decision.
        val d = InterviewTurnDetector(allowUnattributedQuestions = true)
        d.onSegment(
            text = "So that is roughly how we handled the migration.",
            channel = SpeakerChannel.UNKNOWN,
            nowMs = 1_000L,
            heartbeatIntervalMs = heartbeatMs
        )

        assertNull(d.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs))
    }

    @Test
    fun `channel separation still wins when available`() {
        // With a real producer, a CANDIDATE question must never fire even in the
        // permissive mode — known attribution always beats the linguistic guess.
        val d = InterviewTurnDetector(allowUnattributedQuestions = true)
        d.onSegment(
            text = "Do you mean at the cluster level?",
            channel = SpeakerChannel.CANDIDATE,
            nowMs = 1_000L,
            heartbeatIntervalMs = heartbeatMs
        )

        assertNull(d.poll(nowMs = 2_000L, heartbeatIntervalMs = heartbeatMs))
    }
}
