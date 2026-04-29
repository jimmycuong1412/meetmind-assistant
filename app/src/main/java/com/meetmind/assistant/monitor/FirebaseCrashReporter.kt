package com.meetmind.assistant.monitor

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.meetmind.assistant.domain.monitor.CrashReporter

/**
 * [CrashReporter] backed by Firebase Crashlytics.
 *
 * Lives in the app module because Firebase is only wired up at the app level —
 * the data module deliberately stays free of Google Play Services dependencies
 * so it can be reused in test / KMP contexts.
 *
 * The Firebase SDK auto-initialises from `google-services.json` so this class
 * just forwards calls. Crashlytics safely no-ops on devices without Google Play
 * Services so callers don't need to guard.
 */
class FirebaseCrashReporter : CrashReporter {

    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        if (message != null) crashlytics.log(message)
        crashlytics.recordException(throwable)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }
}
