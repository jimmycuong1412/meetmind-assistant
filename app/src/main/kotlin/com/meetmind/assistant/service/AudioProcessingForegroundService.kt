// T031: Foreground service — audio capture + cloud inference routing
// Spec 004 will flesh out VAD/ASR; this file establishes the cloud integration points.
package com.meetmind.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meetmind.assistant.MeetMindApplication
import com.meetmind.assistant.data.model.CloudInferenceRequest
import com.meetmind.assistant.data.model.CloudProvider
import com.meetmind.assistant.data.model.InferenceEvent
import com.meetmind.assistant.data.model.SessionMode
import com.meetmind.assistant.inference.CloudInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Long-running foreground service (foregroundServiceType = microphone|dataSync).
 *
 * Responsibilities:
 *  1. Capture microphone audio (spec 004 — VAD + Vosk ASR wired here later)
 *  2. Detect questions from transcript (spec 004 — rule-based heuristics)
 *  3. T031: Route detected questions to [CloudInferenceEngine] for suggestions
 *  4. Broadcast [InferenceEvent] stream to [SessionViewModel] via shared StateFlow
 *
 * The on-device audio pipeline (spec 004) hooks into [onQuestionDetected].
 */
class AudioProcessingForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "meetmind_session"
        private const val CHANNEL_NAME = "MeetMind Session"

        /** Broadcast action used to relay InferenceEvents to the UI layer */
        const val ACTION_INFERENCE_EVENT = "com.meetmind.assistant.INFERENCE_EVENT"
        const val EXTRA_EVENT_TYPE = "event_type"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_TEXT = "text"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var cloudInferenceEngine: CloudInferenceEngine

    override fun onCreate() {
        super.onCreate()
        val container = (application as MeetMindApplication).container
        cloudInferenceEngine = container.cloudInferenceEngine
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Audio pipeline starts here (spec 004 wires VAD/ASR loop)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Called by the question detection pipeline (spec 004) when a question is detected.
     *
     * T031: Routes the question to [CloudInferenceEngine]; emits [InferenceEvent]s
     * which are forwarded to the overlay / SessionViewModel.
     */
    fun onQuestionDetected(
        questionText: String,
        systemPrompt: String,
        provider: CloudProvider,
        sessionMode: SessionMode
    ) {
        val request = CloudInferenceRequest(
            questionText = questionText,
            systemPrompt = systemPrompt,
            provider = provider,
            sessionMode = sessionMode
        )

        serviceScope.launch {
            cloudInferenceEngine.streamSuggestion(request)
                .onEach { event -> broadcastInferenceEvent(event) }
                .launchIn(this)
        }
    }

    private fun broadcastInferenceEvent(event: InferenceEvent) {
        val intent = Intent(ACTION_INFERENCE_EVENT).apply {
            setPackage(packageName)
            when (event) {
                is InferenceEvent.Token -> {
                    putExtra(EXTRA_EVENT_TYPE, "token")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.text)
                }
                is InferenceEvent.Complete -> {
                    putExtra(EXTRA_EVENT_TYPE, "complete")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.fullText)
                }
                is InferenceEvent.FallbackActivated -> {
                    putExtra(EXTRA_EVENT_TYPE, "fallback")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.reason.name)
                }
                is InferenceEvent.Error -> {
                    putExtra(EXTRA_EVENT_TYPE, "error")
                    putExtra(EXTRA_REQUEST_ID, event.requestId)
                    putExtra(EXTRA_TEXT, event.message)
                }
            }
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeetMind is listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
