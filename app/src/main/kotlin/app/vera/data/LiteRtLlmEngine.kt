package app.vera.data

import android.content.Context
import android.util.Log
import app.vera.core.llm.LlmEngine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device engine via **LiteRT-LM** — Google's supported runtime for `.litertlm` models
 * (the MediaPipe LLM Inference API it replaces is maintenance-only).
 *
 * The backend matters more than the model size on a phone: Gemma 4 E2B peaks around 1.7 GB of RAM
 * on CPU but only ~0.7 GB on GPU, so we try the GPU first and fall back to CPU if the device's
 * driver can't take it. Loading is heavy — [init] must be called off the main thread.
 */
class LiteRtLlmEngine(
    private val context: Context,
    private val modelPath: String
) : LlmEngine {

    @Volatile private var engine: Engine? = null
    /** Which backend actually loaded — surfaced in Settings so the user can see what they're on. */
    @Volatile var backendName: String? = null
        private set

    // One native engine, so serialize calls: concurrent features must not interleave decodes.
    private val lock = Mutex()

    fun init() {
        val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
        // GPU first (far smaller memory peak), CPU as the fallback that works everywhere.
        val attempts = listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
        for ((name, backend) in attempts) {
            val candidate = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = MAX_CONTEXT_TOKENS,
                    cacheDir = cacheDir.absolutePath
                )
            )
            val loaded = runCatching { candidate.initialize().also { probe(candidate) } }
            if (loaded.isSuccess) {
                engine = candidate
                backendName = name
                Log.i(TAG, "LiteRT-LM engine initialized on $name")
                return
            }
            runCatching { candidate.close() }
            Log.w(TAG, "LiteRT-LM $name backend unusable: ${loaded.exceptionOrNull()?.message}")
        }
        throw IllegalStateException("LiteRT-LM could not load the model on either GPU or CPU")
    }

    /**
     * Generate one short answer before accepting a backend. A backend can initialize happily and
     * then fail on every single inference — the GPU backend does exactly that when the device's
     * OpenCL driver isn't reachable — and without this the app would quietly serve placeholder
     * text while claiming on-device AI was active.
     */
    private fun probe(candidate: Engine) {
        candidate.createConversation(ConversationConfig()).use { it.sendMessage("Hi") }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        backendName = null
    }

    override suspend fun isReady(): Boolean = engine != null

    override suspend fun generate(prompt: String, system: String?, maxTokens: Int): String =
        withContext(Dispatchers.Default) {
            val active = engine ?: return@withContext ""
            lock.withLock {
                runCatching {
                    // A fresh conversation per call: these are one-shot prompts, and history from an
                    // unrelated feature leaking into the next answer would be worse than the setup cost.
                    val config = if (system.isNullOrBlank()) ConversationConfig()
                    else ConversationConfig(systemInstruction = Contents.of(system))
                    active.createConversation(config).use { conversation ->
                        conversation.sendMessage(prompt).text()
                    }
                }.getOrElse {
                    Log.w(TAG, "generate failed: ${it.message}")
                    ""
                }
            }
        }

    override fun stream(prompt: String, system: String?, maxTokens: Int): Flow<String> = flow {
        emit(generate(prompt, system, maxTokens))
    }

    /** LiteRT-LM answers with structured content; the features here all want plain text. */
    private fun Message.text(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()

    private companion object {
        const val TAG = "VeraLlm"
        /** Enough for a story body plus the answer; larger contexts cost memory we don't have. */
        const val MAX_CONTEXT_TOKENS = 2048
    }
}
