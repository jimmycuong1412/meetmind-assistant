package com.meetmind.assistant.data.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects whether the running device is a vendor with aggressive background-process
 * killing that affects long foreground recordings, and provides intents to launch
 * the appropriate settings page so the user can whitelist the app.
 *
 * Background: Android's foreground-service contract guarantees the OS won't kill
 * an active foreground service. Several Chinese OEMs (Xiaomi/MIUI, Oppo/ColorOS,
 * Huawei/EMUI, Vivo/FuntouchOS) ignore this contract via custom power-management
 * layers — they will silently kill the recording service mid-meeting, typically
 * around the 30-minute mark, regardless of the foreground notification.
 *
 * The fix the OEMs offer is a per-app whitelist (variously called "Battery saver",
 * "Auto-launch", "Background activity", "Protected apps") — but they bury it in
 * settings so deeply that users will not find it without a deep link.
 *
 * This helper:
 *  1. Identifies devices likely to need the prompt via [Build.MANUFACTURER].
 *  2. Builds the OEM-specific Intent that opens the relevant settings page.
 *  3. Falls back to the standard battery-optimization request on unknown vendors.
 */
@Singleton
class BatteryOptimizationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BatteryOptHelper"

        // Manufacturers known to break the foreground-service contract. Lowercased
        // for case-insensitive matching against Build.MANUFACTURER.
        private val AGGRESSIVE_VENDORS = setOf(
            "xiaomi",      // MIUI / HyperOS
            "redmi",       // sub-brand of Xiaomi
            "poco",        // sub-brand of Xiaomi
            "oppo",        // ColorOS
            "realme",      // ColorOS-derived
            "oneplus",     // OxygenOS — historically aggressive on background apps
            "vivo",        // FuntouchOS / OriginOS
            "iqoo",        // sub-brand of Vivo
            "huawei",      // EMUI / HarmonyOS
            "honor",       // formerly Huawei sub-brand
            "meizu",       // Flyme
            "asus"         // ZenUI on some models has aggressive PowerMaster
        )
    }

    /**
     * True when the device's manufacturer is on the known-aggressive list AND the
     * app is not already exempt from battery optimisations.
     *
     * Returns false on Stock Android, Pixel, Nothing, Sony, Motorola — these honour
     * the foreground-service contract correctly so no prompt is needed.
     */
    fun shouldPromptForBatteryWhitelist(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (manufacturer !in AGGRESSIVE_VENDORS) return false
        return !isIgnoringBatteryOptimizations()
    }

    /**
     * Returns true if the user has already added this app to the system battery
     * optimization whitelist. On API < 23 (where this concept doesn't exist) returns
     * true so no prompt is shown.
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Build an Intent that opens the most relevant settings page for the user to
     * whitelist the app. Caller is responsible for [Intent.FLAG_ACTIVITY_NEW_TASK]
     * if launching from a non-Activity context.
     *
     * Strategy:
     *  - Standard battery-optimization request on Stock Android (fastest path).
     *  - Vendor-specific deep links where the OEM provides one.
     *  - Generic app-info page as a last resort so the user can navigate manually.
     *
     * The returned intent is verified resolvable against the package manager —
     * `null` is returned only if every fallback (including generic settings) fails,
     * which in practice never happens.
     */
    fun buildWhitelistIntent(): Intent? {
        // Try OEM-specific deep links first — these land directly on the relevant page.
        val vendorIntent = buildVendorSpecificIntent()
        if (vendorIntent != null && resolves(vendorIntent)) return vendorIntent

        // Standard Android battery-optimization opt-out request (works on Stock + most OEMs).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val standardIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (resolves(standardIntent)) return standardIntent
        }

        // Last resort: open the per-app battery-usage page.
        val perAppIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return perAppIntent.takeIf { resolves(it) }
    }

    private fun buildVendorSpecificIntent(): Intent? {
        // Each vendor has its own component name for the autostart / background-app
        // settings screen. These are public APIs in their respective firmware but not
        // documented anywhere centrally — names sourced from each vendor's developer
        // forum and verified by the dontkillmyapp.com community.
        val candidates: List<Intent> = when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            "oppo", "realme" -> listOf(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                componentIntent("com.oppo.safe",          "com.oppo.safe.permission.startup.StartupAppListActivity")
            )
            "vivo", "iqoo" -> listOf(
                componentIntent("com.iqoo.secure",     "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            )
            "huawei", "honor" -> listOf(
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            "oneplus" -> listOf(
                componentIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            "meizu" -> listOf(
                componentIntent("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC")
            )
            "asus" -> listOf(
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { resolves(it) }
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().apply { setClassName(pkg, cls) }

    private fun resolves(intent: Intent): Boolean = try {
        context.packageManager.resolveActivity(intent, 0) != null
    } catch (e: Exception) {
        Log.w(TAG, "resolveActivity failed for ${intent.component ?: intent.action}", e)
        false
    }
}
