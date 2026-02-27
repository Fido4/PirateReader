package com.piratereader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderCatalogTest {
    @Test
    fun launch_isEpubOnly() {
        assertThat(ReaderCatalog.supportedFormatsAtLaunch).containsExactly("EPUB")
    }

    @Test
    fun launchFontSet_hasAtLeastFiveFonts_andTwoTerminalFonts() {
        assertThat(ReaderCatalog.launchFonts.size).isAtLeast(5)

        val terminalFonts = ReaderCatalog.launchFonts.count {
            it.category == ReadingFontCategory.TERMINAL_MONO
        }
        assertThat(terminalFonts).isAtLeast(2)
    }

    @Test
    fun launchFontIds_areUnique() {
        val ids = ReaderCatalog.launchFonts.map { it.id }
        assertThat(ids.toSet().size).isEqualTo(ids.size)
    }
}

