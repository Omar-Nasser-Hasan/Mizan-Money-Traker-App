// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

enum class MizanThemeMode { SYSTEM, LIGHT, DARK, TRUE_BLACK }

object InkGold {
    val DarkBackground = Color(0xFF0A0F1A)
    val DarkSurface = Color(0xFF121A29)
    val DarkRaised = Color(0xFF1A2438)
    val DarkOutline = Color(0xFF26324A)
    val DarkText = Color(0xFFEAF0FA)
    val DarkDim = Color(0xFF93A1B8)
    val LightBackground = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightRaised = Color(0xFFEDEFF3)
    val LightOutline = Color(0xFFD8DDE6)
    val LightText = Color(0xFF0F1624)
    val LightDim = Color(0xFF5A667A)
    val GoldDark = Color(0xFFF2B84B)
    val GoldLight = Color(0xFFE0A030)
    val OnGold = Color(0xFF1A1405)
    val GoldTextLight = Color(0xFF8A5A0B)
    val IncomeDark = Color(0xFF34D399)
    val IncomeLight = Color(0xFF0A7F57)
    val ExpenseDark = Color(0xFFFF6B6B)
    val ExpenseLight = Color(0xFFD93A40)
    val TransferDark = Color(0xFF5BC0EB)
    val TransferLight = Color(0xFF1479B0)
    val InvestDark = Color(0xFFA78BFA)
    val InvestLight = Color(0xFF7C5CDB)
    val WarningDark = Color(0xFFFF8A3D)
    val WarningLight = Color(0xFFC2531A)
}

private val DarkColors =
    darkColorScheme(
        primary = InkGold.GoldDark,
        onPrimary = InkGold.OnGold,
        background = InkGold.DarkBackground,
        onBackground = InkGold.DarkText,
        surface = InkGold.DarkSurface,
        onSurface = InkGold.DarkText,
        surfaceVariant = InkGold.DarkRaised,
        onSurfaceVariant = InkGold.DarkDim,
        outline = InkGold.DarkOutline,
    )

private val TrueBlackColors = DarkColors.copy(background = Color.Black)

private val LightColors =
    lightColorScheme(
        primary = InkGold.GoldLight,
        onPrimary = InkGold.OnGold,
        background = InkGold.LightBackground,
        onBackground = InkGold.LightText,
        surface = InkGold.LightSurface,
        onSurface = InkGold.LightText,
        surfaceVariant = InkGold.LightRaised,
        onSurfaceVariant = InkGold.LightDim,
        outline = InkGold.LightOutline,
    )

@Composable
@Suppress("ktlint:standard:function-naming")
fun MizanTheme(
    mode: MizanThemeMode = MizanThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark =
        LocalConfiguration.current.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val colors =
        when (mode) {
            MizanThemeMode.LIGHT -> LightColors
            MizanThemeMode.DARK -> DarkColors
            MizanThemeMode.TRUE_BLACK -> TrueBlackColors
            MizanThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
        }
    MaterialTheme(colorScheme = colors, content = content)
}

data class TextContrastPair(
    val foreground: String,
    val background: String,
)

/** All textual token uses in M0; the unit test guards the WCAG 4.5:1 rule. */
val TextContrastPairs =
    listOf(
        TextContrastPair("EAF0FA", "0A0F1A"),
        TextContrastPair("93A1B8", "0A0F1A"),
        TextContrastPair("EAF0FA", "121A29"),
        TextContrastPair("93A1B8", "121A29"),
        TextContrastPair("EAF0FA", "000000"),
        TextContrastPair("93A1B8", "000000"),
        TextContrastPair("0F1624", "F5F6F8"),
        TextContrastPair("5A667A", "F5F6F8"),
        TextContrastPair("0F1624", "FFFFFF"),
        TextContrastPair("5A667A", "FFFFFF"),
        TextContrastPair("1A1405", "F2B84B"),
        TextContrastPair("1A1405", "E0A030"),
        TextContrastPair("8A5A0B", "FFFFFF"),
        TextContrastPair("0A7F57", "FFFFFF"),
        TextContrastPair("D93A40", "FFFFFF"),
        TextContrastPair("1479B0", "FFFFFF"),
        TextContrastPair("7C5CDB", "FFFFFF"),
        TextContrastPair("C2531A", "FFFFFF"),
    )
