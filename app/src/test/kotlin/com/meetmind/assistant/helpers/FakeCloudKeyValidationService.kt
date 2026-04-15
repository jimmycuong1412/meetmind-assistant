// T018: Fake CloudKeyValidationService for ViewModel unit tests
package com.meetmind.assistant.helpers

import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.inference.CloudKeyValidationService
import com.meetmind.assistant.inference.ValidationResult

/**
 * Test double for [CloudKeyValidationService].
 *
 * Returns [result] for every call to [validate], without making real network requests.
 * Captures the last validated key for assertion in tests.
 */
class FakeCloudKeyValidationService(
    private val result: ValidationResult = ValidationResult.SUCCESS
) : CloudKeyValidationService() {

    var lastProvider: CloudProvider? = null
        private set
    var lastKey: String? = null
        private set

    override suspend fun validate(provider: CloudProvider, apiKey: String): ValidationResult {
        lastProvider = provider
        lastKey = apiKey
        return result
    }
}
