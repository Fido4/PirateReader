package com.piratereader.data.library

enum class LibraryImportProgressStage {
    PREPARING,
    COPYING_SOURCE,
    PARSING_METADATA,
    EXTRACTING_COVER,
    SAVING_LIBRARY,
}

data class LibraryImportProgress(
    val stage: LibraryImportProgressStage,
    val percent: Int,
) {
    val boundedPercent: Int
        get() = percent.coerceIn(0, 100)
}
