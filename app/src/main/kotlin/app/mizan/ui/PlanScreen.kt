// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.BudgetEntity
import app.mizan.data.db.DebtEntity
import app.mizan.data.db.PlannedPaymentEntity
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun planScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    var section by remember { mutableStateOf("Budgets") }
    val scope = rememberCoroutineScope()
    val budgets by repository.observeBudgets().collectAsStateWithLifecycle(emptyList())
    val planned by repository.observePlannedPayments().collectAsStateWithLifecycle(emptyList())
    val debts by repository.observeDebts().collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Plan", style = MaterialTheme.typography.headlineMedium)
        listOf("Budgets", "Planned", "Debts").forEach { option ->
            FilterChip(selected = section == option, onClick = { section = option }, label = { Text(option) })
        }
        OutlinedTextField(name, { name = it }, label = {
            Text(
                if (section ==
                    "Debts"
                ) {
                    "Person"
                } else {
                    "Name"
                },
            )
        }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (EGP)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val minor = amountToMinor(amount, 2)
            val account = accounts.firstOrNull() ?: return@Button
            if (name.isBlank() || minor <= 0) return@Button
            scope.launch {
                when (section) {
                    "Budgets" -> {
                        repository.saveBudget(
                            BudgetEntity(UUID.randomUUID().toString(), name, "ALL", "", "MONTHLY", limitMinor = minor, rollover = "NONE"),
                        )
                    }

                    "Planned" -> {
                        repository.savePlannedPayment(
                            PlannedPaymentEntity(
                                UUID.randomUUID().toString(),
                                "EXPENSE",
                                name,
                                accountId = account.id,
                                currency = "EGP",
                                amountMinor = minor,
                                paymentType = "CASH",
                                frequency = "MONTHLY",
                                interval = 1,
                                startDate = LocalDate.now().toString(),
                                remindDaysBefore = 1,
                            ),
                        )
                    }

                    else -> {
                        repository.saveDebt(
                            DebtEntity(UUID.randomUUID().toString(), name, "LENT", "EGP", minor, LocalDate.now().toString()),
                        )
                    }
                }
                name = ""
                amount = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
        LazyColumn(Modifier.fillMaxSize()) {
            when (section) {
                "Budgets" -> {
                    items(budgets) { budget ->
                        Column(Modifier.padding(8.dp)) {
                            Text("${budget.name} · ${budget.period}")
                            amountText(budget.limitMinor, "EGP")
                        }
                    }
                }

                "Planned" -> {
                    items(
                        planned,
                    ) { payment ->
                        Column(Modifier.padding(8.dp)) {
                            Text("${payment.name} · ${payment.frequency}")
                            amountText(payment.amountMinor, payment.currency)
                        }
                    }
                }

                else -> {
                    items(debts) { debt ->
                        Column(Modifier.padding(8.dp)) {
                            Text("${debt.person} · ${debt.direction}")
                            amountText(debt.principalMinor, debt.currency)
                        }
                    }
                }
            }
        }
    }
}
