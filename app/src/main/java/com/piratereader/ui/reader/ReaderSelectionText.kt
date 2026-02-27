package com.piratereader.ui.reader

data class ReaderDictionaryLookupResult(
    val selectedText: String,
    val lookupTerm: String,
    val definition: String?,
)

object ReaderSelectionText {
    fun normalizeSelection(raw: String?): String? {
        val collapsed = raw
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.ifBlank { null }
            ?: return null
        return collapsed.take(220)
    }

    fun bestLookupTerm(selection: String?): String? {
        val normalized = normalizeSelection(selection) ?: return null
        val firstToken = normalized
            .split(' ')
            .firstOrNull()
            ?.trim('"', '\'', '“', '”', '‘', '’', '(', ')', '[', ']', '{', '}', ',', '.', ';', ':', '!', '?')
            ?.ifBlank { null }
            ?: return null
        return firstToken.lowercase()
    }

    fun lookupSelectionDefinition(
        selection: String?,
        dictionaryStatus: OfflineDictionaryLoadStatus,
    ): ReaderDictionaryLookupResult? {
        val normalizedSelection = normalizeSelection(selection) ?: return null
        val lookupTerm = bestLookupTerm(normalizedSelection) ?: return null
        val index = (dictionaryStatus as? OfflineDictionaryLoadStatus.Ready)?.index ?: return null
        return ReaderDictionaryLookupResult(
            selectedText = normalizedSelection,
            lookupTerm = lookupTerm,
            definition = index.lookup(lookupTerm),
        )
    }
}
