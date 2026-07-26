package app.vera.data

import android.app.ActivityManager
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
    val message: String? = null,
    /** "GPU" or "CPU" once a model is loaded. Part of the state so the banner recomposes with it. */
    val backend: String? = null
)

/**
 * The on-device model, downloaded on demand. The point of a **one-tap install**: no adb, no manual
 * file pushing — the app fetches the weights, stores them in its private files dir, and hands them to
 * [SwitchableLlmEngine]. Each [ModelCatalog.ModelOption] must be a direct download of a `.litertlm`
 * model that [LiteRtLlmEngine] can load; the bundled options are ungated, so no token is needed.
 */
class ModelManager(
    private val context: Context,
    private val engine: SwitchableLlmEngine
) {
    private val modelsDir = File(context.filesDir, "models")

    /** Total device RAM — a model this phone would be killed loading is not worth offering. */
    private val deviceRamMb: Int = run {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        (info.totalMem / (1024L * 1024L)).toInt()
    }

    /** The models worth offering on this phone. */
    val options: List<ModelCatalog.ModelOption> get() = ModelCatalog.optionsFor(deviceRamMb)

    /** The installed model, whichever option it is — null when none has been downloaded yet. */
    fun installedFile(): File? = ModelCatalog.OPTIONS
        .map { File(modelsDir, it.fileName) }
        .firstOrNull { it.exists() && it.length() > MIN_VALID_BYTES }

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

    fun isInstalled(): Boolean = installedFile() != null

    /** If a model is already present (e.g. app relaunch), load it into the engine. */
    suspend fun loadIfPresent() {
        val file = installedFile() ?: return
        if (engine.usingRealModel) return
        runCatching { engine.loadModel(file.absolutePath) }
            .onSuccess { _status.value = ModelStatus(ModelPhase.READY, backend = engine.backendName) }
            .onFailure { _status.value = ModelStatus(ModelPhase.ERROR, message = it.message) }
    }

    suspend fun download(option: ModelCatalog.ModelOption = ModelCatalog.DEFAULT) = withContext(Dispatchers.IO) {
        val modelFile = File(modelsDir, option.fileName)
        if (modelFile.exists() && modelFile.length() > MIN_VALID_BYTES) {
            loadIfPresent(); return@withContext
        }
        try {
            _status.value = ModelStatus(ModelPhase.DOWNLOADING, 0f, "Starting…")
            modelsDir.mkdirs()
            val part = File(modelsDir, option.fileName + ".part")

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
            // Models are gigabytes each: never leave a previous one (or its compile cache) behind.
            evictAllExcept(modelFile)

            _status.value = ModelStatus(ModelPhase.DOWNLOADING, 1f, "Loading model into memory…")
            engine.loadModel(modelFile.absolutePath)
            _status.value = ModelStatus(ModelPhase.READY, backend = engine.backendName)
        } catch (e: Exception) {
            _status.value = ModelStatus(ModelPhase.ERROR, message = e.message ?: "Download error")
        }
    }

    /** Frees the disk taken by any other model file, plus the old engine's compile caches. */
    private fun evictAllExcept(keep: File) {
        modelsDir.listFiles()?.forEach { f ->
            if (f.absolutePath != keep.absolutePath) runCatching { f.delete() }
        }
        runCatching { File(context.cacheDir, "litertlm").deleteRecursively() }
    }

    fun delete() {
        modelsDir.listFiles()?.forEach { f -> runCatching { f.delete() } }
        runCatching { File(context.cacheDir, "litertlm").deleteRecursively() }
        _status.value = ModelStatus(ModelPhase.ABSENT)
    }

    companion object {
        private const val MIN_VALID_BYTES = 50_000_000L  // guard against HTML error pages
    }
}

/**
 * The models the one-tap button can download — **Gemma 4** in Google's `.litertlm` format, run by
 * [LiteRtLlmEngine]. Both are **ungated** on Hugging Face, so they still install with no token and
 * no license step, which is what makes one-tap possible.
 *
 * Sizes are the memory peak, not the file: E2B needs ~1.7 GB on CPU but ~0.7 GB on GPU, E4B ~3.3 GB
 * on CPU — which is why E4B is offered only to phones with the RAM to survive a CPU fallback.
 */
object ModelCatalog {
    const val TOKEN = ""   // set a HF read token only if you point an option at a gated model

    data class ModelOption(
        val id: String,
        val title: String,
        val note: String,
        val url: String,
        val fileName: String,
        val approxMb: Int,
        /** Total device RAM below which this option is hidden — it would be killed on load. */
        val minDeviceRamMb: Int
    )

    val OPTIONS = listOf(
        ModelOption(
            id = "gemma4e2b", title = "Recommended", note = "Gemma 4 E2B · ~2.6 GB · 140+ languages",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "vera-gemma4-e2b.litertlm",
            approxMb = 2588,
            minDeviceRamMb = 6000
        ),
        ModelOption(
            id = "gemma4e4b", title = "Best quality", note = "Gemma 4 E4B · ~3.7 GB · needs 12 GB RAM",
            url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            fileName = "vera-gemma4-e4b.litertlm",
            approxMb = 3660,
            minDeviceRamMb = 12000
        )
    )
    val DEFAULT = OPTIONS.first()

    /** The options worth showing on this phone — a model it cannot load is not a choice. */
    fun optionsFor(deviceRamMb: Int): List<ModelOption> =
        OPTIONS.filter { deviceRamMb >= it.minDeviceRamMb }.ifEmpty { listOf(DEFAULT) }

    fun byId(id: String?): ModelOption? = OPTIONS.firstOrNull { it.id == id }
}
