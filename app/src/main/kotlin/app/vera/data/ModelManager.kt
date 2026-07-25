package app.vera.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ModelPhase { ABSENT, DOWNLOADING, READY, ERROR }

data class ModelStatus(
    val phase: ModelPhase,
    val progress: Float = 0f,
    val message: String? = null
)

/**
 * The on-device model, downloaded on demand. The point of a **one-tap install**: no adb, no manual
 * file pushing — the app fetches the weights, stores them in its private files dir, and hands them to
 * [SwitchableLlmEngine]. [ModelCatalog.URL] must be a direct download of a MediaPipe-compatible
 * `.task` model (Gemma is license-gated; set a token or point at your own mirror if the default 401s).
 */
class ModelManager(
    private val context: Context,
    private val engine: SwitchableLlmEngine
) {
    val modelFile = File(File(context.filesDir, "models"), ModelCatalog.FILE_NAME)

    private val _status = MutableStateFlow(
        ModelStatus(if (isInstalled()) ModelPhase.READY else ModelPhase.ABSENT)
    )
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    // Dedicated client with no call timeout — model files are large.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() > MIN_VALID_BYTES

    /** If a model is already present (e.g. app relaunch), load it into the engine. */
    suspend fun loadIfPresent() {
        if (isInstalled() && !engine.usingRealModel) {
            runCatching { engine.loadModel(modelFile.absolutePath) }
                .onSuccess { _status.value = ModelStatus(ModelPhase.READY) }
        }
    }

    suspend fun download(option: ModelCatalog.ModelOption = ModelCatalog.DEFAULT) = withContext(Dispatchers.IO) {
        if (isInstalled()) {
            loadIfPresent(); return@withContext
        }
        try {
            _status.value = ModelStatus(ModelPhase.DOWNLOADING, 0f, "Starting…")
            modelFile.parentFile?.mkdirs()
            val part = File(modelFile.parentFile, ModelCatalog.FILE_NAME + ".part")

            val builder = Request.Builder().url(option.url)
            if (ModelCatalog.TOKEN.isNotBlank()) builder.header("Authorization", "Bearer ${ModelCatalog.TOKEN}")

            downloadClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    _status.value = ModelStatus(
                        ModelPhase.ERROR, message =
                        "Download failed (HTTP ${resp.code}). The model may require accepting its license or a token."
                    )
                    return@withContext
                }
                val body = resp.body ?: run {
                    _status.value = ModelStatus(ModelPhase.ERROR, message = "Empty response"); return@withContext
                }
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buf = ByteArray(1 shl 16)
                        var readTotal = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            readTotal += n
                            val pct = if (total > 0) readTotal.toFloat() / total else 0f
                            _status.value = ModelStatus(ModelPhase.DOWNLOADING, pct,
                                "${readTotal / 1_000_000} MB" + (if (total > 0) " / ${total / 1_000_000} MB" else ""))
                        }
                    }
                }
            }

            if (part.length() < MIN_VALID_BYTES) {
                part.delete()
                _status.value = ModelStatus(ModelPhase.ERROR, message = "Downloaded file too small — check the URL/license.")
                return@withContext
            }
            if (modelFile.exists()) modelFile.delete()
            part.renameTo(modelFile)

            _status.value = ModelStatus(ModelPhase.DOWNLOADING, 1f, "Loading model into memory…")
            engine.loadModel(modelFile.absolutePath)
            _status.value = ModelStatus(ModelPhase.READY)
        } catch (e: Exception) {
            _status.value = ModelStatus(ModelPhase.ERROR, message = e.message ?: "Download error")
        }
    }

    fun delete() {
        runCatching { modelFile.delete() }
        _status.value = ModelStatus(ModelPhase.ABSENT)
    }

    companion object {
        private const val MIN_VALID_BYTES = 50_000_000L  // guard against HTML error pages
    }
}

enum class PromptFormat { CHATML, GEMMA, RAW }

/**
 * The models the one-tap button can download. Both defaults are **Qwen2.5-Instruct** MediaPipe
 * `.task` files — Apache-2.0 and **ungated** on Hugging Face, so they install with no token and no
 * license step. The user chooses fast-and-light vs best-quality. Google's Gemma is also a drop-in
 * (add a [ModelOption] with a Gemma URL + a HF token in [TOKEN], and set PROMPT to GEMMA).
 */
object ModelCatalog {
    const val FILE_NAME = "vera-model.task"
    const val TOKEN = ""              // set a HF read token only for a gated model (e.g. Gemma)
    val PROMPT = PromptFormat.CHATML  // both bundled models are Qwen (ChatML)

    data class ModelOption(
        val id: String,
        val title: String,
        val note: String,
        val url: String,
        val approxMb: Int
    )

    val OPTIONS = listOf(
        ModelOption(
            id = "qwen15", title = "Best quality", note = "Qwen 1.5B · ~1.6 GB",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            approxMb = 1600
        ),
        ModelOption(
            id = "qwen05", title = "Fast & light", note = "Qwen 0.5B · ~550 MB",
            url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            approxMb = 550
        )
    )
    val DEFAULT = OPTIONS.first()

    fun formatPrompt(system: String?, prompt: String): String = when (PROMPT) {
        PromptFormat.CHATML -> buildString {
            if (!system.isNullOrBlank()) append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            append("<|im_start|>user\n").append(prompt).append("<|im_end|>\n<|im_start|>assistant\n")
        }
        PromptFormat.GEMMA -> {
            val content = if (system.isNullOrBlank()) prompt else "$system\n\n$prompt"
            "<start_of_turn>user\n$content<end_of_turn>\n<start_of_turn>model\n"
        }
        PromptFormat.RAW -> if (system.isNullOrBlank()) prompt else "$system\n\n$prompt"
    }
}
