// T021: Fake network connectivity checker for fallback tests
package com.meetmind.assistant.helpers

/**
 * Injectable fake for [CloudInferenceEngine]'s `connectivityChecker` parameter.
 *
 * Set [isAvailable] to `false` to simulate no-network conditions.
 */
class FakeConnectivityChecker(var isAvailable: Boolean = true) {
    val asLambda: () -> Boolean get() = { isAvailable }
}
