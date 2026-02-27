package com.piratereader.ui.library

import com.piratereader.epub.EpubTocCodec

data class TocPreviewLine(
    val label: String,
    val depth: Int,
)

object LibraryTocPreviewFormatter {
    fun previewLines(
        tocEntriesSerialized: String?,
        maxLines: Int = 5,
    ): List<TocPreviewLine> {
        if (maxLines <= 0) return emptyList()
        return EpubTocCodec.decode(tocEntriesSerialized)
            .take(maxLines)
            .map { entry ->
                TocPreviewLine(
                    label = entry.label,
                    depth = entry.depth.coerceAtLeast(0),
                )
            }
    }
}
