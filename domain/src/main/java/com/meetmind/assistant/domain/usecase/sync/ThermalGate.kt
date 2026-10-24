package com.meetmind.assistant.domain.usecase.sync

/**
 * Mid-session thermal gate (spec 011, US1): pauses live LLM inference while the phone is
 * hot so inference stops adding heat during the recording.
 *
 * Fed one reading per second by the recording timer. Turns on at the first hot reading
 * (thermal status MODERATE or above) and turns off only after [coolReadingsToRelease]
 * cool readings in a row, so it does not flap at the threshold. Resuming is expensive:
 * the next inference reloads the model and runs at full load, so the release delay is
 * deliberately long (30 s by default; the original used 2 s).
 *
 * [isActive] is read from the inference coroutine and written from the timer, hence
 * the volatile fields.
 */
class ThermalGate(private val coolReadingsToRelease: Int = DEFAULT_COOL_READINGS_TO_RELEASE) {

    @Volatile
    var isActive: Boolean = false
        private set

    /** True once the gate has been active at any point since the last [reset]. */
    @Volatile
    var wasActive: Boolean = false
        private set

    private var coolReadings = 0

    /** Record one thermal reading and return whether the gate is now active. */
    fun onReading(overheating: Boolean): Boolean {
        if (overheating) {
            coolReadings = 0
            isActive = true
            wasActive = true
        } else if (isActive) {
            coolReadings++
            if (coolReadings >= coolReadingsToRelease) {
                isActive = false
                coolReadings = 0
            }
        }
        return isActive
    }

    fun reset() {
        isActive = false
        wasActive = false
        coolReadings = 0
    }

    companion object {
        const val DEFAULT_COOL_READINGS_TO_RELEASE = 30
    }
}
