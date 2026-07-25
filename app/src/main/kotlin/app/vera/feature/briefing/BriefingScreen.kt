package app.vera.feature.briefing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                item(key = "model-banner") { app.vera.feature.model.ModelBanner() }
                items(state.items, key = { it.item.article.id }) { ui ->
                    BriefingCard(
                        ui = ui,
                        onGetSources = { viewModel.requestResearch(ui.item.article); onOpenResearch() },
                        onReadIt = { viewModel.onEngaged(); openUrl(context, ui.item.article.url) },
                        onMoreDetails = { viewModel.moreDetails(ui.item.article) }
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
    onMoreDetails: suspend () -> String
) {
    val item = ui.item
    var detail by remember(item.article.id) { mutableStateOf<String?>(null) }
    var loadingDetail by remember(item.article.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    buildString {
                        append(ui.outlet)
                        if (ui.country.isNotBlank()) append(" · ${ui.country}")
                    },
                    color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                if (ui.leaning != Leaning.UNKNOWN) {
                    Text(ui.leaning.label, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp))
                            .background(Amber.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
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

            // Actions replace the old quiz.
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
                    if (detail == null && !loadingDetail) {
                        loadingDetail = true
                        scope.launch { detail = onMoreDetails(); loadingDetail = false }
                    } else if (detail != null) {
                        detail = null
                    }
                }) {
                    Text(if (detail != null) "Hide details" else "More details",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            if (loadingDetail) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("Vera is reading it…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                }
            }
            detail?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.5.sp,
                    lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
