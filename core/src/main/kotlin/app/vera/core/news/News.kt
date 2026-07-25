package app.vera.core.news

import app.vera.core.model.Article
import app.vera.core.model.NewsSource
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

/** Loads the bundled source catalog (assets/sources_catalog.json) from its raw JSON string. */
object SourceCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(raw: String): List<NewsSource> = json.decodeFromString(raw)
}

/** Fetches and returns articles for a source. Real impl (OkHttp) lives in :app; fakes in tests. */
interface NewsRepository {
    suspend fun fetch(source: NewsSource): List<Article>
}

/**
 * Dependency-free RSS 2.0 / Atom parser. Pure JVM (javax.xml) so it runs in fast unit tests and
 * on-device unchanged. Never throws on malformed feeds — returns what it can.
 */
object RssParser {

    fun parse(xml: String, sourceId: String): List<Article> {
        if (xml.isBlank()) return emptyList()
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                // Harden against XXE — we never need external entities.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val doc = factory.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
            doc.documentElement.normalize()

            val items = doc.getElementsByTagName("item")
            if (items.length > 0) (0 until items.length).mapNotNull { i ->
                rssItem(items.item(i) as? Element ?: return@mapNotNull null, sourceId)
            } else {
                val entries = doc.getElementsByTagName("entry")
                (0 until entries.length).mapNotNull { i ->
                    atomEntry(entries.item(i) as? Element ?: return@mapNotNull null, sourceId)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rssItem(el: Element, sourceId: String): Article? {
        val title = text(el, "title") ?: return null
        val link = text(el, "link").orEmpty()
        val body = stripHtml(text(el, "description") ?: text(el, "content:encoded").orEmpty())
        return Article(
            id = link.ifBlank { "$sourceId:$title" },
            sourceId = sourceId,
            title = title.trim(),
            body = body.trim(),
            url = link.trim(),
            publishedAtEpochMs = 0L,
            imageUrl = attr(el, "enclosure", "url") ?: attr(el, "media:content", "url")
        )
    }

    private fun atomEntry(el: Element, sourceId: String): Article? {
        val title = text(el, "title") ?: return null
        val link = linkHref(el)
        val body = stripHtml(text(el, "summary") ?: text(el, "content").orEmpty())
        return Article(
            id = link.ifBlank { "$sourceId:$title" },
            sourceId = sourceId,
            title = title.trim(),
            body = body.trim(),
            url = link.trim()
        )
    }

    private fun text(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.parentNode == parent) return n.textContent
        }
        return if (nodes.length > 0) nodes.item(0).textContent else null
    }

    private fun attr(parent: Element, tag: String, attr: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        val v = (nodes.item(0) as? Element)?.getAttribute(attr)
        return v?.takeIf { it.isNotBlank() }
    }

    private fun linkHref(entry: Element): String {
        val links = entry.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val e = links.item(i) as? Element ?: continue
            val rel = e.getAttribute("rel")
            if (rel.isBlank() || rel == "alternate") {
                val href = e.getAttribute("href")
                if (href.isNotBlank()) return href
            }
        }
        return (links.item(0) as? Element)?.getAttribute("href").orEmpty()
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
}
