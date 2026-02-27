package com.piratereader.ui.library

import com.google.common.truth.Truth.assertThat
import com.piratereader.epub.EpubTocCodec
import com.piratereader.epub.EpubTocEntry
import org.junit.Test

class LibraryTocPreviewFormatterTest {
    @Test
    fun previewLines_decodesAndTruncatesEntries() {
        val serialized = EpubTocCodec.encode(
            listOf(
                EpubTocEntry(label = "Intro", hrefZipPath = "OEBPS/intro.xhtml", depth = 0),
                EpubTocEntry(label = "Part I", hrefZipPath = "OEBPS/p1.xhtml", depth = 1),
                EpubTocEntry(label = "Part II", hrefZipPath = "OEBPS/p2.xhtml", depth = 2),
            ),
        )

        val preview = LibraryTocPreviewFormatter.previewLines(serialized, maxLines = 2)

        assertThat(preview).containsExactly(
            TocPreviewLine(label = "Intro", depth = 0),
            TocPreviewLine(label = "Part I", depth = 1),
        ).inOrder()
    }

    @Test
    fun previewLines_returnsEmptyForMissingOrInvalidData() {
        assertThat(LibraryTocPreviewFormatter.previewLines(null)).isEmpty()
        assertThat(LibraryTocPreviewFormatter.previewLines("bad-data")).isEmpty()
        assertThat(LibraryTocPreviewFormatter.previewLines("1\t\t", maxLines = 3)).isEmpty()
    }
}
