// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun designGalleryEntry(modifier: Modifier = Modifier) {
    var modeIndex by remember { mutableIntStateOf(0) }
    val mode = listOf(MizanThemeMode.DARK, MizanThemeMode.LIGHT, MizanThemeMode.TRUE_BLACK)[modeIndex]
    MizanTheme(mode = mode) {
        val tokens = LocalMzTokens.current
        var input by remember { mutableStateOf("Mizan") }
        var toggle by remember { mutableStateOf(true) }
        var selectedChip by remember { mutableStateOf(true) }
        var datePicker by remember { mutableStateOf(false) }
        var selectedSlice by remember { mutableStateOf<Int?>(null) }
        Box(modifier = modifier.fillMaxSize().background(tokens.canvas)) {
            Column {
                MzTopBar("Design gallery")
                MzSegmented(
                    listOf("Dark", "Light", "OLED"),
                    modeIndex,
                    onSelected = { modeIndex = it },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { GalleryTitle("Actions") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MzButton("Primary", {}, Modifier.weight(1f))
                            MzButton("Secondary", {}, Modifier.weight(1f), MzButtonStyle.SECONDARY)
                        }
                    }
                    item { MzButton("Ghost action", {}, style = MzButtonStyle.GHOST) }
                    item { GalleryTitle("Filters and fields") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MzChip("Selected", selectedChip, { selectedChip = !selectedChip })
                            MzChip("Unselected", false, {})
                        }
                    }
                    item { MzInput(input, { input = it }, "Account name") }
                    item { MzToggle(toggle, { toggle = it }, "Hide amounts") }
                    item { MzSegmented(listOf("Week", "Month", "Year"), 1, {}) }
                    item { GalleryTitle("Cards and rows") }
                    item {
                        MzCard(Modifier.fillMaxWidth()) {
                            MzListRow("shopping_bag", "Groceries", "Groceries", "Today · Cash") {
                                AmountText(-12_580, "EGP", AmountStyle.EXPENSE, false)
                            }
                            MzListRow("payments", "Income", "Salary", "Yesterday · Bank") {
                                AmountText(125_800, "EGP", AmountStyle.INCOME, false)
                            }
                        }
                    }
                    item { MzBanner("Your grocery budget is nearly reached", {}) }
                    item { MzBudgetBar(0.55f) }
                    item { MzBudgetBar(0.85f) }
                    item { MzBudgetBar(1.1f) }
                    item { GalleryTitle("Charts") }
                    item { MzSparkline(listOf(12, 18, 15, 25, 22, 34), Modifier.fillMaxWidth()) }
                    item { MzAreaChart(listOf(10, 18, 14, 24, 21, 30, 29, 38)) }
                    item { MzBarChart(listOf(30, 48, 36, 52), listOf(18, 33, 24, 40)) }
                    item { MzMultiLine(listOf(10, 20, 18, 30), listOf(8, 12, 28, 24), listOf(4, 18, 14, 20)) }
                    item {
                        MzDonut(
                            listOf(
                                MzDonutSlice("Food", 45, tokens.expense),
                                MzDonutSlice("Travel", 30, tokens.warning),
                                MzDonutSlice("Other", 25, tokens.investment),
                            ),
                            centerText = "EGP 100.00",
                            selectedIndex = selectedSlice,
                            onSliceSelected = { selectedSlice = it },
                        )
                    }
                    item { MzWaterfall(listOf(50, -18, -12, 32, -28)) }
                    item { MzCompositionBar(listOf(MzDonutSlice("Cash", 40, tokens.income), MzDonutSlice("Bank", 60, tokens.transfer))) }
                    item { MzProgressRing(0.68f, "68%") }
                    item { GalleryTitle("States") }
                    item { MzSkeleton(Modifier.fillMaxWidth(), 64.dp) }
                    item { MzEmptyState("No accounts yet", "Create an account to begin tracking your money.", "Add account", {}) }
                    item { MzButton("Open date picker", { datePicker = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY) }
                    item { MzKeypad({}, Modifier.fillMaxWidth()) }
                    item { Spacer(Modifier.height(72.dp)) }
                }
                MzBottomNav("Home", {})
            }
            MzFab({}, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 84.dp))
        }
        if (datePicker) {
            MzDatePickerSheet(LocalDate.now(), onDismissRequest = { datePicker = false }, onDateSelected = { datePicker = false })
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun GalleryTitle(text: String) {
    Text(text.uppercase(), color = LocalMzTokens.current.textDim, style = MzTypography.labelCaps)
}
