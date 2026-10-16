// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.SplitLineEntity
import app.mizan.data.db.SplitTemplateEntity
import app.mizan.domain.ledger.IncomeAllocation
import app.mizan.domain.ledger.IncomeSplitCalculator
import app.mizan.domain.ledger.SplitLine
import app.mizan.domain.ledger.SplitMode

private data class EditableSplitLine(
    val accountId: String,
    val mode: SplitMode,
    val value: String,
)

@Composable
fun incomeSplitSheet(
    amountMinor: Long,
    accounts: List<AccountEntity>,
    templates: List<SplitTemplateEntity>,
    selectedTemplateId: String?,
    templateLines: List<SplitLineEntity>,
    onTemplateSelected: (String?) -> Unit,
    onAllocationsChanged: (List<IncomeAllocation>) -> Unit,
) {
    val lines = remember { mutableStateListOf<EditableSplitLine>() }
    val selectedTemplate = templates.firstOrNull { it.id == selectedTemplateId }
    LaunchedEffect(selectedTemplateId, templateLines) {
        if (selectedTemplateId != null) {
            lines.clear()
            lines += templateLines.map { EditableSplitLine(it.accountId, SplitMode.valueOf(it.mode), it.value.toString()) }
        }
    }
    val parsed =
        lines.mapIndexedNotNull { index, line ->
            line.value.toLongOrNull()?.takeIf { it >= 0 }?.let {
                SplitLine("custom-$index", "custom", line.accountId, line.mode, it)
            }
        }
    val result =
        if (amountMinor > 0 && parsed.size == lines.size && parsed.isNotEmpty()) {
            runCatching { IncomeSplitCalculator.allocate(amountMinor, parsed, selectedTemplate?.remainderAccountId) }.getOrNull()
        } else {
            null
        }
    LaunchedEffect(result) { onAllocationsChanged(result?.allocations.orEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Income split")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            templates.forEach { template ->
                FilterChip(
                    selected = template.id == selectedTemplateId,
                    onClick = { onTemplateSelected(template.id) },
                    label = { Text(template.name) },
                )
            }
            FilterChip(
                selected = selectedTemplateId == null,
                onClick = {
                    onTemplateSelected(null)
                    lines.clear()
                },
                label = { Text("Custom") },
            )
        }
        Button(onClick = {
            val account = accounts.firstOrNull() ?: return@Button
            onTemplateSelected(null)
            lines.clear()
            lines += EditableSplitLine(account.id, SplitMode.PERCENT, "10000")
        }, modifier = Modifier.fillMaxWidth()) { Text("All to ${accounts.firstOrNull()?.name ?: "account"}") }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            lines.indices.forEach { index ->
                val line = lines[index]
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { account ->
                            FilterChip(
                                selected = line.accountId == account.id,
                                onClick = { lines[index] = line.copy(accountId = account.id) },
                                label = { Text(account.name) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = line.mode == SplitMode.PERCENT,
                            onClick = { lines[index] = line.copy(mode = SplitMode.PERCENT) },
                            label = { Text("Percent") },
                        )
                        FilterChip(
                            selected = line.mode == SplitMode.FIXED,
                            onClick = { lines[index] = line.copy(mode = SplitMode.FIXED) },
                            label = { Text("Fixed minor") },
                        )
                        OutlinedTextField(
                            value = line.value,
                            onValueChange = { lines[index] = line.copy(value = it) },
                            label = { Text(if (line.mode == SplitMode.PERCENT) "Basis points" else "Minor units") },
                        )
                    }
                }
            }
        }
        if (result != null) {
            Text("Remainder: ${amountMinor - result.allocations.sumOf { it.amountMinor }} minor")
            result.allocations.forEach { allocation ->
                Text("${accounts.firstOrNull { it.id == allocation.accountId }?.name}: ${allocation.amountMinor}")
            }
            if (result.fixedAmountsClamped) Text("Fixed lines were clamped to the income amount.")
        } else {
            Text("Add a valid split line to see the live remainder.")
        }
    }
}
