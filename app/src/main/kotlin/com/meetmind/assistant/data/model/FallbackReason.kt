// T007: Reason a cloud inference attempt fell back to on-device
package com.meetmind.assistant.data.model

enum class FallbackReason {
    /** Cloud provider did not respond within 5 s SLA */
    TIMEOUT,

    /** Device has no internet connectivity */
    NETWORK_UNAVAILABLE,

    /** API key missing, invalid, or rejected (HTTP 401/403) */
    AUTH_ERROR,

    /** Provider returned an unexpected error (5xx, malformed response) */
    PROVIDER_ERROR,

    /** User has cloud inference toggled off */
    CLOUD_DISABLED
}
