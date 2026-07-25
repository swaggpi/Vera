package app.vera.core.research

import app.vera.core.llm.LlmEngine
import app.vera.core.model.Ownership

/** A raw web result before analysis. */
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceName: String = ""
)

/** Pluggable web search. Real impls (DuckDuckGo, Wikipedia; Brave/Tavily later) live in :app. */
interface SearchProvider {
    suspend fun search(query: String): List<SearchResult>
}

class FakeSearchProvider(private val results: List<SearchResult>) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> = results
}

/** Runs several providers and concatenates their results (order preserved, de-duplicated by URL). */
class MultiSearchProvider(private val providers: List<SearchProvider>) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> {
        val seen = HashSet<String>()
        val out = ArrayList<SearchResult>()
        for (p in providers) {
            val results = runCatching { p.search(query) }.getOrDefault(emptyList())
            for (r in results) if (seen.add(OutletDirectory.hostOf(r.url) + "|" + r.url)) out.add(r)
        }
        return out
    }
}

/** A search result after Vera analyses who published it and what it says. */
data class AnalyzedSource(
    val title: String,
    val url: String,
    val outletName: String,
    val ownership: Ownership,
    val leaning: Leaning,
    val pressFreedomTier: Int,
    val summary: String,   // one-line "what this source says"
    val biasNote: String   // how to weigh this outlet
)

data class ResearchResult(
    val claim: String,
    val coaching: String,
    val diversityNote: String,
    val sources: List<AnalyzedSource>
)

/**
 * "Check what you heard": search widely, then keep a *relevant and diverse* set of outlets (not just
 * the first N), summarise each, and flag likely bias — so the user sees the spread of who is saying
 * what. On-device Gemma sharpens the per-source summaries and coaching; without it, deterministic
 * heuristics keep everything useful and testable.
 */
