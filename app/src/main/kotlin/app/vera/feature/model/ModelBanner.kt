package app.vera.feature.model

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vera.data.ModelCatalog
import app.vera.data.ModelManager
import app.vera.data.ModelPhase
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Teal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val manager: ModelManager
) : ViewModel() {
    val status = manager.status
    init { viewModelScope.launch { manager.loadIfPresent() } }
    fun download(option: ModelCatalog.ModelOption) { viewModelScope.launch { manager.download(option) } }
}

/**
 * The one-tap on-device-AI installer. Shown on Briefing until the Gemma model is downloaded; then it
 * confirms real AI is active. No adb or manual setup — the app fetches and loads the weights itself.
 */
@Composable
fun ModelBanner(onOpenAiSettings: () -> Unit = {}, viewModel: ModelViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            when (status.phase) {
                ModelPhase.ABSENT -> {
                    Text("Turn on real on-device AI", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Summaries are generic placeholders until you install a model. It runs entirely on " +
                        "your phone — nothing uploaded. Pick one (Wi-Fi recommended):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp,
                        lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    androidx.compose.material3.TextButton(onClick = onOpenAiSettings) {
                        Text("or use a cloud model (advanced) →", color = Amber, fontSize = 12.sp)
                    }
                    ModelCatalog.OPTIONS.forEachIndexed { i, opt ->
                        Button(
                            onClick = { viewModel.download(opt) },
                            colors = if (i == 0)
                                ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400))
                            else ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Download · ${opt.title}  (${opt.note})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                ModelPhase.DOWNLOADING -> {
                    Text("Installing on-device AI…", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { status.progress.coerceIn(0f, 1f) },
                        color = Amber, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    Text(status.message ?: "${(status.progress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp))
                }
                ModelPhase.READY -> {
                    Text("✓ On-device AI active", color = Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.TextButton(onClick = onOpenAiSettings) {
                        Text("AI engine settings →", color = Amber, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("A language model is running locally on your phone — summaries and coaching are " +
                        "real and private, nothing is uploaded. Pull to refresh a story for a real summary.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                ModelPhase.ERROR -> {
                    Text("Couldn't install the model", color = Rose, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(status.message ?: "Unknown error", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
                    Button(onClick = { viewModel.download(ModelCatalog.DEFAULT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                        modifier = Modifier.padding(top = 10.dp)) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
