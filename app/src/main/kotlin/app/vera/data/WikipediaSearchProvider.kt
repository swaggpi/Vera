package app.vera.data

import app.vera.core.research.SearchProvider
import app.vera.core.research.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keyless default search backend (Wikipedia REST). Reliable and free; good for background and
 * definitions. Swap/add a `BraveSearchProvider` or `TavilySearchProvider` (API key) for live
 * news-claim checking — same [SearchProvider] interface.
 */
class WikipediaSearchProvider(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SearchProvider {

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://en.wikipedia.org/w/rest.php/v1/search/page".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", "5")
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "VeraMIL/0.1 (non-commercial media-literacy prototype)")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<WikiResponse>(body).pages.map {
                    SearchResult(
                        title = it.title,
                        url = "https://en.wikipedia.org/wiki/${it.key}",
                        snippet = stripHtml(it.excerpt ?: it.description.orEmpty()),
                        sourceName = "Wikipedia"
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun stripHtml(s: String) = s.replace(Regex("<[^>]*>"), "").trim()

    @Serializable private data class WikiResponse(val pages: List<WikiPage> = emptyList())
    @Serializable private data class WikiPage(
        val key: String = "",
        val title: String = "",
        val excerpt: String? = null,
        val description: String? = null
    )
}
