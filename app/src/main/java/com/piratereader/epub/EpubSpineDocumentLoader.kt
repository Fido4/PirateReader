package com.piratereader.epub

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

data class EpubSpineDocument(
    val packageTitle: String?,
    val chapterZipPath: String,
    val chapterMediaType: String,
    val chapterMarkup: String,
    val spineIndex: Int,
    val spineItemCount: Int,
    val previousChapterZipPath: String?,
    val nextChapterZipPath: String?,
)

enum class EpubSpineDocumentFailureReason {
    INVALID_SOURCE_FILE,
    INVALID_ZIP_CONTAINER,
    MISSING_CONTAINER_XML,
    INVALID_CONTAINER_XML,
    MISSING_PACKAGE_PATH_IN_CONTAINER,
    MISSING_PACKAGE_DOCUMENT,
    INVALID_PACKAGE_DOCUMENT_XML,
    EMPTY_SPINE,
    SPINE_ITEM_NOT_IN_MANIFEST,
    MISSING_SPINE_DOCUMENT_ENTRY,
    UNSUPPORTED_SPINE_DOCUMENT_TYPE,
    UNREADABLE_SPINE_DOCUMENT,
}

sealed interface EpubSpineDocumentLoadResult {
    data class Success(val document: EpubSpineDocument) : EpubSpineDocumentLoadResult

    data class Failure(
        val reason: EpubSpineDocumentFailureReason,
        val causeMessage: String? = null,
    ) : EpubSpineDocumentLoadResult {
        fun userFacingMessage(): String =
            when (reason) {
                EpubSpineDocumentFailureReason.INVALID_SOURCE_FILE -> "Unreadable EPUB file"
                EpubSpineDocumentFailureReason.INVALID_ZIP_CONTAINER ->
                    "Unreadable EPUB (invalid ZIP container)"

                EpubSpineDocumentFailureReason.MISSING_CONTAINER_XML ->
                    "Invalid EPUB (missing META-INF/container.xml)"

                EpubSpineDocumentFailureReason.INVALID_CONTAINER_XML ->
                    "Invalid EPUB (malformed container.xml)"

                EpubSpineDocumentFailureReason.MISSING_PACKAGE_PATH_IN_CONTAINER ->
                    "Invalid EPUB (container.xml missing package path)"

                EpubSpineDocumentFailureReason.MISSING_PACKAGE_DOCUMENT ->
                    "Invalid EPUB (package document not found)"

                EpubSpineDocumentFailureReason.INVALID_PACKAGE_DOCUMENT_XML ->
                    "Invalid EPUB (malformed package document)"

                EpubSpineDocumentFailureReason.EMPTY_SPINE ->
                    "EPUB has no readable spine items"

                EpubSpineDocumentFailureReason.SPINE_ITEM_NOT_IN_MANIFEST ->
                    "EPUB spine item not found in manifest"

                EpubSpineDocumentFailureReason.MISSING_SPINE_DOCUMENT_ENTRY ->
                    "EPUB spine document file is missing"

                EpubSpineDocumentFailureReason.UNSUPPORTED_SPINE_DOCUMENT_TYPE ->
                    "EPUB spine item format is not supported yet"

                EpubSpineDocumentFailureReason.UNREADABLE_SPINE_DOCUMENT ->
                    "Failed to read EPUB spine document"
            }
    }
}

object EpubSpineDocumentLoader {
    fun loadFirstSpineDocument(epubFile: File): EpubSpineDocumentLoadResult {
        return loadSpineDocumentForReading(
            epubFile = epubFile,
            preferredChapterZipPath = null,
        )
    }

    fun loadSpineDocumentForReading(
        epubFile: File,
        preferredChapterZipPath: String?,
    ): EpubSpineDocumentLoadResult {
        if (!epubFile.exists() || !epubFile.isFile) {
            return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.INVALID_SOURCE_FILE)
        }

        return try {
            ZipFile(epubFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.MISSING_CONTAINER_XML)

                val packagePath = try {
                    zip.getInputStream(containerEntry).use(::parseContainerForPackagePath)
                } catch (t: Throwable) {
                    return EpubSpineDocumentLoadResult.Failure(
                        EpubSpineDocumentFailureReason.INVALID_CONTAINER_XML,
                        t.message,
                    )
                } ?: return EpubSpineDocumentLoadResult.Failure(
                    EpubSpineDocumentFailureReason.MISSING_PACKAGE_PATH_IN_CONTAINER,
                )

                val opfEntry = zip.getEntry(packagePath)
                    ?: return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.MISSING_PACKAGE_DOCUMENT)

