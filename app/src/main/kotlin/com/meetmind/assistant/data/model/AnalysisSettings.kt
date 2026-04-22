// T003 (spec 008): DataStore-backed config for continuous conversation analysis
package com.meetmind.assistant.data.model

/**
 * Runtime configuration for the continuous conversation analysis cadence.
 *
 * Persisted to DataStore Preferences with key prefix `analysis_`.
 *
 * @param analysisEnabled   Whether the cadence analysis ticker is active. Defaults to true.
 * @param analysisIntervalS Cadence interval in seconds. Clamped to 10..120. Default 20s.
 * @param analysisWindowS   Rolling transcript window size in seconds. Clamped to 15..300. Default 60s.
 */
data class AnalysisSettings(
    val analysisEnabled: Boolean = DEFAULT_ENABLED,
    val analysisIntervalS: Int = DEFAULT_INTERVAL_S,
    val analysisWindowS: Int = DEFAULT_WINDOW_S
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_INTERVAL_S = 20
        const val DEFAULT_WINDOW_S = 60
        const val MIN_INTERVAL_S = 10
        const val MAX_INTERVAL_S = 120
        const val MIN_WINDOW_S = 15
        const val MAX_WINDOW_S = 300
    }
}
