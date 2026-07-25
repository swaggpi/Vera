package app.vera.feature.briefing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vera.core.research.Leaning
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Violet
import kotlinx.coroutines.launch

@Composable
fun BriefingScreen(
    onOpenSources: () -> Unit,
    onOpenResearch: () -> Unit,
    onOpenAiSettings: () -> Unit,
    viewModel: BriefingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(viewModel.slotTitle(), color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("🔥 ${progress.streak}-day streak · ${progress.xp} XP",
                    color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Filled.Tune, contentDescription = "Choose sources", tint = Violet)
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            state.loading -> Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Amber)
                Text("Vera is preparing your briefing…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp)
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "model-banner") { app.vera.feature.model.ModelBanner(onOpenAiSettings = onOpenAiSettings) }
                items(state.items, key = { it.item.article.id }) { ui ->
                    BriefingCard(
                        ui = ui,
                        onGetSources = { viewModel.requestResearch(ui.item.article); onOpenResearch() },
                        onReadIt = { viewModel.onEngaged(); openUrl(context, ui.item.article.url) },
                        onKeyPoints = { viewModel.keyPoints(ui.item.article) },
                        onAsk = { q, history -> viewModel.ask(ui.item.article, q, history) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BriefingCard(
    ui: BriefingUi,
    onGetSources: () -> Unit,
    onReadIt: () -> Unit,
    onKeyPoints: suspend () -> List<String>,
    onAsk: suspend (question: String, history: String) -> String
) {
    val item = ui.item
    var points by remember(item.article.id) { mutableStateOf<List<String>?>(null) }
    var loading by remember(item.article.id) { mutableStateOf(false) }
    var showFull by remember(item.article.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            OutletRow(ui)
            Text(item.article.title, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            Text(item.plainSummary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp)

            if (item.manipulationWatch.isNotBlank()) {
                Row(
                    Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp))
                        .background(Violet.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("👁  ${item.manipulationWatch}", color = Violet, fontSize = 12.5.sp, lineHeight = 17.sp)
                }
            }

            Button(
                onClick = onGetSources,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            ) { Text("🔎  Get more sources", fontWeight = FontWeight.Bold) }

            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onReadIt) {
                    Text("Read it yourself ↗", color = Violet, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    if (points == null && !loading) {
                        loading = true
                        scope.launch { points = onKeyPoints(); loading = false }
                    } else {
                        points = null
                    }
                }) {
                    Text(if (points != null) "Hide key points" else "More details",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            if (loading) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("Vera is pulling out the key points…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            }
            points?.let { pts ->
                Column(Modifier.padding(top = 10.dp)) {
                    Text("KEY POINTS", color = Amber, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    pts.forEach { Bullet(it) }
                    TextButton(onClick = { showFull = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = Amber,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Full screen · ask questions", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showFull) {
        StoryDetailDialog(ui = ui, keyPoints = points.orEmpty(), onAsk = onAsk, onClose = { showFull = false })
    }
}

@Composable
private fun OutletRow(ui: BriefingUi) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            buildString { append(ui.outlet); if (ui.country.isNotBlank()) append(" · ${ui.country}") },
            color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        if (ui.leaning != Leaning.UNKNOWN) {
            Text(ui.leaning.label, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp))
                    .background(Amber.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(top = 6.dp)) {
        Text("•", color = Amber, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, lineHeight = 19.sp)
    }
}

private data class ChatMsg(val fromUser: Boolean, val text: String)

@Composable
private fun StoryDetailDialog(
    ui: BriefingUi,
    keyPoints: List<String>,
    onAsk: suspend (question: String, history: String) -> String,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val messages = remember { mutableStateListOf<ChatMsg>() }
            var input by remember { mutableStateOf("") }
            var sending by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            fun send() {
                val q = input.trim()
                if (q.isEmpty() || sending) return
                val history = messages.joinToString("\n") { (if (it.fromUser) "USER" else "VERA") + ": " + it.text }
                messages.add(ChatMsg(true, q))
                input = ""
                sending = true
                scope.launch {
                    val a = onAsk(q, history)
                    messages.add(ChatMsg(false, a))
                    sending = false
                }
            }

            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Top bar
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Story detail", color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // Scrollable content
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    OutletRow(ui)
                    Text(ui.item.article.title, color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)

                    Text("KEY POINTS", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
                    if (keyPoints.isEmpty()) {
                        Text(ui.item.plainSummary, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    } else {
                        keyPoints.forEach { Bullet(it) }
                    }

                    Text("ASK ABOUT THIS STORY", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                    if (messages.isEmpty()) {
                        Text("Ask Vera anything about this story — answers come from the article, on-device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
                    }
                    messages.forEach { m -> ChatBubble(m) }
                    if (sending) {
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("Vera is thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                        }
                    }
                    Spacer(Modifier.size(16.dp))
                }

                // Input row
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask a follow-up…", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        enabled = !sending
                    )
                    IconButton(onClick = { send() }, enabled = !sending && input.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = "Send",
                            tint = if (input.isNotBlank()) Amber else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    val bg = if (m.fromUser) Violet.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(m.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, lineHeight = 19.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
