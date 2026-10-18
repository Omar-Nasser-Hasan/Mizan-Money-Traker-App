// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
    showCurrency: Boolean = true,
) {
    val tokens = LocalMzTokens.current
    val formatted = formatMizanAmount(minor, currency, style, hideAmounts, showCurrency)
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
        if (textStyle == MzTypography.hero && !hideAmounts) {
            val parts = heroAmountParts(minor, currency)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (showCurrency) {
                    Text(currency, color = tokens.textDim, style = MzTypography.labelCaps)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    parts.first,
                    color = color,
                    style = MzTypography.hero.copy(fontFeatureSettings = "tnum"),
                )
                Text(
                    parts.second,
                    color = tokens.textDim,
                    style = MzTypography.amountLarge.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"),
                )
            }
        } else {
            Text(
                text = formatted,
                color = color,
                maxLines = 1,
                softWrap = false,
                style = if (fit < 1f) resolvedStyle.copy(fontSize = resolvedStyle.fontSize * fit) else resolvedStyle,
            )
        }
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
    showCurrency: Boolean = true,
) = AmountText(money.minor, money.currency, style, hideAmounts, modifier, textStyle, showCurrency)

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
    showCurrency: Boolean = true,
): String {
    if (hideAmounts) return if (showCurrency) "$currency ••••••" else "••••••"
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
    return sign + (if (showCurrency) "$currency " else "") + number
}

internal fun heroAmountParts(
    minor: Long,
    currency: String,
): Pair<String, String> {
    val decimals =
        if (currency == "KWD") {
            3
        } else if (currency == "JPY") {
            0
        } else {
            2
        }
    val absolute = BigDecimal.valueOf(minor).abs().movePointLeft(decimals)
    val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
    val value = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(absolute)
    val sign = if (minor < 0) "− " else ""
    val pieces = value.split('.')
    return (sign + pieces[0]) to if (pieces.size > 1) ".${pieces[1]}" else ""
}
