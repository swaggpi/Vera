package app.vera.core.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * On-device language model abstraction. The real implementation (`MediaPipeLlmEngine`, in :app)
 * wraps MediaPipe LLM Inference running Gemma on the device's GPU. Everything device-bound lives
 * behind this interface so the whole app's logic is unit-testable on the JVM with [FakeLlmEngine].
 */
interface LlmEngine {
    suspend fun isReady(): Boolean
    suspend fun generate(prompt: String, system: String? = null, maxTokens: Int = 512): String
    fun stream(prompt: String, system: String? = null, maxTokens: Int = 512): Flow<String>
}

/**
 * Deterministic in-memory engine for tests and for running the app before the Gemma model is
 * side-loaded. [responder] maps a prompt to a canned reply; the default returns a valid briefing
 * JSON so the happy path is exercised end-to-end without a model.
 */
class FakeLlmEngine(
    private val ready: Boolean = true,
    private val responder: (prompt: String) -> String = { DEFAULT_BRIEFING_JSON }
) : LlmEngine {

    override suspend fun isReady(): Boolean = ready

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        responder(prompt)

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> = flow {
        for (chunk in responder(prompt).chunked(24)) emit(chunk)
    }

    companion object {
        val DEFAULT_BRIEFING_JSON = """
            {
              "summary": "In plain terms: a policy change was announced today; here is what it does and who it affects.",
              "whyItMatters": "It touches everyday costs, so it is likely to be shared with strong framing.",
              "manipulationWatch": "Watch for missing context and emotionally loaded headlines when this spreads.",
              "quiz": [
                {
                  "question": "Before sharing this story, the strongest first move is to…",
                  "options": ["Check who published it", "Share it quickly", "Trust the headline", "Ignore the date"],
                  "correctIndex": 0,
                  "explanation": "Investigating the source is the 'I' in SIFT — always start there."
                }
              ]
            }
        """.trimIndent()
    }
}
