package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Test

class OfflineDictionaryIndexLoaderTest {
    @Test
    fun loadForBookLocalPath_missingDictionary_returnsMissingWithExpectedPath() {
        val libraryRoot = createTempLibraryRoot()
        val bookPath = File(libraryRoot, "epub/sample.epub").apply {
            parentFile?.mkdirs()
            writeText("placeholder")
        }.absolutePath

        val status = OfflineDictionaryIndexLoader.loadForBookLocalPath(bookPath)

        assertThat(status).isInstanceOf(OfflineDictionaryLoadStatus.Missing::class.java)
        val missing = status as OfflineDictionaryLoadStatus.Missing
        assertThat(missing.expectedPath).endsWith("library/dictionary.tsv")
    }

    @Test
    fun loadForBookLocalPath_validTsvDictionary_returnsReadyAndSupportsCaseInsensitiveLookup() {
        val libraryRoot = createTempLibraryRoot()
        val dictionaryFile = File(libraryRoot, OfflineDictionaryIndexLoader.DEFAULT_DICTIONARY_FILE_NAME)
        dictionaryFile.writeText(
            """
            # comment
            Apple	A fruit.
            boat	A vehicle on water.
            apple	A technology company.
            """.trimIndent(),
        )
        val bookPath = File(libraryRoot, "epub/sample.epub").apply {
            parentFile?.mkdirs()
            writeText("placeholder")
        }.absolutePath

        val status = OfflineDictionaryIndexLoader.loadForBookLocalPath(bookPath)

        assertThat(status).isInstanceOf(OfflineDictionaryLoadStatus.Ready::class.java)
        val ready = status as OfflineDictionaryLoadStatus.Ready
        assertThat(ready.index.sourceFileName).isEqualTo("dictionary.tsv")
        assertThat(ready.index.entryCount).isEqualTo(2)
        assertThat(ready.index.lookup("apple")).isEqualTo("A technology company.")
        assertThat(ready.index.lookup("APPLE")).isEqualTo("A technology company.")
        assertThat(ready.index.lookup("boat")).isEqualTo("A vehicle on water.")
    }

    @Test
    fun loadForBookLocalPath_invalidDictionary_returnsInvalid() {
        val libraryRoot = createTempLibraryRoot()
        val dictionaryFile = File(libraryRoot, OfflineDictionaryIndexLoader.DEFAULT_DICTIONARY_FILE_NAME)
        dictionaryFile.writeText(
            """
            missing delimiter line
            another invalid line
            """.trimIndent(),
        )
        val bookPath = File(libraryRoot, "epub/sample.epub").apply {
            parentFile?.mkdirs()
            writeText("placeholder")
        }.absolutePath

        val status = OfflineDictionaryIndexLoader.loadForBookLocalPath(bookPath)

        assertThat(status).isInstanceOf(OfflineDictionaryLoadStatus.Invalid::class.java)
        val invalid = status as OfflineDictionaryLoadStatus.Invalid
        assertThat(invalid.path).endsWith("library/dictionary.tsv")
        assertThat(invalid.reason).contains("no valid tab-delimited entries")
    }

    private fun createTempLibraryRoot(): File {
        val testRoot = createTempDirectory(prefix = "piratereader-dictionary-test-").toFile()
        return File(testRoot, "library").apply { mkdirs() }
    }
}
