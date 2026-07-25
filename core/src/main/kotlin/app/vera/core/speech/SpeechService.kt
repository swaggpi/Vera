package app.vera.core.speech

/**
 * On-device voice I/O — text-to-speech for audio briefings and speech-to-text for "say what you
 * heard". Real impl (Android TextToSpeech + SpeechRecognizer) lives in :app; [FakeSpeechService]
 * keeps ViewModels testable on the JVM.
 */
interface SpeechService {
    fun isAvailable(): Boolean
    suspend fun speak(text: String)
    fun stopSpeaking()
    /** Listen once and return the transcript, or "" if nothing was recognised. */
    suspend fun listenOnce(): String
}

class FakeSpeechService(
    private val available: Boolean = true,
    private val transcript: String = ""
) : SpeechService {
    var spoken: String? = null
        private set

    override fun isAvailable(): Boolean = available
    override suspend fun speak(text: String) { spoken = text }
    override fun stopSpeaking() {}
    override suspend fun listenOnce(): String = transcript
}
