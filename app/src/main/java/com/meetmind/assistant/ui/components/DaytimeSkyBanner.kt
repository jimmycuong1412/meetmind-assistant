package com.meetmind.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.meetmind.assistant.ui.ui.theme.SkyAfternoon
import com.meetmind.assistant.ui.ui.theme.SkyDawn
import com.meetmind.assistant.ui.ui.theme.SkyDusk
import com.meetmind.assistant.ui.ui.theme.SkyHillBack
import com.meetmind.assistant.ui.ui.theme.SkyHillFront
import com.meetmind.assistant.ui.ui.theme.SkyMoonCrater
import com.meetmind.assistant.ui.ui.theme.SkyMoonDisk
import com.meetmind.assistant.ui.ui.theme.SkyMorning
import com.meetmind.assistant.ui.ui.theme.SkyNight
import com.meetmind.assistant.ui.ui.theme.SkyScrimAlpha
import com.meetmind.assistant.ui.ui.theme.SkyStar
import com.meetmind.assistant.ui.ui.theme.SkySunDisk
import com.meetmind.assistant.ui.ui.theme.SkySunGlow
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/**
 * Flat-design sky banner that reflects the current local time of day.
 *
 * Drawing order (back to front):
 *   sky gradient -> stars -> back hills -> celestial body -> front hills -> scrim
 *
 * This puts the sun/moon between the two hill layers: it peeks above the distant
 * hills but is hidden behind the foreground hills at dawn and dusk.
 *
 * Updates once per minute via LaunchedEffect. On return from background the
 * composable is recomposed from scratch, so it always shows the correct time.
 *
 * **Legibility.** The banner carries white header text (date, session stats), so
 * every sky state has to support it. The previous cool palette did not: white on
 * the morning sky measured 1.33:1 and on the afternoon sky 2.14:1, because the old
 * scrim started at 45% height and the text sits above that. Four of the five states
 * failed AA.
 *
 * The fix is structural rather than chromatic - a uniform [SkyScrimAlpha] veil over
 * the whole banner - so hue is free to say what time it is while contrast stays
 * fixed. Worst case across all five states is now 5.14:1. Guarded by
 * `DaytimeSkyBannerContrastTest`; palette values live in Color.kt.
 */
@Composable
fun DaytimeSkyBanner(modifier: Modifier = Modifier) {
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val cal = Calendar.getInstance()
            hour = cal.get(Calendar.HOUR_OF_DAY)
            minute = cal.get(Calendar.MINUTE)
        }
    }

    val totalMinutes = hour * 60 + minute

    val (skyTop, skyBottom) = remember(hour, minute) {
        when {
            totalMinutes < 5 * 60 || totalMinutes >= 21 * 60 -> SkyNight
            totalMinutes < 8 * 60 -> SkyDawn
            totalMinutes < 12 * 60 -> SkyMorning
            totalMinutes < 17 * 60 -> SkyAfternoon
            else -> SkyDusk
        }
    }

    val isNight = totalMinutes < 6 * 60 || totalMinutes >= 20 * 60

    // Normalised arc position t in [0, 1]: 0 = rising, 0.5 = zenith, 1 = setting
    val celestialT = remember(hour, minute) {
        if (!isNight) {
            // Sun visible 06:00 - 20:00 (14 h window)
            ((totalMinutes - 6 * 60).coerceIn(0, 14 * 60)).toFloat() / (14 * 60)
        } else {
            // Moon visible 20:00 - 06:00 (10 h window, wraps midnight)
            val adj = if (totalMinutes < 6 * 60) totalMinutes + 24 * 60 else totalMinutes
            ((adj - 20 * 60).coerceIn(0, 10 * 60)).toFloat() / (10 * 60)
        }
    }

    val scrimColor = MaterialTheme.colorScheme.scrim

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Sky gradient
        drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom), 0f, h))

        // 2. Stars - night only, deterministic positions via fixed seed
        if (isNight) {
            val rng = java.util.Random(0xDEADBEEF)
            repeat(45) {
                val sx = rng.nextFloat() * w
                val sy = rng.nextFloat() * h * 0.70f
                val sr = rng.nextFloat() * 1.4f + 0.5f
                drawCircle(SkyStar, radius = sr, center = Offset(sx, sy))
            }
        }

        // 3. Celestial body on a parabolic arc.
        // Arc: starts/ends at horizonY (hill horizon line), peaks at zenithY.
        val cx = w * 0.08f + (w * 0.84f) * celestialT
        val horizonY = h * 0.72f  // matches left-edge of front hill path
        val zenithY = h * 0.12f   // highest point of arc (noon / midnight)
        val cy = horizonY - (horizonY - zenithY) * sin(celestialT * PI).toFloat()

        if (!isNight) {
            // Sun: warm glow rings + disk
            drawCircle(SkySunGlow.copy(alpha = 0.18f), 30.dp.toPx(), Offset(cx, cy))
            drawCircle(SkySunGlow.copy(alpha = 0.10f), 40.dp.toPx(), Offset(cx, cy))
            drawCircle(SkySunDisk, 13.dp.toPx(), Offset(cx, cy))
        } else {
            // Moon: subtle glow + disk + small crater
            drawCircle(SkyMoonDisk.copy(alpha = 0.12f), 22.dp.toPx(), Offset(cx, cy))
            drawCircle(SkyMoonDisk, 11.dp.toPx(), Offset(cx, cy))
            drawCircle(SkyMoonCrater, 3.5.dp.toPx(), Offset(cx + 4.dp.toPx(), cy - 3.dp.toPx()))
        }

        // 4. Back hills - distant layer, low alpha
        val backHill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.60f)
            quadraticTo(w * 0.28f, h * 0.30f, w * 0.52f, h * 0.52f)
            quadraticTo(w * 0.76f, h * 0.70f, w, h * 0.44f)
            lineTo(w, h)
            close()
        }
        drawPath(backHill, SkyHillBack)

        // 5. Front hills - foreground layer, fully opaque.
        //    Drawn after the celestial body so it hides the body near the horizon.
        val frontHill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.76f)
            quadraticTo(w * 0.18f, h * 0.56f, w * 0.38f, h * 0.70f)
            quadraticTo(w * 0.58f, h * 0.84f, w * 0.74f, h * 0.65f)
            quadraticTo(w * 0.87f, h * 0.52f, w, h * 0.68f)
            lineTo(w, h)
            close()
        }
        drawPath(frontHill, SkyHillFront)

        // 6. Uniform scrim across the FULL height.
        //    Not a bottom fade: the header text sits in the top third, which a bottom
        //    fade never reached. This is what makes every sky state legible.
        drawRect(color = scrimColor.copy(alpha = SkyScrimAlpha))
    }
}
