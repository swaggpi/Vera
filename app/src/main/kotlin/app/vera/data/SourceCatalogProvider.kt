package app.vera.data

import android.content.Context
import app.vera.core.model.NewsSource
import app.vera.core.news.SourceCatalog

/** Loads and caches the bundled source catalog from assets/sources_catalog.json. */
class SourceCatalogProvider(private val context: Context) {

    private val cached: List<NewsSource> by lazy {
        val raw = context.assets.open("sources_catalog.json")
            .bufferedReader().use { it.readText() }
        SourceCatalog.parse(raw)
    }

    fun all(): List<NewsSource> = cached

    /** Sources the briefing should use: the user's picks, or the catalog defaults if none chosen. */
    fun selected(enabledIds: Set<String>): List<NewsSource> =
        if (enabledIds.isEmpty()) cached.filter { it.defaultOn }
        else cached.filter { it.id in enabledIds }
}
