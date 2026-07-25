package app.vera.data

import app.vera.core.model.Article
import app.vera.core.model.NewsSource
import app.vera.core.news.NewsRepository
import app.vera.core.news.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches a source's RSS/Atom feed over the network and parses it with the pure :core parser. */
class NewsRepositoryImpl(private val client: OkHttpClient) : NewsRepository {

    override suspend fun fetch(source: NewsSource): List<Article> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(source.rssUrl)
                .header("User-Agent", "VeraMIL/0.1 (+non-commercial media-literacy prototype)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                RssParser.parse(body, source.id)
            }
        } catch (_: Exception) {
            emptyList()   // offline / blocked feed → caller falls back to sample content
        }
    }
}
