package app.vera.core.briefing

import app.vera.core.model.BriefingSlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A briefing rendered ahead of time and stored, so opening the app is instant.
 *
 * On-device generation takes a couple of minutes for a full briefing — far too slow to do while the
 * user waits. The background worker produces this, and the UI just reads it.
 */
@Serializable
data class CachedCard(
    val articleId: String,
    val title: String,
    /** Headline in the reader's language; falls back to the original. */
    val displayTitle: String = "",
    val url: String,
    val summary: String,
    val manipulationWatch: String,
    val outlet: String,
    val country: String,
    val ownership: String,
    val leaning: String,
    val alsoReportedBy: List<String> = emptyList(),
    val matchedInterests: List<String> = emptyList(),
    val body: String = ""
)

@Serializable
data class CachedBriefing(
    val slot: String,
    val languageCode: String = "en",
    val generatedAtEpochMs: Long,
    val cards: List<CachedCard>
) {
    fun isFresh(nowEpochMs: Long, maxAgeMs: Long = FRESH_FOR_MS): Boolean =
        cards.isNotEmpty() && nowEpochMs - generatedAtEpochMs < maxAgeMs

    companion object {
        /** A briefing is worth showing for half a day — the gap between morning and evening. */
        const val FRESH_FOR_MS = 12 * 60 * 60 * 1000L

        private val json = Json { ignoreUnknownKeys = true }
        fun encode(b: CachedBriefing): String = json.encodeToString(serializer(), b)
        fun decode(raw: String): CachedBriefing? = runCatching { json.decodeFromString<CachedBriefing>(raw) }.getOrNull()
    }
}

/** Which briefing an hour of the day belongs to. */
fun slotForHour(hour: Int): BriefingSlot =
    if (hour < 14) BriefingSlot.MORNING else BriefingSlot.EVENING
