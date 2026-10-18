// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
    Text(
        text = formatted,
        color = color,
        modifier = modifier.semantics { contentDescription = formatted },
        style = textStyle.copy(fontFeatureSettings = "tnum"),
    )
}

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
