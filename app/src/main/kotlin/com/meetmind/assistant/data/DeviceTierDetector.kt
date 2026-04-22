// spec 009 — T030: DeviceTierDetector — RAM + SDK → recommended GemmaModelVariant
package com.meetmind.assistant.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.meetmind.assistant.data.model.GemmaModelVariant
import javax.inject.Inject

/**
 * Recommends a [GemmaModelVariant] based on device RAM and Android SDK version.
 *
 * Decision thresholds (contracts.md C4.1 / C4.2):
 *  - ≥ 8 GB RAM **and** SDK ≥ 33 (Android 13+) → [GemmaModelVariant.Q4_K_M]
 *  - Otherwise → [GemmaModelVariant.IQ4_NL]
 *
 * Both Lenovo Y700 Gen 3 and Honor Magic 6 Pro (12 GB, SDK 34) qualify for Q4_K_M.
 *
 * spec 009 — T030
 */
class DeviceTierDetector @Inject constructor() {

    companion object {
        private const val TAG = "DeviceTierDetector"
        private const val RAM_THRESHOLD_BYTES = 8_000_000_000L  // 8 GB
        private const val SDK_THRESHOLD = 33                    // Android 13
    }

    /**
     * Pure function — usable in JVM unit tests without an Android Context.
     *
     * @param totalRamBytes Total physical RAM in bytes (from [ActivityManager.MemoryInfo.totalMem])
     * @param sdkVersion    [android.os.Build.VERSION.SDK_INT]
     */
    fun recommendedVariant(totalRamBytes: Long, sdkVersion: Int): GemmaModelVariant {
        val isFlagship = totalRamBytes >= RAM_THRESHOLD_BYTES && sdkVersion >= SDK_THRESHOLD
        val recommendation = if (isFlagship) GemmaModelVariant.Q4_K_M else GemmaModelVariant.IQ4_NL
        Log.d(
            TAG,
            "recommendedVariant: ram=${totalRamBytes / 1_000_000_000}GB sdk=$sdkVersion → $recommendation"
        )
        return recommendation
    }

    /**
     * Convenience overload using the device's actual RAM and current SDK.
     */
    fun recommendedVariant(context: Context): GemmaModelVariant {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return recommendedVariant(
            totalRamBytes = memInfo.totalMem,
            sdkVersion = Build.VERSION.SDK_INT
        )
    }
}
