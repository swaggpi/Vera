package app.vera.data

import app.vera.core.research.SearchResult
import app.vera.core.research.SearchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

/**
 * Keyless multi-domain web search via DuckDuckGo's HTML endpoint. Gives results across many outlets
 * (news, science, fact-checkers) so the research feature can show a *variety* of sources, not one
 * site. Best-effort: DDG may throttle/return no rows, in which case we fall back to other providers.
 */
class DuckDuckGoSearchProvider(private val client: OkHttpClient) : SearchProvider {

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=" + query.encode())
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) VeraMIL/0.1")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                parse(resp.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parse(html: String): List<SearchResult> {
        val links = LINK.findAll(html).toList()
        val snippets = SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
        return links.mapIndexed { i, m ->
            val url = decodeUddg(m.groupValues[1])
            SearchResult(
                title = clean(m.groupValues[2]),
                url = url,
                snippet = snippets.getOrElse(i) { "" }
            )
        }.filter { it.url.startsWith("http") && it.title.isNotBlank() }.take(15)
    }

    private fun decodeUddg(href: String): String {
        val fixed = if (href.startsWith("//")) "https:$href" else href
        val idx = fixed.indexOf("uddg=")
        if (idx < 0) return fixed
        val enc = fixed.substring(idx + 5).substringBefore('&')
        return runCatching { URLDecoder.decode(enc, "UTF-8") }.getOrDefault(fixed)
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&#x27;", "'").replace("&quot;", "\"")
        .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("\\s+"), " ").trim()

    private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private val LINK = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        private val SNIPPET = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
    }
}
