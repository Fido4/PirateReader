package com.piratereader.ui.reader

data class ReaderChromeThemeTokens(
    val topBarContainer: String,
    val topBarContent: String,
    val topBarAction: String,
    val panelContainer: String,
    val panelBorder: String,
    val primaryText: String,
    val secondaryText: String,
    val chipContainer: String,
    val chipContainerActive: String,
    val chipBorder: String,
    val chipBorderActive: String,
    val chipLabel: String,
    val chipLabelActive: String,
)

object ReaderChromeThemeTuning {
    fun tokensFor(
        theme: ReaderThemePreset,
        isEinkOptimizedMode: Boolean,
        einkContrastPreset: ReaderEinkContrastPreset,
    ): ReaderChromeThemeTokens {
        val highEinkContrast = isEinkOptimizedMode && einkContrastPreset == ReaderEinkContrastPreset.HIGH

        val topBarContainer = when {
            highEinkContrast -> theme.background
            theme.isDark -> blendHex(theme.text, theme.background, 0.05f)
            else -> blendHex(theme.text, theme.background, 0.02f)
        }
        val panelContainer = when {
            highEinkContrast -> theme.background
            isEinkOptimizedMode -> blendHex(theme.text, theme.background, if (theme.isDark) 0.07f else 0.025f)
            else -> theme.panelBackground
        }
        val panelBorder = when {
            highEinkContrast ->
                blendHex(
                    foreground = theme.text,
                    background = theme.background,
                    foregroundWeight = if (theme.isDark) 0.64f else 0.46f,
                )
            isEinkOptimizedMode ->
                blendHex(
                    foreground = theme.text,
                    background = theme.background,
                    foregroundWeight = if (theme.isDark) 0.28f else 0.18f,
                )
            else -> theme.panelBorder
        }
        val secondaryText = when {
            highEinkContrast ->
                blendHex(
                    foreground = theme.text,
                    background = theme.background,
                    foregroundWeight = if (theme.isDark) 0.88f else 0.76f,
                )
            isEinkOptimizedMode ->
                blendHex(
                    foreground = theme.text,
                    background = theme.background,
                    foregroundWeight = if (theme.isDark) 0.78f else 0.62f,
                )
            else -> theme.muted
        }
        val actionColor = if (theme.isEinkPreset) theme.text else theme.link
        val chipContainer = when {
            highEinkContrast -> theme.background
            isEinkOptimizedMode ->
                blendHex(theme.text, theme.background, if (theme.isDark) 0.08f else 0.03f)
            else -> panelContainer
        }
        val chipContainerActive = when {
            highEinkContrast ->
                blendHex(theme.text, theme.background, if (theme.isDark) 0.18f else 0.10f)
            isEinkOptimizedMode ->
                blendHex(theme.text, theme.background, if (theme.isDark) 0.14f else 0.08f)
            else ->
                blendHex(theme.text, theme.background, if (theme.isDark) 0.16f else 0.07f)
        }
        val chipBorder = when {
            highEinkContrast ->
                blendHex(theme.text, theme.background, if (theme.isDark) 0.72f else 0.54f)
            else -> panelBorder
        }
        val chipBorderActive = actionColor
        val chipLabel = secondaryText
        val chipLabelActive = actionColor

        return ReaderChromeThemeTokens(
            topBarContainer = topBarContainer,
            topBarContent = theme.text,
            topBarAction = actionColor,
            panelContainer = panelContainer,
            panelBorder = panelBorder,
            primaryText = theme.text,
            secondaryText = secondaryText,
            chipContainer = chipContainer,
            chipContainerActive = chipContainerActive,
            chipBorder = chipBorder,
            chipBorderActive = chipBorderActive,
            chipLabel = chipLabel,
            chipLabelActive = chipLabelActive,
        )
    }

    private fun blendHex(
        foreground: String,
        background: String,
        foregroundWeight: Float,
    ): String {
        val fg = parseHexColor(foreground)
        val bg = parseHexColor(background)
        val weight = foregroundWeight.coerceIn(0f, 1f)
        val r = (fg.r * weight + bg.r * (1f - weight)).toInt().coerceIn(0, 255)
        val g = (fg.g * weight + bg.g * (1f - weight)).toInt().coerceIn(0, 255)
        val b = (fg.b * weight + bg.b * (1f - weight)).toInt().coerceIn(0, 255)
        return toHexColor(r, g, b)
    }

    private fun parseHexColor(value: String): RgbColor {
        val hex = normalizeHexColor(value).removePrefix("#")
        return RgbColor(
            r = hex.substring(0, 2).toInt(16),
            g = hex.substring(2, 4).toInt(16),
            b = hex.substring(4, 6).toInt(16),
        )
    }

    private fun normalizeHexColor(value: String): String {
        val hex = value.trim().removePrefix("#")
        require(hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Expected #RRGGBB color, got: $value"
        }
        return "#${hex.uppercase()}"
    }

    private fun toHexColor(
        r: Int,
        g: Int,
        b: Int,
    ): String = "#%02X%02X%02X".format(r, g, b)

    private data class RgbColor(
        val r: Int,
        val g: Int,
        val b: Int,
    )
}
