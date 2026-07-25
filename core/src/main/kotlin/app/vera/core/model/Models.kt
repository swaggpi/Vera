package app.vera.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who ultimately controls an outlet — a teachable Media & Information Literacy signal. */
@Serializable
enum class Ownership {
    @SerialName("public") PUBLIC,     // public-service broadcaster (e.g. ARD/BBC/NPR)
    @SerialName("private") PRIVATE,   // privately/commercially owned
    @SerialName("state") STATE,       // state-controlled (treat with extra scrutiny)
    @SerialName("unknown") UNKNOWN    // outlet not in our directory — verify who runs it
}

/**
 * A selectable news source. Catalog entries live in `assets/sources_catalog.json`.
 * [pressFreedomTier]: 1 = good … 5 = very serious situation (RSF-style banding) — surfaced to the
 * user so they learn to weigh where a claim comes from.
 */
@Serializable
data class NewsSource(
    val id: String,
    val name: String,
    val country: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("rss_url") val rssUrl: String,
    val language: String,
    val ownership: Ownership,
    @SerialName("press_freedom_tier") val pressFreedomTier: Int = 3,
    @SerialName("default_on") val defaultOn: Boolean = false
)

/** A single fetched news story (before any AI processing). */
data class Article(
    val id: String,
    val sourceId: String,
    val title: String,
    val body: String,
    val url: String,
    val publishedAtEpochMs: Long = 0L,
    val imageUrl: String? = null
)

/** One question in a briefing quiz. [correctIndex] indexes [options]. */
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

/** AI-processed story: plain-language summary, context, a manipulation-watch note, and a quiz. */
data class BriefingItem(
    val article: Article,
    val plainSummary: String,
    val whyItMatters: String,
    val manipulationWatch: String,
    val quiz: List<QuizQuestion>
)

enum class BriefingSlot { MORNING, EVENING }

/** Gamification state, persisted per user. [lastCompletedEpochDay] is days since the Unix epoch. */
data class UserProgress(
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val xp: Int = 0,
    val lastCompletedEpochDay: Long = -1L
)
