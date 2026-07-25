package app.vera.core.research

import app.vera.core.llm.LlmEngine

/** A single web result used to ground the model's coaching. */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceName: String
)

/** Pluggable web search. Real impls (Wikipedia now; Brave/Tavily later) live in :app; fakes in tests. */
interface SearchProvider {
    suspend fun search(query: String): List<SearchResult>
}

class FakeSearchProvider(private val results: List<SearchResult>) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> = results
}

data class ResearchResult(
    val claim: String,
    val coaching: String,          // grounded "what we found + how to weigh it" — never a bare verdict
    val sources: List<SearchResult>
)

/**
 * The "check what you heard" pipeline: turn a spoken/typed claim into a grounded, cited coaching
 * answer. Orchestration is testable on the JVM with [FakeLlmEngine] + [FakeSearchProvider]; the real
 * model and network are injected in :app.
 */
class ResearchRepository(
    private val llm: LlmEngine,
    private val search: SearchProvider
) {

    suspend fun investigate(input: String): ResearchResult {
        val query = buildQuery(input)
        val results = runCatching { search.search(query) }.getOrDefault(emptyList()).take(5)
        val coaching = synthesize(input, results)
        return ResearchResult(claim = input.trim(), coaching = coaching, sources = results)
    }

    /** Keep the search query tight; heuristic keeps it deterministic and offline-safe. */
    internal fun buildQuery(input: String): String =
        input.trim().split(Regex("\\s+")).take(12).joinToString(" ")

    private suspend fun synthesize(input: String, results: List<SearchResult>): String {
        if (results.isEmpty()) return noSourcesCoaching(input)
        val context = results.joinToString("\n") { "- ${it.sourceName}: ${it.title} — ${it.snippet}" }
        val raw = runCatching {
            llm.generate(
                prompt = """
                    Claim: "$input"
                    Sources found:
                    $context

                    In 3-4 sentences, tell the reader what these sources do and don't establish, and
                    how to weigh them (who published, how independent, how recent). Do NOT declare the
                    claim simply true or false — coach them to judge it. Plain text.
                """.trimIndent(),
                system = "You are Vera. Ground every statement in the given sources. Never fabricate.",
                maxTokens = 320
            )
        }.getOrNull()?.trim()

        return if (!raw.isNullOrBlank() && !looksLikeJsonEcho(raw)) raw
        else groundedFallback(results)
    }

    private fun looksLikeJsonEcho(s: String): Boolean = s.startsWith("{") && s.endsWith("}")

    private fun groundedFallback(results: List<SearchResult>): String {
        val outlets = results.map { it.sourceName }.distinct().joinToString(", ").ifBlank { "a few sources" }
        return "I found ${results.size} result${if (results.size == 1) "" else "s"} from $outlets. Read at " +
            "least two, check who published each and how recent it is, and see whether independent outlets " +
            "agree before you trust or share this."
    }

    private fun noSourcesCoaching(input: String): String =
        "I couldn't pull up sources for \"${input.take(80)}\" right now. Try rephrasing it as a specific, " +
            "checkable claim, then investigate who is making it and search for independent coverage."
}
