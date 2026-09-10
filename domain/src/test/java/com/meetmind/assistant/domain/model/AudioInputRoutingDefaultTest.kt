package com.meetmind.assistant.domain.model

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regression guard for the audio input routing default.
 *
 * ## Why this test exists
 *
 * `SherpaOnnxDataSource` originally activated Bluetooth SCO unconditionally whenever a
 * headset was paired. That looks like a feature ("use the mic closest to the speaker")
 * but silently costs transcription accuracy: selecting a BT input device puts the audio
 * stack into communication (HFP/SCO) mode, which routes capture through the telephony
 * path — narrowband (commonly 8 kHz), lossy-codec compressed, and HAL noise-reduced.
 *
 * That is the very processing the pipeline avoids by choosing `AudioSource.MIC` over
 * `VOICE_RECOGNITION` (README §STT Pipeline). So the old behaviour discarded a
 * deliberate accuracy decision precisely when it mattered most — during a remote
 * meeting or interview, when the user is most likely wearing earbuds.
 *
 * The default must therefore stay **off**. This is a plausible thing for a future
 * change to "fix" back, so the default is pinned here with the rationale attached.
 */
class AudioInputRoutingDefaultTest {

    @Test
    fun `bluetooth mic capture is off by default`() {
        assertFalse(
            "preferBluetoothMic must default to false: routing capture to a Bluetooth " +
                "headset forces the narrowband HFP/SCO telephony path and degrades STT " +
                "accuracy versus the built-in mic's full-band 16 kHz raw PCM. " +
                "If you are changing this default, read README §VAD / Audio Optimizations first.",
            AppSettings().preferBluetoothMic
        )
    }
}
