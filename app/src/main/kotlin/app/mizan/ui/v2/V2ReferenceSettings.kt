// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.R
import app.mizan.data.db.CategoryEntity
import app.mizan.data.db.LabelEntity
import app.mizan.data.repository.MizanRepository
import app.mizan.data.repository.ReferenceSettingsWriter
import app.mizan.data.settings.SettingText
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun V2ReferenceSettingsScreen(repository: MizanRepository) {
    val rows by remember(repository) { repository.observeSetupCategories() }.collectAsStateWithLifecycle(null)
    val labels by remember(repository) { repository.observeAllLabels() }.collectAsStateWithLifecycle(emptyList())
    val settings by repository.settings.snapshots.collectAsStateWithLifecycle(null)
    val order = settings?.get(SettingText.LABEL_ORDER).orEmpty().split("|")
    val orderedLabels = labels.sortedWith(compareBy({ order.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }, { it.name }))
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    V2ReferenceSettingsContent(
        rows,
        orderedLabels,
        onSaveCategory = { value ->
            scope.launch { error = runCatching { repository.saveCategory(value) }.isFailure }
        },
        onArchiveCategory = { value ->
            val snapshot = ReferenceSettingsWriter.visibilityScope(value.id, rows.orEmpty())
            scope.launch {
                error = runCatching { repository.archiveCategory(value.id, !value.archived) }.isFailure
                if (!error) {
                    undo = {
                        scope.launch { repository.referenceSettings.restoreVisibility(snapshot) }
                        undo = null
                    }
                }
            }
        },
        onMove = { ids -> scope.launch { error = runCatching { repository.referenceSettings.reorder(ids) }.isFailure } },
        onSaveLabel = { value -> scope.launch { error = runCatching { repository.saveLabel(value) }.isFailure } },
        onArchiveLabel = { value ->
            scope.launch {
                error = runCatching { repository.archiveLabel(value.id, !value.archived) }.isFailure
                if (!error) {
                    undo = {
                        scope.launch { repository.archiveLabel(value.id, value.archived) }
                        undo = null
                    }
                }
            }
        },
        onMoveLabels = { ids -> scope.launch { repository.settings.set(SettingText.LABEL_ORDER, ids.joinToString("|")) } },
        onUndo = undo,
        error = error,
    )
}

