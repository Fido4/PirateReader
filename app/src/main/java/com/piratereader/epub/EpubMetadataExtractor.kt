package com.piratereader.epub

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

data class EpubPackageMetadata(
    val title: String?,
    val authors: List<String>,
    val coverZipPath: String?,
    val tocEntries: List<EpubTocEntry>,
)

data class EpubTocEntry(
    val label: String,
    val hrefZipPath: String?,
    val depth: Int,
)

enum class EpubMetadataFailureReason {
    INVALID_SOURCE_FILE,
    INVALID_ZIP_CONTAINER,
    MISSING_MIMETYPE_ENTRY,
    INVALID_MIMETYPE_ENTRY,
    MISSING_CONTAINER_XML,
    INVALID_CONTAINER_XML,
    MISSING_PACKAGE_PATH_IN_CONTAINER,
    MISSING_PACKAGE_DOCUMENT,
    INVALID_PACKAGE_DOCUMENT_XML,
}

sealed interface EpubMetadataExtractionResult {
    data class Success(val metadata: EpubPackageMetadata) : EpubMetadataExtractionResult

    data class Failure(
        val reason: EpubMetadataFailureReason,
        val causeMessage: String? = null,
    ) : EpubMetadataExtractionResult {
        fun userFacingMessage(): String =
            when (reason) {
                EpubMetadataFailureReason.INVALID_SOURCE_FILE -> "Unreadable EPUB file"
                EpubMetadataFailureReason.INVALID_ZIP_CONTAINER -> "Unreadable EPUB (invalid ZIP container)"
                EpubMetadataFailureReason.MISSING_MIMETYPE_ENTRY ->
                    "Invalid EPUB (missing mimetype entry)"
                EpubMetadataFailureReason.INVALID_MIMETYPE_ENTRY ->
                    "Invalid EPUB (bad mimetype entry)"
                EpubMetadataFailureReason.MISSING_CONTAINER_XML -> "Invalid EPUB (missing META-INF/container.xml)"
                EpubMetadataFailureReason.INVALID_CONTAINER_XML -> "Invalid EPUB (malformed container.xml)"
                EpubMetadataFailureReason.MISSING_PACKAGE_PATH_IN_CONTAINER ->
                    "Invalid EPUB (container.xml missing package path)"

                EpubMetadataFailureReason.MISSING_PACKAGE_DOCUMENT ->
                    "Invalid EPUB (package document not found)"

                EpubMetadataFailureReason.INVALID_PACKAGE_DOCUMENT_XML ->
                    "Invalid EPUB (malformed package document)"
            }
    }
}

object EpubMetadataExtractor {
    fun extract(file: File): EpubPackageMetadata? {
        return when (val result = extractDetailed(file)) {
            is EpubMetadataExtractionResult.Success -> result.metadata
            is EpubMetadataExtractionResult.Failure -> null
        }
    }

    fun extractDetailed(file: File): EpubMetadataExtractionResult {
        if (!file.exists() || !file.isFile) {
            return EpubMetadataExtractionResult.Failure(EpubMetadataFailureReason.INVALID_SOURCE_FILE)
        }

        return try {
            ZipFile(file).use { zip ->
                val mimetypeEntry = zip.getEntry("mimetype")
                    ?: return EpubMetadataExtractionResult.Failure(EpubMetadataFailureReason.MISSING_MIMETYPE_ENTRY)
                val mimetypeValue = runCatching {
                    zip.getInputStream(mimetypeEntry).use { input ->
                        input.readBytes().toString(Charsets.US_ASCII).trim()
                    }
                }.getOrNull()
                if (!mimetypeValue.equals("application/epub+zip", ignoreCase = false)) {
                    return EpubMetadataExtractionResult.Failure(EpubMetadataFailureReason.INVALID_MIMETYPE_ENTRY)
                }

                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: return EpubMetadataExtractionResult.Failure(EpubMetadataFailureReason.MISSING_CONTAINER_XML)

                val packagePath = try {
                    zip.getInputStream(containerEntry).use { input ->
                        parseContainerForPackagePath(input)
                    }
                } catch (t: Throwable) {
                    return EpubMetadataExtractionResult.Failure(
                        reason = EpubMetadataFailureReason.INVALID_CONTAINER_XML,
                        causeMessage = t.message,
                    )
                } ?: return EpubMetadataExtractionResult.Failure(
                    EpubMetadataFailureReason.MISSING_PACKAGE_PATH_IN_CONTAINER,
                )

                val opfEntry = zip.getEntry(packagePath)
                    ?: return EpubMetadataExtractionResult.Failure(EpubMetadataFailureReason.MISSING_PACKAGE_DOCUMENT)

                val metadata = try {
                    zip.getInputStream(opfEntry).use { input ->
                        parsePackageMetadata(
                            zip = zip,
                            input = input,
                            opfPath = packagePath,
                        )
                    }
                } catch (t: Throwable) {
                    return EpubMetadataExtractionResult.Failure(
                        reason = EpubMetadataFailureReason.INVALID_PACKAGE_DOCUMENT_XML,
                        causeMessage = t.message,
                    )
                }

                EpubMetadataExtractionResult.Success(metadata)
            }
        } catch (t: Throwable) {
            EpubMetadataExtractionResult.Failure(
                reason = EpubMetadataFailureReason.INVALID_ZIP_CONTAINER,
                causeMessage = t.message,
            )
        }
    }

