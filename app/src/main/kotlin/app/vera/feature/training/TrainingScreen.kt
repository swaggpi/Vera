package app.vera.feature.training

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.vera.core.training.InoculationBank
import app.vera.core.training.InoculationScoring
import app.vera.core.training.SiftGuidance
import app.vera.core.training.SiftCoach
import app.vera.core.training.TechniqueChallenge
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val coach: SiftCoach
) : ViewModel() {

    data class UiState(
        val challengeIndex: Int = 0,
        val picked: Int? = null,
        val streak: Int = 0,
        val coachInput: String = "",
        val coaching: Boolean = false,
        val guidance: SiftGuidance? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val challenge: TechniqueChallenge
        get() = InoculationBank.challenges[_state.value.challengeIndex % InoculationBank.challenges.size]

    fun answer(index: Int) {
        if (_state.value.picked != null) return
        val correct = InoculationScoring.isCorrect(challenge, index)
        _state.value = _state.value.copy(
            picked = index,
            streak = if (correct) _state.value.streak + 1 else 0
        )
    }

    fun next() {
        _state.value = _state.value.copy(challengeIndex = _state.value.challengeIndex + 1, picked = null)
    }

    fun onCoachInput(text: String) { _state.value = _state.value.copy(coachInput = text) }

    fun askCoach() {
        val claim = _state.value.coachInput.trim()
        if (claim.isEmpty() || _state.value.coaching) return
        _state.value = _state.value.copy(coaching = true, guidance = null)
        viewModelScope.launch {
            val g = coach.coach(claim)
            _state.value = _state.value.copy(coaching = false, guidance = g)
        }
    }
}

@Composable
fun TrainingScreen(viewModel: TrainingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val challenge = viewModel.challenge

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Train your eye", color = MaterialTheme.colorScheme.onBackground,
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))

        // --- Daily prebunking challenge ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🔥 ${state.streak} in a row · spot the technique", color = Amber,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(challenge.claim, color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp), lineHeight = 21.sp)
                Text("— ${challenge.source}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))

                challenge.options.forEachIndexed { i, opt ->
                    val revealed = state.picked != null
                    val isCorrect = i == challenge.correctIndex
                    val bg = when {
                        revealed && isCorrect -> Amber.copy(alpha = 0.16f)
                        revealed && state.picked == i -> Rose.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(bg).clickable(enabled = !revealed) { viewModel.answer(i) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)) {
                        Text((if (revealed && isCorrect) "✓  " else if (revealed && state.picked == i) "✕  " else "") + opt,
                            color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp)
                    }
                }
                if (state.picked != null) {
                    Text("${challenge.technique}: ${challenge.explanation}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 12.dp), lineHeight = 17.sp)
                    Button(onClick = { viewModel.next() },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                        modifier = Modifier.padding(top = 12.dp)) {
                        Text("Next challenge →", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- SIFT coach ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Coach me through something I saw", color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.coachInput,
                    onValueChange = viewModel::onCoachInput,
                    placeholder = { Text("e.g. a photo of a shark on a flooded road", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    keyboardActions = KeyboardActions(onDone = { viewModel.askCoach() })
                )
                Button(onClick = { viewModel.askCoach() }, enabled = !state.coaching,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    modifier = Modifier.padding(top = 10.dp)) {
                    Text(if (state.coaching) "Vera is thinking…" else "Coach me")
                }
                state.guidance?.let { g -> SiftGuidanceView(g) }
            }
        }
    }
}

@Composable
private fun SiftGuidanceView(g: SiftGuidance) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(g.lead, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, lineHeight = 20.sp)
        g.steps.forEachIndexed { i, step ->
            Row(Modifier.padding(top = 12.dp)) {
                Text("${i + 1}", color = Violet, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text(step.label.uppercase(), color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(step.question, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp, lineHeight = 18.sp)
                }
            }
        }
        if (g.closing.isNotBlank()) {
            Text(g.closing, color = Amber, fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium)
        }
    }
}
