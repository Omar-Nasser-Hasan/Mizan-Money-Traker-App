// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:max-line-length")

package app.mizan.ui.v2

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.RecordRow
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun V2RecordDetailScreen(
    repository: MizanRepository,
    transactionId: String,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDuplicated: (String) -> Unit,
) {
    val rows by remember(repository, transactionId) { repository.observeRecordRows(transactionId) }.collectAsStateWithLifecycle(emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmReconciledEdit by remember(transactionId) { mutableStateOf(false) }
    var zoomedUri by remember { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    repository.addRecordAttachments(transactionId, uris.map(Uri::toString))
                }
            }
        }
    val first = rows.firstOrNull()
    if (first == null) {
        MzEmptyState(
            title = "Record unavailable",
            description = "This record may have been deleted.",
            actionLabel = "Back",
            onAction = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val outgoingTransferLeg = rows.firstOrNull { it.amountMinor < 0 }
    val headlineAmount = if (first.kind == "TRANSFER") outgoingTransferLeg?.amountMinor ?: 0L else rows.sumOf(RecordRow::amountMinor)
    val headlineCurrency = if (first.kind == "TRANSFER") outgoingTransferLeg?.currency ?: first.currency else first.currency
    val kindTitle =
        when {
            first.kind == "EXPENSE" -> "Expense"
            first.kind == "INCOME" -> "Income"
            first.kind == "TRANSFER" -> "Transfer"
            first.kind.startsWith("ASSET_") -> "Invested"
            first.kind.startsWith("DEBT_") -> "Debt"
            else -> first.kind.replace('_', ' ')
        }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
            verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text(first.categoryName ?: first.payeeName ?: kindTitle, style = MzTypography.title)
                    Text(
                        "$kindTitle · ${first.bookedDate} · ${first.status.lowercase()}",
                        color = LocalMzTokens.current.textDim,
                        style = MzTypography.bodySmall,
                    )
                    if (first.status == "UNCLEARED") MzChip("Uncleared", true, {})
                    if (first.kind.startsWith("ASSET_")) MzChip("Invested", true, {})
                    AmountText(
                        headlineAmount,
                        headlineCurrency,
                        kindAmountStyle(first),
                        hideAmounts,
                        textStyle = MzTypography.hero,
                        showCurrency = true,
                    )
                }
            }
            if (first.kind == "TRANSFER") {
                item {
                    val sent = rows.firstOrNull { it.amountMinor < 0 }
                    val received = rows.firstOrNull { it.amountMinor > 0 }
                    if (sent != null && received != null) {
                        val rate = impliedRate(sent, received)
                        MzCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
                            Column(Modifier.padding(MzSpacing.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("From · ${sent.accountName}", style = MzTypography.body)
                                AmountText(sent.amountMinor, sent.currency, AmountStyle.TRANSFER, hideAmounts)
                                Text("To · ${received.accountName}", style = MzTypography.body)
                                AmountText(received.amountMinor, received.currency, AmountStyle.TRANSFER, hideAmounts)
                                Text(
                                    "Implied rate · 1 ${sent.currency} = $rate ${received.currency}",
                                    color = LocalMzTokens.current.textDim,
                                    style = MzTypography.bodySmall,
                                )
                            }
                        }
                    }
                }
            } else if (first.kind == "INCOME" && rows.size > 1) {
                item { Text("Income allocations", style = MzTypography.labelCaps) }
                items(rows, key = RecordRow::entryId) { row -> RecordLeg(row, hideAmounts, "Allocation", onEdit) }
            } else {
                items(
                    rows,
                    key = RecordRow::entryId,
                ) { row -> RecordLeg(row, hideAmounts, if (row.kind.startsWith("ASSET_")) "Invested" else null, onEdit) }
            }
            if (first.kind == "TRANSFER") {
                items(rows.filter { it.kind != "TRANSFER" }, key = RecordRow::entryId) { row -> RecordLeg(row, hideAmounts, null, onEdit) }
            }
            first.note.takeIf(String::isNotBlank)?.let { note ->
                item {
                    MzCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(MzSpacing.gutter)) {
                            Text("Note", style = MzTypography.labelCaps)
                            Text(note, style = MzTypography.body)
                        }
                    }
                }
            }
            first.labels.takeIf(String::isNotBlank)?.let { labels ->
                item { Text("Labels · ${labels.replace('|', '·')}", style = MzTypography.bodySmall) }
            }
            item {
                Text("Attachments", style = MzTypography.labelCaps)
                val attachments = first.attachments.split('|').filter(String::isNotBlank)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(attachments, key = { it }) { uri ->
                        RecordAttachment(uri, Modifier.size(96.dp).clickable { zoomedUri = uri })
                    }
                    item {
                        MzChip("+ Add receipt", false, {
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                    }
                }
            }
            item {
                if (first.status == "RECONCILED" && !confirmReconciledEdit) {
                    MzButton("Edit reconciled record", { confirmReconciledEdit = true }, Modifier.fillMaxWidth(), MzButtonStyle.SECONDARY)
                } else {
                    MzButton(
                        if (first.status ==
                            "RECONCILED"
                        ) {
                            "Confirm edit reconciled record"
                        } else {
                            "Edit record"
                        },
                        onEdit,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MzButton("Duplicate record", {
                        scope.launch { repository.duplicateRecord(transactionId)?.let(onDuplicated) }
                    }, Modifier.weight(1f), MzButtonStyle.SECONDARY)
                    MzButton("Delete", {
                        scope.launch {
                            repository.deleteRecord(transactionId)
                            val result = snackbar.showSnackbar("Record deleted", actionLabel = "Undo")
                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                repository.restoreRecord(transactionId)
                            } else {
                                onBack()
                            }
                        }
                    }, Modifier.weight(1f), MzButtonStyle.SECONDARY)
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    zoomedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { zoomedUri = null },
            confirmButton = { TextButton(onClick = { zoomedUri = null }) { Text("Close") } },
            text = { RecordAttachment(uri, Modifier.fillMaxWidth().height(480.dp)) },
        )
    }
}

