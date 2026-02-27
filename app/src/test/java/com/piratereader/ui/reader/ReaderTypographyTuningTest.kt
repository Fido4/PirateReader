package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderTypographyTuningTest {
    @Test
    fun recommendedDefaults_forPhoneMode_matchCurrentReaderBaseline() {
        val defaults = ReaderTypographyTuning.recommendedDefaults(isEinkOptimizedMode = false)

        assertThat(defaults).isEqualTo(
            ReaderTypographyControlIndices(
                fontIndex = 0,
                textScaleIndex = 1,
                lineHeightIndex = 1,
                marginIndex = 1,
                alignmentIndex = 0,
            ),
        )
    }

    @Test
    fun recommendedDefaults_forEinkMode_useLargerSpacingAndJustify() {
        val defaults = ReaderTypographyTuning.recommendedDefaults(isEinkOptimizedMode = true)

        assertThat(defaults).isEqualTo(
            ReaderTypographyControlIndices(
                fontIndex = 0,
                textScaleIndex = 2,
                lineHeightIndex = 2,
                marginIndex = 2,
                alignmentIndex = 1,
            ),
        )
    }

    @Test
    fun shouldAutoApplyEinkDefaultsOnEnable_onlyWhenReaderStillAtPhoneDefaults() {
        val phoneDefaults = ReaderTypographyTuning.recommendedDefaults(isEinkOptimizedMode = false)
        val custom = phoneDefaults.copy(textScaleIndex = 3)

        assertThat(ReaderTypographyTuning.shouldAutoApplyEinkDefaultsOnEnable(phoneDefaults)).isTrue()
        assertThat(ReaderTypographyTuning.shouldAutoApplyEinkDefaultsOnEnable(custom)).isFalse()
    }
}
