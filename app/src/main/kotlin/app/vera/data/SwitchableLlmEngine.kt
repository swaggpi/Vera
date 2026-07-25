package app.vera.data

import android.content.Context
import app.vera.core.llm.CloudProvider
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The app's [LlmEngine]. Resolution order, highest first:
 *
 *  1. **Cloud** — only when the user has explicitly opted in *and* supplied their own API key.
 *  2. **On-device** — the downloaded local model (the default, private path).
 *  3. **Fake** — deterministic placeholder text, so the app is usable before either is set up.
 *
 * Local stays the default: cloud is a deliberate, reversible choice the user makes in Settings.
 */
class SwitchableLlmEngine(
    private val context: Context,
    private val keys: SecureKeyStore,
    private val httpClient: OkHttpClient
) : LlmEngine {

    private val fake = FakeLlmEngine()
    @Volatile private var real: MediaPipeLlmEngine? = null

    val usingRealModel: Boolean get() = real != null

    /** Rebuilt on demand so Settings changes take effect immediately. */
    private fun cloud(): LlmEngine? {
        val cfg = keys.config()
        if (!cfg.usable) return null
        val key = keys.apiKey(cfg.provider)
        return when (cfg.provider) {
            CloudProvider.ANTHROPIC -> AnthropicLlmEngine(httpClient, key, cfg.modelId)
            CloudProvider.OPENAI -> OpenAiLlmEngine(httpClient, key, cfg.modelId)
        }
    }

    /** Load the downloaded model into a real engine (heavy — call off the main thread). */
    suspend fun loadModel(path: String) = withContext(Dispatchers.Default) {
        real?.close()
        real = MediaPipeLlmEngine(context, path).also { it.init() }
    }

    private fun active(): LlmEngine = cloud() ?: real ?: fake

    /** True when a *real* engine (cloud or on-device) is available — not the placeholder. */
    override suspend fun isReady(): Boolean = cloud() != null || (real?.isReady() ?: false)

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String {
        val chosen = active()
        val out = chosen.generate(prompt, system, maxTokens)
        // A failed cloud call returns "" — fall back to whatever runs locally rather than showing nothing.
        if (out.isBlank() && chosen !== fake) {
            return (real ?: fake).generate(prompt, system, maxTokens)
        }
        return out
    }

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> =
        active().stream(prompt, system, maxTokens)
}
