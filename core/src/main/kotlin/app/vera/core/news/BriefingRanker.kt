package app.vera.core.news

import app.vera.core.model.Article

/**
 * One real-world story, possibly reported by several outlets.
 *
 * [outletCount] doubles as an importance signal: if six outlets across four countries ran the same
 * story, it matters more than something one site published — and the reader should see it *once*,
 * not six times.
 */
data class StoryCluster(
    val lead: Article,
    val alsoReportedBy: List<String>,     // display names of the other outlets
    val countries: Set<String>,
    val matchedInterests: List<String>,
    val score: Double
) {
    val outletCount: Int get() = alsoReportedBy.size + 1
}

/**
 * Turns a raw pile of feed items into the briefing: de-duplicated, ordered by how much the story
 * matters *and* how much this particular reader cares.
 *
 * Score = cross-outlet corroboration + international spread + interest match + feed prominence.
 * Pure Kotlin, so the whole ranking is unit-tested without a device.
 */
object BriefingRanker {

    private val STOP = setOf(
        "the", "and", "for", "with", "from", "that", "this", "have", "has", "was", "were", "are",
        "will", "after", "over", "into", "says", "say", "said", "new", "amid", "its", "his", "her",
        "their", "they", "you", "but", "not", "who", "how", "why", "what", "when", "where", "all",
        "more", "than", "out", "off", "how", "about", "against", "under", "first", "two", "one"
    )

    /** Significant words of a headline — the fingerprint used to match the same story across outlets. */
    internal fun tokens(s: String): Set<String> =
        s.lowercase()
            .split(Regex("[^a-z0-9äöüßàâçéèêëîïôùûÿñæœ]+"))
            .filter { it.length > 3 && it !in STOP }
            .toSet()

    /** "like a wildfire", "as an avalanche" — the word is a figure of speech, not the subject. */
    private val SIMILE = Regex("""\b(?:like|as)\s+(?:a|an|the)?\s*$""")

    /**
     * True when [text] is really *about* [keyword].
     *
     * Matches whole words only, so "science" doesn't fire on "conscience", and skips similes, so a
     * story about Ebola "spreading like a wildfire" isn't filed under wildfires. Genuine metaphor
     * detection needs semantics — this catches the common case, not every case.
     */
    internal fun mentions(text: String, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        val hay = text.lowercase()
        val kw = Regex.escape(keyword.lowercase())
        for (m in Regex("""(?<![\p{L}\p{N}])$kw(?![\p{L}\p{N}])""").findAll(hay)) {
            val before = hay.substring(0, m.range.first)
            if (!SIMILE.containsMatchIn(before)) return true
        }
        return false
    }

    /** Jaccard overlap of two headlines' significant words. */
    internal fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        if (inter == 0) return 0.0
        return inter.toDouble() / (a.size + b.size - inter)
    }

    /**
     * @param articles feed items, in the order they were fetched (earlier ≈ more prominent)
     * @param outletName maps a sourceId to a display name
     * @param countryOf maps a sourceId to a country
     * @param interests user keywords ("football", "science", "Kenya") — empty means no preference
     */
    fun rank(
        articles: List<Article>,
        outletName: (String) -> String = { it },
        countryOf: (String) -> String = { "" },
        interests: List<String> = emptyList(),
        limit: Int = 8,
        similarityThreshold: Double = 0.42
    ): List<StoryCluster> {
        if (articles.isEmpty()) return emptyList()

        // --- 1. cluster near-duplicate headlines ---
        val fingerprints = articles.map { tokens(it.title) }
        val clusterOf = IntArray(articles.size) { -1 }
        val clusters = ArrayList<MutableList<Int>>()
        for (i in articles.indices) {
            if (clusterOf[i] != -1) continue
            val members = mutableListOf(i)
            clusterOf[i] = clusters.size
            for (j in i + 1 until articles.size) {
                if (clusterOf[j] != -1) continue
                if (similarity(fingerprints[i], fingerprints[j]) >= similarityThreshold) {
                    clusterOf[j] = clusters.size
                    members.add(j)
                }
            }
            clusters.add(members)
        }

        // --- 2. score each cluster ---
        val lowerInterests = interests.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val scored = clusters.map { members ->
            // Lead = the member with the most substantial body (best material to summarise).
            val leadIdx = members.maxByOrNull { articles[it].body.length } ?: members.first()
            val lead = articles[leadIdx]
            val others = members.filter { it != leadIdx }
                .map { outletName(articles[it].sourceId) }
                .distinct()
                .filter { it != outletName(lead.sourceId) }
            val countries = members.map { countryOf(articles[it].sourceId) }
                .filter { it.isNotBlank() }.toSet()

            val matched = lowerInterests.filter { kw -> mentions(lead.title, kw) || mentions(lead.body, kw) }

            // Corroboration and international spread carry the most weight; a keyword hit is a strong
            // personal boost; earlier items in a feed are usually the ones editors led with.
            val corroboration = (others.size + 1).toDouble()
            val spread = countries.size.toDouble()
            val prominence = 1.0 - (leadIdx.toDouble() / articles.size)
            val interestBoost = if (matched.isNotEmpty()) 3.0 + matched.size else 0.0

            StoryCluster(
                lead = lead,
                alsoReportedBy = others,
                countries = countries,
                matchedInterests = matched,
                score = corroboration * 2.0 + spread * 1.5 + interestBoost + prominence
            )
        }

        return scored.sortedByDescending { it.score }.take(limit)
    }
}
