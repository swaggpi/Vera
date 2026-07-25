package app.vera.data

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import app.vera.core.speech.SpeechService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Real on-device voice I/O. TTS reads briefings aloud; SpeechRecognizer captures "say what you
 * heard". Verified on device (Tier-2) — SpeechRecognizer must be created/started on the main thread.
 */
class AndroidSpeechService(private val context: Context) : SpeechService {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override suspend fun speak(text: String) = withContext(Dispatchers.Main) {
        val engine = tts ?: TextToSpeech(context) { }.also {
            it.language = Locale.getDefault()
            tts = it
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vera-tts")
        Unit
    }

    override fun stopSpeaking() {
        tts?.stop()
    }

    override suspend fun listenOnce(): String {
        val result = CompletableDeferred<String>()
        main.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                result.complete(""); return@post
            }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (!result.isCompleted) result.complete(text)
                    recognizer.destroy()
                }
                override fun onError(error: Int) {
                    if (!result.isCompleted) result.complete("")
                    recognizer.destroy()
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
        }
        return result.await()
    }
}
