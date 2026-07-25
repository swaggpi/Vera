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
    fun download() { viewModelScope.launch { manager.download() } }
}

/**
 * The one-tap on-device-AI installer. Shown on Briefing until the Gemma model is downloaded; then it
 * confirms real AI is active. No adb or manual setup — the app fetches and loads the weights itself.
 */
@Composable
fun ModelBanner(viewModel: ModelViewModel = hiltViewModel()) {
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
                    Text("Right now summaries are generic placeholders. Download Vera's brain " +
                        "(~${ModelCatalog.APPROX_MB} MB, Wi-Fi recommended) to get real, private AI — it runs " +
                        "entirely on your phone, nothing uploaded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp,
                        lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = viewModel::download,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                        modifier = Modifier.padding(top = 10.dp)) {
                        Text("Download Vera's brain", fontWeight = FontWeight.Bold)
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
                    Text("Gemma is running locally on your phone. Summaries and coaching are real and private.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                ModelPhase.ERROR -> {
                    Text("Couldn't install the model", color = Rose, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(status.message ?: "Unknown error", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp), lineHeight = 16.sp)
                    Button(onClick = viewModel::download,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                        modifier = Modifier.padding(top = 10.dp)) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
