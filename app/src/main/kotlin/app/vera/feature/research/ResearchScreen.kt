package app.vera.feature.research

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vera.core.model.Ownership
import app.vera.core.research.AnalyzedSource
import app.vera.core.research.Leaning
import app.vera.core.research.ResearchRepository
import app.vera.core.research.ResearchResult
import app.vera.core.speech.SpeechService
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Teal
import app.vera.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResearchViewModel @Inject constructor(
    private val research: ResearchRepository,
    private val speech: SpeechService
) : ViewModel() {

    data class UiState(
        val input: String = "",
        val loading: Boolean = false,
        val listening: Boolean = false,
        val result: ResearchResult? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val voiceAvailable: Boolean get() = speech.isAvailable()

    fun onInput(text: String) { _state.value = _state.value.copy(input = text) }

    fun investigate() {
        val claim = _state.value.input.trim()
        if (claim.isEmpty() || _state.value.loading) return
        _state.value = _state.value.copy(loading = true, result = null)
        viewModelScope.launch {
            val r = research.investigate(claim)
            _state.value = _state.value.copy(loading = false, result = r)
        }
    }

    fun speakInput() {
        _state.value = _state.value.copy(listening = true)
        viewModelScope.launch {
            val text = speech.listenOnce()
            _state.value = _state.value.copy(listening = false, input = text.ifBlank { _state.value.input })
            if (text.isNotBlank()) investigate()
        }
    }

    fun readResultAloud() {
        val text = _state.value.result?.coaching ?: return
        viewModelScope.launch { speech.speak(text) }
    }
}

@Composable
fun ResearchScreen(viewModel: ResearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Check what you heard", color = MaterialTheme.colorScheme.onBackground,
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Text("Say or type something you heard. Vera looks it up and coaches you — no verdicts, just how to weigh it.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInput,
                placeholder = { Text("e.g. \"I heard coffee cures headaches\"", fontSize = 13.sp) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.speakInput() }, enabled = viewModel.voiceAvailable && !state.listening) {
                Icon(Icons.Filled.Mic, contentDescription = "Speak",
                    tint = if (state.listening) Amber else Violet)
            }
        }
        Button(onClick = { viewModel.investigate() }, enabled = !state.loading,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = androidx.compose.ui.graphics.Color(0xFF201400))) {
            Text(if (state.loading) "Researching…" else "Check it", fontWeight = FontWeight.Bold)
        }

        if (state.loading) {
            CircularProgressIndicator(color = Amber, modifier = Modifier.padding(top = 8.dp))
        }

        state.result?.let { result ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("What Vera found", color = Amber, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.readResultAloud() }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", tint = Violet)
                        }
                    }
                    Text(result.coaching, color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                    if (result.sources.isNotEmpty()) {
                        Text("SOURCES · ${result.diversityNote}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                        result.sources.forEach { s -> SourceCard(s) { openUrl(context, s.url) } }
                        Text("Bias/leaning labels are approximate and for reflection, not definitive ratings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp,
                            modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(s: AnalyzedSource, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(s.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.outletName, color = Violet, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Chip(ownershipLabel(s.ownership), ownershipColor(s.ownership))
            if (s.leaning != Leaning.UNKNOWN) Chip(s.leaning.label, Amber)
        }
        if (s.summary.isNotBlank()) {
            Text(s.summary, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 6.dp), lineHeight = 17.sp)
        }
        Text("⚖ ${s.biasNote}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 6.dp), lineHeight = 16.sp)
    }
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

private fun ownershipLabel(o: Ownership) = when (o) {
    Ownership.PUBLIC -> "Public"
    Ownership.PRIVATE -> "Private"
    Ownership.STATE -> "State"
    Ownership.UNKNOWN -> "Unrated"
}

private fun ownershipColor(o: Ownership) = when (o) {
    Ownership.PUBLIC -> Teal
    Ownership.PRIVATE -> Violet
    Ownership.STATE -> Rose
    Ownership.UNKNOWN -> androidx.compose.ui.graphics.Color(0xFF9AA3B8)
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
