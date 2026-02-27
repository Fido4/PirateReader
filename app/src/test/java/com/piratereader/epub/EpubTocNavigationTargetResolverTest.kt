package com.piratereader.epub

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpubTocNavigationTargetResolverTest {
    @Test
    fun resolve_parsesChapterAndFragment() {
        val target = EpubTocNavigationTargetResolver.resolve("OEBPS/ch1.xhtml#intro")

        assertThat(target).isEqualTo(
            EpubTocNavigationTarget(
                chapterZipPath = "OEBPS/ch1.xhtml",
                fragment = "intro",
            ),
        )
    }

    @Test
    fun resolve_supportsFragmentOnlyLink() {
        val target = EpubTocNavigationTargetResolver.resolve("#footnote-1")

        assertThat(target).isEqualTo(
            EpubTocNavigationTarget(
                chapterZipPath = null,
                fragment = "footnote-1",
            ),
        )
    }

    @Test
    fun resolve_ignoresBlankValues() {
        assertThat(EpubTocNavigationTargetResolver.resolve(null)).isNull()
        assertThat(EpubTocNavigationTargetResolver.resolve("   ")).isNull()
        assertThat(EpubTocNavigationTargetResolver.resolve("#")).isNull()
    }

    @Test
    fun resolve_stripsQueryPortionFromPath() {
        val target = EpubTocNavigationTargetResolver.resolve("Text/ch2.xhtml?foo=1#sec")

        assertThat(target?.chapterZipPath).isEqualTo("Text/ch2.xhtml")
        assertThat(target?.fragment).isEqualTo("sec")
    }
}
