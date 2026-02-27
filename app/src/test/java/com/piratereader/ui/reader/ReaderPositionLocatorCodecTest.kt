package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderPositionLocatorCodecTest {
    @Test
    fun encodeDecode_roundTripsLocator() {
        val locator = ReaderPositionLocator(
            chapterZipPath = "OEBPS/ch1.xhtml",
            anchorFragment = "sec-1:intro",
            pageMode = ReaderPageMode.PAGINATED,
            scrollX = 1080,
            scrollY = 24,
            maxScrollX = 6480,
            maxScrollY = 2200,
            pageIndex = 2,
            pageCount = 7,
            visibleTextHint = "Visible paragraph text hint",
        )

        val encoded = ReaderPositionLocatorCodec.encode(locator)
        val decoded = ReaderPositionLocatorCodec.decode(encoded)

        assertThat(encoded).startsWith("v2|")
        assertThat(decoded).isEqualTo(locator)
    }

    @Test
    fun decode_returnsNullForInvalidOrBlankPayload() {
        assertThat(ReaderPositionLocatorCodec.decode(null)).isNull()
        assertThat(ReaderPositionLocatorCodec.decode("")).isNull()
        assertThat(ReaderPositionLocatorCodec.decode("bad")).isNull()
    }

    @Test
    fun decode_supportsLegacyV1PayloadWithoutTextHint() {
        val decoded = ReaderPositionLocatorCodec.decode(
            "v1|OEBPS%2Fch1.xhtml|intro|SCROLL|10|20|100|200|1|3",
        )

        assertThat(decoded).isNotNull()
        assertThat(decoded?.chapterZipPath).isEqualTo("OEBPS/ch1.xhtml")
        assertThat(decoded?.anchorFragment).isEqualTo("intro")
        assertThat(decoded?.pageMode).isEqualTo(ReaderPageMode.SCROLL)
        assertThat(decoded?.visibleTextHint).isNull()
    }

    @Test
    fun progressPermille_usesScrollAndPageHints() {
        val locator = ReaderPositionLocator(
            chapterZipPath = "OEBPS/ch1.xhtml",
            anchorFragment = null,
            pageMode = ReaderPageMode.PAGINATED,
            scrollX = 500,
            scrollY = 250,
            maxScrollX = 1000,
            maxScrollY = 500,
            pageIndex = 3,
            pageCount = 5,
        )

        assertThat(locator.scrollXProgressPermille()).isEqualTo(500)
        assertThat(locator.scrollYProgressPermille()).isEqualTo(500)
        assertThat(locator.pageProgressPermille()).isEqualTo(500)
    }
}
