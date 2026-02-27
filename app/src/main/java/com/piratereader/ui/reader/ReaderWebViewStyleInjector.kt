package com.piratereader.ui.reader

import com.piratereader.ReaderCatalog
import com.piratereader.ReadingFontCategory

data class ReaderThemePreset(
    val id: String,
    val displayName: String,
    val readestThemeKey: String,
    val background: String,
    val text: String,
    val muted: String,
    val link: String,
    val selectionBackground: String,
    val selectionText: String,
    val panelBackground: String,
    val panelBorder: String,
    val isDark: Boolean,
    val isEinkPreset: Boolean,
)

enum class ReaderPageMode {
    SCROLL,
    PAGINATED,
}

enum class ReaderTextAlign {
    START,
    JUSTIFY,
    CENTER,
}

enum class ReaderEinkContrastPreset {
    BALANCED,
    HIGH,
}

data class ReaderWebViewStyleSettings(
    val themeId: String,
    val fontId: String,
    val fontSizePx: Int,
    val lineHeight: Float,
    val horizontalPaddingPx: Int,
    val textAlign: ReaderTextAlign,
    val pageMode: ReaderPageMode,
    val isEinkOptimizedMode: Boolean = false,
    val einkContrastPreset: ReaderEinkContrastPreset = ReaderEinkContrastPreset.BALANCED,
)

object ReaderWebViewStyleInjector {
    val themePresets: List<ReaderThemePreset> = listOf(
        readestThemePreset(
            id = "default",
            displayName = "Light",
            background = "#FBF6EE",
            text = "#000000",
        ),
        readestThemePreset(
            id = "gray",
            displayName = "Gray",
            background = "#D4D4D4",
            text = "#2A2A2A",
            isEinkPreset = true,
        ),
        readestThemePreset(
            id = "sepia",
            displayName = "Sepia",
            background = "#F4ECD8",
            text = "#5B4636",
        ),
        readestThemePreset(
            id = "grass",
            displayName = "Grass",
            background = "#D4E6D4",
            text = "#2A4035",
            isEinkPreset = true,
        ),
        readestThemePreset(
            id = "cherry",
            displayName = "Cherry",
            background = "#F7D4D4",
            text = "#4A2A2A",
        ),
        readestThemePreset(
            id = "eye",
            displayName = "Eye Care",
            background = "#CDE4C7",
            text = "#2B3A29",
            isEinkPreset = true,
        ),
        readestThemePreset(
            id = "night",
            displayName = "Night",
            background = "#1F1F1F",
            text = "#D4D4D4",
            isDark = true,
        ),
        readestThemePreset(
            id = "gold",
            displayName = "Gold",
            background = "#2C2A23",
            text = "#E6D7B8",
            isDark = true,
        ),
        readestThemePreset(
            id = "berry",
            displayName = "Berry",
            background = "#2B1F2A",
            text = "#E8D8E6",
            isDark = true,
        ),
        readestThemePreset(
            id = "bw",
            displayName = "B&W",
            background = "#FFFFFF",
            text = "#000000",
            isEinkPreset = true,
        ),
        readestThemePreset(
            id = "wbb",
            displayName = "White on Black",
            background = "#000000",
            text = "#FFFFFF",
            isDark = true,
            isEinkPreset = true,
        ),
    )

    val defaultSettings = ReaderWebViewStyleSettings(
        themeId = themePresets.first().id,
        fontId = ReaderCatalog.launchFonts.first().id,
        fontSizePx = 18,
        lineHeight = 1.7f,
        horizontalPaddingPx = 20,
        textAlign = ReaderTextAlign.START,
        pageMode = ReaderPageMode.SCROLL,
        isEinkOptimizedMode = false,
        einkContrastPreset = ReaderEinkContrastPreset.BALANCED,
    )

