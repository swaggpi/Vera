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

    // domain -> profile. Keys are matched exactly first, then as a domain suffix
    // (edition.cnn.com -> cnn.com), so subdomain-specific keys must come before their parent.
    private val map: Map<String, OutletProfile> = mapOf(
        // --- wires / agencies ---
        "reuters.com" to p("Reuters", Ownership.PRIVATE, Leaning.CENTER, 1),
        "apnews.com" to p("Associated Press", Ownership.PRIVATE, Leaning.CENTER, 1),
        "afp.com" to p("Agence France-Presse", Ownership.PRIVATE, Leaning.CENTER, 1),
        "upi.com" to p("United Press International", Ownership.PRIVATE, Leaning.CENTER, 2),
        "dpa.com" to p("Deutsche Presse-Agentur", Ownership.PRIVATE, Leaning.CENTER, 1),

        // --- public broadcasters ---
        "bbc.com" to p("BBC", Ownership.PUBLIC, Leaning.CENTER, 2),
        "bbc.co.uk" to p("BBC", Ownership.PUBLIC, Leaning.CENTER, 2),
        "npr.org" to p("NPR", Ownership.PUBLIC, Leaning.CENTER_LEFT, 2),
        "pbs.org" to p("PBS", Ownership.PUBLIC, Leaning.CENTER_LEFT, 2),
        "tagesschau.de" to p("Tagesschau", Ownership.PUBLIC, Leaning.CENTER, 1),
        "ard.de" to p("ARD", Ownership.PUBLIC, Leaning.CENTER, 1),
        "zdf.de" to p("ZDF", Ownership.PUBLIC, Leaning.CENTER, 1),
        "dw.com" to p("Deutsche Welle", Ownership.PUBLIC, Leaning.CENTER, 1),
        "france24.com" to p("France 24", Ownership.PUBLIC, Leaning.CENTER, 2),
        "rfi.fr" to p("RFI", Ownership.PUBLIC, Leaning.CENTER, 2),
        "nos.nl" to p("NOS", Ownership.PUBLIC, Leaning.CENTER, 1),
        "rte.ie" to p("RTÉ", Ownership.PUBLIC, Leaning.CENTER, 1),
        "cbc.ca" to p("CBC", Ownership.PUBLIC, Leaning.CENTER, 1),
        "abc.net.au" to p("ABC News (Australia)", Ownership.PUBLIC, Leaning.CENTER, 1),
        "nhk.or.jp" to p("NHK", Ownership.PUBLIC, Leaning.CENTER, 2),
        "euronews.com" to p("Euronews", Ownership.PRIVATE, Leaning.CENTER, 2),

        // --- US national ---
        "nytimes.com" to p("The New York Times", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "washingtonpost.com" to p("The Washington Post", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "usatoday.com" to p("USA Today", Ownership.PRIVATE, Leaning.CENTER, 2),
        "usnews.com" to p("U.S. News & World Report", Ownership.PRIVATE, Leaning.CENTER, 2),
        "latimes.com" to p("Los Angeles Times", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "chicagotribune.com" to p("Chicago Tribune", Ownership.PRIVATE, Leaning.CENTER, 2),
        "bostonglobe.com" to p("The Boston Globe", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "abcnews.go.com" to p("ABC News", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "cbsnews.com" to p("CBS News", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "nbcnews.com" to p("NBC News", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "msnbc.com" to p("MSNBC", Ownership.PRIVATE, Leaning.LEFT, 2),
        "cnn.com" to p("CNN", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "foxnews.com" to p("Fox News", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "nypost.com" to p("New York Post", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "huffpost.com" to p("HuffPost", Ownership.PRIVATE, Leaning.LEFT, 2),
        "politico.com" to p("Politico", Ownership.PRIVATE, Leaning.CENTER, 2),
        "politico.eu" to p("Politico Europe", Ownership.PRIVATE, Leaning.CENTER, 2),
        "axios.com" to p("Axios", Ownership.PRIVATE, Leaning.CENTER, 2),
        "thehill.com" to p("The Hill", Ownership.PRIVATE, Leaning.CENTER, 2),
        "newsweek.com" to p("Newsweek", Ownership.PRIVATE, Leaning.CENTER, 2),
        "time.com" to p("TIME", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "theatlantic.com" to p("The Atlantic", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "newyorker.com" to p("The New Yorker", Ownership.PRIVATE, Leaning.LEFT, 2),
        "vox.com" to p("Vox", Ownership.PRIVATE, Leaning.LEFT, 2),
        "slate.com" to p("Slate", Ownership.PRIVATE, Leaning.LEFT, 2),
        "motherjones.com" to p("Mother Jones", Ownership.PRIVATE, Leaning.LEFT, 2),
        "theintercept.com" to p("The Intercept", Ownership.PRIVATE, Leaning.LEFT, 2),
        "propublica.org" to p("ProPublica", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "nationalreview.com" to p("National Review", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "washingtontimes.com" to p("The Washington Times", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "washingtonexaminer.com" to p("Washington Examiner", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "dailywire.com" to p("The Daily Wire", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "newsmax.com" to p("Newsmax", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "breitbart.com" to p("Breitbart", Ownership.PRIVATE, Leaning.RIGHT, 2),

        // --- business / tech ---
        "wsj.com" to p("The Wall Street Journal", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "ft.com" to p("Financial Times", Ownership.PRIVATE, Leaning.CENTER, 2),
        "economist.com" to p("The Economist", Ownership.PRIVATE, Leaning.CENTER, 2),
        "bloomberg.com" to p("Bloomberg", Ownership.PRIVATE, Leaning.CENTER, 2),
        "cnbc.com" to p("CNBC", Ownership.PRIVATE, Leaning.CENTER, 2),
        "forbes.com" to p("Forbes", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "businessinsider.com" to p("Business Insider", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "theverge.com" to p("The Verge", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "arstechnica.com" to p("Ars Technica", Ownership.PRIVATE, Leaning.CENTER, 2),
        "wired.com" to p("WIRED", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "techcrunch.com" to p("TechCrunch", Ownership.PRIVATE, Leaning.CENTER, 2),
        "engadget.com" to p("Engadget", Ownership.PRIVATE, Leaning.CENTER, 2),

        // --- aggregators / portals (they syndicate, so the leaning is whoever they reprinted) ---
        "msn.com" to p("MSN", Ownership.PRIVATE, Leaning.MIXED, 2),
        "news.yahoo.com" to p("Yahoo News", Ownership.PRIVATE, Leaning.MIXED, 2),
        "yahoo.com" to p("Yahoo", Ownership.PRIVATE, Leaning.MIXED, 2),
        "news.google.com" to p("Google News", Ownership.PRIVATE, Leaning.MIXED, 2),
        "flipboard.com" to p("Flipboard", Ownership.PRIVATE, Leaning.MIXED, 2),

        // --- UK / Ireland ---
        "theguardian.com" to p("The Guardian", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "independent.co.uk" to p("The Independent", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "telegraph.co.uk" to p("The Telegraph", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "thetimes.co.uk" to p("The Times", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "sky.com" to p("Sky News", Ownership.PRIVATE, Leaning.CENTER, 2),
        "mirror.co.uk" to p("Daily Mirror", Ownership.PRIVATE, Leaning.LEFT, 2),
        "dailymail.co.uk" to p("Daily Mail", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "thesun.co.uk" to p("The Sun", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "express.co.uk" to p("Daily Express", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "irishtimes.com" to p("The Irish Times", Ownership.PRIVATE, Leaning.CENTER, 2),

        // --- Europe ---
        "lemonde.fr" to p("Le Monde", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "lefigaro.fr" to p("Le Figaro", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "liberation.fr" to p("Libération", Ownership.PRIVATE, Leaning.LEFT, 2),
        "spiegel.de" to p("Der Spiegel", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "zeit.de" to p("Die Zeit", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "sueddeutsche.de" to p("Süddeutsche Zeitung", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "faz.net" to p("Frankfurter Allgemeine Zeitung", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "welt.de" to p("Die Welt", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "bild.de" to p("Bild", Ownership.PRIVATE, Leaning.RIGHT, 2),
        "elpais.com" to p("El País", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "elmundo.es" to p("El Mundo", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 2),
        "repubblica.it" to p("la Repubblica", Ownership.PRIVATE, Leaning.CENTER_LEFT, 2),
        "corriere.it" to p("Corriere della Sera", Ownership.PRIVATE, Leaning.CENTER, 2),
        "nrc.nl" to p("NRC", Ownership.PRIVATE, Leaning.CENTER, 1),

        // --- rest of the world ---
        "aljazeera.com" to p("Al Jazeera", Ownership.STATE, Leaning.MIXED, 4),
        "haaretz.com" to p("Haaretz", Ownership.PRIVATE, Leaning.CENTER_LEFT, 3),
        "timesofisrael.com" to p("The Times of Israel", Ownership.PRIVATE, Leaning.CENTER, 3),
        "jpost.com" to p("The Jerusalem Post", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 3),
        "thehindu.com" to p("The Hindu", Ownership.PRIVATE, Leaning.CENTER_LEFT, 3),
        "indiatimes.com" to p("The Times of India", Ownership.PRIVATE, Leaning.CENTER, 3),
        "indianexpress.com" to p("The Indian Express", Ownership.PRIVATE, Leaning.CENTER, 3),
        "ndtv.com" to p("NDTV", Ownership.PRIVATE, Leaning.CENTER, 3),
        "scmp.com" to p("South China Morning Post", Ownership.PRIVATE, Leaning.MIXED, 4),
        "straitstimes.com" to p("The Straits Times", Ownership.PRIVATE, Leaning.CENTER, 4),
        "japantimes.co.jp" to p("The Japan Times", Ownership.PRIVATE, Leaning.CENTER, 2),
        "smh.com.au" to p("The Sydney Morning Herald", Ownership.PRIVATE, Leaning.CENTER, 1),
        "theglobeandmail.com" to p("The Globe and Mail", Ownership.PRIVATE, Leaning.CENTER, 1),
        "africanews.com" to p("Africanews", Ownership.PRIVATE, Leaning.CENTER, 3),
        "allafrica.com" to p("AllAfrica", Ownership.PRIVATE, Leaning.CENTER, 3),
        "news24.com" to p("News24", Ownership.PRIVATE, Leaning.CENTER, 2),
        "clarin.com" to p("Clarín", Ownership.PRIVATE, Leaning.CENTER_RIGHT, 3),
        "infobae.com" to p("Infobae", Ownership.PRIVATE, Leaning.CENTER, 3),
        "globo.com" to p("O Globo", Ownership.PRIVATE, Leaning.CENTER, 3),

        // --- state-run / state-aligned ---
        "rt.com" to p("RT", Ownership.STATE, Leaning.MIXED, 5),
        "sputniknews.com" to p("Sputnik", Ownership.STATE, Leaning.MIXED, 5),
        "tass.com" to p("TASS", Ownership.STATE, Leaning.MIXED, 5),
        "xinhuanet.com" to p("Xinhua", Ownership.STATE, Leaning.MIXED, 5),
        "globaltimes.cn" to p("Global Times", Ownership.STATE, Leaning.MIXED, 5),
        "cgtn.com" to p("CGTN", Ownership.STATE, Leaning.MIXED, 5),
        "presstv.ir" to p("Press TV", Ownership.STATE, Leaning.MIXED, 5),

        // --- reference / science / health / institutions — treated as center, high reliability ---
        "wikipedia.org" to p("Wikipedia", Ownership.PRIVATE, Leaning.CENTER, 1),
        "nature.com" to p("Nature", Ownership.PRIVATE, Leaning.CENTER, 1),
        "science.org" to p("Science", Ownership.PRIVATE, Leaning.CENTER, 1),
        "sciencedirect.com" to p("ScienceDirect", Ownership.PRIVATE, Leaning.CENTER, 1),
        "thelancet.com" to p("The Lancet", Ownership.PRIVATE, Leaning.CENTER, 1),
        "nejm.org" to p("The New England Journal of Medicine", Ownership.PRIVATE, Leaning.CENTER, 1),
        "bmj.com" to p("The BMJ", Ownership.PRIVATE, Leaning.CENTER, 1),
        "theconversation.com" to p("The Conversation", Ownership.PRIVATE, Leaning.CENTER_LEFT, 1),
        "who.int" to p("World Health Organization", Ownership.PUBLIC, Leaning.CENTER, 1),
        "un.org" to p("United Nations", Ownership.PUBLIC, Leaning.CENTER, 1),
        "unesco.org" to p("UNESCO", Ownership.PUBLIC, Leaning.CENTER, 1),
        "ipcc.ch" to p("IPCC", Ownership.PUBLIC, Leaning.CENTER, 1),
        "nih.gov" to p("US National Institutes of Health", Ownership.PUBLIC, Leaning.CENTER, 1),
        "cdc.gov" to p("US CDC", Ownership.PUBLIC, Leaning.CENTER, 1),
        "nasa.gov" to p("NASA", Ownership.PUBLIC, Leaning.CENTER, 1),
        "noaa.gov" to p("NOAA", Ownership.PUBLIC, Leaning.CENTER, 1),

        // --- fact-checkers ---
        "snopes.com" to p("Snopes", Ownership.PRIVATE, Leaning.CENTER, 1),
        "factcheck.org" to p("FactCheck.org", Ownership.PRIVATE, Leaning.CENTER, 1),
        "politifact.com" to p("PolitiFact", Ownership.PRIVATE, Leaning.CENTER, 1),
        "fullfact.org" to p("Full Fact", Ownership.PRIVATE, Leaning.CENTER, 1),
        "correctiv.org" to p("CORRECTIV", Ownership.PRIVATE, Leaning.CENTER, 1)
    )

    fun forUrl(url: String): OutletProfile {
        val host = hostOf(url)
        val registrable = registrable(host)
        map[host]?.let { return it }
        map[registrable]?.let { return it }
        // suffix match (e.g. edition.cnn.com -> cnn.com)
        map.entries.firstOrNull { host == it.key || host.endsWith(".${it.key}") }?.let { return it.value }
        return OutletProfile(prettyName(host), Ownership.UNKNOWN, Leaning.UNKNOWN, 3)
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

    /** Second-level labels that are part of the public suffix, not the outlet's name. */
    private val suffixLabels = setOf(
        "co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go", "gob", "gouv", "mil", "info"
    )

    /** Trailing words a news domain glues onto its name ("dailymail" -> "Daily Mail"). */
    private val gluedWords = listOf(
        "news", "times", "post", "journal", "herald", "tribune", "gazette", "observer", "chronicle",
        "daily", "today", "weekly", "report", "review", "press", "media", "wire", "watch", "mail",
        "online", "digital", "magazine", "radio", "world", "star", "sun", "globe", "record"
    )

    private val vowels = setOf('a', 'e', 'i', 'o', 'u')

    /**
     * Best-effort display name for a domain that isn't in the directory. Strips the public suffix
     * (including two-part ones like co.uk, so "somepaper.co.uk" isn't shown as "Co"), then splits the
     * outlet's own label into words on separators, letter/digit boundaries and glued-on news words, so
     * "some-news-blog.example" reads "Some News Blog" and "denverpost.com" reads "Denver Post".
     */
    private fun prettyName(host: String): String {
        var labels = host.split('.').filter { it.isNotBlank() }
        if (labels.size > 1) labels = labels.dropLast(1)                                  // TLD
        if (labels.size > 1 && labels.last() in suffixLabels) labels = labels.dropLast(1) // co.uk, go.com
        val label = labels.lastOrNull().orEmpty()
        val words = splitWords(label)
        if (words.isEmpty()) return "Unknown source"
        return words.joinToString(" ") { titleCase(it) }
    }

    private fun splitWords(label: String): List<String> {
        val separated = label.split('-', '_', '+').filter { it.isNotBlank() }
        return separated.flatMap { splitDigits(it) }.flatMap { splitGlued(it) }
    }

    /** "france24" -> ["france", "24"] */
    private fun splitDigits(word: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in word) {
            if (current.isNotEmpty() && c.isDigit() != current.last().isDigit()) {
                out.add(current.toString())
                current.clear()
            }
            current.append(c)
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }

    /** "thedailymail" -> ["the", "daily", "mail"]. Only splits off words that leave a real stem. */
    private fun splitGlued(word: String): List<String> {
        if (word.length < 6) return listOf(word)
        val glued = gluedWords.firstOrNull { word.length - it.length >= 3 && word.endsWith(it) }
        if (glued != null) return splitGlued(word.dropLast(glued.length)) + glued
        if (word.startsWith("the") && word.length - 3 >= 4) return listOf("the") + splitGlued(word.drop(3))
        return listOf(word)
    }

    /** Short vowel-less words are almost always initialisms: "msn" -> "MSN", "cnbc" -> "CNBC". */
    private fun titleCase(word: String): String =
        if (word.length in 2..4 && word.all { it.isLetter() } && word.none { it in vowels }) word.uppercase()
        else word.replaceFirstChar { it.uppercase() }
}
