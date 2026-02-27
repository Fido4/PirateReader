package com.piratereader.ui.reader

import java.io.File

data class OfflineDictionaryIndex(
    val sourceFileName: String,
    val entries: Map<String, String>,
) {
    val entryCount: Int get() = entries.size

    fun lookup(term: String): String? = entries[normalizeDictionaryTerm(term)]
}

sealed interface OfflineDictionaryLoadStatus {
    data class Ready(val index: OfflineDictionaryIndex) : OfflineDictionaryLoadStatus

    data class Missing(val expectedPath: String) : OfflineDictionaryLoadStatus

    data class Invalid(
        val path: String,
        val reason: String,
    ) : OfflineDictionaryLoadStatus
}

object OfflineDictionaryIndexLoader {
    const val DEFAULT_DICTIONARY_FILE_NAME = "dictionary.tsv"

    fun loadForBookLocalPath(bookLocalPath: String): OfflineDictionaryLoadStatus {
        val libraryRoot = libraryRootForBookLocalPath(bookLocalPath)
            ?: return OfflineDictionaryLoadStatus.Invalid(
                path = bookLocalPath,
                reason = "could not resolve library root from book path",
            )
        val dictionaryFile = File(libraryRoot, DEFAULT_DICTIONARY_FILE_NAME)
        return loadFromDictionaryFile(dictionaryFile)
    }

    fun libraryRootForBookLocalPath(bookLocalPath: String): File? {
        val localPath = bookLocalPath.trim()
        if (localPath.isBlank()) return null
        val file = File(localPath)
        val parent = file.parentFile ?: return null
        return parent.parentFile
    }

    fun loadFromDictionaryFile(dictionaryFile: File): OfflineDictionaryLoadStatus {
        if (!dictionaryFile.exists()) {
            return OfflineDictionaryLoadStatus.Missing(
                expectedPath = dictionaryFile.absolutePath,
            )
        }
        if (!dictionaryFile.isFile) {
            return OfflineDictionaryLoadStatus.Invalid(
                path = dictionaryFile.absolutePath,
                reason = "path exists but is not a file",
            )
        }
        return runCatching {
            val parsed = parseTsvDictionary(dictionaryFile)
            if (parsed.isEmpty()) {
                OfflineDictionaryLoadStatus.Invalid(
                    path = dictionaryFile.absolutePath,
                    reason = "dictionary file has no valid tab-delimited entries",
                )
            } else {
                OfflineDictionaryLoadStatus.Ready(
                    OfflineDictionaryIndex(
                        sourceFileName = dictionaryFile.name,
                        entries = parsed,
                    ),
                )
            }
        }.getOrElse { error ->
            OfflineDictionaryLoadStatus.Invalid(
                path = dictionaryFile.absolutePath,
                reason = error.message ?: "failed to parse dictionary file",
            )
        }
    }

    private fun parseTsvDictionary(dictionaryFile: File): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        dictionaryFile.useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val parts = rawLine.split('\t', limit = 2)
                if (parts.size < 2) return@forEach
                val term = normalizeDictionaryTerm(parts[0])
                val definition = parts[1].trim()
                if (term.isEmpty() || definition.isEmpty()) return@forEach
                entries[term] = definition
            }
        }
        return entries
    }
}

private fun normalizeDictionaryTerm(value: String): String = value.trim().lowercase()
