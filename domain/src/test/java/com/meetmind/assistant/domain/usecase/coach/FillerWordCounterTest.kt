package com.meetmind.assistant.domain.usecase.coach

import com.meetmind.assistant.domain.model.FillerWordStats
import org.junit.Assert.assertEquals
import org.junit.Test

class FillerWordCounterTest {

    @Test
    fun `counts hesitation sounds with punctuation attached`() {
        assertEquals(3, FillerWordCounter.count("Um, I think, uh, we shipped it. Hmm."))
    }

    @Test
    fun `counts phrases once and only on whole words`() {
        assertEquals(2, FillerWordCounter.count("You know, it was, I mean, fine"))
        assertEquals(0, FillerWordCounter.count("You knowledge of Kotlin is good, I meant it"))
    }

    @Test
    fun `ignores like and so used as ordinary words`() {
        assertEquals(0, FillerWordCounter.count("I like Kotlin so that I can ship faster"))
        assertEquals(0, FillerWordCounter.count("It is likely, not unlike, the old one"))
    }

    @Test
    fun `counts like set off by commas or dashes`() {
        assertEquals(2, FillerWordCounter.count("It was, like, really hard — like — every day"))
    }

    @Test
    fun `counts basically and literally`() {
        assertEquals(2, FillerWordCounter.count("Basically we literally rewrote it"))
    }

    @Test
    fun `counts Vietnamese hesitation sounds`() {
        assertEquals(2, FillerWordCounter.count("Ờ, em nghĩ là, ừm, dự án đó"))
    }

    @Test
    fun `blank text has no fillers`() {
        assertEquals(0, FillerWordCounter.count("   "))
    }

    @Test
    fun `rate uses at least one minute and levels follow thresholds`() {
        assertEquals(4f, FillerWordStats.of(4, recordingDurationMs = 10_000).ratePerMinute)
        assertEquals(2f, FillerWordStats.of(8, recordingDurationMs = 240_000).ratePerMinute)
        assertEquals(FillerWordStats.Level.CALM, FillerWordStats.of(2, 60_000).level)
        assertEquals(FillerWordStats.Level.ELEVATED, FillerWordStats.of(3, 60_000).level)
        assertEquals(FillerWordStats.Level.HIGH, FillerWordStats.of(6, 60_000).level)
    }
}
