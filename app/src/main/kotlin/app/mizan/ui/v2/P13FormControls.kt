// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:function-naming")

package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mizan.R

@Composable
fun P13Choice(
    label: String,
    selected: String?,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.wrapContentSize()) {
        TextButton(onClick = { expanded = true }) {
            Text("$label · ${options.firstOrNull { it.first == selected }?.second.orEmpty()}")
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

val P13Palette =
    listOf(
        "#0785DB",
        "#059093",
        "#717BCB",
        "#9A6EC2",
        "#DE6C64",
        "#D89A42",
        "#3A924F",
        "#C45C8C",
        "#537A9A",
        "#9C754B",
        "#B5B44E",
        "#7D8291",
    )

@Composable
fun P13ColorPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    P13Palette.chunked(6).forEach { row ->
        Row {
            row.forEach { tone ->
                val label = stringResource(R.string.p13_color, P13Palette.indexOf(tone) + 1)
                Box(
                    Modifier.size(48.dp).clickable { onSelect(tone) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(32.dp).background(Color(android.graphics.Color.parseColor(tone)), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected == tone) MzIcon("check", label)
                    }
                }
            }
        }
    }
}
