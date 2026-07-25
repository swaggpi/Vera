package app.vera.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.briefing.StoryAnswer
import app.vera.core.briefing.StoryChat
import app.vera.core.model.Article
import app.vera.core.model.BriefingItem
import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import app.vera.core.model.UserProgress
import app.vera.core.news.BriefingRanker
import app.vera.core.news.NewsRepository
import app.vera.core.research.Leaning
import app.vera.core.research.OutletDirectory
import app.vera.data.ProgressRepository
import app.vera.data.ReadLogRepository
import app.vera.data.ResearchInbox
import app.vera.data.SampleData
import app.vera.data.SettingsRepository
import app.vera.data.SourceCatalogProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** A briefing card plus who published it, so the UI can label outlet + leaning. */
data class BriefingUi(
    val item: BriefingItem,
    val outlet: String,
    val country: String,
    val ownership: Ownership,
    val leaning: Leaning,
    val alsoReportedBy: List<String> = emptyList(),
    val matchedInterests: List<String> = emptyList()
)

@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val news: NewsRepository,
    private val generator: BriefingGenerator,
    private val settings: SettingsRepository,
    private val catalog: SourceCatalogProvider,
    private val progressRepo: ProgressRepository,
    private val readLog: ReadLogRepository,
    private val inbox: ResearchInbox,
    private val storyChat: StoryChat
) : ViewModel() {

    private var selectedSourceIds: List<String> = emptyList()

    data class UiState(
        val loading: Boolean = true,
        val items: List<BriefingUi> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val progress: StateFlow<UserProgress> = progressRepo.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState(loading = true)
            val enabled = settings.enabledSourceIds.first()
            val interests = settings.interests.first().toList()
            val sources = catalog.selected(enabled)
            selectedSourceIds = sources.map { it.id }
            val byId = sources.associateBy { it.id }

            // Pull a wider pool, then let the ranker decide what actually deserves a slot.
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

            // Generate progressively: on-device inference is slow, so surface each card as it's ready
            // instead of blocking on the whole batch.
            val done = ArrayList<BriefingUi>(clusters.size)
            for (cluster in clusters) {
                val article = cluster.lead
                val item = generator.generate(article)
                val src = byId[article.sourceId]
                val profile = OutletDirectory.forUrl(article.url)
                done.add(
                    BriefingUi(
                        item = item,
                        outlet = src?.name ?: profile.name,
                        country = src?.country ?: "",
                        ownership = src?.ownership ?: profile.ownership,
                        leaning = profile.leaning,
                        alsoReportedBy = cluster.alsoReportedBy,
                        matchedInterests = cluster.matchedInterests
                    )
                )
                _state.value = UiState(loading = false, items = done.toList())
            }
            if (done.isEmpty()) _state.value = UiState(loading = false, items = emptyList())
        }
    }

    /** Any real interaction with a story counts as engaging with the briefing (streak/XP once a day). */
    fun onEngaged() {
        viewModelScope.launch {
            progressRepo.completeBriefing(0)
            readLog.log(selectedSourceIds)   // feeds the news-diet meter
        }
    }

    /** "Get more sources": hand this story to the Verify tab, which auto-researches it. */
    fun requestResearch(article: Article) {
        onEngaged()
        inbox.submit(article.title)
    }

    suspend fun keyPoints(article: Article): List<String> {
        onEngaged()
        return generator.keyPoints(article)
    }

    suspend fun ask(article: Article, question: String, historyText: String): StoryAnswer =
        storyChat.ask(article, question, historyText)

    fun slotTitle(): String =
        if (LocalTime.now().hour < 14) "Morning briefing" else "Evening briefing"

    private companion object {
        // On-device inference is slow; keep the briefing short and let cards stream in.
        const val MAX_STORIES = 5
    }
}
