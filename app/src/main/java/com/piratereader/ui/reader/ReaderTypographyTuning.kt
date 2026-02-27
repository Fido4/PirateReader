package com.piratereader.ui.reader

data class ReaderTypographyControlIndices(
    val fontIndex: Int,
    val textScaleIndex: Int,
    val lineHeightIndex: Int,
    val marginIndex: Int,
    val alignmentIndex: Int,
)

object ReaderTypographyTuning {
    private val phoneDefaults = ReaderTypographyControlIndices(
        fontIndex = 0, // Literata
        textScaleIndex = 1, // 18px
        lineHeightIndex = 1, // 1.6
        marginIndex = 1, // 20px
        alignmentIndex = 0, // Left
    )

    private val einkDefaults = ReaderTypographyControlIndices(
        fontIndex = 0, // Literata
        textScaleIndex = 2, // 21px
        lineHeightIndex = 2, // 1.8
        marginIndex = 2, // 32px
        alignmentIndex = 1, // Justify
    )

    fun recommendedDefaults(isEinkOptimizedMode: Boolean): ReaderTypographyControlIndices =
        if (isEinkOptimizedMode) einkDefaults else phoneDefaults

    fun shouldAutoApplyEinkDefaultsOnEnable(
        current: ReaderTypographyControlIndices,
    ): Boolean = current == phoneDefaults
}
