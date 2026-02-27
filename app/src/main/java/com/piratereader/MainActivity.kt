package com.piratereader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.File
import com.piratereader.data.library.LibraryBook
import com.piratereader.data.library.PirateReaderDatabase
import com.piratereader.data.library.ReaderAnnotation
import com.piratereader.data.library.ReaderAnnotationRepository
import com.piratereader.data.library.ReaderAnnotationType
import com.piratereader.epub.EpubSpineDocumentLoadResult
import com.piratereader.epub.EpubSpineDocumentLoader
import com.piratereader.epub.EpubTocCodec
import com.piratereader.epub.EpubTocEntry
import com.piratereader.epub.EpubTocNavigationTargetResolver
import com.piratereader.epub.EpubWebViewResourceResolver
import com.piratereader.ui.library.LibraryTocPreviewFormatter
import com.piratereader.ui.library.LibraryUiState
import com.piratereader.ui.library.LibraryViewModel
import com.piratereader.ui.library.LibraryStatusKind
import com.piratereader.ui.library.TocPreviewLine
import com.piratereader.ui.reader.OfflineDictionaryIndexLoader
import com.piratereader.ui.reader.OfflineDictionaryLoadStatus
import com.piratereader.ui.reader.ReaderScaffoldHtml
import com.piratereader.ui.reader.ReaderEinkContrastPreset
import com.piratereader.ui.reader.ReaderPageMode
import com.piratereader.ui.reader.ReaderPositionLocator
import com.piratereader.ui.reader.ReaderPositionLocatorCodec
import com.piratereader.ui.reader.ReaderSelectionText
import com.piratereader.ui.reader.ReaderTextAlign
import com.piratereader.ui.reader.ReaderTocSelectionResolver
import com.piratereader.ui.reader.ReaderChromeThemeTuning
import com.piratereader.ui.reader.ReaderTypographyControlIndices
import com.piratereader.ui.reader.ReaderTypographyTuning
import com.piratereader.ui.reader.ReaderViewportMetrics
import com.piratereader.ui.reader.ReaderViewportMetricsCodec
import com.piratereader.ui.reader.ReaderWebViewStyleInjector
import com.piratereader.ui.reader.ReaderWebViewStyleSettings
import com.piratereader.ui.theme.PirateReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels {
        LibraryViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        setContent {
            PirateReaderTheme {
                val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
                val openDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        takePersistableReadPermissionIfPossible(uri)
                        libraryViewModel.importEpub(uri)
                    }
                }

                PirateReaderApp(
                    uiState = uiState,
                    onImportClick = {
                        openDocumentLauncher.launch(
                            arrayOf("application/epub+zip", "application/octet-stream"),
                        )
                    },
                    onClearStatusMessage = libraryViewModel::clearStatusMessage,
                    onStatusActionBook = libraryViewModel::highlightBook,
                    onCloseReader = libraryViewModel::closeReader,
                    onSaveAndCloseReader = libraryViewModel::saveReaderPositionAndClose,
                    onResumeBook = libraryViewModel::resumeBook,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = extractUriFromIntent(intent) ?: return
        takePersistableReadPermissionIfPossible(uri)
        libraryViewModel.importEpub(uri)
    }

    private fun extractUriFromIntent(intent: Intent?): Uri? =
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }

    private fun takePersistableReadPermissionIfPossible(uri: Uri) {
        if (uri.scheme != "content") return

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PirateReaderApp(
    uiState: LibraryUiState,
    onImportClick: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onStatusActionBook: (Long) -> Unit,
    onCloseReader: () -> Unit,
    onSaveAndCloseReader: (Long, String?, String?, Int?, Int?, String?, String?) -> Unit,
    onResumeBook: (Long) -> Unit,
) {
    uiState.activeReaderBook?.let { readerBook ->
        ReaderScaffoldScreen(
            book = readerBook,
            onSaveAndCloseReader = onSaveAndCloseReader,
            onCloseReaderFallback = onCloseReader,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "PirateReader") },
                actions = {
                    TextButton(onClick = onImportClick) {
                        Text("Import")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "EPUB-first Android reader",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Phase 2 EPUB reader MVP is active: import, resume, TOC navigation, " +
                                "scroll/paginated modes, and reader typography/theme controls are available.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                LibrarySection(
                    uiState = uiState,
                    onImportClick = onImportClick,
                    onClearStatusMessage = onClearStatusMessage,
                    onStatusActionBook = onStatusActionBook,
                    onResumeBook = onResumeBook,
                )
            }

            item {
                SectionCard(
                    title = "Target Devices",
                    entries = listOf(
                        "Pixel 9 (GrapheneOS)",
                        "Boox Note Air 3 (e-ink optimization target)",
                    ),
                )
            }

            item {
                SectionCard(
                    title = "EPUB Theme Parity Goal (Readest-inspired)",
                    entries = listOf(
                        "Match Readest theme set and presets",
                        "Provide e-ink-friendly high-contrast options",
                        "Support both paginated and scrolling modes",
                    ),
                )
            }

            item { FontSection(fonts = ReaderCatalog.launchFonts) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderScaffoldScreen(
    book: LibraryBook,
    onSaveAndCloseReader: (Long, String?, String?, Int?, Int?, String?, String?) -> Unit,
    onCloseReaderFallback: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isTocVisible by rememberSaveable(book.id) { mutableStateOf(false) }
    var chapterOverrideZipPath by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var readerThemeIndex by rememberSaveable(book.id) { mutableStateOf(0) }
    var readerFontIndex by rememberSaveable(book.id) { mutableStateOf(0) }
    var readerTextScaleIndex by rememberSaveable(book.id) { mutableStateOf(1) }
    var readerLineHeightIndex by rememberSaveable(book.id) { mutableStateOf(1) }
    var readerMarginIndex by rememberSaveable(book.id) { mutableStateOf(1) }
    var readerAlignmentIndex by rememberSaveable(book.id) { mutableStateOf(0) }
    var isEinkOptimizedMode by rememberSaveable(book.id) { mutableStateOf(false) }
    var einkContrastPresetIndex by rememberSaveable(book.id) { mutableStateOf(0) }
    var selectedTocHref by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var nextLoadRestoreLocatorSerialized by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var nextLoadRestoreScrollX by rememberSaveable(book.id) { mutableStateOf<Int?>(null) }
    var nextLoadRestoreScrollY by rememberSaveable(book.id) { mutableStateOf<Int?>(null) }
    var readerViewportMetrics by remember(book.id) { mutableStateOf<ReaderViewportMetrics?>(null) }
    val defaultHtml = remember(book.id, book.lastOpenedAt) { ReaderScaffoldHtml.build(book) }
    val tocEntries = remember(book.tocEntriesSerialized) { EpubTocCodec.decode(book.tocEntriesSerialized) }
    val requestedLocator = remember(book.lastReadLocatorSerialized) {
        ReaderPositionLocatorCodec.decode(book.lastReadLocatorSerialized)
    }
    val requestedChapterZipPath = requestedLocator?.chapterZipPath ?: book.lastReadChapterZipPath
    val requestedAnchorFragment = (requestedLocator?.anchorFragment ?: book.lastReadAnchorFragment)
        ?.trim()
        ?.ifBlank { null }
    val requestedPageMode = requestedLocator?.pageMode ?: parsePersistedReaderPageMode(book.lastReadPageMode)
    val requestedScrollX = (requestedLocator?.scrollX ?: book.lastReadScrollX)?.coerceAtLeast(0)
    val requestedScrollY = (requestedLocator?.scrollY ?: book.lastReadScrollY)?.coerceAtLeast(0)
    var isPaginatedMode by rememberSaveable(book.id) {
        mutableStateOf(requestedPageMode == ReaderPageMode.PAGINATED)
    }
    var nextLoadAnchorFragment by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var currentReaderAnchorFragment by rememberSaveable(book.id) { mutableStateOf<String?>(requestedAnchorFragment) }
    var isSearchVisible by rememberSaveable(book.id) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(book.id) { mutableStateOf("") }
    var searchMatchCount by rememberSaveable(book.id) { mutableStateOf(0) }
    var searchCurrentMatch by rememberSaveable(book.id) { mutableStateOf(0) }
    var isAnnotationsVisible by rememberSaveable(book.id) { mutableStateOf(false) }
    var selectedReaderText by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var readerActionStatus by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val readerAnnotationRepository = remember(context) {
        ReaderAnnotationRepository(
            PirateReaderDatabase.getInstance(context.applicationContext).readerAnnotationDao(),
        )
    }
    val readerAnnotations by remember(book.id, readerAnnotationRepository) {
        readerAnnotationRepository.observeForBook(book.id)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(book.id, book.lastReadLocatorSerialized) {
        if (nextLoadRestoreLocatorSerialized == null) {
            nextLoadRestoreLocatorSerialized = book.lastReadLocatorSerialized
        }
    }
    LaunchedEffect(book.id, requestedScrollX) {
        if (nextLoadRestoreScrollX == null) {
            nextLoadRestoreScrollX = requestedScrollX
        }
    }
    LaunchedEffect(book.id, requestedScrollY) {
        if (nextLoadRestoreScrollY == null) {
            nextLoadRestoreScrollY = requestedScrollY
        }
    }
    LaunchedEffect(book.id, requestedAnchorFragment) {
        if (nextLoadAnchorFragment == null) {
            nextLoadAnchorFragment = requestedAnchorFragment
        }
    }
    val preferredChapterZipPath = chapterOverrideZipPath ?: requestedChapterZipPath
    val textSizesPx = remember { listOf(16, 18, 21, 24) }
    val lineHeights = remember { listOf(1.4f, 1.6f, 1.8f, 2.0f) }
    val horizontalMarginsPx = remember { listOf(12, 20, 32, 48) }
    val alignments = remember { listOf(ReaderTextAlign.START, ReaderTextAlign.JUSTIFY, ReaderTextAlign.CENTER) }
    val einkContrastPresets = remember {
        listOf(
            ReaderEinkContrastPreset.BALANCED,
            ReaderEinkContrastPreset.HIGH,
        )
    }
    val themePreset = ReaderWebViewStyleInjector.themePresets[readerThemeIndex % ReaderWebViewStyleInjector.themePresets.size]
    val fontOption = ReaderCatalog.launchFonts[readerFontIndex % ReaderCatalog.launchFonts.size]
    val pageMode = if (isPaginatedMode) ReaderPageMode.PAGINATED else ReaderPageMode.SCROLL
    val alignment = alignments[readerAlignmentIndex.coerceIn(0, alignments.lastIndex)]
    val einkContrastPreset = einkContrastPresets[
        einkContrastPresetIndex.coerceIn(0, einkContrastPresets.lastIndex)
    ]
    val readerChromeTokens = remember(themePreset.id, isEinkOptimizedMode, einkContrastPreset) {
        ReaderChromeThemeTuning.tokensFor(
            theme = themePreset,
            isEinkOptimizedMode = isEinkOptimizedMode,
            einkContrastPreset = einkContrastPreset,
        )
    }
    val readerTopBarContainerColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.topBarContainer,
        fallback = MaterialTheme.colorScheme.surface,
    )
    val readerTopBarContentColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.topBarContent,
        fallback = MaterialTheme.colorScheme.onSurface,
    )
    val readerTopBarActionColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.topBarAction,
        fallback = MaterialTheme.colorScheme.primary,
    )
    val readerPanelContainerColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.panelContainer,
        fallback = MaterialTheme.colorScheme.surfaceVariant,
    )
    val readerPanelBorderColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.panelBorder,
        fallback = MaterialTheme.colorScheme.outlineVariant,
    )
    val readerPrimaryTextColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.primaryText,
        fallback = MaterialTheme.colorScheme.onSurface,
    )
    val readerStatusTextColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.secondaryText,
        fallback = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val readerChipContainerColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipContainer,
        fallback = readerPanelContainerColor,
    )
    val readerChipContainerActiveColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipContainerActive,
        fallback = readerPanelContainerColor,
    )
    val readerChipBorderColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipBorder,
        fallback = readerPanelBorderColor,
    )
    val readerChipBorderActiveColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipBorderActive,
        fallback = readerTopBarActionColor,
    )
    val readerChipLabelColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipLabel,
        fallback = readerStatusTextColor,
    )
    val readerChipLabelActiveColor = composeColorFromHexOrFallback(
        hex = readerChromeTokens.chipLabelActive,
        fallback = readerTopBarActionColor,
    )
    val readerChipDisabledContainerColor = readerChipContainerColor.copy(
        alpha = if (themePreset.isDark) 0.55f else 0.72f,
    )
    val readerChipDisabledBorderColor = readerChipBorderColor.copy(alpha = 0.45f)
    val readerChipDisabledLabelColor = readerChipLabelColor.copy(alpha = 0.55f)

    @Composable
    fun ReaderControlChip(
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        active: Boolean = false,
    ) {
        AssistChip(
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (active) {
                    readerChipContainerActiveColor
                } else {
                    readerChipContainerColor
                },
                labelColor = if (active) {
                    readerChipLabelActiveColor
                } else {
                    readerChipLabelColor
                },
                disabledContainerColor = readerChipDisabledContainerColor,
                disabledLabelColor = readerChipDisabledLabelColor,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    !enabled -> readerChipDisabledBorderColor
                    active -> readerChipBorderActiveColor
                    else -> readerChipBorderColor
                },
            ),
        )
    }
    val currentTypographyIndices = ReaderTypographyControlIndices(
        fontIndex = readerFontIndex,
        textScaleIndex = readerTextScaleIndex,
        lineHeightIndex = readerLineHeightIndex,
        marginIndex = readerMarginIndex,
        alignmentIndex = readerAlignmentIndex,
    )
    val applyRecommendedTypographyDefaults: (Boolean) -> Unit = { einkModeEnabled ->
        val defaults = ReaderTypographyTuning.recommendedDefaults(einkModeEnabled)
        readerFontIndex = defaults.fontIndex.coerceIn(0, ReaderCatalog.launchFonts.lastIndex)
        readerTextScaleIndex = defaults.textScaleIndex.coerceIn(0, textSizesPx.lastIndex)
        readerLineHeightIndex = defaults.lineHeightIndex.coerceIn(0, lineHeights.lastIndex)
        readerMarginIndex = defaults.marginIndex.coerceIn(0, horizontalMarginsPx.lastIndex)
        readerAlignmentIndex = defaults.alignmentIndex.coerceIn(0, alignments.lastIndex)
    }
    val advanceThemePreset: () -> Unit = remember(isEinkOptimizedMode) {
        {
            val themes = ReaderWebViewStyleInjector.themePresets
            if (!isEinkOptimizedMode) {
                readerThemeIndex = (readerThemeIndex + 1) % themes.size
            } else {
                var attempts = 0
                var nextIndex = readerThemeIndex
                while (attempts < themes.size) {
                    nextIndex = (nextIndex + 1) % themes.size
                    if (themes[nextIndex].isEinkPreset) {
                        readerThemeIndex = nextIndex
                        break
                    }
                    attempts += 1
                }
            }
        }
    }
    val styleSettings = remember(
        themePreset.id,
        fontOption.id,
        readerTextScaleIndex,
        readerLineHeightIndex,
        readerMarginIndex,
        readerAlignmentIndex,
        isPaginatedMode,
        isEinkOptimizedMode,
        einkContrastPresetIndex,
    ) {
        ReaderWebViewStyleSettings(
            themeId = themePreset.id,
            fontId = fontOption.id,
            fontSizePx = textSizesPx[readerTextScaleIndex.coerceIn(0, textSizesPx.lastIndex)],
            lineHeight = lineHeights[readerLineHeightIndex.coerceIn(0, lineHeights.lastIndex)],
            horizontalPaddingPx = horizontalMarginsPx[readerMarginIndex.coerceIn(0, horizontalMarginsPx.lastIndex)],
            textAlign = alignment,
            pageMode = pageMode,
            isEinkOptimizedMode = isEinkOptimizedMode,
            einkContrastPreset = einkContrastPreset,
        )
    }
    val readerContentState by produceState<ReaderContentState>(
        initialValue = ReaderContentState.Loading,
        key1 = book.localPath,
        key2 = preferredChapterZipPath,
        key3 = book.lastOpenedAt,
    ) {
        value = withContext(Dispatchers.IO) {
            when (
                val result = EpubSpineDocumentLoader.loadSpineDocumentForReading(
                    epubFile = File(book.localPath),
                    preferredChapterZipPath = preferredChapterZipPath,
                )
            ) {
                is EpubSpineDocumentLoadResult.Success -> ReaderContentState.Loaded(result)
                is EpubSpineDocumentLoadResult.Failure -> ReaderContentState.Failed(result.userFacingMessage())
            }
        }
    }
    val offlineDictionaryStatus by produceState<OfflineDictionaryLoadStatus>(
        initialValue = OfflineDictionaryLoadStatus.Missing(
            expectedPath = OfflineDictionaryIndexLoader.DEFAULT_DICTIONARY_FILE_NAME,
        ),
        key1 = book.localPath,
    ) {
        value = withContext(Dispatchers.IO) {
            OfflineDictionaryIndexLoader.loadForBookLocalPath(book.localPath)
        }
    }
    val selectionDictionaryLookup = remember(selectedReaderText, offlineDictionaryStatus) {
        ReaderSelectionText.lookupSelectionDefinition(
            selection = selectedReaderText,
            dictionaryStatus = offlineDictionaryStatus,
        )
    }
    val webViewHtml = when (val state = readerContentState) {
        is ReaderContentState.Loaded -> ReaderWebViewStyleInjector.injectIntoChapterMarkup(
            chapterMarkup = state.result.document.chapterMarkup,
            settings = styleSettings,
        )
        else -> defaultHtml
    }
    val webViewBaseUrl = when (val state = readerContentState) {
        is ReaderContentState.Loaded -> EpubWebViewResourceResolver.chapterBaseUrl(
            state.result.document.chapterZipPath,
        )
        else -> null
    }
    val chapterZipPathForResources = (readerContentState as? ReaderContentState.Loaded)
        ?.result
        ?.document
        ?.chapterZipPath
    val loadedReaderDocument = (readerContentState as? ReaderContentState.Loaded)?.result?.document
    val currentLoadedChapterZipPath = chapterZipPathForResources
    val currentChapterTocFragments = remember(tocEntries, currentLoadedChapterZipPath) {
        val chapterPath = currentLoadedChapterZipPath ?: return@remember emptyList()
        tocEntries.mapNotNull { entry ->
            val target = EpubTocNavigationTargetResolver.resolve(entry.hrefZipPath) ?: return@mapNotNull null
            if (target.chapterZipPath != chapterPath) return@mapNotNull null
            target.fragment?.trim()?.ifBlank { null }
        }.distinct()
    }
    val setSelectedTocForLocation: (String?, String?) -> Unit = remember(tocEntries) {
        { chapterZipPath: String?, fragment: String? ->
            selectedTocHref = ReaderTocSelectionResolver.selectHref(
                entries = tocEntries,
                chapterZipPath = chapterZipPath,
                anchorFragment = fragment,
            )
        }
    }
    LaunchedEffect(currentLoadedChapterZipPath, tocEntries) {
        val chapterPath = currentLoadedChapterZipPath ?: return@LaunchedEffect
        val selectedTarget = EpubTocNavigationTargetResolver.resolve(selectedTocHref)
        if (selectedTarget?.chapterZipPath == chapterPath) return@LaunchedEffect
        setSelectedTocForLocation(chapterPath, null)
    }
    val handleViewportMetrics: (ReaderViewportMetrics) -> Unit = viewport@{ metrics ->
        readerViewportMetrics = metrics
        val chapterPath = currentLoadedChapterZipPath ?: return@viewport
        val viewportAnchor = metrics.anchorFragment?.trim()?.ifBlank { null }
        val tocAnchor = metrics.tocAnchorFragment?.trim()?.ifBlank { null }
        if (viewportAnchor != null) {
            currentReaderAnchorFragment = viewportAnchor
        } else if (tocAnchor != null) {
            // Prefer a TOC-backed anchor over clearing state when generic DOM anchors are sparse.
            currentReaderAnchorFragment = tocAnchor
        } else if ((metrics.scrollY ?: 1) == 0 && pageMode == ReaderPageMode.SCROLL) {
            currentReaderAnchorFragment = null
        } else if ((metrics.pageIndex ?: 1) == 1 && pageMode == ReaderPageMode.PAGINATED) {
            currentReaderAnchorFragment = null
        }
        setSelectedTocForLocation(chapterPath, tocAnchor ?: viewportAnchor ?: currentReaderAnchorFragment)
    }
    val navigateToChapter: (String, String?, Int?, Int?, String?) -> Unit = {
            chapterZipPath,
            fragment,
            restoreScrollX,
            restoreScrollY,
            restoreLocatorSerialized,
        ->
        nextLoadRestoreLocatorSerialized = restoreLocatorSerialized?.trim()?.ifBlank { null }
        nextLoadRestoreScrollX = restoreScrollX?.coerceAtLeast(0)
        nextLoadRestoreScrollY = restoreScrollY?.coerceAtLeast(0)
        nextLoadAnchorFragment = fragment?.trim()?.ifBlank { null }
        currentReaderAnchorFragment = nextLoadAnchorFragment
        (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)?.setPendingAnchorFragment(fragment)
        chapterOverrideZipPath = chapterZipPath
        setSelectedTocForLocation(chapterZipPath, fragment)
    }
    val applySearchQuery: () -> Unit = applySearchQuery@{
        val normalized = searchQuery.trim()
        val webView = webViewRef
        if (webView == null) {
            readerActionStatus = "Search is unavailable until the chapter finishes loading."
            return@applySearchQuery
        }
        if (normalized.isEmpty()) {
            webView.clearMatches()
            searchMatchCount = 0
            searchCurrentMatch = 0
            readerActionStatus = "Search cleared."
        } else {
            webView.findAllAsync(normalized)
        }
    }
    val bookmarkCurrentLocation: () -> Unit = bookmarkCurrentLocation@{
        val chapterPath = currentLoadedChapterZipPath
        if (chapterPath == null) {
            readerActionStatus = "Bookmark failed: chapter is not loaded."
            return@bookmarkCurrentLocation
        }
        val locator = buildReaderPositionLocator(
            chapterZipPath = chapterPath,
            anchorFragment = currentReaderAnchorFragment ?: readerViewportMetrics?.anchorFragment,
            pageMode = pageMode,
            metrics = readerViewportMetrics,
            fallbackScrollX = webViewRef?.scrollX,
            fallbackScrollY = webViewRef?.scrollY,
        )
        val serializedLocator = ReaderPositionLocatorCodec.encode(locator)
        coroutineScope.launch {
            runCatching {
                readerAnnotationRepository.addBookmark(
                    bookId = book.id,
                    chapterZipPath = chapterPath,
                    anchorFragment = currentReaderAnchorFragment ?: readerViewportMetrics?.anchorFragment,
                    locatorSerialized = serializedLocator,
                )
            }.onSuccess {
                readerActionStatus = "Bookmark saved."
            }.onFailure { error ->
                readerActionStatus = "Bookmark failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    val highlightSelectedText: () -> Unit = highlightSelectedText@{
        val chapterPath = currentLoadedChapterZipPath
        val selection = ReaderSelectionText.normalizeSelection(selectedReaderText)
        if (chapterPath == null || selection.isNullOrBlank()) {
            readerActionStatus = "Highlight failed: select text first."
            return@highlightSelectedText
        }
        val locator = buildReaderPositionLocator(
            chapterZipPath = chapterPath,
            anchorFragment = currentReaderAnchorFragment ?: readerViewportMetrics?.anchorFragment,
            pageMode = pageMode,
            metrics = readerViewportMetrics,
            fallbackScrollX = webViewRef?.scrollX,
            fallbackScrollY = webViewRef?.scrollY,
        )
        val serializedLocator = ReaderPositionLocatorCodec.encode(locator)
        coroutineScope.launch {
            runCatching {
                readerAnnotationRepository.addHighlight(
                    bookId = book.id,
                    chapterZipPath = chapterPath,
                    anchorFragment = currentReaderAnchorFragment ?: readerViewportMetrics?.anchorFragment,
                    selectedText = selection,
                    locatorSerialized = serializedLocator,
                )
            }.onSuccess {
                readerActionStatus = "Highlight saved."
            }.onFailure { error ->
                readerActionStatus = "Highlight failed: ${error.message ?: "unknown error"}"
            }
        }
    }
    val jumpToAnnotation: (ReaderAnnotation) -> Unit = annotationJump@{ annotation ->
        val locator = ReaderPositionLocatorCodec.decode(annotation.locatorSerialized)
        if (annotation.chapterZipPath == currentLoadedChapterZipPath) {
            val targetAnchor = annotation.anchorFragment?.trim()?.ifBlank { null }
            if (targetAnchor != null) {
                currentReaderAnchorFragment = targetAnchor
                (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)?.scrollToAnchor(
                    webViewRef,
                    targetAnchor,
                )
                return@annotationJump
            }
            val restoreX = locator?.scrollX ?: 0
            val restoreY = locator?.scrollY ?: 0
            webViewRef?.scrollTo(restoreX, restoreY)
            (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)?.requestViewportMetrics(
                webViewRef,
                force = true,
            )
            return@annotationJump
        }
        navigateToChapter(
            annotation.chapterZipPath,
            annotation.anchorFragment,
            locator?.scrollX,
            locator?.scrollY,
            annotation.locatorSerialized,
        )
    }
    val jumpToTocEntry: (EpubTocEntry) -> Unit = tocJump@{ entry ->
        val target = EpubTocNavigationTargetResolver.resolve(entry.hrefZipPath) ?: return@tocJump
        val readerClient = (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)
        selectedTocHref = entry.hrefZipPath

        when {
            target.chapterZipPath != null && target.chapterZipPath != currentLoadedChapterZipPath -> {
                navigateToChapter(target.chapterZipPath, target.fragment, 0, 0)
            }

            target.fragment != null -> {
                currentReaderAnchorFragment = target.fragment
                readerClient?.scrollToAnchor(webViewRef, target.fragment)
            }

            target.chapterZipPath != null && target.chapterZipPath == currentLoadedChapterZipPath -> {
                currentReaderAnchorFragment = null
                webViewRef?.scrollTo(0, 0)
            }
        }
    }
    val handleInternalReaderChapterNavigation: (String, String?) -> Unit = { chapterZipPath, fragment ->
        if (chapterZipPath == currentLoadedChapterZipPath) {
            if (!fragment.isNullOrBlank()) {
                currentReaderAnchorFragment = fragment
                setSelectedTocForLocation(chapterZipPath, fragment)
            } else {
                currentReaderAnchorFragment = null
            }
        } else {
            navigateToChapter(chapterZipPath, fragment, 0, 0)
        }
    }
    val closeReader = {
        val loadedDocument = (readerContentState as? ReaderContentState.Loaded)?.result?.document
        if (loadedDocument != null) {
            val latestMetrics = readerViewportMetrics
            val savedScrollX = latestMetrics?.scrollX ?: webViewRef?.scrollX ?: requestedScrollX
            val savedScrollY = latestMetrics?.scrollY ?: webViewRef?.scrollY ?: requestedScrollY
            val locator = buildReaderPositionLocator(
                chapterZipPath = loadedDocument.chapterZipPath,
                anchorFragment = currentReaderAnchorFragment ?: latestMetrics?.anchorFragment,
                pageMode = pageMode,
                metrics = latestMetrics,
                fallbackScrollX = savedScrollX,
                fallbackScrollY = savedScrollY,
            )
            onSaveAndCloseReader(
                book.id,
                loadedDocument.chapterZipPath,
                currentReaderAnchorFragment,
                savedScrollX,
                savedScrollY,
                pageMode.name,
                ReaderPositionLocatorCodec.encode(locator),
            )
        } else {
            onCloseReaderFallback()
        }
    }
    BackHandler(onBack = closeReader)
    DisposableEffect(Unit) {
        onDispose { webViewRef = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = readerTopBarContainerColor,
                    titleContentColor = readerTopBarContentColor,
                    actionIconContentColor = readerTopBarActionColor,
                ),
                title = {
                    Column {
                        Text(
                            text = book.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "EPUB Reader (Phase 3 Polish)",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerStatusTextColor,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = closeReader) {
                        Text("Library", color = readerTopBarActionColor)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = readerPanelContainerColor,
                ),
                border = BorderStroke(1.dp, readerPanelBorderColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderControlChip(
                            onClick = { isPaginatedMode = true },
                            active = isPaginatedMode,
                            label = {
                                Text(
                                    if (isPaginatedMode) {
                                        "Paginated • on"
                                    } else {
                                        "Paginated"
                                    },
                                )
                            },
                        )
                        ReaderControlChip(
                            onClick = { isPaginatedMode = false },
                            active = !isPaginatedMode,
                            label = {
                                Text(
                                    if (!isPaginatedMode) {
                                        "Scroll • on"
                                    } else {
                                        "Scroll"
                                    },
                                )
                            },
                        )
                        if (tocEntries.isNotEmpty()) {
                            ReaderControlChip(
                                onClick = { isTocVisible = !isTocVisible },
                                active = isTocVisible,
                                label = { Text(if (isTocVisible) "Hide TOC" else "TOC") },
                            )
                        }
                        ReaderControlChip(
                            onClick = {
                                advanceThemePreset()
                            },
                            label = { Text("Theme: ${themePreset.displayName}") },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderControlChip(
                            onClick = {
                                readerFontIndex = (readerFontIndex + 1) % ReaderCatalog.launchFonts.size
                            },
                            label = { Text("Font: ${fontOption.displayName}") },
                        )
                        ReaderControlChip(
                            onClick = {
                                readerTextScaleIndex = (readerTextScaleIndex + 1) % textSizesPx.size
                            },
                            label = { Text("Text: ${textSizesPx[readerTextScaleIndex]}px") },
                        )
                        ReaderControlChip(
                            onClick = {
                                readerLineHeightIndex = (readerLineHeightIndex + 1) % lineHeights.size
                            },
                            label = { Text("Line: ${lineHeights[readerLineHeightIndex]}") },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderControlChip(
                            onClick = {
                                val enabling = !isEinkOptimizedMode
                                val shouldAutoApplyEinkTypography =
                                    enabling &&
                                        ReaderTypographyTuning.shouldAutoApplyEinkDefaultsOnEnable(
                                            currentTypographyIndices,
                                        )
                                isEinkOptimizedMode = enabling
                                if (enabling) {
                                    if (!isPaginatedMode) {
                                        isPaginatedMode = true
                                    }
                                    if (!themePreset.isEinkPreset) {
                                        val einkIndex = ReaderWebViewStyleInjector.themePresets
                                            .indexOfFirst { it.isEinkPreset }
                                        if (einkIndex >= 0) {
                                            readerThemeIndex = einkIndex
                                        }
                                    }
                                    if (shouldAutoApplyEinkTypography) {
                                        applyRecommendedTypographyDefaults(true)
                                    }
                                }
                            },
                            active = isEinkOptimizedMode,
                            label = {
                                Text(
                                    if (isEinkOptimizedMode) {
                                        "E-Ink • on"
                                    } else {
                                        "E-Ink"
                                    },
                                )
                            },
                        )
                        ReaderControlChip(
                            onClick = {
                                readerMarginIndex = (readerMarginIndex + 1) % horizontalMarginsPx.size
                            },
                            label = { Text("Margins: ${horizontalMarginsPx[readerMarginIndex]}") },
                        )
                        ReaderControlChip(
                            onClick = {
                                readerAlignmentIndex = (readerAlignmentIndex + 1) % alignments.size
                            },
                            label = {
                                Text(
                                    "Align: ${ReaderWebViewStyleInjector.textAlignDisplayName(alignment)}",
                                )
                            },
                        )
                        ReaderControlChip(
                            onClick = {
                                (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)
                                    ?.navigateByViewport(
                                        webView = webViewRef,
                                        pageMode = pageMode,
                                        direction = -1,
                                    )
                            },
                            label = { Text("Prev") },
                        )
                        ReaderControlChip(
                            onClick = {
                                (webViewRef?.webViewClient as? ReaderScaffoldWebViewClient)
                                    ?.navigateByViewport(
                                        webView = webViewRef,
                                        pageMode = pageMode,
                                        direction = 1,
                                    )
                            },
                            label = { Text("Next") },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReaderControlChip(
                            onClick = { applyRecommendedTypographyDefaults(isEinkOptimizedMode) },
                            label = { Text("Reset Type") },
                        )
                        if (isEinkOptimizedMode) {
                            ReaderControlChip(
                                onClick = {
                                    einkContrastPresetIndex =
                                        (einkContrastPresetIndex + 1) % einkContrastPresets.size
                                },
                                label = {
                                    Text(
                                        "Contrast: ${
                                            ReaderWebViewStyleInjector.einkContrastDisplayName(
                                                einkContrastPreset,
                                            )
                                        }",
                                    )
                                },
                            )
                        }
                    }
                    if (loadedReaderDocument != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderControlChip(
                                onClick = {
                                    loadedReaderDocument.previousChapterZipPath?.let { previousChapter ->
                                        navigateToChapter(previousChapter, null, 0, 0)
                                    }
                                },
                                enabled = loadedReaderDocument.previousChapterZipPath != null,
                                label = { Text("Prev Chapter") },
                            )
                            ReaderControlChip(
                                onClick = {
                                    loadedReaderDocument.nextChapterZipPath?.let { nextChapter ->
                                        navigateToChapter(nextChapter, null, 0, 0)
                                    }
                                },
                                enabled = loadedReaderDocument.nextChapterZipPath != null,
                                label = { Text("Next Chapter") },
                            )
                        }
                    }
                    when (val state = readerContentState) {
                        ReaderContentState.Loading -> {
                            Text(
                                text = "Loading first EPUB spine document...",
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                            )
                        }

                        is ReaderContentState.Loaded -> {
                            Text(
                                text = "Loaded chapter ${state.result.document.spineIndex + 1}/" +
                                    "${state.result.document.spineItemCount}: " +
                                    state.result.document.chapterZipPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Resource interception enabled for package-relative CSS/images/fonts",
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                            )
                            val dictionaryStatusText = when (val status = offlineDictionaryStatus) {
                                is OfflineDictionaryLoadStatus.Ready ->
                                    "Offline dictionary: ${status.index.sourceFileName} (${status.index.entryCount} entries) loaded from library root."
                                is OfflineDictionaryLoadStatus.Missing ->
                                    "Offline dictionary: place ${OfflineDictionaryIndexLoader.DEFAULT_DICTIONARY_FILE_NAME} in the root library folder to enable long-press definitions."
                                is OfflineDictionaryLoadStatus.Invalid ->
                                    "Offline dictionary: found but invalid (${status.reason})."
                            }
                            Text(
                                text = dictionaryStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Reader CSS injection active: ${themePreset.displayName} • " +
                                    "${fontOption.displayName} • ${styleSettings.fontSizePx}px • " +
                                    "${ReaderWebViewStyleInjector.pageModeDisplayName(pageMode)} • " +
                                    "${ReaderWebViewStyleInjector.textAlignDisplayName(alignment)}" +
                                    if (isEinkOptimizedMode) {
                                        " • E-Ink ${ReaderWebViewStyleInjector.einkContrastDisplayName(einkContrastPreset)}"
                                    } else {
                                        ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isEinkOptimizedMode) {
                                Text(
                                    text = "E-Ink mode: disables nonessential motion, prefers paginated mode, reduces viewport metric polling churn, supports a ${ReaderWebViewStyleInjector.einkContrastDisplayName(einkContrastPreset)} contrast preset, and can apply tuned typography defaults via Reset Type.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerStatusTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            readerViewportMetrics?.let { metrics ->
                                val viewportStatus = when (pageMode) {
                                    ReaderPageMode.PAGINATED -> buildString {
                                        append("Paginated viewport: page ")
                                        append(metrics.pageIndex ?: "?")
                                        append(" / ")
                                        append(metrics.pageCount ?: "?")
                                        metrics.anchorFragment?.let { anchor ->
                                            append(" • anchor #")
                                            append(anchor)
                                        }
                                    }

                                    ReaderPageMode.SCROLL -> buildString {
                                        append("Scroll viewport: y=")
                                        append(metrics.scrollY ?: 0)
                                        metrics.anchorFragment?.let { anchor ->
                                            append(" • anchor #")
                                            append(anchor)
                                        }
                                    }
                                }
                                Text(
                                    text = viewportStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerStatusTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "Chapter controls: spine prev/next buttons plus TOC chapter jumps (anchor-aware)",
                                style = MaterialTheme.typography.bodySmall,
                                color = readerStatusTextColor,
                            )
                            if (
                                requestedChapterZipPath != null ||
                                requestedScrollX != null ||
                                requestedScrollY != null ||
                                requestedPageMode != null
                            ) {
                                Text(
                                    text = "Resume target: " +
                                        (requestedChapterZipPath ?: "first chapter") +
                                        " • " +
                                        ReaderWebViewStyleInjector.pageModeDisplayName(
                                            requestedPageMode ?: ReaderPageMode.SCROLL,
                                        ) +
                                        " @ x=${requestedScrollX ?: 0}, y=${requestedScrollY ?: 0}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = readerStatusTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        is ReaderContentState.Failed -> {
                            Text(
                                text = "Reader fallback: ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (isTocVisible && tocEntries.isNotEmpty()) {
                ReaderTocPanel(
                    entries = tocEntries,
                    currentChapterZipPath = currentLoadedChapterZipPath,
                    selectedTocHref = selectedTocHref,
                    containerColor = readerPanelContainerColor,
                    borderColor = readerPanelBorderColor,
                    titleColor = readerPrimaryTextColor,
                    entryPrimaryColor = readerPrimaryTextColor,
                    entryAccentColor = readerTopBarActionColor,
                    secondaryTextColor = readerStatusTextColor,
                    onSelectEntry = jumpToTocEntry,
                )
            }

            val pendingRestoreLocator = ReaderPositionLocatorCodec.decode(nextLoadRestoreLocatorSerialized)
            val transientReloadLocator = buildReaderPositionLocator(
                chapterZipPath = currentLoadedChapterZipPath,
                anchorFragment = currentReaderAnchorFragment ?: readerViewportMetrics?.anchorFragment,
                pageMode = pageMode,
                metrics = readerViewportMetrics,
                fallbackScrollX = webViewRef?.scrollX,
                fallbackScrollY = webViewRef?.scrollY,
            )

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val readerClient = ReaderScaffoldWebViewClient(
                        epubFilePath = book.localPath,
                        onInternalChapterNavigation = handleInternalReaderChapterNavigation,
                        onViewportMetrics = handleViewportMetrics,
                    ).apply {
                        setCallbacks(
                            onInternalChapterNavigation = handleInternalReaderChapterNavigation,
                            onViewportMetrics = handleViewportMetrics,
                        )
                        setChapterZipPath(chapterZipPathForResources)
                        setPageMode(pageMode)
                        setEinkOptimizedMode(isEinkOptimizedMode)
                        setTocAnchorFragments(currentChapterTocFragments)
                        setPendingRestoreLocator(pendingRestoreLocator)
                        setPendingRestoreScroll(nextLoadRestoreScrollX, nextLoadRestoreScrollY)
                        setPendingAnchorFragment(nextLoadAnchorFragment)
                    }
                    WebView(context).apply {
                        webViewRef = this
                        webViewClient = readerClient
                        setOnScrollChangeListener { v, _, _, _, _ ->
                            (v as? WebView)?.let { scrolledWebView ->
                                (scrolledWebView.webViewClient as? ReaderScaffoldWebViewClient)
                                    ?.requestViewportMetrics(scrolledWebView)
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        setBackgroundColor(
                            runCatching { android.graphics.Color.parseColor(themePreset.background) }
                                .getOrDefault(android.graphics.Color.TRANSPARENT),
                        )
                    }
                },
                update = { webView ->
                    val contentSignature = buildString {
                        append(webViewBaseUrl.orEmpty())
                        append('|')
                        append(chapterZipPathForResources.orEmpty())
                        append('|')
                        append(webViewHtml.hashCode())
                    }
                    (webView.webViewClient as? ReaderScaffoldWebViewClient)?.let { client ->
                        client.setCallbacks(
                            onInternalChapterNavigation = handleInternalReaderChapterNavigation,
                            onViewportMetrics = handleViewportMetrics,
                        )
                        client.setChapterZipPath(chapterZipPathForResources)
                        client.setPageMode(pageMode)
                        client.setEinkOptimizedMode(isEinkOptimizedMode)
                        client.setTocAnchorFragments(currentChapterTocFragments)
                        if ((webView.tag as? String) != contentSignature) {
                            client.setPendingRestoreLocator(pendingRestoreLocator ?: transientReloadLocator)
                            client.setPendingRestoreScroll(
                                nextLoadRestoreScrollX ?: webView.scrollX,
                                nextLoadRestoreScrollY ?: webView.scrollY,
                            )
                            client.setPendingAnchorFragment(nextLoadAnchorFragment)
                        }
                    }
                    webView.setBackgroundColor(
                        runCatching { android.graphics.Color.parseColor(themePreset.background) }
                            .getOrDefault(android.graphics.Color.TRANSPARENT),
                    )
                    if ((webView.tag as? String) != contentSignature) {
                        webView.tag = contentSignature
                        webView.loadDataWithBaseURL(
                            webViewBaseUrl,
                            webViewHtml,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        nextLoadAnchorFragment = null
                        nextLoadRestoreLocatorSerialized = null
                        nextLoadRestoreScrollX = null
                        nextLoadRestoreScrollY = null
                    }
                },
            )
        }
    }
}

private class ReaderScaffoldWebViewClient(
    private val epubFilePath: String,
    private val onInternalChapterNavigation: (String, String?) -> Unit,
    private val onViewportMetrics: (ReaderViewportMetrics) -> Unit,
) : WebViewClient() {
    private var onInternalChapterNavigationCallback: (String, String?) -> Unit = onInternalChapterNavigation
    private var onViewportMetricsCallback: (ReaderViewportMetrics) -> Unit = onViewportMetrics
    private var chapterZipPath: String? = null
    private var pageMode: ReaderPageMode = ReaderPageMode.SCROLL
    private var einkOptimizedMode: Boolean = false
    private var tocAnchorFragments: List<String> = emptyList()
    private var pendingRestoreLocator: ReaderPositionLocator? = null
    private var pendingRestoreScrollX: Int? = null
    private var pendingRestoreScrollY: Int? = null
    private var pendingAnchorFragment: String? = null
    private var lastViewportMetricsRequestUptimeMs: Long = 0L
    private var viewportMetricsRequestScheduled: Boolean = false

    fun setChapterZipPath(chapterZipPath: String?) {
        this.chapterZipPath = chapterZipPath
    }

    fun setCallbacks(
        onInternalChapterNavigation: (String, String?) -> Unit,
        onViewportMetrics: (ReaderViewportMetrics) -> Unit,
    ) {
        onInternalChapterNavigationCallback = onInternalChapterNavigation
        onViewportMetricsCallback = onViewportMetrics
    }

    fun setPageMode(pageMode: ReaderPageMode) {
        this.pageMode = pageMode
    }

    fun setEinkOptimizedMode(enabled: Boolean) {
        einkOptimizedMode = enabled
    }

    fun setTocAnchorFragments(fragments: List<String>) {
        tocAnchorFragments = fragments
            .mapNotNull { it.trim().ifBlank { null } }
            .distinct()
            .take(128)
    }

    fun setPendingRestoreLocator(locator: ReaderPositionLocator?) {
        pendingRestoreLocator = locator
    }

    fun setPendingRestoreScroll(
        scrollX: Int?,
        scrollY: Int?,
    ) {
        pendingRestoreScrollX = scrollX?.coerceAtLeast(0)
        pendingRestoreScrollY = scrollY?.coerceAtLeast(0)
    }

    fun setPendingAnchorFragment(fragment: String?) {
        pendingAnchorFragment = fragment?.trim()?.ifBlank { null }
        if (pendingAnchorFragment != null) {
            pendingRestoreLocator = null
            pendingRestoreScrollX = null
            pendingRestoreScrollY = null
        }
    }

    fun scrollToAnchor(
        webView: WebView?,
        fragment: String?,
    ) {
        val view = webView ?: return
        val normalized = fragment?.trim()?.ifBlank { null } ?: return
        view.post {
            view.evaluateJavascript(
                buildScrollToAnchorJavascript(normalized),
            ) {
                requestViewportMetrics(view, force = true)
            }
        }
    }

    fun navigateByViewport(
        webView: WebView?,
        pageMode: ReaderPageMode,
        direction: Int,
    ) {
        val view = webView ?: return
        val stepDirection = direction.coerceIn(-1, 1).takeIf { it != 0 } ?: return
        view.post {
            view.evaluateJavascript(
                buildViewportNavigationJavascript(
                    pageMode = pageMode,
                    direction = stepDirection,
                ),
            ) {
                requestViewportMetrics(view, force = true)
            }
        }
    }

    fun requestViewportMetrics(
        webView: WebView?,
        force: Boolean = false,
    ) {
        val view = webView ?: return
        val now = SystemClock.uptimeMillis()
        val debounceMs = viewportMetricsDebounceMs()
        if (!force && now - lastViewportMetricsRequestUptimeMs < debounceMs) {
            if (!viewportMetricsRequestScheduled) {
                viewportMetricsRequestScheduled = true
                view.postDelayed({
                    viewportMetricsRequestScheduled = false
                    requestViewportMetrics(view, force = true)
                }, debounceMs)
            }
            return
        }
        lastViewportMetricsRequestUptimeMs = now
        viewportMetricsRequestScheduled = false
        view.evaluateJavascript(
            buildReaderViewportMetricsJavascript(
                pageMode = pageMode,
                tocAnchorFragments = tocAnchorFragments,
            ),
        ) { rawResult ->
            ReaderViewportMetricsCodec.parseEvaluateJavascriptResult(rawResult)?.let(onViewportMetricsCallback)
        }
    }

    private fun viewportMetricsDebounceMs(): Long =
        if (einkOptimizedMode) 420L else 180L

    override fun shouldInterceptRequest(
        view: WebView?,
        url: String?,
    ): WebResourceResponse? {
        return url?.let(::interceptUrl)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
    ): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null
        return interceptUrl(requestUrl)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        url: String?,
    ): Boolean {
        val requestUrl = url ?: return false
        return handleInternalNavigationRequest(view, requestUrl)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: android.webkit.WebResourceRequest?,
    ): Boolean {
        val requestUrl = request?.url?.toString() ?: return false
        return handleInternalNavigationRequest(view, requestUrl)
    }

    private fun interceptUrl(requestUrl: String): WebResourceResponse? {
        val chapterZipPath = chapterZipPath ?: return null
        val resource = EpubWebViewResourceResolver.loadResource(
            epubFile = File(epubFilePath),
            chapterZipPath = chapterZipPath,
            requestUrl = requestUrl,
        ) ?: return null

        return WebResourceResponse(
            resource.mimeType,
            resource.encoding,
            EpubWebViewResourceResolver.toInputStream(resource),
        )
    }

    private fun handleInternalNavigationRequest(
        view: WebView?,
        requestUrl: String,
    ): Boolean {
        val currentChapter = chapterZipPath ?: return false
        val targetZipPath = EpubWebViewResourceResolver.resolveRequestZipPath(
            chapterZipPath = currentChapter,
            requestUrl = requestUrl,
        ) ?: return false
        if (!targetZipPath.looksLikeHtmlDocumentZipPath()) return false

        val fragment = Uri.parse(requestUrl).fragment?.trim()?.ifBlank { null }
        if (targetZipPath == currentChapter) {
            if (fragment != null) {
                scrollToAnchor(view, fragment)
                onInternalChapterNavigationCallback(targetZipPath, fragment)
                return true
            }
            return false
        }

        setPendingAnchorFragment(fragment)
        onInternalChapterNavigationCallback(targetZipPath, fragment)
        return true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        val anchorFragment = pendingAnchorFragment
        if (!anchorFragment.isNullOrBlank()) {
            pendingAnchorFragment = null
            scrollToAnchor(view, anchorFragment)
            return
        }
        val restoreLocator = pendingRestoreLocator
        val restoreX = pendingRestoreScrollX
        val restoreY = pendingRestoreScrollY
        pendingRestoreLocator = null
        pendingRestoreScrollX = null
        pendingRestoreScrollY = null
        val restoreScript = buildReaderRestoreFromLocatorJavascript(
            pageMode = pageMode,
            locator = restoreLocator,
            fallbackScrollX = restoreX,
            fallbackScrollY = restoreY,
        )
        if (!restoreScript.isNullOrBlank()) {
            view?.post {
                view.evaluateJavascript(restoreScript) {
                    requestViewportMetrics(view, force = true)
                }
            }
            return
        }
        if (restoreX != null || restoreY != null) {
            view?.post {
                view.scrollTo(restoreX ?: 0, restoreY ?: 0)
                requestViewportMetrics(view, force = true)
            }
            return
        }
        requestViewportMetrics(view, force = true)
    }
}

private fun String.looksLikeHtmlDocumentZipPath(): Boolean {
    val ext = substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext == "xhtml" || ext == "html" || ext == "htm"
}

private sealed interface ReaderContentState {
    data object Loading : ReaderContentState

    data class Loaded(val result: EpubSpineDocumentLoadResult.Success) : ReaderContentState

    data class Failed(val message: String) : ReaderContentState
}

@Composable
private fun ReaderTocPanel(
    entries: List<EpubTocEntry>,
    currentChapterZipPath: String?,
    selectedTocHref: String?,
    containerColor: Color,
    borderColor: Color,
    titleColor: Color,
    entryPrimaryColor: Color,
    entryAccentColor: Color,
    secondaryTextColor: Color,
    onSelectEntry: (EpubTocEntry) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Table of Contents",
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = entries,
                    key = { entry -> "${entry.hrefZipPath}|${entry.depth}|${entry.label}" },
                ) { entry ->
                    val indentDp = (entry.depth.coerceIn(0, 6) * 12).dp
                    val target = EpubTocNavigationTargetResolver.resolve(entry.hrefZipPath)
                    val chapterPath = target?.chapterZipPath
                    val isCurrentChapter = chapterPath != null && chapterPath == currentChapterZipPath
                    val isSelectedTocTarget = selectedTocHref != null && selectedTocHref == entry.hrefZipPath
                    val tocEntryColor = when {
                        isSelectedTocTarget -> entryAccentColor
                        isCurrentChapter -> entryPrimaryColor
                        else -> secondaryTextColor
                    }
                    TextButton(
                        onClick = { onSelectEntry(entry) },
                        enabled = target != null,
                        modifier = Modifier.padding(start = indentDp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = tocEntryColor,
                            disabledContentColor = secondaryTextColor.copy(alpha = 0.55f),
                        ),
                    ) {
                        Text(
                            text = buildString {
                                append(entry.label)
                                if (isSelectedTocTarget) append(" (selected)")
                                if (isCurrentChapter) append(" (current chapter)")
                            },
                            fontWeight = if (isSelectedTocTarget || isCurrentChapter) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text = "TOC navigation reloads target chapters and uses scripted anchor scroll for better fragment reliability.",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    entries: List<String>,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            entries.forEach { entry ->
                Text(
                    text = "• $entry",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private enum class LibraryPresentationMode {
    LIST,
    GRID,
}

@Composable
private fun LibrarySection(
    uiState: LibraryUiState,
    onImportClick: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onStatusActionBook: (Long) -> Unit,
    onResumeBook: (Long) -> Unit,
) {
    var presentationMode by rememberSaveable { mutableStateOf(LibraryPresentationMode.LIST) }
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Library (EPUB)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Button(onClick = onImportClick, enabled = !uiState.isImporting) {
                    Text(if (uiState.isImporting) "Importing..." else "Import EPUB")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { presentationMode = LibraryPresentationMode.LIST },
                    label = { Text(if (presentationMode == LibraryPresentationMode.LIST) "List view • on" else "List view") },
                )
                AssistChip(
                    onClick = { presentationMode = LibraryPresentationMode.GRID },
                    label = { Text(if (presentationMode == LibraryPresentationMode.GRID) "Grid view • on" else "Grid view") },
                )
                Text(
                    text = "${uiState.books.size} book${if (uiState.books.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.isImporting) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { (uiState.importProgressPercent ?: 0).coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = uiState.importProgressLabel ?: "Importing EPUB...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            uiState.statusMessage?.let { status ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.statusKind) {
                            LibraryStatusKind.INFO -> MaterialTheme.colorScheme.secondaryContainer
                            LibraryStatusKind.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
                            LibraryStatusKind.WARNING -> MaterialTheme.colorScheme.primaryContainer
                            LibraryStatusKind.ERROR -> MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        uiState.statusAction?.let { action ->
                            TextButton(onClick = { onStatusActionBook(action.bookId) }) {
                                Text(action.label)
                            }
                        }
                        TextButton(onClick = onClearStatusMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            if (uiState.books.isEmpty()) {
                Text(
                    text = "Recently Opened (0)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "No recent books yet. Use Resume after opening a book to build this list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text(
                    text = "All EPUBs (0)",
                    style = MaterialTheme.typography.titleSmall,
                )
                EmptyLibraryState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val recentBooks = uiState.books
                        .filter { it.lastOpenedAt > it.addedAt }
                        .sortedByDescending { it.lastOpenedAt }
                    val recentBooksDisplayed = recentBooks.take(5)
                    val hiddenRecentCount = (recentBooks.size - recentBooksDisplayed.size).coerceAtLeast(0)
                    val recentBookIds = recentBooks.map { it.id }.toSet()
                    val otherBooks = uiState.books.filterNot { it.id in recentBookIds }
                    val unreadCount = uiState.books.count { it.lastOpenedAt <= it.addedAt }

                    if (recentBooks.isNotEmpty()) {
                        Text(
                            text = "Recently Opened (${recentBooks.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Quick resume list (showing up to 5) • $unreadCount unread/new",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LibraryBooksCollection(
                            books = recentBooksDisplayed,
                            presentationMode = presentationMode,
                            highlightedBookId = uiState.highlightedBookId,
                            onResumeBook = onResumeBook,
                        )
                        if (hiddenRecentCount > 0) {
                            Text(
                                text = "$hiddenRecentCount more recently opened book${if (hiddenRecentCount == 1) "" else "s"} are shown in All EPUBs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = "Recently Opened (0)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "No recent books yet. Use Resume after opening a book to build this list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "All EPUBs (${otherBooks.size})",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (otherBooks.isEmpty()) {
                        Text(
                            text = "All imported books are currently in Recently Opened.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Library catalog (excluding books already shown above)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LibraryBooksCollection(
                            books = otherBooks,
                            presentationMode = presentationMode,
                            highlightedBookId = uiState.highlightedBookId,
                            onResumeBook = onResumeBook,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBooksCollection(
    books: List<LibraryBook>,
    presentationMode: LibraryPresentationMode,
    highlightedBookId: Long?,
    onResumeBook: (Long) -> Unit,
) {
    if (presentationMode == LibraryPresentationMode.GRID) {
        LibraryBookGrid(
            books = books,
            highlightedBookId = highlightedBookId,
            onResumeBook = onResumeBook,
        )
    } else {
        LibraryBookList(
            books = books,
            highlightedBookId = highlightedBookId,
            onResumeBook = onResumeBook,
        )
    }
}

@Composable
private fun LibraryBookList(
    books: List<LibraryBook>,
    highlightedBookId: Long?,
    onResumeBook: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        books.forEachIndexed { index, book ->
            LibraryBookRow(
                book = book,
                isHighlighted = highlightedBookId == book.id,
                onResumeBook = { onResumeBook(book.id) },
            )
            if (index < books.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LibraryBookGrid(
    books: List<LibraryBook>,
    highlightedBookId: Long?,
    onResumeBook: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        books.chunked(2).forEach { rowBooks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                rowBooks.forEach { book ->
                    LibraryBookGridCard(
                        book = book,
                        isHighlighted = highlightedBookId == book.id,
                        modifier = Modifier.weight(1f),
                        onResumeBook = { onResumeBook(book.id) },
                    )
                }
                if (rowBooks.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "No EPUBs imported yet",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Use \"Import EPUB\" to add a book from the Android file picker.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LibraryBookGridCard(
    book: LibraryBook,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier,
    onResumeBook: () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CoverThumbnail(
                    coverPath = book.coverPath,
                    title = book.title,
                    width = 84.dp,
                    height = 118.dp,
                )
            }
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authors.isNotBlank()) {
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = buildString {
                    append(if (book.lastOpenedAt > book.addedAt) "Recent" else "New")
                    if (book.tocEntryCount > 0) {
                        append(" • TOC ")
                        append(book.tocEntryCount)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onResumeBook,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Resume")
            }
        }
    }
}

@Composable
private fun LibraryBookRow(
    book: LibraryBook,
    isHighlighted: Boolean,
    onResumeBook: () -> Unit,
) {
    var isTocExpanded by rememberSaveable(book.id) { mutableStateOf(false) }
    val tocPreviewLines = remember(book.tocEntriesSerialized) {
        LibraryTocPreviewFormatter.previewLines(book.tocEntriesSerialized, maxLines = 6)
    }

    val rowHighlightShape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowHighlightShape)
            .background(
                if (isHighlighted) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    Color.Transparent
                },
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoverThumbnail(
            coverPath = book.coverPath,
            title = book.title,
            width = 56.dp,
            height = 80.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (isHighlighted) {
                Text(
                    text = "Highlighted in library",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (book.authors.isNotBlank()) {
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = book.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("Last opened: ")
                        append(formatTimestamp(book.lastOpenedAt))
                        if (book.coverPath != null) {
                            append("  •  cover")
                        }
                        if (book.tocEntryCount > 0) {
                            append("  •  TOC ")
                            append(book.tocEntryCount)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResumeBook) {
                    Text("Resume")
                }
            }

            if (book.tocEntryCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Table of contents (${book.tocEntryCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { isTocExpanded = !isTocExpanded }) {
                        Text(if (isTocExpanded) "Hide TOC" else "Show TOC")
                    }
                }
            }

            if (book.tocEntryCount > 0 && isTocExpanded) {
                if (tocPreviewLines.isEmpty()) {
                    Text(
                        text = "TOC metadata is stored, but a preview is not available for this book yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TocPreviewList(
                        previewLines = tocPreviewLines,
                        totalCount = book.tocEntryCount,
                    )
                }
            }
        }
    }
}

@Composable
private fun TocPreviewList(
    previewLines: List<TocPreviewLine>,
    totalCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        previewLines.forEach { line ->
            val indentDp = (line.depth.coerceIn(0, 4) * 12).dp
            Text(
                text = "\u2022 ${line.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = indentDp),
            )
        }
        val remaining = totalCount - previewLines.size
        if (remaining > 0) {
            Text(
                text = "... $remaining more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoverThumbnail(
    coverPath: String?,
    title: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    val coverBitmap = remember(coverPath) { CoverThumbnailMemoryCache.load(coverPath) }

    val shape = MaterialTheme.shapes.small
    val modifier = Modifier
        .width(width)
        .height(height)
        .clip(shape)

    if (coverBitmap != null) {
        Image(
            bitmap = coverBitmap,
            contentDescription = "Cover for $title",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "EPUB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private object CoverThumbnailMemoryCache {
    private val bitmapCache = object : LruCache<String, Bitmap>(48) {}

    fun load(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
        val normalizedPath = path?.takeIf { it.isNotBlank() } ?: return null
        val cached = bitmapCache.get(normalizedPath)
        if (cached != null && !cached.isRecycled) return cached.asImageBitmap()

        val decoded = runCatching { BitmapFactory.decodeFile(normalizedPath) }.getOrNull()
        return if (decoded != null) {
            bitmapCache.put(normalizedPath, decoded)
            decoded.asImageBitmap()
        } else {
            bitmapCache.remove(normalizedPath)
            null
        }
    }
}

@Composable
private fun FontSection(fonts: List<ReadingFontOption>) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Launch Font Set (Planned)",
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = {}, label = { Text("2 terminal fonts") })
                AssistChip(onClick = {}, label = { Text("5+ total fonts") })
            }
            fonts.forEach { font ->
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (font.category == ReadingFontCategory.TERMINAL_MONO) {
                            FontFamily.Monospace
                        } else {
                            FontFamily.Default
                        },
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PirateReaderPreview() {
    PirateReaderTheme {
        PirateReaderApp(
            uiState = LibraryUiState(
                books = listOf(
                    LibraryBook(
                        id = 1,
                        title = "Sample EPUB",
                        authors = "Author Name",
                        coverPath = "/data/user/0/com.piratereader/files/library/covers/sample.jpg",
                        tocEntryCount = 12,
                        tocEntriesSerialized = EpubTocCodec.encode(
                            listOf(
                                EpubTocEntry("Chapter 1", "OEBPS/one.xhtml", 0),
                                EpubTocEntry("Section 1.1", "OEBPS/one.xhtml#s1", 1),
                                EpubTocEntry("Chapter 2", "OEBPS/two.xhtml", 0),
                            ),
                        ),
                        fileName = "sample.epub",
                        localPath = "/data/user/0/com.piratereader/files/library/epub/sample.epub",
                        sourceUri = null,
                        fileSizeBytes = 12345,
                        lastReadChapterZipPath = "OEBPS/one.xhtml",
                        lastReadPageMode = ReaderPageMode.PAGINATED.name,
                        lastReadScrollX = 1080,
                        lastReadScrollY = 240,
                        addedAt = 0L,
                        lastOpenedAt = 0L,
                    ),
                ),
                statusMessage = "Imported: Sample EPUB",
            ),
            onImportClick = {},
            onClearStatusMessage = {},
            onStatusActionBook = {},
            onCloseReader = {},
            onSaveAndCloseReader = { _, _, _, _, _, _, _ -> },
            onResumeBook = {},
        )
    }
}

private fun parsePersistedReaderPageMode(value: String?): ReaderPageMode? {
    val normalized = value?.trim()?.ifBlank { null } ?: return null
    return ReaderPageMode.values().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun buildReaderPositionLocator(
    chapterZipPath: String?,
    anchorFragment: String?,
    pageMode: ReaderPageMode,
    metrics: ReaderViewportMetrics?,
    fallbackScrollX: Int?,
    fallbackScrollY: Int?,
): ReaderPositionLocator? {
    val chapter = chapterZipPath?.trim()?.ifBlank { null } ?: return null
    return ReaderPositionLocator(
        chapterZipPath = chapter,
        anchorFragment = anchorFragment?.trim()?.ifBlank { null },
        pageMode = pageMode,
        scrollX = (metrics?.scrollX ?: fallbackScrollX)?.coerceAtLeast(0),
        scrollY = (metrics?.scrollY ?: fallbackScrollY)?.coerceAtLeast(0),
        maxScrollX = metrics?.maxScrollX?.coerceAtLeast(0),
        maxScrollY = metrics?.maxScrollY?.coerceAtLeast(0),
        pageIndex = metrics?.pageIndex,
        pageCount = metrics?.pageCount,
        visibleTextHint = metrics?.visibleTextHint?.trim()?.ifBlank { null },
    )
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return runCatching {
        formatter.format(
            Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault()),
        )
    }.getOrDefault(epochMillis.toString())
}

private fun buildScrollToAnchorJavascript(fragment: String): String {
    val escapedFragment = escapeJavascriptSingleQuoted(fragment)
    return """
        (function() {
          var raw = '$escapedFragment';
          var id = raw;
          try { id = decodeURIComponent(raw); } catch (e) {}
          var target = document.getElementById(id);
          if (!target) {
            try {
              target = document.querySelector('[name="' + CSS.escape(id) + '"]');
            } catch (e) {
              var nodes = document.getElementsByName(id);
              target = nodes && nodes.length ? nodes[0] : null;
            }
          }
          if (!target && raw !== id) {
            target = document.getElementById(raw);
          }
          if (target && target.scrollIntoView) {
            target.scrollIntoView({block:'start', inline:'nearest', behavior:'auto'});
            return 'ok';
          }
          return 'missing';
        })();
    """.trimIndent()
}

private fun buildViewportNavigationJavascript(
    pageMode: ReaderPageMode,
    direction: Int,
): String {
    val step = if (direction >= 0) 1 else -1
    return when (pageMode) {
        ReaderPageMode.SCROLL -> """
            (function() {
              var amount = Math.max(120, Math.floor(window.innerHeight * 0.88)) * $step;
              window.scrollBy({ top: amount, left: 0, behavior: 'auto' });
              return amount;
            })();
        """.trimIndent()

        ReaderPageMode.PAGINATED -> """
            (function() {
              var pageWidth = Math.max(1, Math.floor(window.innerWidth));
              var current = Math.max(0, Math.floor(window.scrollX || window.pageXOffset || 0));
              var currentPage = Math.round(current / pageWidth);
              var maxScrollX = Math.max(
                0,
                Math.floor(
                  ((document.scrollingElement && document.scrollingElement.scrollWidth) || document.documentElement.scrollWidth || 0)
                  - pageWidth
                )
              );
              var maxPage = Math.max(0, Math.ceil(maxScrollX / pageWidth));
              var targetPage = Math.max(0, Math.min(maxPage, currentPage + ($step)));
              window.scrollTo({ left: targetPage * pageWidth, top: 0, behavior: 'auto' });
              return targetPage + 1;
            })();
        """.trimIndent()
    }
}

private fun buildReaderViewportMetricsJavascript(
    pageMode: ReaderPageMode,
    tocAnchorFragments: List<String>,
): String {
    val isPaginated = if (pageMode == ReaderPageMode.PAGINATED) "true" else "false"
    val tocAnchorArrayLiteral = buildJavascriptSingleQuotedArrayLiteral(tocAnchorFragments)
    return """
        (function() {
          var scrolling = document.scrollingElement || document.documentElement || document.body;
          var scrollX = Math.max(0, Math.floor(window.scrollX || window.pageXOffset || 0));
          var scrollY = Math.max(0, Math.floor(window.scrollY || window.pageYOffset || 0));
          var viewportW = Math.max(1, Math.floor(window.innerWidth || 1));
          var viewportH = Math.max(1, Math.floor(window.innerHeight || 1));
          var maxScrollX = Math.max(0, Math.floor((scrolling ? scrolling.scrollWidth : 0) - viewportW));
          var maxScrollY = Math.max(0, Math.floor((scrolling ? scrolling.scrollHeight : 0) - viewportH));
          var pageIndex = 1;
          var pageCount = 1;
          if ($isPaginated) {
            pageIndex = Math.max(1, Math.round(scrollX / viewportW) + 1);
            pageCount = Math.max(1, Math.ceil(maxScrollX / viewportW) + 1);
          }

          var anchor = '';
          var tocAnchor = '';
          var textHint = '';
          function findNamedAnchor(raw) {
            if (!raw) return null;
            var decoded = raw;
            try { decoded = decodeURIComponent(raw); } catch (e) {}
            var target = document.getElementById(decoded);
            if (!target) {
              try {
                target = document.querySelector('[name="' + CSS.escape(decoded) + '"]');
              } catch (e) {
                var nodes = document.getElementsByName(decoded);
                target = nodes && nodes.length ? nodes[0] : null;
              }
            }
            if (!target && decoded !== raw) {
              target = document.getElementById(raw);
            }
            return target || null;
          }
          try {
            var nodes = document.querySelectorAll('[id], a[name]');
            var best = null;
            var bestTop = -1e9;
            var next = null;
            var nextTop = 1e9;
            var cutoff = Math.floor((window.innerHeight || 0) * 0.35);
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              var rect = el.getBoundingClientRect();
              if (!rect || rect.height === 0 && rect.width === 0) continue;
              if (rect.bottom <= 0) continue;
              var candidateId = el.id || el.getAttribute('name') || '';
              if (!candidateId) continue;
              if (rect.top <= cutoff && rect.top > bestTop) {
                bestTop = rect.top;
                best = candidateId;
              }
              if (rect.top >= 0 && rect.top < nextTop) {
                nextTop = rect.top;
                next = candidateId;
              }
            }
            anchor = best || next || '';
          } catch (e) {
            anchor = '';
          }
          try {
            var tocCandidates = $tocAnchorArrayLiteral;
            if (tocCandidates.length > 0) {
              var tocBest = null;
              var tocBestTop = -1e9;
              var tocNext = null;
              var tocNextTop = 1e9;
              var tocCutoff = Math.floor((window.innerHeight || 0) * 0.35);
              for (var k = 0; k < tocCandidates.length; k++) {
                var tocEl = findNamedAnchor(tocCandidates[k]);
                if (!tocEl || !tocEl.getBoundingClientRect) continue;
                var tocRect = tocEl.getBoundingClientRect();
                if (!tocRect || (tocRect.height === 0 && tocRect.width === 0)) continue;
                if (tocRect.bottom <= 0) continue;
                var tocId = tocEl.id || tocEl.getAttribute('name') || '';
                if (!tocId) continue;
                if (tocRect.top <= tocCutoff && tocRect.top > tocBestTop) {
                  tocBestTop = tocRect.top;
                  tocBest = tocId;
                }
                if (tocRect.top >= 0 && tocRect.top < tocNextTop) {
                  tocNextTop = tocRect.top;
                  tocNext = tocId;
                }
              }
              tocAnchor = tocBest || tocNext || '';
            }
          } catch (e) {
            tocAnchor = '';
          }
          try {
            var textNodes = document.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,blockquote,dd,dt,div');
            var textCutoff = Math.floor((window.innerHeight || 0) * 0.35);
            var bestText = '';
            var bestDistance = 1e9;
            for (var j = 0; j < textNodes.length; j++) {
              var node = textNodes[j];
              var rect2 = node.getBoundingClientRect ? node.getBoundingClientRect() : null;
              if (!rect2 || (rect2.height === 0 && rect2.width === 0)) continue;
              if (rect2.bottom <= 0) continue;
              if (rect2.top >= (window.innerHeight || 0) + 120) continue;
              var text = (node.textContent || '').replace(/\s+/g, ' ').trim();
              if (!text || text.length < 12) continue;
              var distance = Math.abs(rect2.top - textCutoff);
              if (distance < bestDistance) {
                bestDistance = distance;
                bestText = text;
              }
            }
            if (bestText) {
              textHint = bestText.substring(0, 96);
            }
          } catch (e) {
            textHint = '';
          }
          return 'v4|' + pageIndex + '|' + pageCount + '|' + scrollX + '|' + scrollY + '|' + maxScrollX + '|' + maxScrollY + '|' + encodeURIComponent(anchor) + '|' + encodeURIComponent(tocAnchor) + '|' + encodeURIComponent(textHint);
        })();
    """.trimIndent()
}

private fun buildReaderRestoreFromLocatorJavascript(
    pageMode: ReaderPageMode,
    locator: ReaderPositionLocator?,
    fallbackScrollX: Int?,
    fallbackScrollY: Int?,
): String? {
    if (locator == null && fallbackScrollX == null && fallbackScrollY == null) return null

    val xProgressPermille = when (pageMode) {
        ReaderPageMode.PAGINATED -> locator?.pageProgressPermille() ?: locator?.scrollXProgressPermille()
        ReaderPageMode.SCROLL -> locator?.scrollXProgressPermille()
    }?.coerceIn(0, 1000)
    val yProgressPermille = locator?.scrollYProgressPermille()?.coerceIn(0, 1000)
    val fallbackX = fallbackScrollX?.coerceAtLeast(0)
    val fallbackY = fallbackScrollY?.coerceAtLeast(0)
    val visibleTextHint = locator?.visibleTextHint?.trim()?.ifBlank { null }

    if (xProgressPermille == null && yProgressPermille == null && fallbackX == null && fallbackY == null) {
        return null
    }

    val paginated = if (pageMode == ReaderPageMode.PAGINATED) "true" else "false"
    val escapedTextHint = escapeJavascriptSingleQuoted(visibleTextHint.orEmpty())
    return """
        (function() {
          var scrolling = document.scrollingElement || document.documentElement || document.body;
          var viewportW = Math.max(1, Math.floor(window.innerWidth || 1));
          var viewportH = Math.max(1, Math.floor(window.innerHeight || 1));
          var maxScrollX = Math.max(0, Math.floor((scrolling ? scrolling.scrollWidth : 0) - viewportW));
          var maxScrollY = Math.max(0, Math.floor((scrolling ? scrolling.scrollHeight : 0) - viewportH));
          var targetX = ${jsIntOrNullLiteral(fallbackX)};
          var targetY = ${jsIntOrNullLiteral(fallbackY)};
          var xPermille = ${jsIntOrNullLiteral(xProgressPermille)};
          var yPermille = ${jsIntOrNullLiteral(yProgressPermille)};
          var textHint = '$escapedTextHint';
          function normalizeText(value) {
            return (value || '').replace(/\s+/g, ' ').trim().toLowerCase();
          }
          if (xPermille !== null) {
            targetX = Math.max(0, Math.min(maxScrollX, Math.round(maxScrollX * (xPermille / 1000.0))));
          }
          if (yPermille !== null) {
            targetY = Math.max(0, Math.min(maxScrollY, Math.round(maxScrollY * (yPermille / 1000.0))));
          }
          if ($paginated && (targetY === null || targetY < 0)) {
            targetY = 0;
          }
          window.scrollTo({
            left: Math.max(0, targetX || 0),
            top: Math.max(0, targetY || 0),
            behavior: 'auto'
          });
          try {
            var normalizedHint = normalizeText(textHint);
            if (normalizedHint && normalizedHint.length >= 12) {
              var blocks = document.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,blockquote,dd,dt,div');
              var desiredAbsTop = Math.max(0, targetY || 0);
              var bestEl = null;
              var bestScore = 1e18;
              for (var i = 0; i < blocks.length; i++) {
                var el = blocks[i];
                var text = normalizeText(el.textContent || '');
                if (!text || text.length < 8) continue;
                if (text.indexOf(normalizedHint) < 0) continue;
                var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                if (!rect) continue;
                var absTop = Math.max(0, Math.floor((window.scrollY || window.pageYOffset || 0) + rect.top));
                var score = Math.abs(absTop - desiredAbsTop);
                if (score < bestScore) {
                  bestScore = score;
                  bestEl = el;
                }
              }
              if (bestEl && bestEl.scrollIntoView) {
                bestEl.scrollIntoView({ block: 'start', inline: 'nearest', behavior: 'auto' });
              }
            }
          } catch (e) {}
          return 'ok';
        })();
    """.trimIndent()
}

private fun composeColorFromHexOrFallback(
    hex: String,
    fallback: Color,
): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { fallback }

private fun jsIntOrNullLiteral(value: Int?): String = value?.toString() ?: "null"

private fun buildJavascriptSingleQuotedArrayLiteral(values: List<String>): String =
    values.joinToString(prefix = "[", postfix = "]") { value ->
        "'${escapeJavascriptSingleQuoted(value)}'"
    }

private fun escapeJavascriptSingleQuoted(value: String): String =
    buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
