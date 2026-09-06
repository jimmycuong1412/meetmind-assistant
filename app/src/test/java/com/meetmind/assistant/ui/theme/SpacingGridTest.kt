package com.meetmind.assistant.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the 4dp spacing grid in DESIGN.md §11 rule 3.
 *
 * Structural spacing — `padding(...)`, `Arrangement.spacedBy(...)` and `Spacer` gaps —
 * must sit on multiples of 4dp, with **1dp and 2dp allowed** as optical half-steps for
 * tight work: badge padding, chip insets, the gap between a label and its value. Forcing
 * those to 4dp visibly loosens components that are meant to read as one unit.
 *
 * Before this was enforced the UI had drifted to 27× `6.dp`, 27× `10.dp`, 20× `14.dp`,
 * 10× `5.dp`, 10× `18.dp` and 8× `3.dp` — values that are neither grid steps nor
 * deliberate fine adjustments.
 *
 * Deliberately **not** covered, because a 4dp grid is the wrong rule for them:
 *  - `size(...)` on icons — 14/18/22dp are standard optical icon steps.
 *  - Stroke widths, corner radii, divider thicknesses and Canvas geometry, where
 *    fractional and odd values (1.5dp, 2.5dp, 3.5dp) are intentional.
 *
 * This reads source rather than runtime values, so it is a lint rule wearing a test's
 * clothes. That is the cheapest place to catch it — the alternative is a screenshot diff.
 */
class SpacingGridTest {

    private companion object {
        val UI_ROOT = File("src/main/java/com/meetmind/assistant/ui")

        /** `padding(16.dp)`, `padding(horizontal = 12.dp, vertical = 8.dp)`, `spacedBy(4.dp)`. */
        val SPACING_CALL = Regex("""\b(padding|spacedBy)\(([^()]*)\)""")

        /** `Spacer(modifier = Modifier.height(8.dp))`. */
        val SPACER_GAP = Regex("""Spacer\(\s*modifier\s*=\s*Modifier\.(?:height|width)\((\d+)\.dp\)\s*\)""")

        val DP_VALUE = Regex("""\b(\d+)\.dp\b""")
    }

    private fun sourceFiles(): List<File> {
        assertTrue(
            "UI source root not found at ${UI_ROOT.absolutePath} — has the module layout changed?",
            UI_ROOT.isDirectory,
        )
        return UI_ROOT.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun File.relativePath() = relativeTo(UI_ROOT).path.replace('\\', '/')

    /** Multiples of 4dp, plus 1-2dp optical half-steps for tight work. */
    private fun Int.isOnGrid() = this <= 2 || this % 4 == 0

    @Test
    fun `padding and spacedBy sit on the 4dp grid`() {
        val offenders = mutableListOf<String>()

        sourceFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                SPACING_CALL.findAll(line).forEach { call ->
                    DP_VALUE.findAll(call.groupValues[2]).forEach { dp ->
                        val value = dp.groupValues[1].toInt()
                        if (!value.isOnGrid()) {
                            offenders += "${file.relativePath()}:${index + 1}  ${call.value.trim()}"
                        }
                    }
                }
            }
        }

        assertTrue(
            "Off-grid structural spacing (use a multiple of 4dp):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `spacer gaps sit on the 4dp grid`() {
        val offenders = mutableListOf<String>()

        sourceFiles().forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                SPACER_GAP.findAll(line).forEach { gap ->
                    val value = gap.groupValues[1].toInt()
                    if (!value.isOnGrid()) {
                        offenders += "${file.relativePath()}:${index + 1}  ${gap.value.trim()}"
                    }
                }
            }
        }

        assertTrue(
            "Off-grid Spacer gaps (use a multiple of 4dp):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no raw color literals remain in the UI layer`() {
        // Step 2-5 routed every color through the theme. This keeps it that way:
        // a raw Color(0x...) in a screen is how the palette drifted from the spec
        // in the first place. Theme files are where literals are supposed to live.
        val offenders = sourceFiles()
            .filterNot { it.relativePath().startsWith("asr/ui/theme/") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.contains(Regex("""Color\(0x[0-9A-Fa-f]{8}\)"""))) {
                        "${file.relativePath()}:${index + 1}  ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        assertTrue(
            "Raw color literals outside the theme package — read from " +
                "MaterialTheme.colorScheme / semanticColors instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
