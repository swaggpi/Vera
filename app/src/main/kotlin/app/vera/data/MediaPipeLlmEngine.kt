package app.vera.data

import android.content.Context
import app.vera.core.llm.LlmEngine
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Real on-device Gemma engine via MediaPipe LLM Inference (runs on the device GPU/CPU, no network).
 * Created by [SwitchableLlmEngine] once the model file is downloaded. Model loading is heavy, so
 * [init] must be called off the main thread.
 */
class MediaPipeLlmEngine(
    private val context: Context,
    private val modelPath: String
) : LlmEngine {

    @Volatile private var inference: LlmInference? = null

    fun init() {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .build()
        inference = LlmInference.createFromOptions(context, options)
    }

    fun close() {
        runCatching { inference?.close() }
        inference = null
    }

    override suspend fun isReady(): Boolean = inference != null

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        withContext(Dispatchers.Default) {
            val engine = inference ?: return@withContext ""
            runCatching { engine.generateResponse(ModelCatalog.formatPrompt(system, prompt)) }
                .getOrDefault("")
        }

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> = flow {
        emit(generate(prompt, system, maxTokens))
    }
}
