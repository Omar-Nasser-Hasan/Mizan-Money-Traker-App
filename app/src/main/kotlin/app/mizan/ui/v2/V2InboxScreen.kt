// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.AccountEntity
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun V2InboxScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    lastAccountId: String?,
    onConfirm: (InboxDraftEntity) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val drafts by repository.observeInboxDrafts().collectAsStateWithLifecycle(emptyList())
    val accounts by repository.observeAccounts().collectAsStateWithLifecycle(emptyList())
    val payees by repository.observePayees().collectAsStateWithLifecycle(emptyList())
    val expenses by repository.observeCategories("EXPENSE").collectAsStateWithLifecycle(emptyList())
    val incomes by repository.observeCategories("INCOME").collectAsStateWithLifecycle(emptyList())
    val learnedCategories by repository.observeLearnedPayeeCategories().collectAsStateWithLifecycle(emptyMap())
    val categoryNames = remember(expenses, incomes) { (expenses + incomes).associate { it.id to it.name } }
    var duplicateIds by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()
    val suggestedAccount = accounts.firstOrNull { it.id == lastAccountId } ?: accounts.firstOrNull { !it.archived }
    LaunchedEffect(drafts, payees, suggestedAccount) {
        duplicateIds =
            withContext(Dispatchers.IO) {
                drafts
                    .filter { draft ->
                        val payeeId = payees.firstOrNull { it.name.equals(draft.merchant, true) }?.id
                        suggestedAccount != null &&
                            repository.isPossibleDuplicate(
                                suggestedAccount.id,
                                draft.amountMinor,
                                payeeId,
                                Instant.ofEpochMilli(draft.occurredAt),
                                draft.currency,
                            )
                    }.map { it.id }
                    .toSet()
            }
    }
    if (drafts.isEmpty()) {
        MzEmptyState(
            "Inbox is clear",
            "SMS and notification drafts appear here. Nothing posts until you confirm it.",
            "Back",
            onBack,
            Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin),
        verticalArrangement = Arrangement.spacedBy(MzSpacing.gutter),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Inbox", style = MzTypography.title)
                Text("Review each draft before it reaches your records.", color = LocalMzTokens.current.textDim)
            }
        }
        items(drafts, key = InboxDraftEntity::id) { draft ->
            InboxDraftCard(
                draft = draft,
                account = suggestedAccount,
                suggestedCategory =
                    draft.merchant
                        ?.trim()
                        ?.lowercase()
                        ?.let(learnedCategories::get)
                        ?.let(categoryNames::get),
                possibleDuplicate = draft.id in duplicateIds,
                hideAmounts = hideAmounts,
                onConfirm = { onConfirm(draft) },
                onEdit = { onEdit(draft.id) },
                onDismiss = { scope.launch { repository.rejectInboxDraft(draft.id) } },
            )
        }
    }
}

@Composable
internal fun InboxDraftCard(
    draft: InboxDraftEntity,
    account: AccountEntity?,
    suggestedCategory: String?,
    possibleDuplicate: Boolean,
    hideAmounts: Boolean,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    MzCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MzSpacing.gutter), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(draft.merchant ?: draft.kind.lowercase().replaceFirstChar(Char::uppercase), style = MzTypography.title)
                if (possibleDuplicate) Text("Possible duplicate", color = LocalMzTokens.current.expense, style = MzTypography.bodySmall)
            }
            AmountText(
                draft.amountMinor,
                draft.currency,
                when (draft.kind) {
                    "INCOME" -> AmountStyle.INCOME
                    "TRANSFER" -> AmountStyle.TRANSFER
                    "REVERSAL" -> AmountStyle.NEUTRAL
                    else -> AmountStyle.EXPENSE
                },
                hideAmounts,
            )
            val occurred = Instant.ofEpochMilli(draft.occurredAt).atZone(java.time.ZoneId.of("Africa/Cairo"))
            Text(
                "${draft.source} · ${draft.sender} · ${occurred.toLocalDate()} ${occurred.toLocalTime().withSecond(0).withNano(0)}",
                style = MzTypography.bodySmall,
            )
            Text("Suggested category · ${suggestedCategory ?: "Choose in Entry"}", style = MzTypography.bodySmall)
            Text("Suggested account · ${account?.name ?: "Choose in Entry"}", style = MzTypography.bodySmall)
            if (draft.kind == "REVERSAL") {
                Text(
                    if (draft.linkedDraftId !=
                        null
                    ) {
                        "Reversal matched to an earlier draft"
                    } else {
                        "Possible reversal · confirm as a balance adjustment"
                    },
                    color = LocalMzTokens.current.textDim,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MzButton(if (draft.kind == "REVERSAL") "Confirm adjustment" else "Confirm", onConfirm, Modifier.weight(1f))
                MzButton("Edit", onEdit, Modifier.weight(1f), MzButtonStyle.SECONDARY)
            }
            MzButton("Dismiss", onDismiss, Modifier.fillMaxWidth(), MzButtonStyle.GHOST)
        }
    }
}
