package app.vera.core.research

import app.vera.core.model.Ownership

/**
 * Editorial leaning band. These are *approximate, contested, and for reflection only* — the point
 * is to prompt the user to weigh perspective, never to hand down a definitive label.
 */
enum class Leaning(val label: String) {
    LEFT("Left"), CENTER_LEFT("Center-left"), CENTER("Center"),
    CENTER_RIGHT("Center-right"), RIGHT("Right"), MIXED("Mixed"), UNKNOWN("Unrated")
}

data class OutletProfile(
    val name: String,
    val ownership: Ownership,
    val leaning: Leaning,
    val pressFreedomTier: Int
)

/**
 * Maps a URL's domain to a known outlet's ownership + (approximate) leaning + press-freedom band, so
 * the research feature can flag *who* a source is and how to weigh it. Unknown domains are returned
 * as UNKNOWN/Unrated rather than guessed. Ratings are a curated, simplified starting point, not gospel.
 */
object OutletDirectory {

    private fun p(name: String, o: Ownership, l: Leaning, tier: Int) = OutletProfile(name, o, l, tier)

    // domain (registrable) -> profile. Kept intentionally small and balanced.
    private val map: Map<String, OutletProfile> = mapOf(
        "reuters.com" to p("Reuters", Ownership.PRIVATE, Leaning.CENTER, 1),
        "apnews.com" to p("Associated Press", Ownership.PRIVATE, Leaning.CENTER, 1),
        "bbc.com" to p("BBC", Ownership.PUBLIC, Leaning.CENTER, 2),
        "bbc.co.uk" to p("BBC", Ownership.PUBLIC, Leaning.CENTER, 2),
        "npr.org" to p("NPR", Ownership.PUBLIC, Leaning.CENTER_LEFT, 2),
        "pbs.org" to p("PBS", Ownership.PUBLIC, Leaning.CENTER_LEFT, 2),
        "nytimes.com" to p("The New York Times", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "washingtonpost.com" to p("The Washington Post", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "theguardian.com" to p("The Guardian", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "cnn.com" to p("CNN", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "huffpost.com" to p("HuffPost", Ownership.PRIVATE, Leaning.LEFT, 2),
        "wsj.com" to p("The Wall Street Journal", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "economist.com" to p("The Economist", Ownership.PRIVATE, Leaning.CENTER, 2),
        "foxnews.com" to p("Fox News", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "nypost.com" to p("New York Post", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "dailymail.co.uk" to p("Daily Mail", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "breitbart.com" to p("Breitbart", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "aljazeera.com" to p("Al Jazeera", Ownership.STATE, Leaning.MIXED, 4),
        "rt.com" to p("RT", Ownership.STATE, Leaning.MIXED, 5),
        "xinhuanet.com" to p("Xinhua", Ownership.STATE, Leaning.MIXED, 5),
        "presstv.ir" to p("Press TV", Ownership.STATE, Leaning.MIXED, 5),
        "tagesschau.de" to p("Tagesschau", Ownership.PUBLIC, Leaning.CENTER, 1),
        "dw.com" to p("Deutsche Welle", Ownership.PUBLIC, Leaning.CENTER, 1),
        "france24.com" to p("France 24", Ownership.PUBLIC, Leaning.CENTER, 2),
        "thehindu.com" to p("The Hindu", Ownership.PRIVATE, Leaning.CENTER_LEFT, 3),
        // reference / science / health — treated as center, high reliability
        "wikipedia.org" to p("Wikipedia", Ownership.PRIVATE, Leaning.CENTER, 1),
        "nature.com" to p("Nature", Ownership.PRIVATE, Leaning.CENTER, 1),
        "sciencedirect.com" to p("ScienceDirect", Ownership.PRIVATE, Leaning.CENTER, 1),
        "who.int" to p("World Health Organization", Ownership.PUBLIC, Leaning.CENTER, 1),
        "nih.gov" to p("US National Institutes of Health", Ownership.PUBLIC, Leaning.CENTER, 1),
        "cdc.gov" to p("US CDC", Ownership.PUBLIC, Leaning.CENTER, 1),
        "snopes.com" to p("Snopes", Ownership.PRIVATE, Leaning.CENTER, 1),
        "factcheck.org" to p("FactCheck.org", Ownership.PRIVATE, Leaning.CENTER, 1),
        "fullfact.org" to p("Full Fact", Ownership.PRIVATE, Leaning.CENTER, 1)
    )

    fun forUrl(url: String): OutletProfile {
        val host = hostOf(url)
        val registrable = registrable(host)
        map[host]?.let { return it }
        map[registrable]?.let { return it }
        // suffix match (e.g. edition.cnn.com -> cnn.com)
        map.entries.firstOrNull { host == it.key || host.endsWith(".${it.key}") }?.let { return it.value }
        return OutletProfile(prettyName(registrable.ifBlank { host }), Ownership.UNKNOWN, Leaning.UNKNOWN, 3)
    }

    fun hostOf(url: String): String {
        val noScheme = url.substringAfter("://", url)
        val host = noScheme.substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        return host.removePrefix("www.")
    }

    /** Naive registrable domain: last two labels (good enough for our directory). */
    private fun registrable(host: String): String {
        val parts = host.split('.').filter { it.isNotBlank() }
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else host
    }

    private fun prettyName(domain: String): String =
        domain.substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { "Unknown source" }
}
