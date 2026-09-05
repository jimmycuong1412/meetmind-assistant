package com.meetmind.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import com.meetmind.assistant.ui.ui.theme.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guards the WCAG contrast contract in DESIGN.md §2.4 and §7.1.
 *
 * The warm palette has two traps that are easy to reintroduce by eye, because warm
 * mid-tones read darker than they measure:
 *
 *  - White on the brand terracotta `#C96442` is 3.90:1 and **fails AA**. That is why
 *    [TerracottaDeep] exists and holds the `primary` role.
 *  - The obvious warm warning ochre `#B3811F` is 3.13:1 on parchment and also fails.
 *
 * Both shipped in the previous spec. This test exists so they cannot ship again.
 */
class ColorContrastTest {

    private companion object {
        /** WCAG AA for body text. */
        const val AA_TEXT = 4.5

        /** WCAG AA for icons, strokes and large text. */
        const val AA_NON_TEXT = 3.0
    }

    private fun Color.relativeLuminance(): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Composites [this] at [alpha] over [background] — what the eye actually sees. */
    private fun Color.over(alpha: Float, background: Color) = Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )

    private fun assertContrast(label: String, fg: Color, bg: Color, required: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$label: ${"%.2f".format(ratio)}:1 — needs $required:1",
            ratio >= required,
        )
    }

    @Test
    fun `light scheme content colors meet AA`() {
        assertContrast("onPrimary/primary", md_theme_light_onPrimary, md_theme_light_primary, AA_TEXT)
        assertContrast("onSecondary/secondary", md_theme_light_onSecondary, md_theme_light_secondary, AA_TEXT)
        assertContrast("onTertiary/tertiary", md_theme_light_onTertiary, md_theme_light_tertiary, AA_TEXT)
        assertContrast("onError/error", md_theme_light_onError, md_theme_light_error, AA_TEXT)
        assertContrast("onBackground/background", md_theme_light_onBackground, md_theme_light_background, AA_TEXT)
        assertContrast("onSurface/surface", md_theme_light_onSurface, md_theme_light_surface, AA_TEXT)
        assertContrast("onSurfaceVariant/surface", md_theme_light_onSurfaceVariant, md_theme_light_surface, AA_TEXT)
        assertContrast("onSurfaceVariant/background", md_theme_light_onSurfaceVariant, md_theme_light_background, AA_TEXT)
        assertContrast("onSurface/surfaceContainerHigh", md_theme_light_onSurface, md_theme_light_surfaceContainerHigh, AA_TEXT)
    }

    @Test
    fun `light scheme containers meet AA`() {
        assertContrast("onPrimaryContainer", md_theme_light_onPrimaryContainer, md_theme_light_primaryContainer, AA_TEXT)
        assertContrast("onSecondaryContainer", md_theme_light_onSecondaryContainer, md_theme_light_secondaryContainer, AA_TEXT)
        assertContrast("onTertiaryContainer", md_theme_light_onTertiaryContainer, md_theme_light_tertiaryContainer, AA_TEXT)
        assertContrast("onErrorContainer", md_theme_light_onErrorContainer, md_theme_light_errorContainer, AA_TEXT)
    }

    @Test
    fun `dark scheme content colors meet AA`() {
        assertContrast("onPrimary/primary", md_theme_dark_onPrimary, md_theme_dark_primary, AA_TEXT)
        assertContrast("onSecondary/secondary", md_theme_dark_onSecondary, md_theme_dark_secondary, AA_TEXT)
        assertContrast("onTertiary/tertiary", md_theme_dark_onTertiary, md_theme_dark_tertiary, AA_TEXT)
        assertContrast("onError/error", md_theme_dark_onError, md_theme_dark_error, AA_TEXT)
        assertContrast("onBackground/background", md_theme_dark_onBackground, md_theme_dark_background, AA_TEXT)
        assertContrast("onSurface/surface", md_theme_dark_onSurface, md_theme_dark_surface, AA_TEXT)
        assertContrast("onSurfaceVariant/surface", md_theme_dark_onSurfaceVariant, md_theme_dark_surface, AA_TEXT)
        assertContrast("onSurface/surfaceContainerHigh", md_theme_dark_onSurface, md_theme_dark_surfaceContainerHigh, AA_TEXT)
    }

    @Test
    fun `dark scheme containers meet AA`() {
        assertContrast("onPrimaryContainer", md_theme_dark_onPrimaryContainer, md_theme_dark_primaryContainer, AA_TEXT)
        assertContrast("onSecondaryContainer", md_theme_dark_onSecondaryContainer, md_theme_dark_secondaryContainer, AA_TEXT)
        assertContrast("onTertiaryContainer", md_theme_dark_onTertiaryContainer, md_theme_dark_tertiaryContainer, AA_TEXT)
        assertContrast("onErrorContainer", md_theme_dark_onErrorContainer, md_theme_dark_errorContainer, AA_TEXT)
    }

    @Test
    fun `semantic colors meet AA on their own ground`() {
        assertContrast("success light", SuccessLight, md_theme_light_background, AA_TEXT)
        assertContrast("warning light", WarningLight, md_theme_light_background, AA_TEXT)
        assertContrast("error light", md_theme_light_error, md_theme_light_background, AA_TEXT)
        assertContrast("success dark", SuccessDark, md_theme_dark_background, AA_TEXT)
        assertContrast("warning dark", WarningDark, md_theme_dark_background, AA_TEXT)
        assertContrast("error dark", md_theme_dark_error, md_theme_dark_background, AA_TEXT)
    }

    @Test
    fun `outlines meet the non-text threshold`() {
        assertContrast("outline light", md_theme_light_outline, md_theme_light_background, AA_NON_TEXT)
        assertContrast("outline dark", md_theme_dark_outline, md_theme_dark_background, AA_NON_TEXT)
    }

    @Test
    fun `gradient content colors meet AA at the lightest stop`() {
        // GradientTop is the worst case: everything below it is darker.
        assertContrast("onGradient/top", OnGradient, GradientTop, AA_TEXT)
        assertContrast("onGradient/bottom", OnGradient, GradientBottom, AA_TEXT)

        // The variant is translucent, so test what actually composites onto the ground.
        // This is the check the previous violet build would have failed: its secondary
        // text sat at 70-75% alpha and landed near 3.3:1.
        val variantOnTop = OnGradient.over(OnGradientVariant.alpha, GradientTop)
        assertContrast("onGradientVariant/top", variantOnTop, GradientTop, AA_TEXT)

        val variantOnMid = OnGradient.over(OnGradientVariant.alpha, GradientMid)
        assertContrast("onGradientVariant/mid", variantOnMid, GradientMid, AA_TEXT)
    }

    @Test
    fun `brand terracotta is not used where it would fail`() {
        // Documents the trap rather than just avoiding it: if someone later promotes
        // Terracotta to the primary role, this test explains why that breaks.
        val whiteOnBrand = contrast(Color.White, Terracotta)
        assertTrue(
            "Terracotta unexpectedly passes AA (${"%.2f".format(whiteOnBrand)}) — " +
                "if the brand value changed, revisit DESIGN.md §2.4",
            whiteOnBrand < AA_TEXT,
        )
        assertContrast("white on TerracottaDeep", Color.White, TerracottaDeep, AA_TEXT)
        assertContrast("Terracotta as accent on parchment", Terracotta, md_theme_light_background, AA_NON_TEXT)
    }

    @Test
    fun `recording mode accents meet AA in both schemes`() {
        val lightGrounds = listOf(md_theme_light_surface, md_theme_light_background)
        val darkGrounds = listOf(md_theme_dark_surface, md_theme_dark_background)

        // Values mirror ModeColors.kt; these are used as small label text, not just icons.
        val lightAccents = mapOf(
            "capture" to Color(0xFFB35334),
            "translation" to Color(0xFF4F6F82),
            "interview" to Color(0xFF734765),
            "coach" to Color(0xFF547449),
        )
        val darkAccents = mapOf(
            "capture" to Color(0xFFE08A68),
            "translation" to Color(0xFF8FB0C4),
            "interview" to Color(0xFFC495B4),
            "coach" to Color(0xFF9BBE90),
        )

        lightAccents.forEach { (name, color) ->
            lightGrounds.forEach { ground -> assertContrast("mode $name (light)", color, ground, AA_TEXT) }
        }
        darkAccents.forEach { (name, color) ->
            darkGrounds.forEach { ground -> assertContrast("mode $name (dark)", color, ground, AA_TEXT) }
        }
    }
}
