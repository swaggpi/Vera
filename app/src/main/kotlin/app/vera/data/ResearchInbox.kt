package app.vera.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny cross-feature mailbox. "Get more sources" on a briefing card drops the story here and
 * switches to the Verify tab; [app.vera.feature.research.ResearchViewModel] picks it up and runs the
 * research automatically.
 */
@Singleton
class ResearchInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun submit(query: String) { _pending.value = query }
    fun clear() { _pending.value = null }
}
