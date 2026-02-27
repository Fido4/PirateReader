package com.piratereader.ui.reader

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ReaderViewportMetrics(
    val pageIndex: Int?,
    val pageCount: Int?,
    val scrollX: Int?,
    val scrollY: Int?,
    val maxScrollX: Int?,
    val maxScrollY: Int?,
    val anchorFragment: String?,
    val tocAnchorFragment: String?,
    val visibleTextHint: String?,
)

object ReaderViewportMetricsCodec {
    fun parseEvaluateJavascriptResult(raw: String?): ReaderViewportMetrics? {
        val payload = decodeJavascriptStringLiteral(raw) ?: return null
        val version = payload.substringBefore('|', missingDelimiterValue = "")
        return when (version) {
            "v1" -> parseV1(payload)
            "v2" -> parseV2(payload)
            "v3" -> parseV3(payload)
            "v4" -> parseV4(payload)
            else -> null
        }
    }

    private fun parseV1(payload: String): ReaderViewportMetrics? {
        val parts = payload.split('|', limit = 6)
        if (parts.size < 6) return null

        val pageIndex = parts[1].toIntOrNull()?.takeIf { it > 0 }
        val pageCount = parts[2].toIntOrNull()?.takeIf { it > 0 }
        val scrollX = parts[3].toIntOrNull()?.coerceAtLeast(0)
        val scrollY = parts[4].toIntOrNull()?.coerceAtLeast(0)
        val anchor = decodePercentEncoded(parts[5])?.trim()?.ifBlank { null }

        return ReaderViewportMetrics(
            pageIndex = pageIndex,
            pageCount = pageCount,
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = null,
            maxScrollY = null,
            anchorFragment = anchor,
            tocAnchorFragment = null,
            visibleTextHint = null,
        )
    }

    private fun parseV2(payload: String): ReaderViewportMetrics? {
        val parts = payload.split('|', limit = 8)
        if (parts.size < 8) return null

        val pageIndex = parts[1].toIntOrNull()?.takeIf { it > 0 }
        val pageCount = parts[2].toIntOrNull()?.takeIf { it > 0 }
        val scrollX = parts[3].toIntOrNull()?.coerceAtLeast(0)
        val scrollY = parts[4].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollX = parts[5].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollY = parts[6].toIntOrNull()?.coerceAtLeast(0)
        val anchor = decodePercentEncoded(parts[7])?.trim()?.ifBlank { null }

        return ReaderViewportMetrics(
            pageIndex = pageIndex,
            pageCount = pageCount,
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
            anchorFragment = anchor,
            tocAnchorFragment = null,
            visibleTextHint = null,
        )
    }

    private fun parseV3(payload: String): ReaderViewportMetrics? {
        val parts = payload.split('|', limit = 9)
        if (parts.size < 9) return null

        val pageIndex = parts[1].toIntOrNull()?.takeIf { it > 0 }
        val pageCount = parts[2].toIntOrNull()?.takeIf { it > 0 }
        val scrollX = parts[3].toIntOrNull()?.coerceAtLeast(0)
        val scrollY = parts[4].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollX = parts[5].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollY = parts[6].toIntOrNull()?.coerceAtLeast(0)
        val anchor = decodePercentEncoded(parts[7])?.trim()?.ifBlank { null }
        val visibleTextHint = decodePercentEncoded(parts[8])?.trim()?.ifBlank { null }

        return ReaderViewportMetrics(
            pageIndex = pageIndex,
            pageCount = pageCount,
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
            anchorFragment = anchor,
            tocAnchorFragment = null,
            visibleTextHint = visibleTextHint,
        )
    }

    private fun parseV4(payload: String): ReaderViewportMetrics? {
        val parts = payload.split('|', limit = 10)
        if (parts.size < 10) return null

        val pageIndex = parts[1].toIntOrNull()?.takeIf { it > 0 }
        val pageCount = parts[2].toIntOrNull()?.takeIf { it > 0 }
        val scrollX = parts[3].toIntOrNull()?.coerceAtLeast(0)
        val scrollY = parts[4].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollX = parts[5].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollY = parts[6].toIntOrNull()?.coerceAtLeast(0)
        val anchor = decodePercentEncoded(parts[7])?.trim()?.ifBlank { null }
        val tocAnchor = decodePercentEncoded(parts[8])?.trim()?.ifBlank { null }
        val visibleTextHint = decodePercentEncoded(parts[9])?.trim()?.ifBlank { null }

        return ReaderViewportMetrics(
            pageIndex = pageIndex,
            pageCount = pageCount,
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
            anchorFragment = anchor,
            tocAnchorFragment = tocAnchor,
            visibleTextHint = visibleTextHint,
        )
    }

    private fun decodePercentEncoded(value: String): String? =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrNull()

    private fun decodeJavascriptStringLiteral(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value == "null" || value.isBlank()) return null
        if (value.length < 2 || value.first() != '"' || value.last() != '"') return null

        val body = value.substring(1, value.length - 1)
        val out = StringBuilder(body.length)
        var index = 0
        while (index < body.length) {
            val ch = body[index]
            if (ch != '\\') {
                out.append(ch)
                index += 1
                continue
            }
            if (index + 1 >= body.length) return null
            val next = body[index + 1]
            when (next) {
                '\\' -> out.append('\\')
                '"' -> out.append('"')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (index + 6 > body.length) return null
                    val hex = body.substring(index + 2, index + 6)
                    val codePoint = hex.toIntOrNull(16) ?: return null
                    out.append(codePoint.toChar())
                    index += 6
                    continue
                }

                else -> out.append(next)
            }
            index += 2
        }
        return out.toString()
    }
}
