package app.vera.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vera.core.briefing.BriefingGenerator
import app.vera.core.model.BriefingItem
import app.vera.core.model.UserProgress
import app.vera.core.news.NewsRepository
import app.vera.data.ProgressRepository
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

@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val news: NewsRepository,
    private val generator: BriefingGenerator,
    private val settings: SettingsRepository,
    private val catalog: SourceCatalogProvider,
    private val progressRepo: ProgressRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val items: List<BriefingItem> = emptyList()
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
            val fetched = sources.flatMap { runCatching { news.fetch(it).take(3) }.getOrDefault(emptyList()) }
                .take(8)
            val articles = fetched.ifEmpty { SampleData.articles }
            val items = articles.map { generator.generate(it) }
            _state.value = UiState(loading = false, items = items)
        }
    }

    fun onBriefingCompleted(correctAnswers: Int) {
        viewModelScope.launch { progressRepo.completeBriefing(correctAnswers) }
    }

    fun slotTitle(): String =
        if (LocalTime.now().hour < 14) "Morning briefing" else "Evening briefing"
}
