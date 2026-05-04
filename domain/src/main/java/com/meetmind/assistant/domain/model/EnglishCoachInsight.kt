package com.meetmind.assistant.domain.model

data class EnglishCoachInsight(
    val original: String,
    val corrected: String,
    val isCorrect: Boolean,
    val polish: String?,
    val coachingTip: String?,
    val context: String  // "daily" | "professional"
)
