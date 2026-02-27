package com.piratereader.epub

data class EpubTocNavigationTarget(
    val chapterZipPath: String?,
    val fragment: String?,
)

object EpubTocNavigationTargetResolver {
    fun resolve(hrefZipPath: String?): EpubTocNavigationTarget? {
        if (hrefZipPath.isNullOrBlank()) return null

        val hashIndex = hrefZipPath.indexOf('#')
        val rawPath = if (hashIndex >= 0) hrefZipPath.substring(0, hashIndex) else hrefZipPath
        val rawFragment = if (hashIndex >= 0) hrefZipPath.substring(hashIndex + 1) else null

        val chapterZipPath = rawPath
            .substringBefore('?')
            .trim()
            .ifBlank { null }

        val fragment = rawFragment
            ?.substringBefore('?')
            ?.trim()
            ?.ifBlank { null }

        if (chapterZipPath == null && fragment == null) return null
        return EpubTocNavigationTarget(
            chapterZipPath = chapterZipPath,
            fragment = fragment,
        )
    }
}
