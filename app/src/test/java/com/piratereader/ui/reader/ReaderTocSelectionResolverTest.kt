package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.piratereader.epub.EpubTocEntry
import org.junit.Test

class ReaderTocSelectionResolverTest {
    @Test
    fun selectHref_prefersExactFragmentMatch() {
        val entries = listOf(
            EpubTocEntry("Chapter 1", "OEBPS/ch1.xhtml", 0),
            EpubTocEntry("Section 1.1", "OEBPS/ch1.xhtml#intro", 1),
            EpubTocEntry("Chapter 2", "OEBPS/ch2.xhtml", 0),
        )

        val selected = ReaderTocSelectionResolver.selectHref(
            entries = entries,
            chapterZipPath = "OEBPS/ch1.xhtml",
            anchorFragment = "intro",
        )

        assertThat(selected).isEqualTo("OEBPS/ch1.xhtml#intro")
    }

    @Test
    fun selectHref_usesFuzzyFragmentMatchBeforeChapterFallback() {
        val entries = listOf(
            EpubTocEntry("Chapter 1", "OEBPS/ch1.xhtml", 0),
            EpubTocEntry("Section", "OEBPS/ch1.xhtml#section-1-2", 1),
        )

        val selected = ReaderTocSelectionResolver.selectHref(
            entries = entries,
            chapterZipPath = "OEBPS/ch1.xhtml",
            anchorFragment = "Section_1_2",
        )

        assertThat(selected).isEqualTo("OEBPS/ch1.xhtml#section-1-2")
    }

    @Test
    fun selectHref_fallsBackToChapterEntryWhenViewportAnchorIsNotInToc() {
        val entries = listOf(
            EpubTocEntry("Chapter 1", "OEBPS/ch1.xhtml", 0),
            EpubTocEntry("Section", "OEBPS/ch1.xhtml#sec-1", 1),
        )

        val selected = ReaderTocSelectionResolver.selectHref(
            entries = entries,
            chapterZipPath = "OEBPS/ch1.xhtml",
            anchorFragment = "random-inline-span-id",
        )

        assertThat(selected).isEqualTo("OEBPS/ch1.xhtml")
    }
}
