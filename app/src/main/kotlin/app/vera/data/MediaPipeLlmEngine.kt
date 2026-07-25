package app.vera.data

import app.vera.core.llm.LlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * On-device Gemma engine (integration point for the next milestone).
 *
 * Wiring checklist (Tier-2, verified on the Pixel 7a / GrapheneOS):
 *  1. Add dependency:  implementation("com.google.mediapipe:tasks-genai:<latest>")
 *  2. Ship or download a Gemma model, e.g. `gemma2-2b-it-cpu-int4.task`, into app files dir
 *     (NOT bundled in git — see .gitignore; ~1–2 GB, license-gated).
 *  3. Replace the bodies below with MediaPipe's LlmInference:
 *
 *      val options = LlmInference.LlmInferenceOptions.builder()
 *          .setModelPath(modelFile.absolutePath)
 *          .setMaxTokens(1024)
 *          .setPreferredBackend(LlmInference.Backend.GPU)
 *          .build()
 *      val llm = LlmInference.createFromOptions(context, options)
 *      // generate: llm.generateResponse(fullPrompt)
 *      // stream:   llm.generateResponseAsync(fullPrompt) { partial, done -> ... }
 *
 * Until wired, DI binds [app.vera.core.llm.FakeLlmEngine] so the whole app runs and is testable.
 */
class MediaPipeLlmEngine(
    private val modelPath: String
) : LlmEngine {

    override suspend fun isReady(): Boolean = false // TODO: true once the model file loads

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        error("MediaPipeLlmEngine not wired yet — see class KDoc. Use FakeLlmEngine for now.")

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> = flow {
        error("MediaPipeLlmEngine not wired yet — see class KDoc.")
    }
}
