package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import com.piratereader.data.library.LibraryBook
import org.junit.Test

class ReaderScaffoldHtmlTest {
    @Test
    fun build_includesEscapedMetadataAndFallbackDiagnosticsCopy() {
        val book = LibraryBook(
            id = 7L,
            title = "A <B> & \"C\"",
            authors = "Jane & John",
            coverPath = null,
            tocEntryCount = 12,
            tocEntriesSerialized = null,
            fileName = "test.epub",
            localPath = "/data/user/0/com.piratereader/files/library/epub/a<b>.epub",
            sourceUri = null,
            fileSizeBytes = 10L,
            lastReadChapterZipPath = null,
            lastReadScrollY = null,
            addedAt = 1L,
            lastOpenedAt = 2L,
            format = "EPUB",
        )

        val html = ReaderScaffoldHtml.build(book)

        assertThat(html).contains("Reader fallback view")
        assertThat(html).contains("Reader diagnostics")
        assertThat(html).contains("A &lt;B&gt; &amp; &quot;C&quot;")
        assertThat(html).contains("Jane &amp; John")
        assertThat(html).contains("/data/user/0/com.piratereader/files/library/epub/a&lt;b&gt;.epub")
        assertThat(html).contains("12 entries")
    }
}
