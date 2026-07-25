package app.vera.core.briefing

import app.vera.core.llm.LlmEngine
import app.vera.core.model.Article
import app.vera.core.model.BriefingItem
import app.vera.core.model.QuizQuestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns a raw [Article] into a gamified [BriefingItem] using the on-device model: a plain-language
 * summary, a "why it matters" line, a manipulation-watch note, and interactive quiz questions.
 *
 * The model is asked for structured JSON; if it doesn't comply (small on-device models sometimes
 * don't), we fall back to a deterministic heuristic so the feature never breaks in front of a user.
 */
class BriefingGenerator(
    private val llm: LlmEngine,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun generate(article: Article): BriefingItem {
        val raw = llm.generate(buildPrompt(article), system = SYSTEM, maxTokens = 512)
        return parse(raw, article) ?: fallback(article)
    }

    private fun buildPrompt(article: Article): String = """
        Rewrite this news item for a young reader and return ONLY JSON with keys
        "summary", "whyItMatters", "manipulationWatch", "quiz"
        (quiz = array of {"question","options"(4),"correctIndex","explanation"}).

        TITLE: ${article.title}
        BODY: ${article.body.take(1200)}
    """.trimIndent()

    internal fun parse(raw: String, article: Article): BriefingItem? {
        val obj = extractJsonObject(raw) ?: return null
        return try {
            val dto = json.decodeFromString<BriefingDto>(obj)
            val quiz = dto.quiz.orEmpty()
                .filter { it.options.size >= 2 && it.correctIndex in it.options.indices }
                .map { QuizQuestion(it.question, it.options, it.correctIndex, it.explanation.orEmpty()) }
            if (dto.summary.isNullOrBlank()) return null
            BriefingItem(
                article = article,
                plainSummary = dto.summary.trim(),
                whyItMatters = dto.whyItMatters?.trim().orEmpty(),
                manipulationWatch = dto.manipulationWatch?.trim().orEmpty(),
                quiz = quiz.ifEmpty { listOf(defaultQuestion(article)) }
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Deterministic, model-free briefing so the feature degrades gracefully. */
    internal fun fallback(article: Article): BriefingItem {
        val sentences = article.body.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val summary = sentences.take(2).joinToString(" ").ifBlank { article.title }
        return BriefingItem(
            article = article,
            plainSummary = summary,
            whyItMatters = "Stories like this spread fast — pause before you share.",
            manipulationWatch = "Check the source and the date, and look for independent coverage.",
            quiz = listOf(defaultQuestion(article))
        )
    }

    private fun defaultQuestion(article: Article) = QuizQuestion(
        question = "What's the best first step before trusting this story?",
        options = listOf(
            "Investigate who published it",
            "Share it immediately",
            "Assume the headline is accurate",
            "Judge it by the number of likes"
        ),
        correctIndex = 0,
        explanation = "Investigating the source is the 'I' in SIFT — start there, not with the headline."
    )

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    @Serializable
    private data class BriefingDto(
        val summary: String? = null,
        val whyItMatters: String? = null,
        val manipulationWatch: String? = null,
        val quiz: List<QuizDto>? = null
    )

    @Serializable
    private data class QuizDto(
        val question: String,
        val options: List<String> = emptyList(),
        val correctIndex: Int = 0,
        val explanation: String? = null
    )

    companion object {
        const val SYSTEM =
            "You are Vera, a media-literacy coach. Be accurate, neutral, and concise. Never invent facts " +
                "beyond the provided text. Output only valid JSON."
    }
}
