package app.vera.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The story currently open in the full-screen detail view, handed over when the user taps it. */
data class SelectedStory(
    val articleId: String,
    val title: String,
    val body: String,
    val url: String,
    val outlet: String,
    val country: String,
    val leaningLabel: String,
    val keyPoints: List<String>
)

@Singleton
class StoryDetailHolder @Inject constructor() {
    private val _selected = MutableStateFlow<SelectedStory?>(null)
    val selected: StateFlow<SelectedStory?> = _selected

    fun open(story: SelectedStory) { _selected.value = story }
    fun clear() { _selected.value = null }
}