    private fun parseContainerForPackagePath(input: InputStream): String? {
        val doc = parseXml(input)
        val rootfile = firstElementByLocalName(doc, "rootfile") ?: return null
        return rootfile.getAttribute("full-path").takeIf { it.isNotBlank() }
    }

    private fun parsePackageMetadata(
        zip: ZipFile,
        input: InputStream,
        opfPath: String,
    ): EpubPackageMetadata {
        val doc = parseXml(input)
        val manifestItems = parseManifestItems(doc)

        val title = textValuesByLocalName(doc, "title")
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

        val authors = textValuesByLocalName(doc, "creator")
            .map(String::trim)
            .filter(String::isNotBlank)

        val coverZipPath = resolveCoverZipPath(
            doc = doc,
            manifestItems = manifestItems,
            opfPath = opfPath,
        )
        val tocEntries = runCatching {
            resolveTocEntries(
                zip = zip,
                doc = doc,
                manifestItems = manifestItems,
                opfPath = opfPath,
            )
        }.getOrDefault(emptyList())

        return EpubPackageMetadata(
            title = title,
            authors = authors,
            coverZipPath = coverZipPath,
            tocEntries = tocEntries,
        )
    }

    private fun parseXml(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
            setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(input)
    }

    private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun firstElementByLocalName(doc: Document, localName: String): Element? {
        val fromNs = doc.getElementsByTagNameNS("*", localName)
        if (fromNs.length > 0) {
            return fromNs.item(0) as? Element
        }
        val fallback = doc.getElementsByTagName(localName)
        if (fallback.length > 0) {
            return fallback.item(0) as? Element
        }
        return null
    }

    private fun textValuesByLocalName(doc: Document, localName: String): List<String> {
        val result = mutableListOf<String>()

        val fromNs = doc.getElementsByTagNameNS("*", localName)
        for (i in 0 until fromNs.length) {
            val text = fromNs.item(i)?.textContent?.trim().orEmpty()
            if (text.isNotBlank()) result += text
        }
        if (result.isNotEmpty()) return result

        val fallback = doc.getElementsByTagName(localName)
        for (i in 0 until fallback.length) {
            val node = fallback.item(i)
            val text = node?.textContent?.trim().orEmpty()
            if (text.isNotBlank()) result += text
        }
        if (result.isNotEmpty()) return result

        collectPrefixedTagText(doc, localName, result)
        return result
    }

    private fun collectPrefixedTagText(node: Node, localName: String, out: MutableList<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val element = node as Element
            if (element.tagName.endsWith(":$localName")) {
                val text = element.textContent?.trim().orEmpty()
                if (text.isNotBlank()) out += text
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            collectPrefixedTagText(children.item(i), localName, out)
        }
    }

    private fun resolveCoverZipPath(
        doc: Document,
        manifestItems: List<ManifestItem>,
        opfPath: String,
    ): String? {
        if (manifestItems.isEmpty()) return null

        val byId = manifestItems.associateBy { it.id }

        val coverByProperty = manifestItems.firstOrNull { item ->
            item.properties.split(' ').any { it == "cover-image" }
        }?.href

        val coverByMetaName = parseMetaCoverId(doc)
            ?.let { byId[it]?.href }

        val coverByHeuristic = manifestItems.firstOrNull { item ->
            item.mediaType.startsWith("image/") &&
                item.href.contains("cover", ignoreCase = true)
        }?.href

        val href = coverByProperty ?: coverByMetaName ?: coverByHeuristic ?: return null
        return resolveRelativeZipPath(basePath = opfPath, relativePath = href)
    }

