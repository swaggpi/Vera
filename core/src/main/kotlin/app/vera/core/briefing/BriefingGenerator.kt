package app.vera.core.briefing

import app.vera.core.llm.LlmEngine
import app.vera.core.model.Article
import app.vera.core.model.BriefingItem
import app.vera.core.model.QuizQuestion
import kotlin.math.abs

/**
 * Turns a raw [Article] into a gamified [BriefingItem]. The on-device model writes a plain-language
 * summary (small models handle free text far more reliably than strict JSON); the quiz is drawn from
 * a rotating set of SIFT-habit questions. If the model isn't ready or misbehaves, we fall back to a
 * clean excerpt of the article — so a card is always useful.
 */
class BriefingGenerator(private val llm: LlmEngine) {

    suspend fun generate(article: Article): BriefingItem = BriefingItem(
        article = article,
        plainSummary = summarize(article),
        whyItMatters = "Stories like this spread fast — pause before you share.",
        manipulationWatch = "Check the source and the date, and look for independent coverage.",
        quiz = listOf(question(article))
    )

    internal suspend fun summarize(article: Article): String {
        val raw = runCatching {
            llm.generate(
                prompt = "Summarize this news story in two short, plain sentences for a young reader. " +
                    "Reply with the summary only.\nTITLE: ${article.title}\nTEXT: ${article.body.take(900)}",
                system = "You summarize news neutrally and accurately. No preamble, no markdown, no quotes.",
                maxTokens = 200
            )
        }.getOrNull()?.trim()?.removeSurrounding("\"")?.trim()

        return if (raw != null && raw.length in 20..800 && !raw.startsWith("{")) raw
        else excerpt(article)
    }

    /** The must-know points of a story (the "More details" action), as short bullets. */
    suspend fun keyPoints(article: Article): List<String> {
        val raw = runCatching {
            llm.generate(
                prompt = "List the 3 to 5 most important points a reader must know about this story. " +
                    "One short sentence each, most important first. Use only the information given.\n" +
                    "TITLE: ${article.title}\nTEXT: ${article.body.take(1400)}",
                system = "You extract key facts neutrally. Reply as a plain bulleted list, one point per line. No preamble.",
                maxTokens = 300
            )
        }.getOrNull()

        if (raw == null || raw.trim().startsWith("{")) return fallbackPoints(article)

        val points = raw.lines()
            .map { cleanBullet(it) }
            .filter { it.length in 4..300 && !it.contains("\":") && !it.contains("{") && !it.contains("}") }

        return if (points.size >= 2) points.take(6) else fallbackPoints(article)
    }

    /** Answer a follow-up question, grounded strictly in the article (the chat under "More details"). */
    suspend fun answer(article: Article, question: String, historyText: String): String {
        val raw = runCatching {
            llm.generate(
                prompt = buildString {
                    append("ARTICLE TITLE: ${article.title}\nARTICLE: ${article.body.take(1500)}\n\n")
                    if (historyText.isNotBlank()) append(historyText).append("\n")
                    append("QUESTION: $question")
                },
                system = "You are Vera. Answer using ONLY the article above. If the answer isn't in it, say you " +
                    "can't tell from this article and suggest checking other sources. Be concise, neutral, plain text.",
                maxTokens = 300
            )
        }.getOrNull()?.trim()?.removeSurrounding("\"")?.trim()

        return if (raw != null && raw.length in 2..2000 && !raw.startsWith("{")) raw
        else "I can't answer that from this article alone — try “Get more sources” to research it further."
    }

    private fun cleanBullet(line: String): String =
        line.trim()
            .removePrefix("-").removePrefix("•").removePrefix("*").removePrefix("·")
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .trim().trim('"').trim()

    private fun fallbackPoints(article: Article): List<String> =
        article.body.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.take(4)
            .ifEmpty { listOf(article.title) }

    private fun excerpt(article: Article): String {
        val sentences = article.body.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return sentences.take(2).joinToString(" ").ifBlank { article.title }
    }

    private fun question(article: Article): QuizQuestion =
        QUESTIONS[abs(article.id.hashCode()) % QUESTIONS.size]

    companion object {
        private val QUESTIONS = listOf(
            QuizQuestion(
                "What's the best first step before trusting this story?",
                listOf("Investigate who published it", "Share it immediately",
                    "Assume the headline is accurate", "Judge it by the number of likes"),
                0, "Investigating the source is the 'I' in SIFT — start there, not with the headline."
            ),
            QuizQuestion(
                "If this headline makes you feel strong emotion, that's a sign to…",
                listOf("Slow down and check it", "Share it faster", "Trust it more", "Stop reading news"),
                0, "Strong emotion is exactly what viral misinformation targets — pause and verify."
            ),
            QuizQuestion(
                "The most reliable way to confirm this is to…",
                listOf("Find independent, reputable coverage", "See if it has many shares",
                    "Check if a friend posted it", "Read only this one source"),
                0, "Lateral reading — checking other independent sources — is how fact-checkers verify."
            )
        )
    }
}
