// spec 009 — T013: @HiltAndroidApp replaces manual AppContainer DI
// T036 (spec 007): onTrimMemory hook to release on-device model under memory pressure
// T034 (spec 008): onTrimMemory also stops analysis cadence controller
package com.meetmind.assistant

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.meetmind.assistant.analysis.AnalysisCadenceController
import com.meetmind.assistant.inference.OnDeviceLlamaProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MeetMindApplication : Application() {

    /**
     * Injected by Hilt — used only for onTrimMemory() lifecycle hook.
     * The provider itself is a @Singleton scoped in InferenceModule.
     */
    @Inject
    lateinit var onDeviceLlamaProvider: OnDeviceLlamaProvider

    /**
     * Injected by Hilt — used only for onTrimMemory() lifecycle hook.
     * The controller itself is a @Singleton scoped in AnalysisModule.
     */
    @Inject
    lateinit var analysisCadenceController: AnalysisCadenceController

    /**
     * T036 (spec 007): Release the on-device Gemma 4 model when the OS signals
     * critical memory pressure. This frees ~5–7 GB of RAM for the system.
     *
     * The model will be reloaded on the next user interaction via ModelSetupViewModel.
     * All in-flight inference will fail with an Error event — the cloud path (if enabled)
     * remains unaffected.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            Log.w(
                "MeetMindApplication",
                "onTrimMemory level=$level — unloading on-device model + stopping analysis cadence"
            )
            if (::onDeviceLlamaProvider.isInitialized) {
                onDeviceLlamaProvider.unload()
            }
            if (::analysisCadenceController.isInitialized) {
                analysisCadenceController.stop()
            }
        }
    }
}
