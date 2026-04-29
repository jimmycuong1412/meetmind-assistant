package com.meetmind.assistant.domain.monitor

/**
 * Reports the current thermal pressure of the device SoC.
 *
 * Implementations read platform-specific thermal APIs; the domain layer only
 * depends on this interface so use cases remain Android-free.
 */
interface ThermalMonitor {
    /**
     * Returns true when the device is under thermal pressure (MODERATE status or above).
     * Used for fine-grained interval throttling — keep running, just slower.
     * Always returns false on devices running Android 9 or earlier.
     */
    fun isOverheating(): Boolean

    /**
     * Returns true when the device is at SEVERE thermal status or worse.
     * At this level the SoC is aggressively clipping clock speeds and any sustained
     * workload (especially LLM inference) will both fall behind real time and
     * accelerate further heating. Callers should switch from REAL_TIME insight
     * generation to deferred END_OF_SESSION batch processing.
     * Always returns false on devices running Android 9 or earlier.
     */
    fun isCritical(): Boolean
}
