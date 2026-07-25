package app.vera.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.model.Article
import app.vera.core.model.BriefingItem
import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import app.vera.core.model.UserProgress
import app.vera.core.news.NewsRepository
import app.vera.core.research.Leaning
import app.vera.core.research.OutletDirectory
import app.vera.data.ProgressRepository
import app.vera.data.ReadLogRepository
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
    val leaning: Leaning
)

@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val news: NewsRepository,
    private val generator: BriefingGenerator,
    private val settings: SettingsRepository,
    private val catalog: SourceCatalogProvider,
    private val progressRepo: ProgressRepository,
    private val readLog: ReadLogRepository
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
            val sources = catalog.selected(enabled)
            selectedSourceIds = sources.map { it.id }
            val byId = sources.associateBy { it.id }

            // Fetch each feed, then round-robin so the briefing spans sources instead of front-loading one.
            val perSource = sources.map { src ->
                runCatching { news.fetch(src).take(3) }.getOrDefault(emptyList())
            }
            val articles = roundRobin(perSource).take(8).ifEmpty { SampleData.articles }

            val items = articles.map { article ->
                val item = generator.generate(article)
                val src = byId[article.sourceId]
                val profile = OutletDirectory.forUrl(article.url)
                BriefingUi(
                    item = item,
                    outlet = src?.name ?: profile.name,
                    country = src?.country ?: "",
                    ownership = src?.ownership ?: profile.ownership,
                    leaning = profile.leaning
                )
            }
            _state.value = UiState(loading = false, items = items)
        }
    }

    private fun roundRobin(lists: List<List<Article>>): List<Article> {
        val out = ArrayList<Article>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) if (i < l.size) out.add(l[i])
        return out
    }

    fun onBriefingCompleted(correctAnswers: Int) {
        viewModelScope.launch {
            progressRepo.completeBriefing(correctAnswers)
            readLog.log(selectedSourceIds)   // feeds the news-diet meter
        }
    }

    fun slotTitle(): String =
        if (LocalTime.now().hour < 14) "Morning briefing" else "Evening briefing"
}
