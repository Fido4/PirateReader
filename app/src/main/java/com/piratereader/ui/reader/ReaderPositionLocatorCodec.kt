package com.piratereader.ui.reader

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ReaderPositionLocator(
    val chapterZipPath: String?,
    val anchorFragment: String?,
    val pageMode: ReaderPageMode?,
    val scrollX: Int?,
    val scrollY: Int?,
    val maxScrollX: Int?,
    val maxScrollY: Int?,
    val pageIndex: Int?,
    val pageCount: Int?,
    val visibleTextHint: String? = null,
) {
    fun scrollXProgressPermille(): Int? =
        progressPermille(current = scrollX, max = maxScrollX)

    fun scrollYProgressPermille(): Int? =
        progressPermille(current = scrollY, max = maxScrollY)

    fun pageProgressPermille(): Int? {
        val index = pageIndex ?: return null
        val count = pageCount ?: return null
        if (count <= 1 || index <= 1) return if (count >= 1) 0 else null
        val boundedIndex = index.coerceIn(1, count)
        return (((boundedIndex - 1).toDouble() / (count - 1).toDouble()) * 1000.0)
            .toInt()
            .coerceIn(0, 1000)
    }

    private fun progressPermille(
        current: Int?,
        max: Int?,
    ): Int? {
        val boundedCurrent = current?.coerceAtLeast(0) ?: return null
        val boundedMax = max?.coerceAtLeast(0) ?: return null
        if (boundedMax == 0) return 0
        return ((boundedCurrent.toDouble() / boundedMax.toDouble()) * 1000.0)
            .toInt()
            .coerceIn(0, 1000)
    }
}

object ReaderPositionLocatorCodec {
    fun encode(locator: ReaderPositionLocator?): String? {
        if (locator == null) return null
        return listOf(
            "v2",
            encodeNullable(locator.chapterZipPath),
            encodeNullable(locator.anchorFragment),
            locator.pageMode?.name.orEmpty(),
            encodeNullableInt(locator.scrollX),
            encodeNullableInt(locator.scrollY),
            encodeNullableInt(locator.maxScrollX),
            encodeNullableInt(locator.maxScrollY),
            encodeNullableInt(locator.pageIndex),
            encodeNullableInt(locator.pageCount),
            encodeNullable(locator.visibleTextHint),
        ).joinToString("|")
    }

    fun decode(serialized: String?): ReaderPositionLocator? {
        val payload = serialized?.trim()?.ifBlank { null } ?: return null
        val version = payload.substringBefore('|', missingDelimiterValue = "")
        return when (version) {
            "v1" -> decodeV1(payload)
            "v2" -> decodeV2(payload)
            else -> null
        }
    }

    private fun decodeV1(payload: String): ReaderPositionLocator? {
        val parts = payload.split('|', limit = 10)
        if (parts.size < 10) return null
        return decodeShared(parts = parts, visibleTextHint = null)
    }

    private fun decodeV2(payload: String): ReaderPositionLocator? {
        val parts = payload.split('|', limit = 11)
        if (parts.size < 11) return null
        val visibleTextHint = decodeNullable(parts[10])?.trim()?.ifBlank { null }
        return decodeShared(parts = parts, visibleTextHint = visibleTextHint)
    }

    private fun decodeShared(
        parts: List<String>,
        visibleTextHint: String?,
    ): ReaderPositionLocator {
        val chapterZipPath = decodeNullable(parts[1])
        val anchorFragment = decodeNullable(parts[2])?.trim()?.ifBlank { null }
        val pageMode = parts[3]
            .trim()
            .ifBlank { null }
            ?.let(::parseReaderPageMode)
        val scrollX = parts[4].toIntOrNull()?.coerceAtLeast(0)
        val scrollY = parts[5].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollX = parts[6].toIntOrNull()?.coerceAtLeast(0)
        val maxScrollY = parts[7].toIntOrNull()?.coerceAtLeast(0)
        val pageIndex = parts[8].toIntOrNull()?.takeIf { it > 0 }
        val pageCount = parts[9].toIntOrNull()?.takeIf { it > 0 }

        return ReaderPositionLocator(
            chapterZipPath = chapterZipPath,
            anchorFragment = anchorFragment,
            pageMode = pageMode,
            scrollX = scrollX,
            scrollY = scrollY,
            maxScrollX = maxScrollX,
            maxScrollY = maxScrollY,
            pageIndex = pageIndex,
            pageCount = pageCount,
            visibleTextHint = visibleTextHint,
        )
    }

    private fun parseReaderPageMode(value: String): ReaderPageMode? =
        ReaderPageMode.values().firstOrNull { it.name.equals(value, ignoreCase = true) }

    private fun encodeNullable(value: String?): String =
        value?.takeIf { it.isNotBlank() }?.let {
            URLEncoder.encode(it, StandardCharsets.UTF_8.name())
        }.orEmpty()

    private fun decodeNullable(value: String): String? =
        value.ifBlank { null }?.let {
            runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull()
        }

    private fun encodeNullableInt(value: Int?): String = value?.toString().orEmpty()
}