@Composable
fun V2ReferenceSettingsContent(
    categories: List<CategoryEntity>?,
    labels: List<LabelEntity>,
    onSaveCategory: (CategoryEntity) -> Unit,
    onArchiveCategory: (CategoryEntity) -> Unit,
    onMove: (List<String>) -> Unit,
    onSaveLabel: (LabelEntity) -> Unit,
    onArchiveLabel: (LabelEntity) -> Unit,
    onMoveLabels: (List<String>) -> Unit = {},
    onUndo: (() -> Unit)? = null,
    error: Boolean = false,
) {
    var tab by remember { mutableStateOf(0) }
    var archived by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var categoryForm by remember { mutableStateOf<CategoryEntity?>(null) }
    var labelForm by remember { mutableStateOf<LabelEntity?>(null) }
    val tabs = stringArrayResource(R.array.p13_category_tabs).toList()
    val kind = if (tab == 1) "INCOME" else "EXPENSE"
    val shown = remember(categories, tab, archived) { categories.orEmpty().filter { it.kind == kind && it.archived == archived } }
    val roots = if (archived) shown else shown.filter { it.parentId == null }
    V2SettingsList {
        item { MzSegmented(tabs, tab, { tab = it }) }
        item {
            Row {
                MzButton(stringResource(R.string.p13_add), {
                    if (tab == 2) {
                        labelForm = LabelEntity(UUID.randomUUID().toString(), "", P13Palette.first())
                    } else {
                        categoryForm =
                            CategoryEntity(
                                UUID.randomUUID().toString(),
                                name = "",
                                icon = "sell",
                                color = P13Palette.first(),
                                kind = kind,
                            )
                    }
                }, style = MzButtonStyle.SECONDARY)
                TextButton(onClick = { archived = !archived }) {
                    Text(stringResource(if (archived) R.string.p13_active else R.string.p13_archived))
                }
            }
        }
        if (error) item { Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense) }
        if (onUndo != null) item { MzButton(stringResource(R.string.p13_undo), onUndo, style = MzButtonStyle.GHOST) }
        if (categories == null) {
            items(4) { MzSkeleton(Modifier.fillMaxWidth(), 80.dp) }
        } else if (tab == 2) {
            val visible = labels.filter { it.archived == archived }
            if (visible.isEmpty()) {
                item {
                    MzEmptyState(
                        stringResource(R.string.p13_empty),
                        stringResource(R.string.p13_labels_hint),
                        stringResource(R.string.p13_add),
                        {
                            labelForm = LabelEntity(UUID.randomUUID().toString(), "", P13Palette.first())
                        },
                    )
                }
            }
            items(visible, key = LabelEntity::id) { label ->
                MzCard {
                    Column {
                        MzListRow("label", label.name, label.name, "", trailing = {
                            TextButton(onClick = { labelForm = label }) { Text(stringResource(R.string.p13_edit)) }
                            TextButton(onClick = { onArchiveLabel(label) }) {
                                Text(stringResource(if (archived) R.string.p13_restore else R.string.p13_archive))
                            }
                        })
                        if (!archived) {
                            Row {
                                val index = visible.indexOf(label)
                                IconButton(enabled = index > 0, onClick = {
                                    val ids = visible.map { it.id }.toMutableList()
                                    java.util.Collections.swap(ids, index, index - 1)
                                    onMoveLabels(ids)
                                }) { MzIcon("arrow_upward", stringResource(R.string.p13_move_up)) }
                                IconButton(enabled = index < visible.lastIndex, onClick = {
                                    val ids = visible.map { it.id }.toMutableList()
                                    java.util.Collections.swap(ids, index, index + 1)
                                    onMoveLabels(ids)
                                }) { MzIcon("arrow_downward", stringResource(R.string.p13_move_down)) }
                            }
                        }
                    }
                }
            }
        } else {
            if (roots.isEmpty()) {
                item {
                    MzEmptyState(
                        stringResource(R.string.p13_empty),
                        stringResource(R.string.p13_categories_hint),
                        stringResource(R.string.p13_add),
                        {
                            categoryForm =
                                CategoryEntity(
                                    UUID.randomUUID().toString(),
                                    name = "",
                                    icon = "sell",
                                    color = P13Palette.first(),
                                    kind = kind,
                                )
                        },
                    )
                }
            }
            items(roots, key = CategoryEntity::id) { parent ->
                val children = if (archived) emptyList() else shown.filter { it.parentId == parent.id }
                val branchLocked =
                    categories.orEmpty().any {
                        it.id in ReferenceSettingsWriter.branch(parent.id, categories.orEmpty()) && ReferenceSettingsWriter.locked(it)
                    }
                val locked = ReferenceSettingsWriter.locked(parent)
                MzCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (parent.isDebtService) stringResource(R.string.p13_debt_payments) else parent.name,
                                    style = MzTypography.title,
                                )
                                Text(
                                    if (locked) {
                                        stringResource(R.string.p13_linked_locked)
                                    } else {
                                        stringResource(R.string.p13_children, children.size)
                                    },
                                    style = MzTypography.bodySmall,
                                )
                            }
                            if (children.isNotEmpty()) {
                                IconButton(onClick = {
                                    expanded = if (parent.id in expanded) expanded - parent.id else expanded + parent.id
                                }) {
                                    MzIcon(if (parent.id in expanded) "expand_less" else "expand_more", parent.name)
                                }
                            }
                        }
                        Row {
                            if (!locked) {
                                TextButton(onClick = { categoryForm = parent }) {
                                    Text(stringResource(R.string.p13_edit))
                                }
                            }
                            if (!branchLocked || archived) {
                                TextButton(onClick = { onArchiveCategory(parent) }) {
                                    Text(stringResource(if (archived) R.string.p13_restore else R.string.p13_archive))
                                }
                            }
                            if (!archived) {
                                val index = roots.indexOf(parent)
                                IconButton(enabled = index > 0, onClick = {
                                    val ids = roots.map { it.id }.toMutableList()
                                    java.util.Collections.swap(ids, index, index - 1)
                                    onMove(ids)
                                }) { MzIcon("arrow_upward", stringResource(R.string.p13_move_up)) }
                                IconButton(enabled = index < roots.lastIndex, onClick = {
                                    val ids = roots.map { it.id }.toMutableList()
                                    java.util.Collections.swap(ids, index, index + 1)
                                    onMove(ids)
                                }) { MzIcon("arrow_downward", stringResource(R.string.p13_move_down)) }
                            }
                        }
                        if (parent.id in expanded) {
                            children.forEachIndexed { childIndex, child ->
                                Column {
                                    Row {
                                        Text(
                                            if (child.isDebtService) stringResource(R.string.p13_debt_payments) else child.name,
                                            Modifier.weight(1f),
                                            style = MzTypography.body,
                                        )
                                        if (ReferenceSettingsWriter.locked(child)) {
                                            MzIcon(
                                                "lock",
                                                stringResource(R.string.p13_linked_locked),
                                            )
                                        } else {
                                            TextButton(onClick = { categoryForm = child }) { Text(stringResource(R.string.p13_edit)) }
                                            TextButton(onClick = { onArchiveCategory(child) }) {
                                                Text(stringResource(R.string.p13_archive))
                                            }
                                        }
                                    }
                                    Row {
                                        IconButton(enabled = childIndex > 0, onClick = {
                                            val ids = children.map { it.id }.toMutableList()
                                            java.util.Collections.swap(ids, childIndex, childIndex - 1)
                                            onMove(ids)
                                        }) { MzIcon("arrow_upward", stringResource(R.string.p13_move_up)) }
                                        IconButton(enabled = childIndex < children.lastIndex, onClick = {
                                            val ids = children.map { it.id }.toMutableList()
                                            java.util.Collections.swap(ids, childIndex, childIndex + 1)
                                            onMove(ids)
                                        }) { MzIcon("arrow_downward", stringResource(R.string.p13_move_down)) }
                                    }
                                }
                            }
                            TextButton(onClick = {
                                categoryForm =
                                    CategoryEntity(UUID.randomUUID().toString(), parent.id, "", parent.icon, parent.color, parent.kind)
                            }) { Text(stringResource(R.string.p13_add_subcategory)) }
                        }
                    }
                }
            }
        }
    }
    categoryForm?.let { value ->
        ReferenceNameDialog(value.name, value.color, onDismiss = { categoryForm = null }, onSave = { name, color ->
            onSaveCategory(value.copy(name = name, color = color))
            categoryForm = null
        })
    }
    labelForm?.let { value ->
        ReferenceNameDialog(value.name, value.color, onDismiss = { labelForm = null }, onSave = { name, color ->
            onSaveLabel(value.copy(name = name, color = color))
            labelForm = null
        })
    }
}

@Composable
private fun ReferenceNameDialog(
    name: String,
    color: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf(name) }
    var tone by remember { mutableStateOf(color) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.p13_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.p13_name)) }, singleLine = true)
                P13ColorPicker(tone) { tone = it }
                if (invalid) Text(stringResource(R.string.p13_error), color = LocalMzTokens.current.expense)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isBlank()) invalid = true else onSave(text, tone) }) {
                Text(stringResource(R.string.p13_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.p13_cancel)) } },
    )
}
