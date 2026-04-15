// Session mode — determines suggestion framing and question detection heuristics
package com.meetmind.assistant.data.model

enum class SessionMode {
    /** Corporate / team meeting context — framing for collaborative answers */
    MEETING,

    /** Job interview context — framing for personal, competency-based answers */
    INTERVIEW
}
