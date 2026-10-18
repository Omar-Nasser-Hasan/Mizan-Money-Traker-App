// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.mizan.ui.v2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mizan.data.db.AccountEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun V2PeriodSelectorSheet(
    initialPeriod: PeriodState,
    accounts: List<AccountEntity>,
    selectedAccountIds: Set<String>,
    onApply: (PeriodState, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
    var kind by remember(initialPeriod) { mutableStateOf(initialPeriod.kind) }
    var selectedMonth by remember(initialPeriod) { mutableStateOf(YearMonth.from(initialPeriod.start ?: today)) }
    var year by remember(initialPeriod) { mutableStateOf((initialPeriod.start ?: today).year) }
    var startText by remember(initialPeriod) { mutableStateOf(initialPeriod.start?.toString().orEmpty()) }
    var endText by remember(initialPeriod) { mutableStateOf(initialPeriod.end?.toString().orEmpty()) }
    var checkedAccounts by remember(selectedAccountIds) { mutableStateOf(selectedAccountIds) }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMM") }

    MzSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Select period", style = MzTypography.title)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PeriodKind.entries.forEach { option ->
                    MzChip(
                        option.name.lowercase().replaceFirstChar(Char::uppercase),
                        kind == option,
                        {
                            kind = option
                            if (option == PeriodKind.DAY || option == PeriodKind.WEEK) {
                                startText = (initialPeriod.start ?: today).toString()
                                endText = ""
                            }
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MzChip("This month", false, {
                    kind = PeriodKind.CUSTOM
                    selectedMonth = YearMonth.from(today)
                    startText =
                        today.withDayOfMonth(1).toString()
                    endText = today.withDayOfMonth(today.lengthOfMonth()).toString()
                })
                MzChip("Last month", false, {
                    val last = YearMonth.from(today).minusMonths(1)
                    kind = PeriodKind.CUSTOM
                    selectedMonth =
                        last
                    startText = last.atDay(1).toString()
                    endText = last.atEndOfMonth().toString()
                })
                MzChip("Last 30 days", false, {
                    kind = PeriodKind.CUSTOM
                    startText = today.minusDays(29).toString()
                    endText =
                        today.toString()
                })
                MzChip("Year to date", false, {
                    kind = PeriodKind.CUSTOM
                    startText = today.withDayOfYear(1).toString()
                    endText =
                        today.toString()
                })
            }
            if (kind == PeriodKind.YEAR) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (year - 4..year + 1).forEach { value ->
                        MzChip(value.toString(), value == year, {
                            year = value
                            kind = PeriodKind.CUSTOM
                            startText = LocalDate.of(value, 1, 1).toString()
                            endText = LocalDate.of(value, 12, 31).toString()
                        })
                    }
                }
            }
            if (kind == PeriodKind.MONTH || kind == PeriodKind.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    MzChip("‹", false, {
                        selectedMonth = selectedMonth.minusYears(1)
                        year = selectedMonth.year
                    })
                    Text("${selectedMonth.year}", style = MzTypography.body)
                    MzChip("›", false, {
                        selectedMonth = selectedMonth.plusYears(1)
                        year = selectedMonth.year
                    })
                }
                (1..12).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { month ->
                            val value = YearMonth.of(year, month)
                            MzChip(value.format(monthFormat), selectedMonth == value, {
                                selectedMonth = value
                                kind = PeriodKind.CUSTOM
                                startText = value.atDay(1).toString()
                                endText = value.atEndOfMonth().toString()
                            })
                        }
                    }
                }
            }
            if (kind == PeriodKind.CUSTOM || kind == PeriodKind.DAY || kind == PeriodKind.WEEK) {
                Text(
                    if (kind ==
                        PeriodKind.CUSTOM
                    ) {
                        "Custom dates · YYYY-MM-DD"
                    } else {
                        "Anchor date · YYYY-MM-DD"
                    },
                    style = MzTypography.labelCaps,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzInput(startText, {
                        startText = it
                    }, if (kind == PeriodKind.CUSTOM) "From" else "Date", Modifier.weight(1f))
                    if (kind == PeriodKind.CUSTOM) {
                        MzInput(endText, { endText = it }, "To", Modifier.weight(1f))
                    } else {
                        Text(if (kind == PeriodKind.WEEK) "Saturday-start week" else "Selected day", style = MzTypography.bodySmall)
                    }
                }
            }
            Text("Accounts", style = MzTypography.labelCaps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MzChip("All accounts", checkedAccounts.isEmpty(), { checkedAccounts = emptySet() })
                accounts.forEach { account ->
                    val checked = account.id in checkedAccounts
                    MzChip(account.name, checked, {
                        checkedAccounts = if (checked) checkedAccounts - account.id else checkedAccounts + account.id
                    })
                }
            }
            MzButton(
                "Apply",
                {
                    val selected =
                        when (kind) {
                            PeriodKind.CUSTOM -> {
                                val start = startText.toDateOrNull()
                                val end = endText.toDateOrNull()
                                if (start != null && end != null &&
                                    !start.isAfter(end)
                                ) {
                                    PeriodState(PeriodKind.CUSTOM, start, end)
                                } else {
                                    null
                                }
                            }

                            PeriodKind.DAY, PeriodKind.WEEK -> {
                                startText.toDateOrNull()?.let { PeriodState(kind, it) }
                            }

                            else -> {
                                PeriodState(kind)
                            }
                        }
                    if (selected != null) onApply(selected, checkedAccounts)
                },
                Modifier.fillMaxWidth(),
                enabled =
                    when (kind) {
                        PeriodKind.CUSTOM -> startText.toDateOrNull() != null && endText.toDateOrNull() != null
                        PeriodKind.DAY, PeriodKind.WEEK -> startText.toDateOrNull() != null
                        else -> true
                    },
            )
        }
    }
}

private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
