package app.vera.data

import app.vera.core.briefing.BriefingGenerator
import app.vera.core.briefing.CachedBriefing
import app.vera.core.briefing.CachedCard
import app.vera.core.model.AppLanguage
import app.vera.core.model.BriefingSlot
import app.vera.core.news.BriefingRanker
import app.vera.core.news.NewsRepository
import app.vera.core.research.OutletDirectory
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds briefings and keeps the finished result in the database.
 *
 * On-device generation is slow (minutes for a full briefing), so the background worker calls
 * [generate] ahead of time and the UI only ever reads [cached] — which makes opening the app instant.
 */
@Singleton
class BriefingRepository @Inject constructor(
    private val news: NewsRepository,
    private val generator: BriefingGenerator,
    private val settings: SettingsRepository,
    private val catalog: SourceCatalogProvider,
    private val dao: BriefingCacheDao
) {

    suspend fun currentLanguage(): AppLanguage =
        AppLanguage.resolve(settings.language.first(), java.util.Locale.getDefault().language)

    /** A cached briefing is only usable if it was written in the language the user wants now. */
    suspend fun cached(slot: BriefingSlot): CachedBriefing? {
        val c = dao.get(slot.name)?.let { CachedBriefing.decode(it.payload) } ?: return null
        return if (c.languageCode == currentLanguage().code) c else null
    }

    /** Which source ids the last generated briefing drew on (for the news-diet log). */
    suspend fun sourceIdsForSelection(): List<String> =
        catalog.selected(settings.enabledSourceIds.first()).map { it.id }

    /**
     * Fetch → rank → summarise → store. [onCard] fires as each card is finished so a foreground
     * caller can stream results instead of waiting for the whole batch.
     */
    suspend fun generate(
        slot: BriefingSlot,
        nowEpochMs: Long,
        onCard: (List<CachedCard>) -> Unit = {}
    ): CachedBriefing {
        val enabled = settings.enabledSourceIds.first()
        val interests = settings.interests.first().toList()
        val language = currentLanguage()
        val sources = catalog.selected(enabled)
        val byId = sources.associateBy { it.id }

        val pool = sources.flatMap { src ->
            runCatching { news.fetch(src).take(6) }.getOrDefault(emptyList())
        }.ifEmpty { SampleData.articles }

        val clusters = BriefingRanker.rank(
            articles = pool,
            outletName = { id -> byId[id]?.name ?: id },
            countryOf = { id -> byId[id]?.country.orEmpty() },
            interests = interests,
            limit = MAX_STORIES
        )

        val cards = ArrayList<CachedCard>(clusters.size)
        for (cluster in clusters) {
            val article = cluster.lead
            val item = generator.generate(article, language)
            val src = byId[article.sourceId]
            val profile = OutletDirectory.forUrl(article.url)
            cards.add(
                CachedCard(
                    articleId = article.id,
                    title = article.title,
                    displayTitle = generator.localizedTitle(article, language),
                    url = article.url,
                    summary = item.plainSummary,
                    manipulationWatch = item.manipulationWatch,
                    outlet = src?.name ?: profile.name,
                    country = src?.country.orEmpty(),
                    ownership = (src?.ownership ?: profile.ownership).name,
                    leaning = profile.leaning.name,
                    alsoReportedBy = cluster.alsoReportedBy,
                    matchedInterests = cluster.matchedInterests,
                    body = article.body
                )
            )
            onCard(cards.toList())
        }

        val briefing = CachedBriefing(slot.name, language.code, nowEpochMs, cards)
        if (cards.isNotEmpty()) {
            dao.upsert(BriefingCacheEntity(slot.name, CachedBriefing.encode(briefing), nowEpochMs))
        }
        return briefing
    }

    private companion object {
        const val MAX_STORIES = 5
    }
}
