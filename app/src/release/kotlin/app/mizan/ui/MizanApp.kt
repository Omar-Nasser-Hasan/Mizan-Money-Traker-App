// Copyright (c) 2026 Mizan contributors
package app.mizan.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mizan.R

private data class Tab(
    @param:StringRes val label: Int,
    val symbol: String,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
fun MizanApp() {
    val tabs =
        listOf(
            Tab(R.string.tab_home, "⌂"),
            Tab(R.string.tab_records, "≡"),
            Tab(R.string.tab_plan, "□"),
            Tab(R.string.tab_insights, "⌁"),
            Tab(R.string.tab_more, "⋯"),
        )
    var selectedTab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(tabs[selectedTab].label)) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(tab.symbol) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        EmptyTab(stringResource(tabs[selectedTab].label), padding)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun EmptyTab(
    label: String,
    padding: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.empty_tab), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
