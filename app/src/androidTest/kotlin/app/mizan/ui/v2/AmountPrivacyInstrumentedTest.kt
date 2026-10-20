// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AmountPrivacyInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chart_legends_centers_and_accessibility_values_follow_the_eye_toggle() {
        val hidden = mutableStateOf(true)
        val slices =
            listOf(
                MzDonutSlice("Income", 630_000, Color.Yellow),
                MzDonutSlice("Expenses", 450_000, Color.Red),
            )
        compose.setContent {
            MizanTheme {
                Column {
                    MzCompositionBar(slices, hideAmounts = hidden.value)
                    MzDonut(slices, centerText = "EGP 10,800.00", changeText = "+58%", hideAmounts = hidden.value)
                }
            }
        }

        fun assertPrivate() {
            listOf("6,300.00", "4,500.00", "10,800.00", "58%", "41%").forEach { value ->
                compose.onAllNodesWithText(value, substring = true, useUnmergedTree = true).assertCountEquals(0)
                compose.onAllNodesWithContentDescription(value, substring = true, useUnmergedTree = true).assertCountEquals(0)
            }
            assertTrue(compose.onAllNodesWithText("Income", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        assertPrivate()
        compose.runOnIdle { hidden.value = false }
        assertTrue(compose.onAllNodesWithText("6,300.00", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("EGP 10,800.00", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { hidden.value = true }
        assertPrivate()
    }
}
