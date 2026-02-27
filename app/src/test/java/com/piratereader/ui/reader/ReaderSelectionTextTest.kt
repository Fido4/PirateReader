package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderSelectionTextTest {
    @Test
    fun normalizeSelection_collapsesWhitespaceAndTrims() {
        val normalized = ReaderSelectionText.normalizeSelection("  hello   world\n\nnext  ")

        assertThat(normalized).isEqualTo("hello world next")
    }

    @Test
    fun bestLookupTerm_prefersFirstTokenAndStripsPunctuation() {
        val term = ReaderSelectionText.bestLookupTerm("\"Anchor,\" points")

        assertThat(term).isEqualTo("anchor")
    }

    @Test
    fun lookupSelectionDefinition_returnsDefinitionWhenDictionaryReady() {
        val status = OfflineDictionaryLoadStatus.Ready(
            OfflineDictionaryIndex(
                sourceFileName = "dictionary.tsv",
                entries = mapOf("anchor" to "A marker for navigation."),
            ),
        )

        val lookup = ReaderSelectionText.lookupSelectionDefinition("Anchor", status)

        assertThat(lookup).isNotNull()
        assertThat(lookup?.lookupTerm).isEqualTo("anchor")
        assertThat(lookup?.definition).isEqualTo("A marker for navigation.")
    }

    @Test
    fun lookupSelectionDefinition_returnsNullWhenDictionaryMissing() {
        val lookup = ReaderSelectionText.lookupSelectionDefinition(
            selection = "Anchor",
            dictionaryStatus = OfflineDictionaryLoadStatus.Missing(expectedPath = "/tmp/library/dictionary.tsv"),
        )

        assertThat(lookup).isNull()
    }
}
