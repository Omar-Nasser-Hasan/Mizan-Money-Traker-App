// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class ContrastTest {
    @Test
    fun `every M0 text token meets WCAG AA on its declared background`() {
        TextContrastPairs.forEach { pair ->
            val ratio = contrastRatio(pair.foreground, pair.background)
            assertTrue("${pair.foreground} on ${pair.background} was $ratio", ratio >= 4.5)
        }
    }

    private fun contrastRatio(
        foreground: String,
        background: String,
    ): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private fun luminance(hex: String): Double =
        hex
            .chunked(2)
            .map { it.toInt(16) / 255.0 }
            .map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            .let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }

    private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
}
