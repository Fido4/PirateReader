package com.piratereader

enum class ReadingFontCategory {
    SERIF,
    SANS,
    TERMINAL_MONO,
}

data class ReadingFontOption(
    val id: String,
    val displayName: String,
    val category: ReadingFontCategory,
)

object ReaderCatalog {
    val supportedFormatsAtLaunch: List<String> = listOf("EPUB")

    val launchFonts: List<ReadingFontOption> = listOf(
        ReadingFontOption("literata", "Literata", ReadingFontCategory.SERIF),
        ReadingFontOption("merriweather", "Merriweather", ReadingFontCategory.SERIF),
        ReadingFontOption("bitter", "Bitter", ReadingFontCategory.SERIF),
        ReadingFontOption("noto_sans", "Noto Sans", ReadingFontCategory.SANS),
        ReadingFontOption("fira_code", "Fira Code (terminal)", ReadingFontCategory.TERMINAL_MONO),
        ReadingFontOption("jetbrains_mono", "JetBrains Mono (terminal)", ReadingFontCategory.TERMINAL_MONO),
    )
}

