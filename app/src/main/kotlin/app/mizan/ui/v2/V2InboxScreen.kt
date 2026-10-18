// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.db.InboxDraftEntity
import app.mizan.data.repository.MizanRepository

@Composable
fun V2InboxScreen(
    repository: MizanRepository,
    hideAmounts: Boolean,
    onOpen: (String) -> Unit,
) {
    val drafts by repository.observeInboxDrafts().collectAsStateWithLifecycle(emptyList())
    if (drafts.isEmpty()) {
        MzEmptyState(
            "Inbox is clear",
            "Bank messages appear here as drafts. Nothing posts automatically.",
            "Back",
            {},
            Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = MzSpacing.sideMargin)) {
        item { Text("Review bank drafts", style = MzTypography.title) }
        item { Text("Nothing posts until you review and Save.", color = LocalMzTokens.current.textDim) }
        items(drafts, key = InboxDraftEntity::id) { draft ->
            MzCard(Modifier.fillMaxWidth().padding(top = MzSpacing.gutter).clickable { onOpen(draft.id) }) {
                Column(Modifier.padding(MzSpacing.gutter)) {
                    Text(draft.merchant ?: draft.kind.lowercase().replaceFirstChar(Char::uppercase), style = MzTypography.body)
                    AmountText(draft.amountMinor, draft.currency, AmountStyle.EXPENSE, hideAmounts)
                    Text("${draft.source} · tap to review", color = LocalMzTokens.current.textDim, style = MzTypography.bodySmall)
                }
            }
        }
    }
}