    private fun resolveTocEntries(
        zip: ZipFile,
        doc: Document,
        manifestItems: List<ManifestItem>,
        opfPath: String,
    ): List<EpubTocEntry> {
        val navManifestItem = manifestItems.firstOrNull { item ->
            item.properties.split(' ').any { it == "nav" }
        }
        if (navManifestItem != null) {
            val navPath = resolveRelativeZipPath(opfPath, navManifestItem.href)
            parseNavTocEntries(zip, navPath)?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        val byId = manifestItems.associateBy { it.id }
        val spineTocId = parseSpineTocId(doc)
        val ncxManifestItem = spineTocId?.let(byId::get)
            ?: manifestItems.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) }
        if (ncxManifestItem != null) {
            val ncxPath = resolveRelativeZipPath(opfPath, ncxManifestItem.href)
            parseNcxTocEntries(zip, ncxPath)?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        return emptyList()
    }

    private fun parseSpineTocId(doc: Document): String? {
        val spine = firstElementByLocalName(doc, "spine") ?: return null
        return spine.getAttribute("toc").takeIf { it.isNotBlank() }
    }

    private fun parseNavTocEntries(
        zip: ZipFile,
        navZipPath: String,
    ): List<EpubTocEntry>? {
        val entry = zip.getEntry(navZipPath) ?: return null
        val doc = zip.getInputStream(entry).use(::parseXml)
        val navElement = findTocNavElement(doc) ?: return emptyList()
        val result = mutableListOf<EpubTocEntry>()
        collectNavListEntries(navElement, navZipPath, result)
        if (result.isNotEmpty()) return result

        // Fallback: collect all anchors if the nav markup is not list-based.
        val anchors = navElement.getElementsByTagNameNS("*", "a")
        for (i in 0 until anchors.length) {
            val anchor = anchors.item(i) as? Element ?: continue
            val label = anchor.textContent?.trim().orEmpty()
            if (label.isBlank()) continue
            val href = anchor.getAttribute("href").takeIf { it.isNotBlank() }
            result += EpubTocEntry(
                label = label,
                hrefZipPath = href?.let { resolveRelativeZipPath(navZipPath, it) },
                depth = 0,
            )
        }
        return result
    }

    private fun findTocNavElement(doc: Document): Element? {
        val navNodes = doc.getElementsByTagNameNS("*", "nav")
        var firstNav: Element? = null
        for (i in 0 until navNodes.length) {
            val nav = navNodes.item(i) as? Element ?: continue
            if (firstNav == null) firstNav = nav
            val epubType = nav.getAttribute("epub:type")
            val type = nav.getAttribute("type")
            if (epubType.split(' ').any { it == "toc" } || type.split(' ').any { it == "toc" }) {
                return nav
            }
        }
        if (firstNav != null) return firstNav

        val fallback = doc.getElementsByTagName("nav")
        for (i in 0 until fallback.length) {
            val nav = fallback.item(i) as? Element ?: continue
            val epubType = nav.getAttribute("epub:type")
            val type = nav.getAttribute("type")
            if (epubType.split(' ').any { it == "toc" } || type.split(' ').any { it == "toc" }) {
                return nav
            }
            if (firstNav == null) firstNav = nav
        }
        return firstNav
    }

    private fun collectNavListEntries(
        navElement: Element,
        navZipPath: String,
        out: MutableList<EpubTocEntry>,
    ) {
        directChildElements(navElement)
            .filter { it.matchesLocalName("ol") || it.matchesLocalName("ul") }
            .forEach { listElement ->
                collectListNodeEntries(
                    listElement = listElement,
                    baseZipPath = navZipPath,
                    depth = 0,
                    out = out,
                )
            }
    }

    private fun collectListNodeEntries(
        listElement: Element,
        baseZipPath: String,
        depth: Int,
        out: MutableList<EpubTocEntry>,
    ) {
        directChildElements(listElement)
            .filter { it.matchesLocalName("li") }
            .forEach { li ->
                val anchor = findFirstAnchorInLi(li)
                if (anchor != null) {
                    val label = anchor.textContent?.trim().orEmpty()
                    if (label.isNotBlank()) {
                        val href = anchor.getAttribute("href").takeIf { it.isNotBlank() }
                        out += EpubTocEntry(
                            label = label,
                            hrefZipPath = href?.let { resolveRelativeZipPath(baseZipPath, it) },
                            depth = depth,
                        )
                    }
                }

                directChildElements(li)
                    .filter { it.matchesLocalName("ol") || it.matchesLocalName("ul") }
                    .forEach { nested ->
                        collectListNodeEntries(
                            listElement = nested,
                            baseZipPath = baseZipPath,
                            depth = depth + 1,
                            out = out,
                        )
                    }
            }
    }

