package com.piratereader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PirateLightColors = lightColorScheme(
    primary = ReaderBlue,
    secondary = InkOlive,
    tertiary = WarmAccent,
    surface = PaperLight,
    background = PaperLight,
)

private val PirateDarkColors = darkColorScheme(
    primary = ReaderBlueDark,
    secondary = InkOliveDark,
    tertiary = WarmAccentDark,
    surface = InkBackground,
    background = InkBackground,
)

@Composable
fun PirateReaderTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PirateDarkColors else PirateLightColors,
        typography = Typography,
        content = content,
    )
}

