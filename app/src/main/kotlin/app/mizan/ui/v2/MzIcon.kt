// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.mizan.R

private val MaterialSymbolsRounded = FontFamily(Font(R.font.material_symbols_rounded_variable, FontWeight.Light))
private val directGlyphs =
    mapOf(
        "account_balance_wallet" to 0xE850,
        "account_balance" to 0xE84F,
        "credit_card" to 0xE8A1,
        "currency_exchange" to 0xEB70,
    )

/** Renders a Material Symbols Rounded ligature at the reference's 300 outlined weight. */
@Composable
@Suppress("ktlint:standard:function-naming")
fun MzIcon(
    name: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalMzTokens.current.textDim,
    size: androidx.compose.ui.unit.TextUnit = 24.sp,
) {
    Text(
        text = directGlyphs[name]?.toChar()?.toString() ?: name,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        color = tint,
        style =
            TextStyle(
                fontFamily = MaterialSymbolsRounded,
                fontWeight = FontWeight.Light,
                fontSize = size,
                lineHeight = size,
                fontFeatureSettings = "liga",
            ),
    )
}
