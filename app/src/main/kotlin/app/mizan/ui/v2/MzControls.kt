// Copyright (c) 2026 Mizan contributors
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.mizan.ui.v2

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class MzButtonStyle { PRIMARY, SECONDARY, GHOST }

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MzButtonStyle = MzButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val tokens = LocalMzTokens.current
    when (style) {
        MzButtonStyle.GHOST -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.heightIn(min = 52.dp),
                enabled = enabled,
                shape = MzShapes.exterior,
            ) {
                Text(label, color = tokens.accentText, style = MzTypography.button)
            }
        }

        else -> {
            val primary = style == MzButtonStyle.PRIMARY
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = 52.dp),
                enabled = enabled,
                shape = MzShapes.exterior,
                border = if (primary) null else BorderStroke(1.dp, tokens.outline.copy(alpha = 0.4f)),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (primary) tokens.champagne else tokens.raisedSurface,
                        contentColor = if (primary) tokens.onChampagne else tokens.text,
                        disabledContainerColor = tokens.raisedSurface,
                        disabledContentColor = tokens.textDim,
                    ),
            ) {
                Text(label, style = MzTypography.button)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clip(MzShapes.pill).clickable(role = Role.Button, onClick = onClick),
        shape = MzShapes.pill,
        color = if (selected) tokens.champagne else tokens.surface,
        contentColor = if (selected) tokens.onChampagne else tokens.textDim,
        border = if (selected) null else BorderStroke(1.dp, tokens.outline.copy(alpha = 0.4f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MzTypography.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalMzTokens.current
    Surface(
        modifier = modifier,
        shape = MzShapes.exterior,
        color = tokens.surface,
        contentColor = tokens.text,
        border = BorderStroke(1.dp, tokens.outline.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzListRow(
    iconName: String,
    iconDescription: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconColor: Color = LocalMzTokens.current.champagne,
    trailing: @Composable () -> Unit = {},
) {
    val tokens = LocalMzTokens.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MzIcon(iconName, iconDescription, tint = iconColor, size = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, maxLines = 1, style = MzTypography.body.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, maxLines = 1, color = tokens.textDim, style = MzTypography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val tokens = LocalMzTokens.current
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = tokens.textDim, style = MzTypography.labelCaps.copy(fontSize = 11.sp, letterSpacing = 0.sp))
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (singleLine) 56.dp else 88.dp)
                    .clip(MzShapes.inner)
                    .background(tokens.raisedSurface)
                    .drawBehind {
                        drawLine(
                            color = if (focused) tokens.champagne else tokens.outline.copy(alpha = 0.28f),
                            start =
                                androidx.compose.ui.geometry
                                    .Offset(12.dp.toPx(), size.height - 0.5.dp.toPx()),
                            end =
                                androidx.compose.ui.geometry
                                    .Offset(size.width - 12.dp.toPx(), size.height - 0.5.dp.toPx()),
                            strokeWidth = if (focused) 1.5.dp.toPx() else 1.dp.toPx(),
                        )
                    }.onFocusChanged { focused = it.isFocused }
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .semantics { contentDescription = label },
            enabled = enabled,
            textStyle = MzTypography.body.copy(color = tokens.text, textDirection = TextDirection.Content),
            cursorBrush = SolidColor(tokens.champagne),
            singleLine = singleLine,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Row(
        modifier = modifier.fillMaxWidth().background(tokens.lowestSurface, MzShapes.pill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = MzSpacing.touchTarget)
                        .clip(MzShapes.pill)
                        .clickable(role = Role.Tab) { onSelected(index) }
                        .semantics { this.selected = selected },
                shape = MzShapes.pill,
                color = if (selected) tokens.raisedSurface else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        option,
                        color = if (selected) tokens.text else tokens.textDim,
                        style = MzTypography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalMzTokens.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MzSpacing.touchTarget)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                }.clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = tokens.text, style = MzTypography.body)
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = tokens.onChampagne, checkedTrackColor = tokens.champagne),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzKeypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    keys: List<String> = listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", ".", "0", "⌫", "+"),
) {
    val tokens = LocalMzTokens.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val operator = key !in "0123456789"
                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(MzShapes.inner)
                                .clickable { onKey(key) },
                        shape = MzShapes.inner,
                        color = if (operator) tokens.raisedSurface else tokens.surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, color = tokens.text, style = MzTypography.keypad)
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzBudgetBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    val color =
        if (progress >= 1f) {
            tokens.expense
        } else if (progress >= 0.8f) {
            tokens.warning
        } else {
            tokens.champagne
        }
    Box(modifier = modifier.fillMaxWidth().height(6.dp).background(tokens.raisedSurface, MzShapes.pill)) {
        Box(
            modifier =
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(color, MzShapes.pill),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzBanner(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Surface(
        modifier = modifier.fillMaxWidth().clip(MzShapes.inner).clickable(onClick = onClick),
        shape = MzShapes.inner,
        color = tokens.warning.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.4f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MzIcon("warning", "Warning", tint = tokens.warning)
            Spacer(Modifier.width(8.dp))
            Text(message, color = tokens.warning, style = MzTypography.bodySmall)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzEmptyState(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMzTokens.current
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(tokens.champagne.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            MzIcon("account_balance_wallet", "Empty", tint = tokens.champagne)
        }
        Text(title, color = tokens.text, style = MzTypography.title, textAlign = TextAlign.Center)
        Text(description, color = tokens.textDim, style = MzTypography.body, textAlign = TextAlign.Center)
        MzButton(actionLabel, onAction)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzSkeleton(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val tokens = LocalMzTokens.current
    Box(modifier = modifier.height(height).background(tokens.raisedSurface, MzShapes.utility))
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalMzTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = tokens.raisedSurface,
        contentColor = tokens.text,
        dragHandle = { Box(Modifier.width(36.dp).height(4.dp).background(tokens.outline.copy(alpha = 0.6f), MzShapes.pill)) },
    ) {
        Column(modifier = Modifier.padding(horizontal = MzSpacing.sideMargin, vertical = 20.dp), content = content)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun MzDatePickerSheet(
    date: LocalDate,
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val initial = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    MzDatePickerDialog(state, onDismissRequest, onDateSelected)
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun MzDatePickerDialog(
    state: DatePickerState,
    onDismissRequest: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val tokens = LocalMzTokens.current
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { selected ->
                        onDateSelected(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("Apply", color = tokens.accentText) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("Cancel", color = tokens.textDim) } },
    ) { DatePicker(state = state) }
}
