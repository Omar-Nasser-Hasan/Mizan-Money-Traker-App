// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import app.mizan.domain.stats.Period
import app.mizan.domain.stats.StatKind
import app.mizan.domain.stats.StatRecord
import app.mizan.domain.stats.StatsEngine
import java.time.LocalDate

@Composable
fun statsScreen(repository: MizanRepository, modifier: Modifier) {
    var tab by remember { mutableStateOf("Cash-flow") }
    val rows by repository.observeStatsRows().collectAsStateWithLifecycle(emptyList())
    val today = LocalDate.now()
    val period = Period(today.withDayOfMonth(1), today)
    val records = rows.map { StatRecord(it.id, LocalDate.parse(it.bookedDate), runCatching { StatKind.valueOf(it.kind) }.getOrDefault(StatKind.OTHER), it.baseMinor, it.accountId, it.categoryId, it.payeeId) }
    val cashFlow = StatsEngine.cashFlow(records, period)
    val categories = StatsEngine.byCategory(records, period)
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium)
        Text("This month · ${period.start} to ${period.end}")
        listOf("Balance", "Cash-flow", "Spending", "Reports").forEach { option -> FilterChip(selected = tab == option, onClick = { tab = option }, label = { Text(option) }) }
        when (tab) {
            "Cash-flow" -> {
                Text("Income"); amountText(cashFlow.incomeMinor, "EGP")
                Text("Expenses"); amountText(cashFlow.expenseMinor, "EGP")
                Text("Net"); amountText(cashFlow.netMinor, "EGP")
            }
            "Spending" -> LazyColumn { items(categories.toList()) { (category, total) -> Text("$category · ${total / 100}.${(total % 100).toString().padStart(2, '0')} EGP", Modifier.padding(8.dp)) } }
            "Reports" -> Text("Income: ${cashFlow.incomeMinor / 100} EGP · Expenses: ${cashFlow.expenseMinor / 100} EGP · Net: ${cashFlow.netMinor / 100} EGP")
            else -> Text("Balance trend and account allocation are available after account balance history is calculated.")
        }
    }
}
