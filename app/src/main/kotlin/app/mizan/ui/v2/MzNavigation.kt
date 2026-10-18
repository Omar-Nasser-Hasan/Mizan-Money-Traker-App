// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MzNavItem(
    val label: String,
    val icon: String,
)

val MzRootNavItems =
    listOf(
        MzNavItem("Home", "account_balance_wallet"),
        MzNavItem("Records", "receipt_long"),
        MzNavItem("Stats", "monitoring"),
        MzNavItem("Plan", "pie_chart"),
        MzNavItem("Wealth", "savings"),
    )

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val tokens = LocalMzTokens.current
    Surface(modifier = modifier.fillMaxWidth(), color = tokens.canvas, contentColor = tokens.text) {
        Row(
            modifier = Modifier.height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(Modifier.width(12.dp))
            }
            Text(title, modifier = Modifier.weight(1f), style = MzTypography.title)
            actions()
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzBottomNav(
    selectedLabel: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    items: List<MzNavItem> = MzRootNavItems,
) {
    val tokens = LocalMzTokens.current
    Surface(modifier = modifier.fillMaxWidth(), color = tokens.surface, contentColor = tokens.text) {
        Column {
            HorizontalDivider(color = tokens.outline.copy(alpha = 0.4f), thickness = 1.dp)
            Row(
                modifier = Modifier.height(72.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.label == selectedLabel
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(MzShapes.utility)
                                .clickable(role = Role.Tab) { onSelected(item.label) }
                                .semantics { contentDescription = item.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        MzIcon(item.icon, item.label, tint = if (selected) tokens.accentText else tokens.textDim, size = 22.sp)
                        Text(
                            item.label.uppercase(),
                            color = if (selected) tokens.accentText else tokens.textDim,
                            style = MzTypography.labelCaps.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        )
                        if (selected) {
                            Box(modifier = Modifier.padding(top = 2.dp).size(4.dp).background(tokens.champagne, CircleShape))
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Add record",
) {
    val tokens = LocalMzTokens.current
    Surface(
        modifier = modifier.size(60.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = tokens.champagne,
        contentColor = tokens.onChampagne,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MzIcon("add", contentDescription, tint = tokens.onChampagne, size = 28.sp)
        }
    }
}
