package app.vera.core.briefing

import app.vera.core.llm.LlmEngine
import app.vera.core.model.Article
import app.vera.core.research.OutletDirectory
import app.vera.core.research.SearchProvider

/** An answer, plus the outlets it drew on beyond the article itself. */
data class StoryAnswer(
    val text: String,
    val extraSources: List<String> = emptyList()
)

/**
 * Answers follow-up questions about a story.
 *
 * The article alone usually can't answer "why did this happen?" or "what happened next?", so when
 * the question reaches beyond the text, Vera searches the web and answers from what it finds —
 * always naming the outlets it used, so the reader can weigh them.
 */
class StoryChat(
    private val llm: LlmEngine,
    private val search: SearchProvider
) {

    suspend fun ask(article: Article, question: String, historyText: String): StoryAnswer {
        // 1. Try the article first — cheapest, most grounded, and works offline.
        val fromArticle = answerFromArticle(article, question, historyText)
        if (fromArticle != null) return StoryAnswer(fromArticle)

        // 2. Otherwise go and look it up.
        val results = runCatching { search.search(searchQuery(article, question)) }
            .getOrDefault(emptyList())
            .take(5)
        if (results.isEmpty()) {
            return StoryAnswer(
                "I couldn't find that in the article, and no sources came back just now. " +
                    "Try rephrasing it, or open “Get more sources” to research the story."
            )
        }

        val outlets = results.map { OutletDirectory.forUrl(it.url).name }.distinct()
        val context = results.joinToString("\n") { r ->
            "- ${OutletDirectory.forUrl(r.url).name}: ${r.title} — ${r.snippet.take(300)}"
        }
        val raw = runCatching {
            llm.generate(
                prompt = "QUESTION: $question\n\nARTICLE: ${article.title}\n${article.body.take(700)}\n\n" +
                    "WEB RESULTS:\n$context\n\nAnswer in 2-4 sentences using only the article and the web " +
                    "results above. Attribute each claim to the source it came from (\"the WHO page says…\"). " +
                    "These are short search snippets, so avoid absolute statements like \"there are none\" or " +
                    "\"nothing exists\" — say what the snippets show and note that they may be incomplete or " +
                    "out of date. If they don't settle the question, say so plainly.",
                system = "You are Vera, a media-literacy coach. Ground every claim in the material provided and " +
                    "attribute it. Never invent facts. Never over-generalise from a snippet. Plain text.",
                maxTokens = 320
            )
        }.getOrNull()?.trim()

        val text = if (!raw.isNullOrBlank() && raw.length in 10..2000 && !raw.startsWith("{")) raw
        else "Here's what I found on that: " +
            results.take(3).joinToString(" ") { "${OutletDirectory.forUrl(it.url).name} reports “${it.title}”." } +
            " Read a couple of them directly before you rely on it."

        return StoryAnswer(text, outlets)
    }

    /** Returns null when the model says (or shows) that the article doesn't cover it. */
    internal suspend fun answerFromArticle(article: Article, question: String, historyText: String): String? {
        val raw = runCatching {
            llm.generate(
                prompt = buildString {
                    append("ARTICLE TITLE: ${article.title}\nARTICLE: ${article.body.take(1500)}\n\n")
                    if (historyText.isNotBlank()) append(historyText).append("\n")
                    append("QUESTION: $question\n\n")
                    append("If the article answers this, reply with the answer. If it does not, reply with " )
                    append("exactly: NOT_IN_ARTICLE")
                },
                system = "You are Vera. Use only the article. Be concise and neutral. Plain text.",
                maxTokens = 300
            )
        }.getOrNull()?.trim()?.removeSurrounding("\"")?.trim() ?: return null

        if (raw.isBlank() || raw.startsWith("{")) return null
        if (raw.uppercase().contains("NOT_IN_ARTICLE")) return null
        // Small models often phrase the miss instead of using the sentinel. There are too many
        // wordings to list ("the article does not provide specific information about…"), so match
        // the shape of the sentence rather than a fixed set of phrases.
        val miss = listOf("cannot tell", "can't tell", "does not say", "doesn't say", "not mentioned",
            "no information", "not stated", "does not mention", "doesn't mention", "not specified",
            "not provided")
        if (miss.any { raw.lowercase().contains(it) }) return null
        if (ARTICLE_MISS.containsMatchIn(raw)) return null
        if (isVerbatimLift(raw, article)) return null
        return raw
    }

    /**
     * Small models often "answer" by copying sentences straight back out of the article instead of
     * using the NOT_IN_ARTICLE sentinel. That reads like an answer but isn't one — asked which
     * countries landed on the D-Day beaches, the model replayed the article's opening. Treat a
     * mostly-copied reply as a miss so the question goes to search, where it can be answered.
     */
    private fun isVerbatimLift(answer: String, article: Article): Boolean {
        val body = normalizeWords(article.title + " " + article.body).joinToString(" ")
        val words = normalizeWords(answer)
        if (words.size < MIN_LIFT_WORDS) return false
        var longest = 0
        for (start in words.indices) {
            var len = longest                      // only look for runs longer than the best so far
            while (start + len < words.size &&
                body.contains(words.subList(start, start + len + 1).joinToString(" "))
            ) len++
            if (len > longest) longest = len
        }
        return longest >= MIN_LIFT_WORDS && longest >= words.size * LIFT_SHARE
    }

    private fun normalizeWords(s: String): List<String> =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().split(" ").filter { it.isNotBlank() }

    internal fun searchQuery(article: Article, question: String): String {
        // Anchor the question to the story so the search doesn't drift off-topic.
        val topic = article.title.split(Regex("\\s+")).take(8).joinToString(" ")
        return "$topic ${question.trim()}".take(240)
    }

    private companion object {
        /** Shorter replies can be a fair quote; long ones that match are a copy-paste. */
        const val MIN_LIFT_WORDS = 12
        const val LIFT_SHARE = 0.6

        /** "The article does not provide / doesn't mention / fails to state …" in any wording. */
        val ARTICLE_MISS = Regex(
            "(article|story|text|piece)[^.]{0,60}?\\b(does not|doesn't|do not|don't|did not|didn't|" +
                "cannot|can't|fails to|never)\\b[^.]{0,40}?" +
                "\\b(say|says|state|mention|specify|provide|contain|include|detail|indicate|cover|tell|" +
                "answer|information)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
