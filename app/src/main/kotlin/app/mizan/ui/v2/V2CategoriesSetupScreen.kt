// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.mizan.R
import app.mizan.data.db.CategoryEntity
import app.mizan.data.repository.MizanRepository

@Composable
fun V2CategoriesSetupScreen(
    categories: List<CategoryEntity>,
    selection: Set<String>?,
    onChange: (Set<String>) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf("food-drinks")) }
    val selected = selection ?: V2CategorySelection.all(categories)
    val roots =
        remember(categories, query) {
            categories.filter { root ->
                val branch = V2CategorySelection.branch(root.id, categories)
                val matches = categories.any { it.id in branch && it.name.contains(query, true) }
                root.parentId == null && (query.isBlank() || matches)
            }
        }
    V2SetupFrame(
        3,
        stringResource(R.string.setup_categories_title),
        stringResource(R.string.setup_categories_description),
        onBack,
        onContinue,
        onSkip,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.setup_categories_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.setup_selected, selected.size), Modifier.weight(1f), style = MzTypography.bodySmall)
                    TextButton(onClick = { onChange(V2CategorySelection.all(categories)) }) {
                        Text(stringResource(R.string.setup_select_all))
                    }
                    TextButton(onClick = { onChange(V2CategorySelection.clear()) }) { Text(stringResource(R.string.setup_clear)) }
                }
            }
            if (categories.isEmpty()) {
                items(4) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
            } else if (roots.isEmpty()) {
                item {
                    MzEmptyState(
                        stringResource(R.string.setup_no_categories),
                        stringResource(R.string.setup_no_categories_description),
                        stringResource(R.string.setup_clear),
                        { query = "" },
                    )
                }
            }
            items(roots, key = CategoryEntity::id) { parent ->
                val children = categories.filter { it.parentId == parent.id }
                val members = V2CategorySelection.branch(parent.id, categories)
                val state =
                    when {
                        selected.containsAll(members) -> ToggleableState.On
                        members.any { it in selected } -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    }
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(
                                state,
                                onClick = { onChange(V2CategorySelection.toggleBranch(parent.id, selected, categories)) },
                                enabled = parent.id != MizanRepository.UNCATEGORIZED_ID,
                            )
                            MzIcon(parent.icon, parent.name, tint = LocalMzTokens.current.champagne)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(parent.name, style = MzTypography.body)
                                if (children.isNotEmpty()) {
                                    Text(
                                        stringResource(R.string.setup_child_count, children.count { it.id in selected }, children.size),
                                        style = MzTypography.bodySmall,
                                        color = LocalMzTokens.current.textDim,
                                    )
                                }
                            }
                            if (children.isNotEmpty()) {
                                IconButton(onClick = {
                                    expanded = if (parent.id in expanded) expanded - parent.id else expanded + parent.id
                                }) { MzIcon(if (parent.id in expanded) "expand_less" else "expand_more", parent.name) }
                            }
                        }
                        if (parent.id in expanded || query.isNotBlank()) {
                            children.forEach { child ->
                                Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(child.name, Modifier.weight(1f), style = MzTypography.bodySmall)
                                    Checkbox(
                                        child.id in selected,
                                        { onChange(V2CategorySelection.toggleLeaf(child.id, selected, categories)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
