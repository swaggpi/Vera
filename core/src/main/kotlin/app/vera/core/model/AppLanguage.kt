package app.vera.core.model

/**
 * The language Vera writes in — summaries, key points, coaching and chat answers.
 *
 * Feeds are published in their own language (Tagesschau in German, France 24 in French…), so
 * without this a briefing is a patchwork. Choosing a language makes the on-device model render
 * every story in it, whatever the source language.
 */
enum class AppLanguage(val code: String, val label: String, val endonym: String) {
    /** Follow the phone's locale. */
    DEVICE("auto", "Device language", "Automatic"),
    ENGLISH("en", "English", "English"),
    GERMAN("de", "German", "Deutsch"),
    FRENCH("fr", "French", "Français"),
    SPANISH("es", "Spanish", "Español"),
    PORTUGUESE("pt", "Portuguese", "Português"),
    ITALIAN("it", "Italian", "Italiano"),
    DUTCH("nl", "Dutch", "Nederlands"),
    POLISH("pl", "Polish", "Polski"),
    TURKISH("tr", "Turkish", "Türkçe"),
    ARABIC("ar", "Arabic", "العربية"),
    HINDI("hi", "Hindi", "हिन्दी"),
    CHINESE("zh", "Chinese", "中文"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    RUSSIAN("ru", "Russian", "Русский"),
    UKRAINIAN("uk", "Ukrainian", "Українська"),
    SWAHILI("sw", "Swahili", "Kiswahili");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: DEVICE

        /** Resolve DEVICE against the phone's locale, falling back to English. */
        fun resolve(pref: AppLanguage, deviceLanguageCode: String): AppLanguage =
            if (pref != DEVICE) pref
            else entries.firstOrNull { it.code == deviceLanguageCode.lowercase() } ?: ENGLISH
    }
}

/** Instruction appended to model prompts so output comes back in the chosen language. */
fun AppLanguage.writeInInstruction(): String = when (this) {
    AppLanguage.DEVICE, AppLanguage.ENGLISH -> "Write your answer in English."
    else -> "Write your answer in $label ($endonym), translating if the source text is in another language."
}
