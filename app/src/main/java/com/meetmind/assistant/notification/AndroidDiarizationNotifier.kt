package com.meetmind.assistant.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meetmind.assistant.domain.notification.DiarizationNotifier
import com.meetmind.assistant.ui.MainActivity
import com.meetmind.assistant.ui.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the system notification when speaker diarization for a session
 * completes (or fails) so users who have backgrounded the app know to come
 * back. Tapping the notification deep-links straight into the session's
 * details screen via [MainActivity.EXTRA_OPEN_SESSION_ID].
 *
 * Notification IDs are derived from [String.hashCode] of the session id so
 * each session has its own slot — re-running diarization on the same session
 * replaces the previous notification instead of stacking.
 */
@Singleton
class AndroidDiarizationNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : DiarizationNotifier {

    companion object {
        private const val CHANNEL_ID = "diarization_results_channel"
        private const val REQUEST_CODE_BASE = 4000
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannel()
    }

    override fun notifyDiarizationCompleted(
        sessionId: String,
        sessionName: String?,
        speakerCount: Int
    ) {
        val title = context.getString(R.string.diarization_notification_completed_title)
        val displayName = sessionName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.session_unnamed)
        // Quantity-aware body via string-array fallback would be ideal, but for
        // a single English placeholder we just inline the count. Translators
        // can adjust the format string per locale.
        val body = context.getString(
            R.string.diarization_notification_completed_body,
            speakerCount,
            displayName
        )
        post(sessionId, title, body)
    }

    override fun notifyDiarizationFailed(
        sessionId: String,
        sessionName: String?,
        errorMessage: String?
    ) {
        val title = context.getString(R.string.diarization_notification_failed_title)
        val displayName = sessionName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.session_unnamed)
        // Truncate the error message — it's intended as a hint, not a full
        // stack trace, and Android already cap-shrinks long text anyway.
        val tail = errorMessage?.take(120)?.let { ": $it" }.orEmpty()
        val body = context.getString(
            R.string.diarization_notification_failed_body,
            displayName
        ) + tail
        post(sessionId, title, body)
    }

    override fun cancel(sessionId: String) {
        notificationManager.cancel(notificationId(sessionId))
    }

    private fun post(sessionId: String, title: String, body: String) {
        // Tapping opens MainActivity with the session-id extra, which the
        // activity reads in onCreate to deep-link into session details.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SESSION_ID, sessionId)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + sessionId.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_download) // reuse — no dedicated icon yet
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(notificationId(sessionId), notification)
    }

    private fun notificationId(sessionId: String): Int = sessionId.hashCode()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Idempotent — calling createNotificationChannel with an existing id
            // updates the channel without erroring.
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_diarization_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_diarization_description)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
