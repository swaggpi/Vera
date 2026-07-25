package app.vera.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vera.core.insights.DietMeter
import app.vera.core.insights.DietStats
import app.vera.core.model.Ownership
import app.vera.data.ReadLogRepository
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Teal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val readLog: ReadLogRepository
) : ViewModel() {

    data class UiState(val loading: Boolean = true, val stats: DietStats? = null)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState(loading = false, stats = DietMeter.compute(readLog.readSources()))
        }
    }
}

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your news diet", color = MaterialTheme.colorScheme.onBackground,
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))

        val stats = state.stats
        if (stats == null || stats.total == 0) {
            Text("Read a few briefings and Vera will show how varied your sources are — across countries and ownership. The wider your diet, the harder you are to fool.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${(stats.diversityScore * 100).toInt()}%", color = Amber,
                    fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Text("source diversity", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { stats.diversityScore.toFloat() },
                    color = Amber, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                if (stats.isEchoChamber) {
                    Text("⚠ Echo-chamber warning" +
                        (stats.dominantSource?.let { " — most of your reading is from $it." } ?: " — try adding sources from other countries."),
                        color = Rose, fontSize = 12.5.sp, modifier = Modifier.padding(top = 12.dp), lineHeight = 17.sp)
                } else {
                    Text("Nicely balanced — keep reading across the spectrum.",
                        color = Teal, fontSize = 12.5.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        Breakdown("By country", stats.byCountry.mapKeys { it.key })
        Breakdown("By ownership", stats.byOwnership.mapKeys { ownershipLabel(it.key) })
    }
}

@Composable
private fun Breakdown(title: String, data: Map<String, Int>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text("$count", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun ownershipLabel(o: Ownership) = when (o) {
    Ownership.PUBLIC -> "Public broadcasters"
    Ownership.PRIVATE -> "Private"
    Ownership.STATE -> "State-controlled"
}