class ResearchRepository(
    private val llm: LlmEngine,
    private val search: SearchProvider,
    private val maxSources: Int = 5
) {

    suspend fun investigate(input: String): ResearchResult {
        val query = buildQuery(input)
        val raw = runCatching { search.search(query) }.getOrDefault(emptyList())
        val picked = selectDiverse(query, raw, maxSources)
        val analyzed = picked.map { analyze(it) }
        return ResearchResult(
            claim = input.trim(),
            coaching = synthesize(input, analyzed),
            diversityNote = diversityNote(analyzed),
            sources = analyzed
        )
    }

    internal fun buildQuery(input: String): String =
        input.trim().split(Regex("\\s+")).take(12).joinToString(" ")

    // ---- relevance + diversity ----

    private val stop = setOf("the", "a", "an", "is", "are", "was", "were", "of", "to", "in", "on",
        "and", "or", "for", "that", "this", "it", "i", "heard", "about", "with", "from", "did", "do")

    internal fun relevanceScore(query: String, r: SearchResult): Double {
        val q = tokens(query).filter { it !in stop }.toSet()
        if (q.isEmpty()) return 0.0
        val hay = tokens(r.title + " " + r.snippet).toSet()
        val overlap = q.count { it in hay }
        // small boost for matches in the title
        val titleHits = tokens(r.title).toSet().count { it in q }
        return overlap.toDouble() / q.size + titleHits * 0.15
    }

    /** Best result per domain (by relevance), then top [max] domains — forces variety, drops noise. */
    internal fun selectDiverse(query: String, results: List<SearchResult>, max: Int): List<SearchResult> {
        if (results.isEmpty()) return emptyList()
        val scored = results.map { it to relevanceScore(query, it) }
        val relevant = scored.filter { it.second > 0.0 }
        // Drop irrelevant noise; only fall back to everything if nothing scored at all (sparse/offline).
        val pool = relevant.ifEmpty { scored }
        val bestPerDomain = pool
            .groupBy { OutletDirectory.hostOf(it.first.url) }
            .mapValues { (_, list) -> list.maxByOrNull { it.second }!! }
            .values
            .sortedByDescending { it.second }
        return bestPerDomain.take(max).map { it.first }
    }

    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }

    // ---- per-source analysis ----

    private suspend fun analyze(r: SearchResult): AnalyzedSource {
        val profile = OutletDirectory.forUrl(r.url)
        return AnalyzedSource(
            title = r.title,
            url = r.url,
            outletName = profile.name,
            ownership = profile.ownership,
            leaning = profile.leaning,
            pressFreedomTier = profile.pressFreedomTier,
            summary = summarize(r),
            biasNote = biasNote(profile)
        )
    }

    private suspend fun summarize(r: SearchResult): String {
        val snippet = r.snippet.trim()
        if (llm.isReady()) {
            val out = runCatching {
                llm.generate(
                    prompt = "In ONE plain sentence, say what this source states. Title: ${r.title}. Text: ${snippet.take(500)}",
                    system = "You summarise neutrally. One sentence. No preamble.",
                    maxTokens = 80
                ).trim()
            }.getOrNull()
            if (!out.isNullOrBlank() && out.length in 12..300 && !out.startsWith("{")) return out
        }
        return snippet.ifBlank { r.title }.take(180)
    }

    private fun biasNote(p: OutletProfile): String = when (p.ownership) {
        Ownership.STATE -> "State-controlled outlet — expect it to reflect its government's line; corroborate elsewhere."
        Ownership.PUBLIC -> "Public-service broadcaster (${p.leaning.label.lowercase()} lean) — generally accountable, still one viewpoint."
        Ownership.PRIVATE -> "Private outlet, ${p.leaning.label.lowercase()} lean — note its slant and who owns it."
        Ownership.UNKNOWN -> "Unrated source — check who runs it and how it funds itself before trusting it."
    }

    // ---- synthesis ----

    private suspend fun synthesize(input: String, sources: List<AnalyzedSource>): String {
        if (sources.isEmpty()) return noSources(input)
        if (llm.isReady()) {
            val ctx = sources.joinToString("\n") { "- ${it.outletName} (${it.leaning.label}): ${it.summary}" }
            val out = runCatching {
                llm.generate(
                    prompt = "Claim: \"$input\". Sources:\n$ctx\n\nIn 3-4 sentences, say what the sources do and " +
                        "don't establish and how their leanings should shape how the reader weighs them. Do NOT " +
                        "declare the claim true or false. Plain text.",
                    system = "You are Vera. Ground everything in the given sources. Never fabricate. Never give a verdict.",
                    maxTokens = 260
                ).trim()
            }.getOrNull()
            if (!out.isNullOrBlank() && out.length in 40..1200 && !out.startsWith("{")) return out
        }
        return heuristicCoaching(sources)
    }

    private fun heuristicCoaching(sources: List<AnalyzedSource>): String {
        val outlets = sources.joinToString(", ") { it.outletName }
        val leanings = sources.map { it.leaning }.filter { it != Leaning.UNKNOWN }.distinct()
        val spread = if (leanings.size >= 2)
            "They span different perspectives (${leanings.joinToString(", ") { it.label }}), which is good — compare how each frames it. "
        else "They cluster around similar perspectives, so look for an outlet that leans differently before you conclude. "
        return "I found ${sources.size} outlet${if (sources.size == 1) "" else "s"}: $outlets. $spread" +
            "Check who owns each and how recent it is, and see whether independent reporting agrees before you trust or share this."
    }

    private fun diversityNote(sources: List<AnalyzedSource>): String {
        if (sources.isEmpty()) return ""
        val leanings = sources.map { it.leaning }.filter { it != Leaning.UNKNOWN }.distinct().size
        val states = sources.count { it.ownership == Ownership.STATE }
        val parts = mutableListOf("${sources.size} outlets")
        if (leanings > 0) parts += "$leanings perspective${if (leanings == 1) "" else "s"}"
        if (states > 0) parts += "$states state-run"
        return parts.joinToString(" · ")
    }

    private fun noSources(input: String): String =
        "I couldn't pull up sources for \"${input.take(80)}\" right now. Rephrase it as a specific, checkable " +
            "claim, then investigate who is making it and search for independent coverage."
}