    fun injectIntoChapterMarkup(
        chapterMarkup: String,
        settings: ReaderWebViewStyleSettings,
    ): String {
        val theme = themePresets.firstOrNull { it.id == settings.themeId } ?: themePresets.first()
        val fontFamilyCss = cssFontFamilyForFont(settings.fontId)
        val styleBlock = buildStyleBlock(
            theme = theme,
            fontFamilyCss = fontFamilyCss,
            fontSizePx = settings.fontSizePx,
            lineHeight = settings.lineHeight,
            horizontalPaddingPx = settings.horizontalPaddingPx,
            textAlign = settings.textAlign,
            pageMode = settings.pageMode,
            isEinkOptimizedMode = settings.isEinkOptimizedMode,
            einkContrastPreset = settings.einkContrastPreset,
        )

        val styleTag = "<style id=\"piratereader-reader-style\">$styleBlock</style>"
        val markerMeta = "<meta name=\"piratereader-reader-style\" content=\"injected\" />"

        val headRegex = Regex("(?i)</head>")
        if (headRegex.containsMatchIn(chapterMarkup)) {
            return chapterMarkup.replaceFirst(headRegex, "$markerMeta$styleTag</head>")
        }

        val htmlRegex = Regex("(?i)<html(\\s|>)")
        if (htmlRegex.containsMatchIn(chapterMarkup)) {
            return chapterMarkup.replaceFirst(
                htmlRegex,
                "$0<head>$markerMeta$styleTag</head>",
            )
        }

        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8" />
                $markerMeta
                $styleTag
              </head>
              <body>$chapterMarkup</body>
            </html>
        """.trimIndent()
    }

    fun themeDisplayName(themeId: String): String =
        themePresets.firstOrNull { it.id == themeId }?.displayName ?: themeId

    fun fontDisplayName(fontId: String): String =
        ReaderCatalog.launchFonts.firstOrNull { it.id == fontId }?.displayName ?: fontId

    fun textAlignDisplayName(textAlign: ReaderTextAlign): String =
        when (textAlign) {
            ReaderTextAlign.START -> "Left"
            ReaderTextAlign.JUSTIFY -> "Justify"
            ReaderTextAlign.CENTER -> "Center"
        }

    fun pageModeDisplayName(pageMode: ReaderPageMode): String =
        when (pageMode) {
            ReaderPageMode.SCROLL -> "Scroll"
            ReaderPageMode.PAGINATED -> "Paginated"
        }

    fun einkContrastDisplayName(preset: ReaderEinkContrastPreset): String =
        when (preset) {
            ReaderEinkContrastPreset.BALANCED -> "Balanced"
            ReaderEinkContrastPreset.HIGH -> "High"
        }

    private fun cssFontFamilyForFont(fontId: String): String {
        val font = ReaderCatalog.launchFonts.firstOrNull { it.id == fontId } ?: ReaderCatalog.launchFonts.first()
        return when (font.id) {
            "literata" -> "\"Literata\", Georgia, serif"
            "merriweather" -> "\"Merriweather\", Georgia, serif"
            "bitter" -> "\"Bitter\", Georgia, serif"
            "noto_sans" -> "\"Noto Sans\", system-ui, sans-serif"
            "fira_code" -> "\"Fira Code\", \"Cascadia Mono\", monospace"
            "jetbrains_mono" -> "\"JetBrains Mono\", \"Cascadia Mono\", monospace"
            else -> when (font.category) {
                ReadingFontCategory.SERIF -> "Georgia, serif"
                ReadingFontCategory.SANS -> "system-ui, sans-serif"
                ReadingFontCategory.TERMINAL_MONO -> "monospace"
            }
        }
    }

    private fun buildStyleBlock(
        theme: ReaderThemePreset,
        fontFamilyCss: String,
        fontSizePx: Int,
        lineHeight: Float,
        horizontalPaddingPx: Int,
        textAlign: ReaderTextAlign,
        pageMode: ReaderPageMode,
        isEinkOptimizedMode: Boolean,
        einkContrastPreset: ReaderEinkContrastPreset,
    ): String =
        run {
            val effectiveMuted = when {
                !isEinkOptimizedMode -> theme.muted
                einkContrastPreset == ReaderEinkContrastPreset.HIGH ->
                    blendHex(
                        foreground = theme.text,
                        background = theme.background,
                        foregroundWeight = if (theme.isDark) 0.86f else 0.72f,
                    )
                else -> theme.muted
            }
            val effectivePanelBackground = when {
                !isEinkOptimizedMode -> theme.panelBackground
                einkContrastPreset == ReaderEinkContrastPreset.HIGH -> theme.background
                else -> theme.panelBackground
            }
            val effectivePanelBorder = when {
                !isEinkOptimizedMode -> theme.panelBorder
                einkContrastPreset == ReaderEinkContrastPreset.HIGH ->
                    blendHex(
                        foreground = theme.text,
                        background = theme.background,
                        foregroundWeight = if (theme.isDark) 0.68f else 0.52f,
                    )
                else -> theme.panelBorder
            }
            val effectiveSelectionBackground = when {
                !isEinkOptimizedMode -> theme.selectionBackground
                einkContrastPreset == ReaderEinkContrastPreset.HIGH ->
                    blendHex(
                        foreground = theme.text,
                        background = theme.background,
                        foregroundWeight = if (theme.isDark) 0.50f else 0.30f,
                    )
                else -> theme.selectionBackground
            }
            val hrOpacity = when {
                isEinkOptimizedMode && einkContrastPreset == ReaderEinkContrastPreset.HIGH -> "0.68"
                else -> "0.4"
            }

            """
        :root {
          color-scheme: ${if (theme.isDark) "dark" else "light"};
          --pr-bg: ${theme.background};
          --pr-fg: ${theme.text};
          --pr-muted: $effectiveMuted;
          --pr-link: ${theme.link};
          --pr-panel: $effectivePanelBackground;
          --pr-panel-line: $effectivePanelBorder;
          --pr-selection-bg: $effectiveSelectionBackground;
          --pr-selection-fg: ${theme.selectionText};
          --pr-page-gap: ${if (isEinkOptimizedMode) 16 else 28}px;
        }
        html, body {
          margin: 0 !important;
          padding: 0 !important;
          background: var(--pr-bg) !important;
          color: var(--pr-fg) !important;
        }
        body {
          font-family: $fontFamilyCss !important;
          font-size: ${fontSizePx.coerceIn(12, 42)}px !important;
          line-height: ${lineHeight.coerceIn(1.1f, 2.4f)} !important;
          padding: 18px ${horizontalPaddingPx.coerceIn(8, 72)}px 42px !important;
          text-rendering: ${if (isEinkOptimizedMode) "auto" else "optimizeLegibility"};
          -webkit-font-smoothing: ${if (isEinkOptimizedMode) "auto" else "antialiased"};
          text-align: ${textAlign.toCssValue()} !important;
        }
        *::selection {
          background: var(--pr-selection-bg) !important;
          color: var(--pr-selection-fg) !important;
        }
        p, li, blockquote, dd, dt {
          color: var(--pr-fg) !important;
          text-align: ${textAlign.toCssValue()} !important;
        }
        h1, h2, h3, h4, h5, h6 {
          color: var(--pr-fg) !important;
          line-height: 1.25 !important;
          text-align: ${if (textAlign == ReaderTextAlign.CENTER) "center" else "start"} !important;
        }
        a {
          color: var(--pr-link) !important;
        }
        a:visited {
          color: var(--pr-link) !important;
          opacity: 0.92;
        }
        hr {
          border: none !important;
          border-top: 1px solid var(--pr-muted) !important;
          opacity: $hrOpacity;
        }
        blockquote {
          margin: 1em 0 !important;
          padding: 0.65em 0.85em !important;
          border-inline-start: 3px solid var(--pr-panel-line) !important;
          background: var(--pr-panel) !important;
          border-radius: 6px;
        }
        table {
          border-collapse: collapse !important;
          max-width: 100% !important;
        }
        th, td {
          border: 1px solid var(--pr-panel-line) !important;
          padding: 0.4em 0.55em !important;
        }
        img, svg, video {
          max-width: 100% !important;
          height: auto !important;
        }
        pre {
          background: var(--pr-panel) !important;
          border: 1px solid var(--pr-panel-line) !important;
          border-radius: 8px;
          padding: 0.7em 0.8em !important;
          overflow: auto !important;
        }
        code {
          background: var(--pr-panel) !important;
          border-radius: 4px;
          padding: 0.06em 0.24em;
        }
        pre, code {
          font-family: "JetBrains Mono", "Fira Code", monospace !important;
        }
        @media (prefers-reduced-motion: reduce) {
          * {
            animation: none !important;
            transition: none !important;
            scroll-behavior: auto !important;
          }
        }
        ${einkModeCss(isEinkOptimizedMode, einkContrastPreset)}
        ${pageModeCss(pageMode = pageMode, horizontalPaddingPx = horizontalPaddingPx)}
        """.trimIndent()
        }

    private fun ReaderTextAlign.toCssValue(): String =
        when (this) {
            ReaderTextAlign.START -> "start"
            ReaderTextAlign.JUSTIFY -> "justify"
            ReaderTextAlign.CENTER -> "center"
        }

    private fun pageModeCss(
        pageMode: ReaderPageMode,
        horizontalPaddingPx: Int,
    ): String =
        when (pageMode) {
            ReaderPageMode.SCROLL ->
                """
                html, body {
                  overflow-x: hidden !important;
                  overflow-y: auto !important;
                }
                body {
                  column-width: auto !important;
                  column-count: auto !important;
                }
                """.trimIndent()

            ReaderPageMode.PAGINATED ->
                """
                html, body {
                  width: 100% !important;
                  height: 100% !important;
                  overflow: hidden !important;
                }
                body {
                  min-height: 100vh !important;
                  box-sizing: border-box !important;
                  column-width: calc(100vw - ${(horizontalPaddingPx.coerceIn(8, 72) * 2) + 2}px) !important;
                  column-gap: var(--pr-page-gap) !important;
                  column-fill: auto !important;
                  overflow: hidden !important;
                }
                img, svg, video, table, pre {
                  break-inside: avoid-column !important;
                }
                h1, h2, h3, h4, h5, h6, p, li, blockquote, pre {
                  break-inside: avoid !important;
                }
                """.trimIndent()
        }

    private fun einkModeCss(
        enabled: Boolean,
        einkContrastPreset: ReaderEinkContrastPreset,
    ): String {
        if (!enabled) return ""
        val underlineThickness = if (einkContrastPreset == ReaderEinkContrastPreset.HIGH) "2px" else "1px"
        val linkFontWeight = if (einkContrastPreset == ReaderEinkContrastPreset.HIGH) "600" else "inherit"
        return """
            /* piratereader-eink-contrast: ${einkContrastPreset.name.lowercase()} */
            html, body {
              scroll-behavior: auto !important;
            }
            * {
              animation: none !important;
              transition: none !important;
              box-shadow: none !important;
              text-shadow: none !important;
              backdrop-filter: none !important;
            }
            a, a:visited {
              text-decoration-thickness: $underlineThickness !important;
              text-underline-offset: 2px !important;
              font-weight: $linkFontWeight !important;
            }
            img, svg, video, canvas {
              image-rendering: auto !important;
              filter: none !important;
            }
            pre, code, blockquote {
              background: transparent !important;
            }
            table, th, td, pre, code, blockquote {
              border-color: var(--pr-muted) !important;
            }
        """.trimIndent()
    }

    private fun readestThemePreset(
        id: String,
        displayName: String,
        background: String,
        text: String,
        isDark: Boolean = false,
        isEinkPreset: Boolean = false,
    ): ReaderThemePreset {
        val normalizedBg = normalizeHexColor(background)
        val normalizedText = normalizeHexColor(text)
        val muted = blendHex(
            foreground = normalizedText,
            background = normalizedBg,
            foregroundWeight = if (isDark) 0.72f else 0.58f,
        )
        val panelBackground = blendHex(
            foreground = normalizedText,
            background = normalizedBg,
            foregroundWeight = if (isDark) 0.10f else 0.04f,
        )
        val panelBorder = blendHex(
            foreground = normalizedText,
            background = normalizedBg,
            foregroundWeight = if (isDark) 0.24f else 0.14f,
        )
        val selectionBackground = blendHex(
            foreground = normalizedText,
            background = normalizedBg,
            foregroundWeight = if (isDark) 0.34f else 0.18f,
        )
        val link = when {
            isEinkPreset -> normalizedText
            isDark -> "#A8C7FA"
            else -> "#1A5FB4"
        }

        return ReaderThemePreset(
            id = id,
            displayName = displayName,
            readestThemeKey = id,
            background = normalizedBg,
            text = normalizedText,
            muted = muted,
            link = link,
            selectionBackground = selectionBackground,
            selectionText = normalizedText,
            panelBackground = panelBackground,
            panelBorder = panelBorder,
            isDark = isDark,
            isEinkPreset = isEinkPreset,
        )
    }

    private fun normalizeHexColor(value: String): String {
        val hex = value.trim().removePrefix("#")
        require(hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Expected #RRGGBB color, got: $value"
        }
        return "#${hex.uppercase()}"
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
