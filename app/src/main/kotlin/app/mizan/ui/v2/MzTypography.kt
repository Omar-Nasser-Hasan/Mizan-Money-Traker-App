// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mizan.R

object MzTypography {
    val manrope =
        FontFamily(
            Font(R.font.manrope_variable, FontWeight.Normal),
            Font(R.font.manrope_variable, FontWeight.Medium),
            Font(R.font.manrope_variable, FontWeight.SemiBold),
            Font(R.font.manrope_variable, FontWeight.Bold),
        )
    val hero =
        TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em)
    val headline = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 32.sp)
    val title = TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val labelCaps =
        TextStyle(fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em)
    val amountLarge =
        TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontFeatureSettings = "tnum",
        )
    val amountMedium =
        TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontFeatureSettings = "tnum",
        )
    val amountSmall =
        TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontFeatureSettings = "tnum",
        )
    val keypad = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 30.sp)
}
