// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.mizan.domain.money.Money
import java.time.LocalDate

@Composable
fun designGalleryEntry(modifier: Modifier = Modifier) {
    var modeIndex by remember { mutableIntStateOf(0) }
    val mode = listOf(MizanThemeMode.DARK, MizanThemeMode.LIGHT, MizanThemeMode.TRUE_BLACK)[modeIndex]
    MizanTheme(mode = mode) {
        val tokens = LocalMzTokens.current
        val expenseSample = remember { Money(1_258_000, "EGP") }
        val incomeSample = remember { Money(1_258_000, "EGP") }
        LaunchedEffect(Unit) {
            Log.d("P1DesignGallery", "Expense AmountText receives $expenseSample")
            Log.d("P1DesignGallery", "Income AmountText receives $incomeSample")
        }
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
                                AmountText(
                                    expenseSample.copy(minor = -expenseSample.minor),
                                    AmountStyle.EXPENSE,
                                    false,
                                    showCurrency = false,
                                )
                            }
                            MzListRow("payments", "Income", "Salary", "Yesterday · Bank") {
                                AmountText(incomeSample, AmountStyle.INCOME, false, showCurrency = false)
                            }
                        }
                    }
                    item {
                        MzCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("NET WORTH", color = tokens.textDim, style = MzTypography.labelCaps)
                                AmountText(123_456_789, "EGP", hideAmounts = false, textStyle = MzTypography.hero)
                            }
                        }
                    }
                    item { MzBanner("Your grocery budget is nearly reached", {}) }
                    item { MzBudgetBar(0.55f) }
                    item { MzBudgetBar(0.85f) }
                    item { MzBudgetBar(1.1f) }
                    item { GalleryTitle("Charts") }
                    item {
                        MzChartCard("Sparkline", axisLabels = listOf("Jan", "Apr", "Jun")) { chartModifier ->
                            MzSparkline(listOf(12, 18, 15, 25, 22, 34), chartModifier)
                        }
                    }
                    item {
                        MzChartCard("Balance trend", axisLabels = listOf("Jan", "Apr", "Jun")) { chartModifier ->
                            MzAreaChart(listOf(10, 18, 14, 24, 21, 30, 29, 38), chartModifier)
                        }
                    }
                    item {
                        MzChartCard("Cash flow", axisLabels = listOf("Jan", "Apr", "Jun")) { chartModifier ->
                            MzBarChart(listOf(30, 48, 36, 52), listOf(18, 33, 24, 40), chartModifier)
                        }
                    }
                    item {
                        MzChartCard("Period comparison", axisLabels = listOf("Jan", "Apr", "Jun")) { chartModifier ->
                            MzMultiLine(listOf(10, 20, 18, 30), listOf(8, 12, 28, 24), listOf(4, 18, 14, 20), chartModifier)
                        }
                    }
                    item {
                        MzChartCard(
                            "Spending breakdown",
                            chartHeight = 320.dp,
                            showGrid = false,
                            showAxisLabels = false,
                        ) { chartModifier ->
                            MzDonut(
                                listOf(
                                    MzDonutSlice("Food", 4_500, MzAccountColors.all[6]),
                                    MzDonutSlice("Travel", 3_000, MzAccountColors.all[0]),
                                    MzDonutSlice("Other", 2_500, MzAccountColors.all[2]),
                                ),
                                centerText = "EGP 100.00",
                                changeText = "+12.5%",
                                selectedIndex = selectedSlice,
                                onSliceSelected = { selectedSlice = it },
                                modifier = chartModifier,
                            )
                        }
                    }
                    item {
                        MzChartCard("Monthly change", axisLabels = listOf("Jan", "Apr", "Jun")) { chartModifier ->
                            MzWaterfall(listOf(50, -18, -12, 32, -28), chartModifier)
                        }
                    }
                    item {
                        MzChartCard("Account share", chartHeight = 152.dp, showGrid = false, showAxisLabels = false) { chartModifier ->
                            MzCompositionBar(
                                listOf(MzDonutSlice("Cash", 40, tokens.income), MzDonutSlice("Bank", 60, tokens.transfer)),
                                chartModifier,
                            )
                        }
                    }
                    item {
                        MzChartCard("Budget used", chartHeight = 104.dp, showGrid = false, showAxisLabels = false) { _ ->
                            MzProgressRing(0.68f, "68%")
                        }
                    }
                    item { GalleryTitle("States") }
                    item {
                        MzCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GalleryTitle("Empty")
                                MzEmptyState(
                                    "No accounts yet",
                                    "Create an account to begin tracking your money.",
                                    "Add account",
                                    {},
                                    Modifier.padding(0.dp),
                                )
                            }
                        }
                    }
                    item {
                        MzCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GalleryTitle("Loading skeleton")
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    MzSkeleton(Modifier.size(40.dp), 40.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MzSkeleton(Modifier.fillMaxWidth(0.72f), 16.dp)
                                        MzSkeleton(Modifier.fillMaxWidth(0.48f), 12.dp)
                                    }
                                    MzSkeleton(Modifier.size(72.dp), 20.dp)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    MzSkeleton(Modifier.size(40.dp), 40.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MzSkeleton(Modifier.fillMaxWidth(0.65f), 16.dp)
                                        MzSkeleton(Modifier.fillMaxWidth(0.4f), 12.dp)
                                    }
                                    MzSkeleton(Modifier.size(60.dp), 20.dp)
                                }
                            }
                        }
                    }
                    item {
                        MzCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GalleryTitle("Error banner")
                                MzBanner("Couldn’t load this information. Try again.", {})
                            }
                        }
                    }
                    item { MzButton("Open date picker", { datePicker = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY) }
                    item { MzKeypad({}, Modifier.fillMaxWidth()) }
                    item { Spacer(Modifier.height(72.dp)) }
                }
                MzBottomNav("Home", {})
            }
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
