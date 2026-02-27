package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderWebViewStyleInjectorTest {
    @Test
    fun injectIntoChapterMarkup_insertsStyleIntoExistingHead() {
        val html = """
            <html>
              <head><title>Ch1</title></head>
              <body><p>Hello</p></body>
            </html>
        """.trimIndent()

        val styled = ReaderWebViewStyleInjector.injectIntoChapterMarkup(
            chapterMarkup = html,
            settings = ReaderWebViewStyleInjector.defaultSettings,
        )

        assertThat(styled).contains("piratereader-reader-style")
        assertThat(styled).contains("meta name=\"piratereader-reader-style\"")
        assertThat(styled).contains("</head>")
        assertThat(styled).contains("<p>Hello</p>")
    }

    @Test
    fun injectIntoChapterMarkup_wrapsFragmentMarkup() {
        val styled = ReaderWebViewStyleInjector.injectIntoChapterMarkup(
            chapterMarkup = "<p>Fragment only</p>",
            settings = ReaderWebViewStyleSettings(
                themeId = "sepia",
                fontId = "jetbrains_mono",
                fontSizePx = 21,
                lineHeight = 1.6f,
                horizontalPaddingPx = 16,
                textAlign = ReaderTextAlign.JUSTIFY,
                pageMode = ReaderPageMode.PAGINATED,
            ),
        )

        assertThat(styled).contains("<!doctype html>")
        assertThat(styled).contains("<body><p>Fragment only</p></body>")
        assertThat(styled).contains("--pr-bg: #F4ECD8;")
        assertThat(styled).contains("\"JetBrains Mono\"")
        assertThat(styled).contains("font-size: 21px")
        assertThat(styled).contains("text-align: justify")
        assertThat(styled).contains("column-width: calc(100vw")
        assertThat(styled).contains("--pr-selection-bg:")
        assertThat(styled).contains("*::selection")
        assertThat(styled).contains("border-inline-start: 3px solid var(--pr-panel-line)")
    }

    @Test
    fun injectIntoChapterMarkup_appliesEinkOptimizationsWhenEnabled() {
        val styled = ReaderWebViewStyleInjector.injectIntoChapterMarkup(
            chapterMarkup = "<html><head></head><body><p>Eink</p></body></html>",
            settings = ReaderWebViewStyleSettings(
                themeId = "bw",
                fontId = "literata",
                fontSizePx = 18,
                lineHeight = 1.6f,
                horizontalPaddingPx = 20,
                textAlign = ReaderTextAlign.START,
                pageMode = ReaderPageMode.PAGINATED,
                isEinkOptimizedMode = true,
                einkContrastPreset = ReaderEinkContrastPreset.HIGH,
            ),
        )

        assertThat(styled).contains("--pr-page-gap: 16px;")
        assertThat(styled).contains("--pr-panel: #FFFFFF;")
        assertThat(styled).contains("animation: none !important;")
        assertThat(styled).contains("box-shadow: none !important;")
        assertThat(styled).contains("backdrop-filter: none !important;")
        assertThat(styled).contains("background: transparent !important;")
        assertThat(styled).contains("text-rendering: auto")
        assertThat(styled).contains("opacity: 0.68;")
        assertThat(styled).contains("piratereader-eink-contrast: high")
        assertThat(styled).contains("text-decoration-thickness: 2px !important;")
        assertThat(styled).contains("font-weight: 600 !important;")
    }

    @Test
    fun themeAndFontDisplayNames_fallbackToIds() {
        assertThat(ReaderWebViewStyleInjector.themeDisplayName("unknown_theme")).isEqualTo("unknown_theme")
        assertThat(ReaderWebViewStyleInjector.fontDisplayName("unknown_font")).isEqualTo("unknown_font")
    }

    @Test
    fun pageModeAndTextAlignAndEinkContrastDisplayNames_returnReadableLabels() {
        assertThat(ReaderWebViewStyleInjector.pageModeDisplayName(ReaderPageMode.SCROLL)).isEqualTo("Scroll")
        assertThat(ReaderWebViewStyleInjector.pageModeDisplayName(ReaderPageMode.PAGINATED)).isEqualTo("Paginated")
        assertThat(ReaderWebViewStyleInjector.textAlignDisplayName(ReaderTextAlign.START)).isEqualTo("Left")
        assertThat(ReaderWebViewStyleInjector.textAlignDisplayName(ReaderTextAlign.JUSTIFY)).isEqualTo("Justify")
        assertThat(ReaderWebViewStyleInjector.textAlignDisplayName(ReaderTextAlign.CENTER)).isEqualTo("Center")
        assertThat(
            ReaderWebViewStyleInjector.einkContrastDisplayName(ReaderEinkContrastPreset.BALANCED),
        ).isEqualTo("Balanced")
        assertThat(
            ReaderWebViewStyleInjector.einkContrastDisplayName(ReaderEinkContrastPreset.HIGH),
        ).isEqualTo("High")
    }

    @Test
    fun themePresets_matchReadestThemeList_andIncludeEinkVariants() {
        val themeIds = ReaderWebViewStyleInjector.themePresets.map { it.id }

        assertThat(themeIds).containsExactly(
            "default",
            "gray",
            "sepia",
            "grass",
            "cherry",
            "eye",
            "night",
            "gold",
            "berry",
            "bw",
            "wbb",
        ).inOrder()

        val einkThemes = ReaderWebViewStyleInjector.themePresets.count { it.isEinkPreset }
        val darkThemes = ReaderWebViewStyleInjector.themePresets.count { it.isDark }
        assertThat(einkThemes).isAtLeast(4)
        assertThat(darkThemes).isAtLeast(4)
    }
}
