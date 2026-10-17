// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class MzThemeContrastTest {
    @Test
    fun everyV2TextTokenMeetsAaContrastOnItsSurface() {
        val pairs =
            listOf(
                Color(0xFFE9E6DE) to Color(0xFF121316),
                Color(0xFFBEB9AF) to Color(0xFF121316),
                Color(0xFFE9E6DE) to Color(0xFF292A2D),
                Color(0xFFBEB9AF) to Color(0xFF292A2D),
                Color(0xFF2D291E) to Color(0xFFD9C27F),
                Color(0xFF58CFC4) to Color(0xFF121316),
                Color(0xFFF4806E) to Color(0xFF121316),
                Color(0xFFB5C7D8) to Color(0xFF121316),
                Color(0xFFC0A5E8) to Color(0xFF121316),
                Color(0xFFF0BD76) to Color(0xFF121316),
                Color(0xFF2E2B25) to Color(0xFFF5F2E9),
                Color(0xFF5D574C) to Color(0xFFF5F2E9),
                Color(0xFFFFF8E7) to Color(0xFF8B6D2B),
                Color(0xFF0C7466) to Color(0xFFFFFCF5),
                Color(0xFFB03A14) to Color(0xFFFFFCF5),
                Color(0xFF4C6378) to Color(0xFFFFFCF5),
                Color(0xFF6F4A9E) to Color(0xFFFFFCF5),
                Color(0xFF875A15) to Color(0xFFFFFCF5),
            )
        pairs.forEach { (foreground, background) ->
            assertTrue("${foreground.value.toString(16)} on ${background.value.toString(16)}", contrast(foreground, background) >= 4.5)
        }
    }

    private fun contrast(
        first: Color,
        second: Color,
    ): Double {
        val l1 = luminance(first)
        val l2 = luminance(second)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double =
            if (value <= 0.04045f) {
                (value / 12.92f).toDouble()
            } else {
                Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4)
            }
        return channel(color.red) * 0.2126 + channel(color.green) * 0.7152 + channel(color.blue) * 0.0722
    }
}