                val packageDoc = try {
                    zip.getInputStream(opfEntry).use(::parseXml)
                } catch (t: Throwable) {
                    return EpubSpineDocumentLoadResult.Failure(
                        EpubSpineDocumentFailureReason.INVALID_PACKAGE_DOCUMENT_XML,
                        t.message,
                    )
                }

                val manifest = parseManifestItems(packageDoc).associateBy { it.id }
                val spineIdRefs = parseSpineIdRefs(packageDoc)
                if (spineIdRefs.isEmpty()) {
                    return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.EMPTY_SPINE)
                }
                val preferredNormalized = preferredChapterZipPath?.let(::normalizeZipPath)
                val selectedIdRefWithIndex = spineIdRefs
                    .withIndex()
                    .firstOrNull { indexed ->
                        val manifestItem = manifest[indexed.value] ?: return@firstOrNull false
                        val resolvedZipPath = resolveRelativeZipPath(packagePath, manifestItem.href)
                        preferredNormalized != null && resolvedZipPath == preferredNormalized
                    }
                    ?: spineIdRefs.withIndex().firstOrNull()
                    ?: return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.EMPTY_SPINE)

                val spineItem = manifest[selectedIdRefWithIndex.value]
                    ?: return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.SPINE_ITEM_NOT_IN_MANIFEST)

                if (!spineItem.mediaType.isSupportedSpineMediaType()) {
                    return EpubSpineDocumentLoadResult.Failure(
                        EpubSpineDocumentFailureReason.UNSUPPORTED_SPINE_DOCUMENT_TYPE,
                    )
                }

                val chapterZipPath = resolveRelativeZipPath(packagePath, spineItem.href)
                val chapterEntry = zip.getEntry(chapterZipPath)
                    ?: return EpubSpineDocumentLoadResult.Failure(EpubSpineDocumentFailureReason.MISSING_SPINE_DOCUMENT_ENTRY)

                val chapterMarkup = runCatching {
                    zip.getInputStream(chapterEntry).use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrElse { t ->
                    return EpubSpineDocumentLoadResult.Failure(
                        EpubSpineDocumentFailureReason.UNREADABLE_SPINE_DOCUMENT,
                        t.message,
                    )
                }

                val title = textValuesByLocalName(packageDoc, "title").firstOrNull()?.takeIf { it.isNotBlank() }
                val previousChapterZipPath = findAdjacentReadableChapterZipPath(
                    spineIdRefs = spineIdRefs,
                    manifest = manifest,
                    packagePath = packagePath,
                    currentSpineIndex = selectedIdRefWithIndex.index,
                    direction = -1,
                )
                val nextChapterZipPath = findAdjacentReadableChapterZipPath(
                    spineIdRefs = spineIdRefs,
                    manifest = manifest,
                    packagePath = packagePath,
                    currentSpineIndex = selectedIdRefWithIndex.index,
                    direction = 1,
                )
                EpubSpineDocumentLoadResult.Success(
                    EpubSpineDocument(
                        packageTitle = title,
                        chapterZipPath = chapterZipPath,
                        chapterMediaType = spineItem.mediaType,
                        chapterMarkup = chapterMarkup,
                        spineIndex = selectedIdRefWithIndex.index,
                        spineItemCount = spineIdRefs.size,
                        previousChapterZipPath = previousChapterZipPath,
                        nextChapterZipPath = nextChapterZipPath,
                    ),
                )
            }
        } catch (t: Throwable) {
            EpubSpineDocumentLoadResult.Failure(
                EpubSpineDocumentFailureReason.INVALID_ZIP_CONTAINER,
                t.message,
            )
        }
    }

    private fun String.isSupportedSpineMediaType(): Boolean =
        equals("application/xhtml+xml", ignoreCase = true) ||
            equals("text/html", ignoreCase = true) ||
            isBlank()

    private fun parseContainerForPackagePath(input: InputStream): String? {
        val doc = parseXml(input)
        val rootfile = firstElementByLocalName(doc, "rootfile") ?: return null
        return rootfile.getAttribute("full-path").takeIf { it.isNotBlank() }
    }

    private fun parseXml(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(input)
    }

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun parseManifestItems(doc: Document): List<ManifestItem> {
        val items = mutableListOf<ManifestItem>()
        val nsNodes = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until nsNodes.length) {
            val element = nsNodes.item(i) as? Element ?: continue
            element.toManifestItemOrNull()?.let(items::add)
        }
        if (items.isNotEmpty()) return items
        val fallback = doc.getElementsByTagName("item")
        for (i in 0 until fallback.length) {
            val element = fallback.item(i) as? Element ?: continue
            element.toManifestItemOrNull()?.let(items::add)
        }
        return items
    }

    private fun parseSpineIdRefs(doc: Document): List<String> {
        val spine = firstElementByLocalName(doc, "spine") ?: return emptyList()
        return directChildElements(spine)
            .filter { it.matchesLocalName("itemref") }
            .mapNotNull { element -> element.getAttribute("idref").takeIf { it.isNotBlank() } }
    }

    private fun textValuesByLocalName(doc: Document, localName: String): List<String> {
        val out = mutableListOf<String>()
        val nsNodes = doc.getElementsByTagNameNS("*", localName)
        for (i in 0 until nsNodes.length) {
            val text = nsNodes.item(i)?.textContent?.trim().orEmpty()
            if (text.isNotBlank()) out += text
        }
        if (out.isNotEmpty()) return out

        val fallback = doc.getElementsByTagName(localName)
        for (i in 0 until fallback.length) {
            val text = fallback.item(i)?.textContent?.trim().orEmpty()
            if (text.isNotBlank()) out += text
        }
        return out
    }

    private fun firstElementByLocalName(doc: Document, localName: String): Element? {
        val fromNs = doc.getElementsByTagNameNS("*", localName)
        if (fromNs.length > 0) return fromNs.item(0) as? Element
        val fallback = doc.getElementsByTagName(localName)
        if (fallback.length > 0) return fallback.item(0) as? Element
        return null
    }

    private fun Element.toManifestItemOrNull(): ManifestItem? {
        val id = getAttribute("id").orEmpty()
        val href = getAttribute("href").orEmpty()
        if (id.isBlank() || href.isBlank()) return null
        return ManifestItem(
            id = id,
            href = href,
            mediaType = getAttribute("media-type").orEmpty(),
        )
    }

    private fun directChildElements(node: Node): List<Element> {
        val result = mutableListOf<Element>()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) result += child
        }
        return result
    }

    private fun Element.matchesLocalName(localName: String): Boolean {
        val actual = this.localName ?: tagName.substringAfter(':', tagName)
        return actual.equals(localName, ignoreCase = true)
    }

    private fun resolveRelativeZipPath(basePath: String, relativePath: String): String {
        val baseDir = basePath.substringBeforeLast('/', missingDelimiterValue = "")
        val raw = if (baseDir.isBlank()) relativePath else "$baseDir/$relativePath"
        return normalizeZipPath(raw) ?: raw
    }

    private fun findAdjacentReadableChapterZipPath(
        spineIdRefs: List<String>,
        manifest: Map<String, ManifestItem>,
        packagePath: String,
        currentSpineIndex: Int,
        direction: Int,
    ): String? {
        val step = direction.coerceIn(-1, 1).takeIf { it != 0 } ?: return null
        var index = currentSpineIndex + step
        while (index in spineIdRefs.indices) {
            val idRef = spineIdRefs[index]
            val item = manifest[idRef]
            if (item != null && item.mediaType.isSupportedSpineMediaType()) {
                val resolved = normalizeZipPath(
                    buildString {
                        val baseDir = packagePath.substringBeforeLast('/', missingDelimiterValue = "")
                        if (baseDir.isNotBlank()) {
                            append(baseDir)
                            append('/')
                        }
                        append(item.href)
                    },
                )
                if (!resolved.isNullOrBlank()) return resolved
            }
            index += step
        }
        return null
    }

    private fun normalizeZipPath(path: String): String? {
        val segments = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.lastIndex)
                }

                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
    )
}
