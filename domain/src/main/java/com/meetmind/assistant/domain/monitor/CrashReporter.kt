package com.meetmind.assistant.domain.monitor

/**
 * Reports non-fatal exceptions and breadcrumb logs to a crash-reporting backend.
 *
 * Domain layer abstraction so use cases / view models can record handled errors
 * (the kind that get caught and turned into user-facing error strings) without
 * depending directly on Firebase. The data layer provides the concrete impl.
 *
 * Fatal crashes are reported automatically by Firebase auto-init — this interface
 * is for the silent-failure case: caught exceptions that the user only sees as
 * "Failed to generate insight" but which we still want diagnostics for in prod.
 */
interface CrashReporter {
    /**
     * Record a handled exception with optional context message.
     * No-op if reporting is unavailable on the platform.
     */
    fun recordNonFatal(throwable: Throwable, message: String? = null)

    /** Add a breadcrumb visible in the next crash report. Capped to short strings. */
    fun log(message: String)
}
