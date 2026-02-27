package com.piratereader.epub

object EpubTocCodec {
    fun encode(entries: List<EpubTocEntry>): String? {
        if (entries.isEmpty()) return null
        return buildString {
            entries.forEachIndexed { index, entry ->
                if (index > 0) append('\n')
                append(entry.depth)
                append('\t')
                append(escape(entry.label))
                append('\t')
                append(escape(entry.hrefZipPath.orEmpty()))
            }
        }
    }

    fun decode(serialized: String?): List<EpubTocEntry> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = splitEscapedTabs(line)
                if (parts.size < 3) return@mapNotNull null
                val depth = parts[0].toIntOrNull() ?: 0
                val label = unescape(parts[1])
                val href = unescape(parts[2]).ifBlank { null }
                if (label.isBlank()) return@mapNotNull null
                EpubTocEntry(
                    label = label,
                    hrefZipPath = href,
                    depth = depth,
                )
            }
            .toList()
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
        }

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> out.append('\\')
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> {
                        out.append('\\')
                        out.append(next)
                    }
                }
                i += 2
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

    private fun splitEscapedTabs(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\' && i + 1 < line.length) {
                current.append(ch)
                current.append(line[i + 1])
                i += 2
                continue
            }
            if (ch == '\t') {
                parts += current.toString()
                current.clear()
                i++
                continue
            }
            current.append(ch)
            i++
        }
        parts += current.toString()
        return parts
    }
}

