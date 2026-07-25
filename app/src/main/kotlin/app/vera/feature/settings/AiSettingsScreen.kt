package app.vera.feature.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vera.core.llm.CloudCatalog
import app.vera.core.llm.CloudConfig
import app.vera.core.llm.CloudProvider
import app.vera.data.SecureKeyStore
import app.vera.ui.theme.Amber
import app.vera.ui.theme.Rose
import app.vera.ui.theme.Teal
import app.vera.ui.theme.Violet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val keys: SecureKeyStore
) : ViewModel() {

    private val _config = MutableStateFlow(keys.config())
    val config: StateFlow<CloudConfig> = _config.asStateFlow()

    /** Masked for display — the real key is never shown back to the user. */
    fun maskedKey(p: CloudProvider): String {
        val k = keys.apiKey(p)
        return if (k.length < 12) "" else "${k.take(8)}…${k.takeLast(4)}"
    }

    fun setEnabled(v: Boolean) { keys.enabled = v; refresh() }
    fun setProvider(p: CloudProvider) {
        keys.provider = p
        keys.modelId = CloudCatalog.default(p).id
        refresh()
    }
    fun setModel(id: String) { keys.modelId = id; refresh() }
    fun setKey(p: CloudProvider, value: String) { keys.setApiKey(p, value); refresh() }
    fun clearKey(p: CloudProvider) { keys.setApiKey(p, ""); refresh() }

    private fun refresh() { _config.value = keys.config() }
}

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiSettingsViewModel = hiltViewModel()
) {
    val cfg by viewModel.config.collectAsStateWithLifecycle()
    var keyInput by remember(cfg.provider) { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("AI engine", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }

        // ---- Local (default) ----
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("On-device (default)", color = Teal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Vera runs a small model on your phone. Nothing you read or ask is uploaded, it " +
                    "works offline, and it costs nothing. This is the recommended setup.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp,
                    lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // ---- Cloud opt-in ----
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use a cloud model instead", color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Faster and higher quality — but your data leaves the device.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    Switch(checked = cfg.enabled, onCheckedChange = viewModel::setEnabled,
                        colors = SwitchDefaults.colors(checkedThumbColor = Amber))
                }

                if (cfg.enabled) {
                    // Unmissable disclosure — this is the trade-off being made.
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(10.dp))
                        .background(Rose.copy(alpha = 0.14f)).padding(12.dp)) {
                        Text("⚠  With this on, the articles you read and the questions you ask are sent to " +
                            "${cfg.provider.label}. Vera is no longer private or offline. You are billed by " +
                            "them for your own usage.",
                            color = Rose, fontSize = 12.sp, lineHeight = 17.sp)
                    }

                    Text("PROVIDER", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    Row {
                        CloudProvider.entries.forEach { p ->
                            val sel = cfg.provider == p
                            Text(p.label, color = if (sel) Color(0xFF201400) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Amber else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.setProvider(p) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }

                    Text("MODEL", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    CloudCatalog.forProvider(cfg.provider).forEach { m ->
                        val sel = cfg.modelId == m.id
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (sel) Violet.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { viewModel.setModel(m.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            if (sel) Text("✓", color = Violet, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("API KEY", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text("A subscription (ChatGPT Plus / Claude Pro) does not work here — those plans " +
                        "have no API access. Create a pay-per-use key at ${cfg.provider.keyUrl}. " +
                        "It is stored encrypted on this device only.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp, lineHeight = 16.sp)

                    if (cfg.hasKey) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Key saved: ${viewModel.maskedKey(cfg.provider)}", color = Teal,
                                fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearKey(cfg.provider) }) {
                                Text("Remove", color = Rose, fontSize = 12.5.sp)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("${cfg.provider.keyPrefix}…", fontSize = 13.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Button(
                            onClick = { viewModel.setKey(cfg.provider, keyInput); keyInput = "" },
                            enabled = keyInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF201400)),
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Save key", fontWeight = FontWeight.Bold) }
                    }

                    Text(
                        if (cfg.usable) "✓ Cloud model active — ${CloudCatalog.byId(cfg.modelId)?.label ?: cfg.modelId}"
                        else "Add a key to activate. Until then Vera keeps using the on-device model.",
                        color = if (cfg.usable) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
            }
        }
    }
}
