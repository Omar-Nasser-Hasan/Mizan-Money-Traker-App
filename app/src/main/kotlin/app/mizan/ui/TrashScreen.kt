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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mizan.data.repository.MizanRepository
import kotlinx.coroutines.launch

@Composable
fun trashScreen(
    repository: MizanRepository,
    modifier: Modifier,
) {
    val records by repository.observeTrash().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Trash") }
        item { Text("Deleted records stay here for 30 days, then are removed automatically.") }
        if (records.isEmpty()) item { Text("Trash is empty.") }
        items(records, key = { it.id }) { record ->
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("${record.bookedDate} · ${record.kind}")
                Text(record.note.ifBlank { "No note" })
                Button(onClick = { scope.launch { repository.restoreRecord(record.id) } }) { Text("Restore") }
            }
        }
    }
}
