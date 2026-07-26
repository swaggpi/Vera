package app.vera.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.briefing.StoryAnswer
import app.vera.core.briefing.StoryChat
import app.vera.core.model.BriefingItem
import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import app.vera.core.model.UserProgress
import app.vera.core.briefing.CachedBriefing
import app.vera.core.briefing.CachedCard
import app.vera.core.briefing.slotForHour
import app.vera.core.model.Article
import app.vera.core.research.Leaning
import app.vera.core.research.OutletDirectory
import app.vera.data.BriefingRepository
import app.vera.data.ProgressRepository
import app.vera.data.ReadLogRepository
import app.vera.data.ResearchInbox
import app.vera.data.SelectedStory
import app.vera.data.StoryDetailHolder
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
    private val briefings: BriefingRepository,
    private val generator: BriefingGenerator,
    private val progressRepo: ProgressRepository,
    private val readLog: ReadLogRepository,
    private val inbox: ResearchInbox,
    private val storyChat: StoryChat,
    private val storyHolder: StoryDetailHolder
) : ViewModel() {

    private var selectedSourceIds: List<String> = emptyList()

    data class UiState(
        val loading: Boolean = true,
        val items: List<BriefingUi> = emptyList(),
        /** Showing yesterday's cards while a fresh briefing is generated. */
        val stale: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val progress: StateFlow<UserProgress> = progressRepo.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProgress())

    init { load() }

    /** Show the cached briefing immediately; only generate if there isn't a fresh one. */
    fun load(force: Boolean = false) {
        viewModelScope.launch {
            val slot = slotForHour(LocalTime.now().hour)
            val now = System.currentTimeMillis()
            selectedSourceIds = briefings.sourceIdsForSelection()

            if (!force) {
                val cached = briefings.cached(slot)
                if (cached != null && cached.isFresh(now)) {
                    _state.value = UiState(loading = false, items = cached.cards.map(::toUi), stale = false)
                    return@launch
                }
                // Show whatever we have while the fresh one is being written.
                if (cached != null && cached.cards.isNotEmpty()) {
                    _state.value = UiState(loading = false, items = cached.cards.map(::toUi), stale = true)
                }
            }

            if (_state.value.items.isEmpty()) _state.value = UiState(loading = true)
            briefings.generate(slot, now) { cards ->
                _state.value = UiState(loading = false, items = cards.map(::toUi), stale = false)
            }
        }
    }

    fun refresh() = load(force = true)

    private fun toUi(c: CachedCard) = BriefingUi(
        item = BriefingItem(
            article = Article(id = c.articleId, sourceId = "", title = c.displayTitle.ifBlank { c.title }, body = c.body, url = c.url),
            plainSummary = c.summary,
            whyItMatters = "",
            manipulationWatch = c.manipulationWatch,
            quiz = emptyList()
        ),
        outlet = c.outlet,
        country = c.country,
        ownership = runCatching { Ownership.valueOf(c.ownership) }.getOrDefault(Ownership.UNKNOWN),
        leaning = runCatching { Leaning.valueOf(c.leaning) }.getOrDefault(Leaning.UNKNOWN),
        alsoReportedBy = c.alsoReportedBy,
        matchedInterests = c.matchedInterests
    )

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
        return generator.keyPoints(article, briefings.currentLanguage())
    }

    /** Hand a story to the full-screen detail view. */
    fun openStory(ui: BriefingUi, keyPoints: List<String>) {
        onEngaged()
        storyHolder.open(
            SelectedStory(
                articleId = ui.item.article.id,
                title = ui.item.article.title,
                body = ui.item.article.body,
                url = ui.item.article.url,
                outlet = ui.outlet,
                country = ui.country,
                leaningLabel = if (ui.leaning == Leaning.UNKNOWN) "" else ui.leaning.label,
                keyPoints = keyPoints
            )
        )
    }

    fun slotTitle(): String =
        if (LocalTime.now().hour < 14) "Morning briefing" else "Evening briefing"

    private companion object {
        // On-device inference is slow; keep the briefing short and let cards stream in.
        const val MAX_STORIES = 5
    }
}
