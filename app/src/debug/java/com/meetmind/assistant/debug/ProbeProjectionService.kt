package com.meetmind.assistant.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Minimal foreground service that exists solely to satisfy the platform requirement
 * behind `MediaProjectionManager.getMediaProjection()`.
 *
 * Since Android 14 (API 34), calling `getMediaProjection()` without a **running**
 * foreground service of type `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` throws:
 *
 * ```
 * SecurityException: Media projections require a foreground service of type
 * ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
 * ```
 *
 * Ordering matters and is easy to get wrong: the service must be started **and have
 * called `startForeground()`** before `getMediaProjection()` runs — not merely
 * started concurrently. [PlaybackCaptureProbeActivity] therefore starts this service
 * from the consent callback and waits for [isRunning] before creating the projection.
 *
 * Debug-only, like the rest of the probe. The production implementation of this
 * requirement is plan Task 4, which adds `mediaProjection` to the existing
 * `RecordingService` rather than introducing a second service.
 */
class ProbeProjectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "probe_projection"
        private const val NOTIFICATION_ID = 4201

        /**
         * Set once `startForeground()` has returned, cleared on destroy.
         *
         * The activity polls this before calling `getMediaProjection()`: starting the
         * service is asynchronous, so "I called startService()" is not the same as
         * "the platform sees a running foreground service", and only the latter
         * satisfies the check.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ProbeProjectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProbeProjectionService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Playback capture probe",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Playback capture probe")
            .setContentText("Measuring captured audio levels")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