@Composable
private fun RecordLeg(
    row: RecordRow,
    hideAmounts: Boolean,
    tag: String?,
    onEdit: () -> Unit,
) {
    val style = if (row.kind == "TRANSFER") AmountStyle.TRANSFER else kindAmountStyle(row)
    MzCard(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(MzSpacing.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.accountName, style = MzTypography.body)
            AmountText(row.amountMinor, row.currency, style, hideAmounts, showCurrency = row.currency != "EGP")
            Text("Booked at ${row.rateToBase} EGP per unit", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
            tag?.let { MzChip(it, true, {}) }
        }
    }
}

@Composable
private fun RecordAttachment(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri)))
                }.getOrNull()
            }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = "Receipt image", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        MzCard(modifier) { Text("Receipt", modifier = Modifier.padding(12.dp), color = LocalMzTokens.current.textDim) }
    }
}

private fun kindAmountStyle(row: RecordRow): AmountStyle =
    when {
        row.kind == "TRANSFER" -> AmountStyle.TRANSFER
        row.kind.startsWith("ASSET_") -> AmountStyle.INVESTMENT
        row.amountMinor < 0L -> AmountStyle.EXPENSE
        else -> AmountStyle.INCOME
    }

private fun impliedRate(
    sent: RecordRow,
    received: RecordRow,
): String {
    val sourceDecimals = currencyDecimals(sent.currency)
    val targetDecimals = currencyDecimals(received.currency)
    val sourceMajor = BigDecimal.valueOf(kotlin.math.abs(sent.amountMinor)).movePointLeft(sourceDecimals)
    val receivedMajor = BigDecimal.valueOf(kotlin.math.abs(received.amountMinor)).movePointLeft(targetDecimals)
    if (sourceMajor.signum() == 0) return "—"
    return receivedMajor.divide(sourceMajor, 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

private fun currencyDecimals(currency: String): Int =
    when (currency) {
        "KWD" -> 3
        "JPY" -> 0
        else -> 2
    }
