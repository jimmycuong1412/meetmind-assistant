package com.meetmind.assistant.domain.model

/**
 * The kind of question an interviewer just asked.
 *
 * This is not a taxonomy for its own sake — it decides the **shape of the answer**.
 * A behavioural question wants a STAR arc; a system-design question wants
 * constraints → trade-offs → decision. Emitting the same undifferentiated bullet list
 * for both is what makes generated answers read as generic.
 *
 * Parsed from the LLM's `question_type` field. Unrecognised or missing values map to
 * null rather than a default, because guessing wrong here actively misshapes the
 * answer — no hint beats a misleading one.
 */
enum class QuestionType {
    /**
     * Drilling into how something works. *"How do you handle Terraform state locking
     * across teams?"* Wants mechanism, then the trade-off that made you choose it.
     */
    TECHNICAL_DEEP_DIVE,

    /**
     * Past behaviour as a predictor. *"Tell me about a time you disagreed with your
     * lead."* Wants a STAR arc — situation, task, action, result — anchored on one
     * concrete incident rather than a general policy.
     */
    BEHAVIORAL,

    /**
     * Open-ended design under constraints. *"How would you design multi-region
     * failover for this service?"* Wants constraints stated first, then trade-offs,
     * then a decision with its cost acknowledged.
     */
    SYSTEM_DESIGN,

    /**
     * A specific failure and what was learned. *"Walk me through your worst outage."*
     * Wants blast radius, detection, mitigation, and the durable fix — not blame.
     */
    INCIDENT_RETRO,

    /**
     * Ways of working, motivation, team fit. *"What kind of team do you do your best
     * work in?"* Wants a specific preference with a reason, not an agreeable
     * non-answer.
     */
    CULTURE_FIT;

    companion object {
        /**
         * Map the LLM's snake_case `question_type` value onto the enum.
         *
         * @return the matching type, or null when absent or unrecognised. Callers must
         *   treat null as "no hint available" and fall back to a neutral answer shape.
         */
        fun fromWireValue(raw: String?): QuestionType? {
            val key = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.name.lowercase() == key }
        }
    }
}
