# P15 shipped UI audit

Result: PASS; 0 forbidden mock/flag or monetary-type findings.

The numeric literals below were reviewed. Remaining literals are layout/drawing coordinates, formatter patterns, currency decimal counts, percent/basis-point scaling, specification constants (gold ratios, 80/100 alerts, 7-day windows, 85 g nisab), and empty-form defaults. No fixed mock balances, merchants, rates, dates, or counts are used as financial data.

Money stays Long minor units; rates and quantities stay BigDecimal. Float/Double references below are chart geometry, gesture positions, waveform animation, and progress fractions, after monetary values have already been calculated. They never feed ledger, valuation, or displayed-money calculations.

## Floating-point presentation references

- `app/src/main/kotlin/app/mizan/ui/v2/AmountText.kt:53`: `val availableWidth = constraints.maxWidth.toFloat()`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:174`: `val incomeHeight = (income.getOrElse(index) { 0L }.toFloat() / absolute) * (midpoint - 8.dp.toPx())`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:175`: `val expenseHeight = (expense.getOrElse(index) { 0L }.toFloat() / absolute) * (midpoint - 8.dp.toPx())`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:229`: `kotlin.math.atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble()),`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:234`: `).toFloat()`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:238`: `running += slice.value.coerceAtLeast(0L).toFloat() / total * 360f`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:254`: `val gapDegrees = 2.dp.toPx() / (diameter * 0.39f) * (180f / Math.PI.toFloat())`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:257`: `val sweep = slice.value.coerceAtLeast(0L).toFloat() / total * 360f`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:315`: `val availableWidth = constraints.maxWidth.toFloat()`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:344`: `val before = cumulative.toFloat() / maxValue`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:346`: `val after = cumulative.toFloat() / maxValue`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:377`: `val width = available * slice.value.coerceAtLeast(0L).toFloat() / total`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:416`: `progress: Float,`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:467`: `val span = (scale.max - scale.min).toFloat().coerceAtLeast(1f)`
- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:485`: `strokeWidth: Float,`
- `app/src/main/kotlin/app/mizan/ui/v2/MzControls.kt:349`: `progress: Float,`
- `app/src/main/kotlin/app/mizan/ui/v2/V2DebtScreens.kt:410`: `MzBudgetBar(if (row.debt.principalMinor > 0L) row.balance.repaymentsMinor.toFloat() / row.debt.principalMinor else 1f)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2GoalScreens.kt:212`: `MzBudgetBar(if (card.goal.targetMinor > 0) card.progress.progressMinor.toFloat() / card.goal.targetMinor else 0f)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2RecordsScreen.kt:546`: `.toFloat()`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsP8Screens.kt:178`: `val range = (maxValue - minValue).coerceAtLeast(1L).toFloat()`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsP8Screens.kt:182`: `val yFor = { value: Long -> size.height - ((value - minValue).toFloat() / range * size.height) }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsP8Screens.kt:511`: `.fillMaxWidth((ratio.toFloat() / max).coerceIn(0f, 1f))`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsP8Screens.kt:517`: `Modifier.fillMaxWidth((ceiling.toFloat() / max).coerceIn(0f, 1f)).height(18.dp),`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsScreen.kt:758`: `Spacer(Modifier.fillMaxWidth((value.toFloat() / maxOf(value, other, 1L)).coerceIn(0.02f, 1f)).height(6.dp).background(color))`
- `app/src/main/kotlin/app/mizan/ui/v2/V2VoiceReviewScreen.kt:257`: `val height = size.height * (.12f + wave.toFloat() * .8f)`

## Numeric text and defaults reviewed

- `app/src/main/kotlin/app/mizan/ui/v2/MzCharts.kt:295`: `Text("${slice.value.coerceAtLeast(0L) * 100 / total}%", color = tokens.textDim, style = MzTypography.bodySmall)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AccountScreens.kt:189`: `Text(if (card) "Owed · last 30 days" else "Balance · last 30 days", style = MzTypography.title)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AddHoldingScreen.kt:45`: `var identifier by rememberSaveable { mutableStateOf("21K") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AddHoldingScreen.kt:48`: `var karat by rememberSaveable { mutableStateOf("21K") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AssetDetailScreen.kt:58`: `var range by rememberSaveable { mutableStateOf("6M") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AssetDetailScreen.kt:252`: `detailMetrics.xirr?.let { "${it.multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP)}% p.a." } ?: "—",`
- `app/src/main/kotlin/app/mizan/ui/v2/V2AssetTradeScreen.kt:65`: `var fee by rememberSaveable { mutableStateOf("0") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2CurrenciesSettings.kt:60`: `require(percentage >= BigDecimal.ZERO && percentage <= BigDecimal("100"))`
- `app/src/main/kotlin/app/mizan/ui/v2/V2CurrenciesSettings.kt:81`: `var spread by remember { mutableStateOf("0") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PlanScreens.kt:140`: `Text("${page.percentBasisPoints / 100}% spent", style = MzTypography.title)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PlanScreens.kt:230`: `Text("Next 7 days", style = MzTypography.title)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PlanScreens.kt:584`: `var interval by rememberSaveable(paymentId) { mutableStateOf("1") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PlanScreens.kt:587`: `var lead by rememberSaveable(paymentId) { mutableStateOf("0") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PricesScreen.kt:63`: `Text("No saved 21K gold price", style = MzTypography.bodySmall, color = LocalMzTokens.current.warning)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PricesScreen.kt:89`: `label = { Text("21K buy price · EGP per gram") },`
- `app/src/main/kotlin/app/mizan/ui/v2/V2PricesScreen.kt:140`: `Text("24K = 24/21 · 18K = 18/21 · pound = 8 × 21K", style = MzTypography.bodySmall)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2StatsScreen.kt:406`: `Text("Day index · 1 to ${snapshot.daily.size}", color = tokens.textDim, style = MzTypography.bodySmall)`
- `app/src/main/kotlin/app/mizan/ui/v2/V2ZakatSettings.kt:183`: `var grams by remember { mutableStateOf("85") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2ZakatSettings.kt:184`: `var month by remember { mutableStateOf("9") }`
- `app/src/main/kotlin/app/mizan/ui/v2/V2ZakatSettings.kt:185`: `var day by remember { mutableStateOf("1") }`

## Findings

None.
