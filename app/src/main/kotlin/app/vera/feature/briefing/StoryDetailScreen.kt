package app.vera.feature.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vera.core.briefing.StoryChat
import app.vera.core.model.Article
import app.vera.data.StoryDetailHolder
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoryDetailViewModel @Inject constructor(
    private val holder: StoryDetailHolder,
    private val chat: StoryChat
) : ViewModel() {
    val story = holder.selected

    suspend fun ask(question: String, history: String) =
        story.value?.let { s ->
            chat.ask(
                Article(id = s.articleId, sourceId = "", title = s.title, body = s.body, url = s.url),
                question, history
            )
        }
}

private data class Msg(val fromUser: Boolean, val text: String, val sources: List<String> = emptyList())

/**
 * Full-screen story view with a follow-up chat.
 *
 * This is a navigation destination rather than a Dialog: Compose dialogs don't reliably receive
 * window insets, which left the chat box hidden under the navigation bar.
 */
@Composable
fun StoryDetailScreen(
    onBack: () -> Unit,
    viewModel: StoryDetailViewModel = hiltViewModel()
) {
    val story by viewModel.story.collectAsStateWithLifecycle()
    val messages = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val s = story ?: run {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No story selected", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp))
        }
        return
    }

    fun send() {
        val q = input.trim()
        if (q.isEmpty() || sending) return
        val history = messages.joinToString("\n") { (if (it.fromUser) "USER" else "VERA") + ": " + it.text }
        messages.add(Msg(true, q)); input = ""; sending = true
        scope.launch {
            val a = viewModel.ask(q, history)
            messages.add(Msg(false, a?.text ?: "Something went wrong.", a?.extraSources.orEmpty()))
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Story detail", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(buildString { append(s.outlet); if (s.country.isNotBlank()) append(" · ${s.country}") },
                    color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (s.leaningLabel.isNotBlank()) {
                    Text(s.leaningLabel, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp))
                            .background(Amber.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
            Text(s.title, color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)

            Text("KEY POINTS", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            s.keyPoints.forEach { p ->
                Row(Modifier.padding(top = 6.dp)) {
                    Text("•", color = Amber, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(p, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, lineHeight = 19.sp)
                }
            }

            Text("ASK ABOUT THIS STORY", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
            if (messages.isEmpty()) {
                Text("Ask Vera anything about this story. If the article doesn't answer it, Vera looks it up.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp, lineHeight = 17.sp)
            }
            messages.forEach { m ->
                val bg = if (m.fromUser) Violet.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
                Box(Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(bg)
                        .padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(m.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, lineHeight = 19.sp)
                        if (m.sources.isNotEmpty()) {
                            Text("🔎 also checked: ${m.sources.joinToString(", ")}",
                                color = Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                            Text("AI answers can be wrong or out of date — open the sources and check.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp,
                                lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            if (sending) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("Vera is thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.size(16.dp))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
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
