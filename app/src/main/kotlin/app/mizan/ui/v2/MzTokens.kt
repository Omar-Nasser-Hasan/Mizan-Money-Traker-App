// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Immutable visual tokens taken from the Obsidian & Champagne Stitch reference. */
data class MzTokens(
    val canvas: Color,
    val surface: Color,
    val raisedSurface: Color,
    val lowestSurface: Color,
    val text: Color,
    val textDim: Color,
    val outline: Color,
    val champagne: Color,
    val onChampagne: Color,
    val accentText: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val investment: Color,
    val warning: Color,
)

object MzAccountColors {
    val all =
        listOf(
            Color(0xFF188ABB),
            Color(0xFF0785DB),
            Color(0xFF717BCB),
            Color(0xFF9A6EC2),
            Color(0xFFC45AAC),
            Color(0xFFCE588B),
            Color(0xFFDA535F),
            Color(0xFFCA642F),
            Color(0xFFAA7818),
            Color(0xFF7A882F),
            Color(0xFF3A924F),
            Color(0xFF059093),
        )
}

object MzSpacing {
    val sideMargin: Dp = 20.dp
    val gutter: Dp = 12.dp
    val section: Dp = 32.dp
    val touchTarget: Dp = 48.dp
}
