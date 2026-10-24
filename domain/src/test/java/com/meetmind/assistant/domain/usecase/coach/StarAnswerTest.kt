package com.meetmind.assistant.domain.usecase.coach

import com.meetmind.assistant.domain.usecase.coach.StarAnswer.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StarAnswerTest {

    private fun steps(answer: StarAnswer?) = answer?.parts?.map { it.step }

    @Test
    fun `four plain bullets read as S T A R`() {
        val star = StarAnswer.from(listOf("Payments outage", "Own the rollback", "Feature-flag kill switch", "MTTR 40 to 8 min"))

        assertEquals(listOf(Step.SITUATION, Step.TASK, Step.ACTION, Step.RESULT), steps(star))
        assertEquals("Own the rollback", star!!.parts[1].text)
    }

    @Test
    fun `three plain bullets read as S A R from the older prompt`() {
        val star = StarAnswer.from(listOf("Legacy monolith", "Strangler migration", "Deploys 2x faster"))

        assertEquals(listOf(Step.SITUATION, Step.ACTION, Step.RESULT), steps(star))
    }

    @Test
    fun `labelled bullets use their labels and lose the prefix`() {
        val star = StarAnswer.from(listOf("Situation: team split on API design", "**Action** – ran an RFC", "R: shipped v2 on time"))

        assertEquals(listOf(Step.SITUATION, Step.ACTION, Step.RESULT), steps(star))
        assertEquals("ran an RFC", star!!.parts[1].text)
        assertEquals("shipped v2 on time", star.parts[2].text)
    }

    @Test
    fun `Vietnamese labels are recognised`() {
        val star = StarAnswer.from(listOf("Tình huống: hệ thống chậm", "Hành động: thêm cache", "Kết quả: nhanh gấp 3"))

        assertEquals(listOf(Step.SITUATION, Step.ACTION, Step.RESULT), steps(star))
    }

    @Test
    fun `labels out of order or partly labelled are not STAR`() {
        assertNull(StarAnswer.from(listOf("Result: done", "Situation: x", "Action: y")))
        assertNull(StarAnswer.from(listOf("Situation: x", "did y", "Result: z")))
    }

    @Test
    fun `hyphenated words are not mistaken for labels`() {
        val star = StarAnswer.from(listOf("Action-oriented team", "T-shirt sizing", "S3 costs down 30%", "Result-driven review"))

        assertEquals(listOf(Step.SITUATION, Step.TASK, Step.ACTION, Step.RESULT), steps(star))
        assertEquals("Action-oriented team", star!!.parts[0].text)
    }

    @Test
    fun `other lengths stay plain bullets`() {
        assertNull(StarAnswer.from(listOf("one", "two")))
        assertNull(StarAnswer.from(listOf("1", "2", "3", "4", "5")))
        assertNull(StarAnswer.from(listOf("a", " ", "c")))
    }
}
