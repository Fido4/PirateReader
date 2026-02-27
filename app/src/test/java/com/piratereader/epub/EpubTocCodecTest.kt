package com.piratereader.epub

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpubTocCodecTest {
    @Test
    fun encodeDecode_roundTripsEntries() {
        val entries = listOf(
            EpubTocEntry(label = "Chapter 1", hrefZipPath = "OEBPS/ch1.xhtml", depth = 0),
            EpubTocEntry(label = "Section\tOne", hrefZipPath = "OEBPS/ch1.xhtml#sec1", depth = 1),
            EpubTocEntry(label = "Line\nBreak", hrefZipPath = null, depth = 2),
        )

        val encoded = EpubTocCodec.encode(entries)
        val decoded = EpubTocCodec.decode(encoded)

        assertThat(decoded).isEqualTo(entries)
    }

    @Test
    fun encode_emptyList_returnsNull() {
        assertThat(EpubTocCodec.encode(emptyList())).isNull()
        assertThat(EpubTocCodec.decode(null)).isEmpty()
        assertThat(EpubTocCodec.decode("")).isEmpty()
    }
}

