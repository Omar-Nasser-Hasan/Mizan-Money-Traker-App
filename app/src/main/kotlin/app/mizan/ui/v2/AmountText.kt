// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.mizan.domain.money.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
@Suppress("ktlint:standard:function-naming")
fun AmountText(
    minor: Long,
    currency: String,
    style: AmountStyle = AmountStyle.NEUTRAL,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MzTypography.amountMedium,
) {
    val tokens = LocalMzTokens.current
    val formatted = formatMizanAmount(minor, currency, style, hideAmounts)
    val color =
        when (style) {
            AmountStyle.EXPENSE -> tokens.expense
            AmountStyle.INCOME -> tokens.income
            AmountStyle.TRANSFER -> tokens.transfer
            AmountStyle.INVESTMENT -> tokens.investment
            AmountStyle.NEUTRAL -> tokens.text
        }
    val resolvedStyle = textStyle.copy(fontFeatureSettings = "tnum")
    val textMeasurer = rememberTextMeasurer()
    val measuredWidth = remember(formatted, resolvedStyle) { textMeasurer.measure(AnnotatedString(formatted), resolvedStyle).size.width }
    BoxWithConstraints(modifier = modifier.semantics { contentDescription = formatted }) {
        val availableWidth = constraints.maxWidth.toFloat()
        val fit = if (availableWidth > 0f && measuredWidth > availableWidth) availableWidth / measuredWidth else 1f
        Text(
            text = formatted,
            color = color,
            maxLines = 1,
            softWrap = false,
            style = if (fit < 1f) resolvedStyle.copy(fontSize = resolvedStyle.fontSize * fit) else resolvedStyle,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
fun AmountText(
    money: Money,
    style: AmountStyle = AmountStyle.NEUTRAL,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MzTypography.amountMedium,
) = AmountText(money.minor, money.currency, style, hideAmounts, modifier, textStyle)

internal fun formatMizanAmount(
    money: Money,
    style: AmountStyle = AmountStyle.NEUTRAL,
    hideAmounts: Boolean = false,
): String = formatMizanAmount(money.minor, money.currency, style, hideAmounts)

internal fun formatMizanAmount(
    minor: Long,
    currency: String,
    style: AmountStyle,
    hideAmounts: Boolean,
): String {
    if (hideAmounts) return "$currency ••••••"
    val decimals =
        when (currency) {
            "KWD" -> 3
            "JPY" -> 0
            else -> 2
        }
    val absolute = BigDecimal.valueOf(minor).abs().movePointLeft(decimals)
    val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
    val number = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(absolute)
    val sign =
        when (style) {
            AmountStyle.EXPENSE -> "− "
            AmountStyle.INCOME -> "+ "
            AmountStyle.TRANSFER -> "→ "
            AmountStyle.INVESTMENT -> "Invested "
            AmountStyle.NEUTRAL -> if (minor < 0) "− " else ""
        }
    return "$sign$currency $number"
}
