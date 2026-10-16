// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.IncomeSourceEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch
import java.util.UUID

private data class StarterAccount(
    val name: String,
    val color: String,
    val amount: String = "0",
)

@Composable
fun onboardingScreen(
    repository: MizanRepository,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    val accounts =
        remember {
            mutableStateListOf(
                StarterAccount("Essential", "#F2B84B"),
                StarterAccount("Fun", "#A78BFA"),
                StarterAccount("Investment", "#34D399"),
            )
        }
    val expenseCategories by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomeCategories by repository.observeCategories("INCOME").collectAsStateWithLifecycle(emptyList())
    val selectedCategories = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) { repository.seedReferenceData() }
    LaunchedEffect(expenseCategories, incomeCategories) {
        if (selectedCategories.isEmpty()) selectedCategories += (expenseCategories + incomeCategories).map { it.id }
    }
    when (step) {
        0 -> {
            LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Welcome to Mizan", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Start with accounts, their currencies, and opening balances. You can edit all of these later.") }
                items(accounts.indices.toList(), key = { it }) { index ->
                    val account = accounts[index]
                    OutlinedTextField(
                        value = account.name,
                        onValueChange = { accounts[index] = account.copy(name = it) },
                        label = { Text("Account name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = account.amount,
                        onValueChange = { accounts[index] = account.copy(amount = it) },
                        label = { Text("EGP opening balance") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") } }
            }
        }

        1 -> {
            Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Default income split", style = MaterialTheme.typography.headlineMedium)
                Text("Your default sends 10% to Essential, 40% to Fun, and 50% to Investment. You can change it later.")
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Use 10 / 40 / 50") }
            }
        }

        2 -> {
            LazyColumn(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("Choose categories", style = MaterialTheme.typography.headlineMedium) }
                item { Text("Untick a parent or individual category to hide it from entry. Everything remains editable later.") }
                items(expenseCategories + incomeCategories, key = { it.id }) { category ->
                    androidx.compose.foundation.layout.Row {
                        Checkbox(
                            checked = category.id in selectedCategories,
                            onCheckedChange = { checked ->
                                if (checked) selectedCategories += category.id else selectedCategories.remove(category.id)
                            },
                        )
                        Text(category.name, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                item { Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") } }
            }
        }

        else -> {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App lock", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Lock setup is ready for the security milestone. It will use your device biometric or screen lock; no Mizan PIN is created.",
                )
                Button(onClick = {
                    scope.launch {
                        val created =
                            accounts.mapIndexed { index, account ->
                                repository.saveAccount(
                                    name = account.name.ifBlank { "Account ${index + 1}" },
                                    type = "CASH",
                                    color = account.color,
                                    icon = "wallet",
                                    currency = "EGP",
                                    openingMinor = amountToMinor(account.amount, 2),
                                )
                            }
                        val templateId = UUID.randomUUID().toString()
                        repository.saveSplitTemplate(
                            SplitTemplateEntity(templateId, "Default income split", created.first()),
                            created.mapIndexed { index, accountId ->
                                SplitLineEntity(
                                    UUID.randomUUID().toString(),
                                    templateId,
                                    accountId,
                                    "PERCENT",
                                    listOf(1_000L, 4_000L, 5_000L)[index],
                                    index,
                                )
                            },
                        )
                        repository.saveIncomeSource(
                            IncomeSourceEntity(UUID.randomUUID().toString(), "Default income", templateId, "salary"),
                        )
                        (expenseCategories + incomeCategories)
                            .filter {
                                it.id !in selectedCategories &&
                                    it.id != MizanRepository.UNCATEGORIZED_ID
                            }.forEach {
                                repository.archiveCategory(it.id, true)
                            }
                        onDone()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Finish setup") }
            }
        }
    }
}
