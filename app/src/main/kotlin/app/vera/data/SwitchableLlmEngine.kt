package app.vera.data

import android.content.Context
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The app's [LlmEngine]. Delegates to the deterministic [FakeLlmEngine] until the Gemma model is
 * downloaded, then swaps to the real on-device [MediaPipeLlmEngine]. [isReady] reflects whether the
 * *real* model is active, so features (research/briefing) only lean on real generation once it's loaded.
 */
class SwitchableLlmEngine(private val context: Context) : LlmEngine {

    private val fake = FakeLlmEngine()
    @Volatile private var real: MediaPipeLlmEngine? = null

    val usingRealModel: Boolean get() = real != null

    /** Load the downloaded model into a real engine (heavy — call off the main thread). */
    suspend fun loadModel(path: String) = withContext(Dispatchers.Default) {
        real?.close()
        real = MediaPipeLlmEngine(context, path).also { it.init() }
    }

    private fun active(): LlmEngine = real ?: fake

    override suspend fun isReady(): Boolean = real?.isReady() ?: false

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        active().generate(prompt, system, maxTokens)

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> =
        active().stream(prompt, system, maxTokens)
}
