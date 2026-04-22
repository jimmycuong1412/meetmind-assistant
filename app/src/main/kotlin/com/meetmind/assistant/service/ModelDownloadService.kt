// spec 009 — T045: Foreground download service for STT model files
package com.meetmind.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meetmind.assistant.data.ModelDownloadManager
import com.meetmind.assistant.data.model.DownloadProgress
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that downloads the 4 Nemo Parakeet TDT STT model files.
 *
 * Progress is exposed via [downloadProgress] StateFlow for UI consumers.
 * Downloads all 4 files sequentially; each file uses Range-header resume if interrupted.
 *
 * Foreground service type: `dataSync` (declared in manifest).
 *
 * spec 009 — T045
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "meetmind_download"
        private const val CHANNEL_NAME = "MeetMind Model Download"

        /** Exposed for UI binding — access from within the process only. */
        private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
        val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    }

    @Inject
    lateinit var downloadManager: ModelDownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            val sttModelDir = AudioProcessingForegroundService.sttModelDir(this@ModelDownloadService)
            Log.i(TAG, "Starting STT model download to $sttModelDir")

            try {
                downloadManager.downloadSttModel(sttModelDir).collect { progress ->
                    _downloadProgress.value = progress
                    val percent = (progress.progressFraction * 100).toInt()
                    updateNotification("Downloading ${progress.filename}: $percent%")
                    Log.d(TAG, "Progress: ${progress.filename} $percent% (${progress.bytesDownloaded}/${progress.totalBytes})")
                }
                Log.i(TAG, "All STT model files downloaded successfully")
                updateNotification("Download complete")
            } catch (e: Exception) {
                Log.e(TAG, "STT model download failed", e)
                updateNotification("Download failed: ${e.message}")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(contentText: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetMind — Downloading STT Model")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }
}
