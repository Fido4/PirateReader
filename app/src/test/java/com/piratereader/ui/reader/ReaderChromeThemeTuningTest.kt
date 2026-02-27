package com.piratereader.ui.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderChromeThemeTuningTest {
    @Test
    fun tokensFor_nonEinkMode_preservesThemePanelAndMutedColors() {
        val night = ReaderWebViewStyleInjector.themePresets.first { it.id == "night" }

        val tokens = ReaderChromeThemeTuning.tokensFor(
            theme = night,
            isEinkOptimizedMode = false,
            einkContrastPreset = ReaderEinkContrastPreset.BALANCED,
        )

        assertThat(tokens.topBarContent).isEqualTo(night.text)
        assertThat(tokens.topBarAction).isEqualTo(night.link)
        assertThat(tokens.panelContainer).isEqualTo(night.panelBackground)
        assertThat(tokens.panelBorder).isEqualTo(night.panelBorder)
        assertThat(tokens.secondaryText).isEqualTo(night.muted)
        assertThat(tokens.chipContainer).isEqualTo(night.panelBackground)
        assertThat(tokens.chipBorder).isEqualTo(night.panelBorder)
        assertThat(tokens.chipLabel).isEqualTo(night.muted)
        assertThat(tokens.chipLabelActive).isEqualTo(night.link)
        assertThat(tokens.chipBorderActive).isEqualTo(night.link)
        assertThat(tokens.topBarContainer).matches("^#[0-9A-F]{6}$")
        assertThat(tokens.chipContainerActive).matches("^#[0-9A-F]{6}$")
    }

    @Test
    fun tokensFor_highContrastEinkMode_flattensChromeAndStrengthensBorders() {
        val bw = ReaderWebViewStyleInjector.themePresets.first { it.id == "bw" }

        val tokens = ReaderChromeThemeTuning.tokensFor(
            theme = bw,
            isEinkOptimizedMode = true,
            einkContrastPreset = ReaderEinkContrastPreset.HIGH,
        )

        assertThat(tokens.topBarContainer).isEqualTo(bw.background)
        assertThat(tokens.panelContainer).isEqualTo(bw.background)
        assertThat(tokens.topBarContent).isEqualTo(bw.text)
        assertThat(tokens.topBarAction).isEqualTo(bw.text)
        assertThat(tokens.primaryText).isEqualTo(bw.text)
        assertThat(tokens.panelBorder).isNotEqualTo(bw.panelBorder)
        assertThat(tokens.secondaryText).isNotEqualTo(bw.muted)
        assertThat(tokens.chipContainer).isEqualTo(bw.background)
        assertThat(tokens.chipLabelActive).isEqualTo(bw.text)
        assertThat(tokens.chipBorderActive).isEqualTo(bw.text)
        assertThat(tokens.chipBorder).isNotEqualTo(bw.panelBorder)
        assertThat(tokens.panelBorder).matches("^#[0-9A-F]{6}$")
        assertThat(tokens.secondaryText).matches("^#[0-9A-F]{6}$")
        assertThat(tokens.chipContainerActive).matches("^#[0-9A-F]{6}$")
    }
}
