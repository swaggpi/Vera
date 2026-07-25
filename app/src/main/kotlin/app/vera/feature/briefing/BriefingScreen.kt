package app.vera.feature.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vera.core.model.BriefingItem
import app.vera.core.model.QuizQuestion
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Violet

@Composable
fun BriefingScreen(
    onOpenSources: () -> Unit,
    viewModel: BriefingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    viewModel.slotTitle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "🔥 ${progress.streak}-day streak · ${progress.xp} XP",
                    color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Filled.Tune, contentDescription = "Choose sources", tint = Violet)
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(state.items, key = { it.article.id }) { item ->
                    BriefingCard(item) { correct -> viewModel.onBriefingCompleted(correct) }
                }
            }
        }
    }
}

@Composable
private fun BriefingCard(item: BriefingItem, onComplete: (correct: Int) -> Unit) {
    // Tracks the picked option per question index; drives feedback + completion.
    val answers = remember(item.article.id) { mutableStateMapOf<Int, Int>() }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.article.title, color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            Text(item.plainSummary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), lineHeight = 20.sp)

            if (item.manipulationWatch.isNotBlank()) {
                Row(
                    Modifier.padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Violet.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("👁  ${item.manipulationWatch}", color = Violet, fontSize = 12.5.sp, lineHeight = 17.sp)
                }
            }

            item.quiz.forEachIndexed { qi, q ->
                QuizBlock(
                    question = q,
                    picked = answers[qi],
                    onPick = { chosen -> if (answers[qi] == null) answers[qi] = chosen }
                )
            }

            val answeredAll = answers.size == item.quiz.size && item.quiz.isNotEmpty()
            if (answeredAll) {
                val correct = item.quiz.indices.count { answers[it] == item.quiz[it].correctIndex }
                CompleteRow(correct = correct, total = item.quiz.size, onComplete = { onComplete(correct) })
            }
        }
    }
}

@Composable
private fun QuizBlock(question: QuizQuestion, picked: Int?, onPick: (Int) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(question.question, color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
        question.options.forEachIndexed { i, opt ->
            val isCorrect = i == question.correctIndex
            val revealed = picked != null
            val bg = when {
                revealed && isCorrect -> Amber.copy(alpha = 0.16f)
                revealed && picked == i -> Rose.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            val border = when {
                revealed && isCorrect -> Amber
                revealed && picked == i -> Rose
                else -> Color.Transparent
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(enabled = !revealed) { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (if (revealed && isCorrect) "✓  " else if (revealed && picked == i) "✕  " else "") + opt,
                    color = if (border == Color.Transparent) MaterialTheme.colorScheme.onSurface else border,
                    fontSize = 13.5.sp
                )
            }
        }
        if (picked != null && question.explanation.isNotBlank()) {
            Text(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp), lineHeight = 17.sp)
        }
    }
}

@Composable
private fun CompleteRow(correct: Int, total: Int, onComplete: () -> Unit) {
    var done by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Surface(
        color = if (done) Amber.copy(alpha = 0.18f) else Amber,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            .clickable(enabled = !done) { done = true; onComplete() }
    ) {
        Text(
            if (done) "✓ Added to your streak · $correct/$total correct"
            else "Mark briefing done · $correct/$total correct",
            color = if (done) Amber else Color(0xFF201400),
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
