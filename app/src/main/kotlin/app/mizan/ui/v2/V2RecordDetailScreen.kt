// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.RecordRow
import app.mizan.data.repository.MizanRepository

@Composable
fun V2RecordDetailScreen(
    repository: MizanRepository,
    transactionId: String,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
) {
    val rows by repository.observeRecordRows(transactionId).collectAsStateWithLifecycle(emptyList())
    var confirmReconciledEdit by remember(transactionId) { mutableStateOf(false) }
    val first = rows.firstOrNull()
    if (first == null) {
        MzEmptyState(
            title = "Record unavailable",
            description = "This record may have been deleted.",
            actionLabel = "Back",
            onAction = {},
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
    ) {
        item {
            Text(
                first.categoryName ?: first.payeeName ?: first.kind.replace('_', ' '),
                style = MzTypography.title,
            )
        }
        item { Text("${first.bookedDate} · ${first.status.lowercase()}", color = LocalMzTokens.current.textDim) }
        items(rows, key = RecordRow::entryId) { row -> RecordLeg(row, hideAmounts) }
        first.note.takeIf(String::isNotBlank)?.let { note -> item { Text(note, style = MzTypography.body) } }
        first.labels.takeIf(String::isNotBlank)?.let { labels -> item { Text("Labels · $labels", style = MzTypography.bodySmall) } }
        first.attachments.takeIf(String::isNotBlank)?.let { attachments ->
            item { Text("${attachments.split('|').size} receipt${if ('|' in attachments) "s" else ""}", style = MzTypography.bodySmall) }
        }
        item {
            if (first.status == "RECONCILED" && !confirmReconciledEdit) {
                MzButton(
                    "Edit reconciled record",
                    { confirmReconciledEdit = true },
                    modifier = Modifier.fillMaxWidth(),
                    style = MzButtonStyle.SECONDARY,
                )
            } else {
                MzButton(
                    if (first.status == "RECONCILED") "Confirm edit reconciled record" else "Edit record",
                    onEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (confirmReconciledEdit) {
            item {
                Text(
                    "This record is reconciled. Confirming will preserve its old version in Trash.",
                    color = LocalMzTokens.current.warning,
                    style = MzTypography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RecordLeg(
    row: RecordRow,
    hideAmounts: Boolean,
) {
    val style =
        when {
            row.kind == "TRANSFER" -> AmountStyle.TRANSFER
            row.amountMinor < 0L -> AmountStyle.EXPENSE
            else -> AmountStyle.INCOME
        }
    MzCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MzSpacing.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.accountName, style = MzTypography.body)
            AmountText(row.amountMinor, row.currency, style, hideAmounts)
            Text("Booked at ${row.rateToBase} EGP per unit", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
        }
    }
}
