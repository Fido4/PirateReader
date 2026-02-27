package com.piratereader.ui.reader

import com.piratereader.epub.EpubTocEntry
import com.piratereader.epub.EpubTocNavigationTargetResolver
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object ReaderTocSelectionResolver {
    fun selectHref(
        entries: List<EpubTocEntry>,
        chapterZipPath: String?,
        anchorFragment: String?,
    ): String? {
        val chapter = chapterZipPath?.trim()?.ifBlank { null } ?: return null
        val chapterTargets = entries.mapNotNull { entry ->
            val target = EpubTocNavigationTargetResolver.resolve(entry.hrefZipPath) ?: return@mapNotNull null
            if (target.chapterZipPath != chapter) return@mapNotNull null
            ChapterTocTarget(entry = entry, fragment = target.fragment)
        }
        if (chapterTargets.isEmpty()) return null

        val anchor = anchorFragment?.trim()?.ifBlank { null }
        if (anchor == null) {
            return chapterTargets.firstOrNull { it.fragment.isNullOrBlank() }?.entry?.hrefZipPath
                ?: chapterTargets.first().entry.hrefZipPath
        }

        chapterTargets.firstOrNull { target ->
            target.fragment.equals(anchor, ignoreCase = true)
        }?.let { return it.entry.hrefZipPath }

        val normalizedAnchor = normalizeFragment(anchor)
        if (normalizedAnchor.isNotBlank()) {
            chapterTargets.lastOrNull { target ->
                val normalizedTarget = normalizeFragment(target.fragment)
                normalizedTarget.isNotBlank() &&
                    (normalizedTarget == normalizedAnchor ||
                        normalizedTarget.contains(normalizedAnchor) ||
                        normalizedAnchor.contains(normalizedTarget))
            }?.let { return it.entry.hrefZipPath }
        }

        return chapterTargets.firstOrNull { it.fragment.isNullOrBlank() }?.entry?.hrefZipPath
            ?: chapterTargets.first().entry.hrefZipPath
    }

    private fun normalizeFragment(fragment: String?): String {
        val decoded = fragment
            ?.trim()
            ?.ifBlank { null }
            ?.let { raw ->
                runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
            }
            ?: return ""
        return buildString(decoded.length) {
            decoded.lowercase(Locale.US).forEach { ch ->
                if (ch.isLetterOrDigit()) append(ch)
            }
        }
    }

    private data class ChapterTocTarget(
        val entry: EpubTocEntry,
        val fragment: String?,
    )
}
