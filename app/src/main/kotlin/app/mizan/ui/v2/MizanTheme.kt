// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.res.Configuration
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

enum class MizanThemeMode { FOLLOW_SYSTEM, LIGHT, DARK, TRUE_BLACK }

internal val DarkTokens =
    MzTokens(
        canvas = Color(0xFF121316),
        surface = Color(0xFF292A2D),
        raisedSurface = Color(0xFF34363A),
        lowestSurface = Color(0xFF0D0E11),
        text = Color(0xFFE9E6DE),
        textDim = Color(0xFFBEB9AF),
        outline = Color(0xFF5B5A54),
        champagne = Color(0xFFD9C27F),
        onChampagne = Color(0xFF2D291E),
        accentText = Color(0xFFE6D29A),
        income = Color(0xFF58CFC4),
        expense = Color(0xFFF4806E),
        transfer = Color(0xFFB5C7D8),
        investment = Color(0xFFC0A5E8),
        warning = Color(0xFFF0BD76),
    )

internal val TrueBlackTokens = DarkTokens.copy(canvas = Color.Black, lowestSurface = Color.Black)

internal val LightTokens =
    MzTokens(
        canvas = Color(0xFFF5F2E9),
        surface = Color(0xFFFFFCF5),
        raisedSurface = Color(0xFFEBE6DA),
        lowestSurface = Color(0xFFFFFCF5),
        text = Color(0xFF2E2B25),
        textDim = Color(0xFF5D574C),
        outline = Color(0xFF938A78),
        champagne = Color(0xFF8B6D2B),
        onChampagne = Color(0xFFFFF8E7),
        accentText = Color(0xFF77591D),
        income = Color(0xFF0C7466),
        expense = Color(0xFFB03A14),
        transfer = Color(0xFF4C6378),
        investment = Color(0xFF6F4A9E),
        warning = Color(0xFF875A15),
    )

val LocalMzTokens = staticCompositionLocalOf { DarkTokens }

@Composable
@Suppress("ktlint:standard:function-naming")
fun MizanTheme(
    mode: MizanThemeMode = MizanThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val systemDark =
        LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val tokens =
        when (mode) {
            MizanThemeMode.DARK -> DarkTokens
            MizanThemeMode.LIGHT -> LightTokens
            MizanThemeMode.TRUE_BLACK -> TrueBlackTokens
            MizanThemeMode.FOLLOW_SYSTEM -> if (systemDark) DarkTokens else LightTokens
        }
    val colors =
        if (tokens == LightTokens) {
            lightColorScheme(
                primary = tokens.champagne,
                onPrimary = tokens.onChampagne,
                background = tokens.canvas,
                onBackground = tokens.text,
                surface = tokens.surface,
                onSurface = tokens.text,
                surfaceVariant = tokens.raisedSurface,
                onSurfaceVariant = tokens.textDim,
                outline = tokens.outline,
            )
        } else {
            darkColorScheme(
                primary = tokens.champagne,
                onPrimary = tokens.onChampagne,
                background = tokens.canvas,
                onBackground = tokens.text,
                surface = tokens.surface,
                onSurface = tokens.text,
                surfaceVariant = tokens.raisedSurface,
                onSurfaceVariant = tokens.textDim,
                outline = tokens.outline,
            )
        }
    CompositionLocalProvider(LocalMzTokens provides tokens, LocalContentColor provides tokens.text) {
        MaterialTheme(
            colorScheme = colors,
            typography = MzTypography.material,
            shapes = androidx.compose.material3.Shapes(),
            content = content,
        )
    }
}
