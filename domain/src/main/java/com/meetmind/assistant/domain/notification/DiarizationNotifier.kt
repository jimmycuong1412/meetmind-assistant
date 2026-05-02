package com.meetmind.assistant.domain.notification

/**
 * Posts user-facing notifications for the speaker diarization pipeline.
 *
 * Domain abstraction so [com.meetmind.assistant.domain.usecase.transcription.RunDiarizationUseCase]
 * can notify the user when a long-running diarization completes — even if the
 * app has been backgrounded since the run started — without depending on
 * Android NotificationManager directly. The app layer provides the concrete
 * implementation.
 *
 * Notifications carry a deep link to open the session details screen so the
 * user can immediately review the speaker labels.
 */
interface DiarizationNotifier {

    /**
     * Post a "diarization completed" notification.
     *
     * @param sessionId        The session whose diarization finished — used to
     *                         build the tap-to-open deep link.
     * @param sessionName      Human-readable session name for the notification
     *                         body. Null falls back to a generic "Untitled session".
     * @param speakerCount     Number of distinct speakers identified. Used in
     *                         the body copy ("Identified 3 speakers in …").
     */
    fun notifyDiarizationCompleted(
        sessionId: String,
        sessionName: String?,
        speakerCount: Int
    )

    /**
     * Post a "diarization failed" notification so the user knows the run they
     * left in the background didn't finish and can retry from the session
     * details screen.
     */
    fun notifyDiarizationFailed(
        sessionId: String,
        sessionName: String?,
        errorMessage: String?
    )

    /**
     * Cancel any previously posted diarization notification for [sessionId].
     * Called when the user opens the session details screen so they don't see
     * a stale "completed" notification for a session they're already viewing.
     */
    fun cancel(sessionId: String)
}
