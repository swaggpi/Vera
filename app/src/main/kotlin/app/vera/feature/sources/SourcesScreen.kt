package app.vera.feature.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import app.vera.data.SettingsRepository
import app.vera.data.SourceCatalogProvider
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Teal
import app.vera.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val catalog: SourceCatalogProvider,
    private val settings: SettingsRepository
) : ViewModel() {

    val all: List<NewsSource> = catalog.all()

    val enabled: StateFlow<Set<String>> = settings.enabledSourceIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun isOn(source: NewsSource, enabled: Set<String>): Boolean =
        if (enabled.isEmpty()) source.defaultOn else source.id in enabled

    fun toggle(source: NewsSource, on: Boolean) {
        viewModelScope.launch { settings.toggleSource(source.id, on) }
    }
}

@Composable
fun SourcesScreen(
    onBack: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val grouped = viewModel.all.groupBy { it.country }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text("Your sources", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Read across countries and ownership — the wider, the harder to fool.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            grouped.forEach { (country, sources) ->
                item(key = "h_$country") {
                    Text(country.uppercase(), color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                }
                items(sources.size, key = { sources[it].id }) { idx ->
                    SourceRow(
                        source = sources[idx],
                        on = viewModel.isOn(sources[idx], enabled),
                        onToggle = { viewModel.toggle(sources[idx], it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: NewsSource, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(source.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Text(ownershipLabel(source.ownership), color = ownershipColor(source.ownership), fontSize = 11.sp)
                Text("  ·  ${pressFreedomLabel(source.pressFreedomTier)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Switch(
            checked = on,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Amber, checkedTrackColor = Amber.copy(alpha = 0.4f))
        )
    }
}

private fun ownershipLabel(o: Ownership) = when (o) {
    Ownership.PUBLIC -> "Public broadcaster"
    Ownership.PRIVATE -> "Private"
    Ownership.STATE -> "State-controlled"
}

private fun ownershipColor(o: Ownership) = when (o) {
    Ownership.PUBLIC -> Teal
    Ownership.PRIVATE -> Violet
    Ownership.STATE -> Rose
}

private fun pressFreedomLabel(tier: Int) = when (tier) {
    1 -> "Press freedom: good"
    2 -> "Press freedom: satisfactory"
    3 -> "Press freedom: problematic"
    4 -> "Press freedom: difficult"
    else -> "Press freedom: very serious"
}
