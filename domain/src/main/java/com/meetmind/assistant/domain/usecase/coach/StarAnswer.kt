package com.meetmind.assistant.domain.usecase.coach

/**
 * A behavioural answer skeleton laid out as STAR — Situation, Task, Action, Result
 * (spec 010, US5), so the card can label each beat instead of showing bare bullets.
 *
 * Built from the skeleton bullets the Interview prompt already returns, not from a new
 * output format, so no extra tokens are spent and older insights still render.
 */
data class StarAnswer(val parts: List<Part>) {

    enum class Step { SITUATION, TASK, ACTION, RESULT }

    data class Part(val step: Step, val text: String)

    companion object {

        private val LABEL = Regex(
            "^\\s*(?:\\*\\*)?(situation|task|action|result|tình huống|nhiệm vụ|hành động|kết quả|[star])(?:\\*\\*)?" +
                "\\s*(?::|\\s[-–—]\\s)\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )

        private val STEP_BY_LABEL = mapOf(
            "situation" to Step.SITUATION, "s" to Step.SITUATION, "tình huống" to Step.SITUATION,
            "task" to Step.TASK, "t" to Step.TASK, "nhiệm vụ" to Step.TASK,
            "action" to Step.ACTION, "a" to Step.ACTION, "hành động" to Step.ACTION,
            "result" to Step.RESULT, "r" to Step.RESULT, "kết quả" to Step.RESULT
        )

        /**
         * Read [skeleton] as STAR, or return null when it does not look like one.
         *
         * Labelled bullets ("Situation: …", "S — …") are trusted when every bullet has a
         * label and the steps run in order. Otherwise the shape decides: 4 bullets are
         * S, T, A, R; 3 bullets are S, A, R — the shape the Interview prompt asked for
         * before the Task step was added, and what devices keep using when their saved
         * prompt predates the change. Anything else stays a plain bullet list, because a
         * wrong label misleads more than no label.
         */
        fun from(skeleton: List<String>): StarAnswer? {
            val bullets = skeleton.map { it.trim() }.filter { it.isNotEmpty() }
            if (bullets.size !in 3..4) return null

            val labelled = bullets.map { bullet ->
                LABEL.matchEntire(bullet)?.let { match ->
                    STEP_BY_LABEL[match.groupValues[1].lowercase()]?.let { Part(it, match.groupValues[2].trim()) }
                }
            }
            if (labelled.all { it != null }) {
                val parts = labelled.filterNotNull()
                val inOrder = parts.zipWithNext().all { (a, b) -> a.step < b.step }
                return if (inOrder && parts.first().step == Step.SITUATION && parts.last().step == Step.RESULT) {
                    StarAnswer(parts)
                } else {
                    null
                }
            }
            if (labelled.any { it != null }) return null

            val steps = if (bullets.size == 4) {
                listOf(Step.SITUATION, Step.TASK, Step.ACTION, Step.RESULT)
            } else {
                listOf(Step.SITUATION, Step.ACTION, Step.RESULT)
            }
            return StarAnswer(steps.zip(bullets) { step, text -> Part(step, text) })
        }
    }
}