    private fun findFirstAnchorInLi(li: Element): Element? {
        directChildElements(li).forEach { child ->
            if (child.matchesLocalName("a")) return child
            if (!child.matchesLocalName("ol") && !child.matchesLocalName("ul")) {
                findFirstAnchorInBranch(child)?.let { return it }
            }
        }
        return null
    }

    private fun findFirstAnchorInBranch(element: Element): Element? {
        if (element.matchesLocalName("a")) return element
        directChildElements(element).forEach { child ->
            if (child.matchesLocalName("ol") || child.matchesLocalName("ul")) return@forEach
            findFirstAnchorInBranch(child)?.let { return it }
        }
        return null
    }

    private fun parseNcxTocEntries(
        zip: ZipFile,
        ncxZipPath: String,
    ): List<EpubTocEntry>? {
        val entry = zip.getEntry(ncxZipPath) ?: return null
        val doc = zip.getInputStream(entry).use(::parseXml)

        val navMap = firstElementByLocalName(doc, "navMap") ?: return emptyList()
        val result = mutableListOf<EpubTocEntry>()
        directChildElements(navMap)
            .filter { it.matchesLocalName("navPoint") }
            .forEach { navPoint ->
                collectNcxNavPoints(
                    navPoint = navPoint,
                    baseZipPath = ncxZipPath,
                    depth = 0,
                    out = result,
                )
            }
        return result
    }

    private fun collectNcxNavPoints(
        navPoint: Element,
        baseZipPath: String,
        depth: Int,
        out: MutableList<EpubTocEntry>,
    ) {
        val label = firstElementByLocalNameDesc(navPoint, "text")
            ?.textContent
            ?.trim()
            .orEmpty()
        val contentSrc = firstElementByLocalNameDesc(navPoint, "content")
            ?.getAttribute("src")
            ?.takeIf { it.isNotBlank() }
        if (label.isNotBlank()) {
            out += EpubTocEntry(
                label = label,
                hrefZipPath = contentSrc?.let { resolveRelativeZipPath(baseZipPath, it) },
                depth = depth,
            )
        }

        directChildElements(navPoint)
            .filter { it.matchesLocalName("navPoint") }
            .forEach { child ->
                collectNcxNavPoints(
                    navPoint = child,
                    baseZipPath = baseZipPath,
                    depth = depth + 1,
                    out = out,
                )
            }
    }

    private fun parseMetaCoverId(doc: Document): String? {
        val metaNodes = doc.getElementsByTagNameNS("*", "meta")
        for (i in 0 until metaNodes.length) {
            val element = metaNodes.item(i) as? Element ?: continue
            if (element.getAttribute("name") == "cover") {
                return element.getAttribute("content").takeIf { it.isNotBlank() }
            }
        }

        val fallback = doc.getElementsByTagName("meta")
        for (i in 0 until fallback.length) {
            val element = fallback.item(i) as? Element ?: continue
            if (element.getAttribute("name") == "cover") {
                return element.getAttribute("content").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun parseManifestItems(doc: Document): List<ManifestItem> {
        val items = mutableListOf<ManifestItem>()

        val nsNodes = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until nsNodes.length) {
            val element = nsNodes.item(i) as? Element ?: continue
            val parsed = element.toManifestItemOrNull()
            if (parsed != null) items += parsed
        }
        if (items.isNotEmpty()) return items

        val fallback = doc.getElementsByTagName("item")
        for (i in 0 until fallback.length) {
            val element = fallback.item(i) as? Element ?: continue
            val parsed = element.toManifestItemOrNull()
            if (parsed != null) items += parsed
        }
        return items
    }

    private fun Element.toManifestItemOrNull(): ManifestItem? {
        val id = getAttribute("id").orEmpty()
        val href = getAttribute("href").orEmpty()
        val mediaType = getAttribute("media-type").orEmpty()
        val properties = getAttribute("properties").orEmpty()
        if (id.isBlank() || href.isBlank()) return null
        return ManifestItem(
            id = id,
            href = href,
            mediaType = mediaType,
            properties = properties,
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

    private fun firstElementByLocalNameDesc(node: Node, localName: String): Element? {
        if (node is Element && node.matchesLocalName(localName)) return node
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val found = firstElementByLocalNameDesc(child, localName)
            if (found != null) return found
        }
        return null
    }

    private fun resolveRelativeZipPath(basePath: String, relativePath: String): String {
        val baseDir = basePath.substringBeforeLast('/', missingDelimiterValue = "")
        val raw = if (baseDir.isBlank()) relativePath else "$baseDir/$relativePath"
        val segments = mutableListOf<String>()
        raw.split('/').forEach { segment ->
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )
}
