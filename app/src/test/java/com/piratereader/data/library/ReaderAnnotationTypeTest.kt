package com.piratereader.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderAnnotationTypeTest {
    @Test
    fun parseReaderAnnotationType_defaultsUnknownToBookmark() {
        assertThat(parseReaderAnnotationType("unknown")).isEqualTo(ReaderAnnotationType.BOOKMARK)
        assertThat(parseReaderAnnotationType(null)).isEqualTo(ReaderAnnotationType.BOOKMARK)
    }

    @Test
    fun parseReaderAnnotationType_parsesHighlightCaseInsensitive() {
        assertThat(parseReaderAnnotationType("highlight")).isEqualTo(ReaderAnnotationType.HIGHLIGHT)
        assertThat(parseReaderAnnotationType("HIGHLIGHT")).isEqualTo(ReaderAnnotationType.HIGHLIGHT)
    }
}
