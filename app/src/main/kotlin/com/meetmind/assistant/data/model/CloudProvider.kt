// T005: Cloud AI provider enum — identifies which cloud inference backend to use
package com.meetmind.assistant.data.model

enum class CloudProvider {
    /** Google Gemini via Firebase AI Logic SDK */
    GEMINI,

    /** Anthropic Claude via official Java SDK */
    CLAUDE
}
