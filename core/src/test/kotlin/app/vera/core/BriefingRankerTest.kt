package app.vera.core

import app.vera.core.model.Article
import app.vera.core.news.BriefingRanker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BriefingRankerTest {

    private fun a(id: String, src: String, title: String, body: String = "body text here") =
        Article(id = id, sourceId = src, title = title, body = body, url = "https://x/$id")

    private val names = mapOf("bbc" to "BBC", "npr" to "NPR", "ard" to "Tagesschau", "hindu" to "The Hindu")
    private val countries = mapOf("bbc" to "UK", "npr" to "USA", "ard" to "Germany", "hindu" to "India")
    private fun name(id: String) = names[id] ?: id
    private fun country(id: String) = countries[id] ?: ""

    @Test fun `the same story from several outlets is collapsed into one card`() {
        val articles = listOf(
            a("1", "bbc", "Major earthquake strikes coastal region, thousands displaced"),
            a("2", "npr", "Thousands displaced after major earthquake strikes coastal region"),
            a("3", "ard", "Major earthquake strikes coastal region displacing thousands"),
            a("4", "hindu", "Local cricket league announces new season schedule")
        )
        val out = BriefingRanker.rank(articles, ::name, ::country)

        assertThat(out).hasSize(2)                                  // 3 dupes merged + 1 unrelated
        val quake = out.first()
        assertThat(quake.outletCount).isEqualTo(3)
        assertThat(quake.alsoReportedBy).hasSize(2)
        assertThat(quake.countries).containsAtLeast("UK", "USA", "Germany")
    }

    @Test fun `a story carried internationally outranks a single-outlet story`() {
        val articles = listOf(
            a("solo", "hindu", "Regional council approves a small local budget item"),
            a("1", "bbc", "Global summit agrees landmark climate finance deal"),
            a("2", "npr", "Landmark climate finance deal agreed at global summit"),
            a("3", "ard", "Global summit agrees landmark climate finance deal")
        )
        val out = BriefingRanker.rank(articles, ::name, ::country)
        assertThat(out.first().lead.title).contains("climate finance")
    }

    @Test fun `user interests boost matching stories to the top`() {
        val articles = listOf(
            a("1", "bbc", "Central bank holds interest rates steady this quarter"),
            a("2", "npr", "Football world cup qualifiers deliver dramatic upsets", "football match report")
        )
        val plain = BriefingRanker.rank(articles, ::name, ::country)
        assertThat(plain.first().lead.title).contains("Central bank")   // no interests -> feed order

        val withInterest = BriefingRanker.rank(articles, ::name, ::country, interests = listOf("football"))
        assertThat(withInterest.first().lead.title).contains("Football")
        assertThat(withInterest.first().matchedInterests).contains("football")
    }

    @Test fun `a keyword used as a simile does not count as a topic match`() {
        // Found in live testing: an Ebola story "spreading like a wildfire" was filed under wildfires.
        val articles = listOf(
            a("ebola", "bbc", "Ebola deaths surge as virus spreads like a wildfire",
                "The outbreak is growing quickly across the region.")
        )
        val out = BriefingRanker.rank(articles, ::name, ::country, interests = listOf("wildfire"))
        assertThat(out.first().matchedInterests).isEmpty()
    }

    @Test fun `a real mention of the topic still matches`() {
        val articles = listOf(
            a("fire", "bbc", "Wildfire forces evacuations near Bordeaux", "Crews battle the blaze.")
        )
        val out = BriefingRanker.rank(articles, ::name, ::country, interests = listOf("wildfire"))
        assertThat(out.first().matchedInterests).containsExactly("wildfire")
    }

    @Test fun `keywords match whole words only`() {
        assertThat(BriefingRanker.mentions("A matter of conscience for voters", "science")).isFalse()
        assertThat(BriefingRanker.mentions("New science funding announced", "science")).isTrue()
        assertThat(BriefingRanker.mentions("The start of something", "art")).isFalse()
    }

    @Test fun `one outlet cannot sweep the briefing just because it was fetched first`() {
        // Reproduces a live bug: the pool is built source-by-source, so BBC's items all sat at the
        // front and won every tie — the whole briefing came from one outlet.
        val bbcHeadlines = listOf(
            "Housing bill clears committee stage", "Ferry strike halts island crossings",
            "Museum returns disputed bronze artefacts", "Rainfall records broken in the north")
        val nprHeadlines = listOf(
            "Federal reserve holds rates unchanged", "Wheat harvest beats early forecasts",
            "Coastal restoration project wins funding", "Teacher shortage worsens in rural districts")
        val ardHeadlines = listOf(
            "Autobahn tolls debated in parliament", "Solar capacity overtakes coal generation",
            "Bavarian brewery wins export award", "Rail operator apologises for delays")
        val pool = buildList {
            bbcHeadlines.forEachIndexed { i, t -> add(a("bbc$i", "bbc", t)) }
            nprHeadlines.forEachIndexed { i, t -> add(a("npr$i", "npr", t)) }
            ardHeadlines.forEachIndexed { i, t -> add(a("ard$i", "ard", t)) }
        }
        val out = BriefingRanker.rank(pool, ::name, ::country, limit = 5)
        val outlets = out.map { name(it.lead.sourceId) }.toSet()

        assertThat(out).hasSize(5)
        assertThat(outlets.size).isAtLeast(3)                 // all three outlets represented
        assertThat(outlets).containsAtLeast("BBC", "NPR", "Tagesschau")
    }

    @Test fun `a single available outlet still fills the briefing`() {
        val pool = listOf(
            "Harbour redevelopment plan approved", "Flu vaccination drive begins early",
            "Cycling network expands into suburbs", "Historic cinema reopens after refit",
            "Seabird colony returns to cliffs", "Bridge inspection closes two lanes"
        ).mapIndexed { i, t -> a("bbc$i", "bbc", t) }
        assertThat(BriefingRanker.rank(pool, ::name, ::country, limit = 5)).hasSize(5)
    }

    @Test fun `corroborated stories still outrank the diversity cap`() {
        val pool = buildList {
            add(a("bbc1", "bbc", "Global summit agrees landmark climate finance deal"))
            add(a("npr1", "npr", "Landmark climate finance deal agreed at global summit"))
            add(a("ard1", "ard", "Global summit agrees landmark climate finance deal"))
            repeat(4) { add(a("bbc$it", "bbc", "Minor domestic filler story number $it")) }
        }
        val out = BriefingRanker.rank(pool, ::name, ::country, limit = 3)
        assertThat(out.first().lead.title).contains("climate finance")
        assertThat(out.first().outletCount).isEqualTo(3)
    }

    @Test fun `distinct stories are never merged`() {
        val articles = listOf(
            a("1", "bbc", "Election results announced in northern province"),
            a("2", "npr", "Scientists discover new deep sea species near trench"),
            a("3", "ard", "Rail strike disrupts commuters across the country")
        )
        assertThat(BriefingRanker.rank(articles, ::name, ::country)).hasSize(3)
    }

    @Test fun `lead article is the one with the most body to summarise`() {
        val articles = listOf(
            a("thin", "bbc", "Budget talks collapse in parliament", "Short."),
            a("rich", "npr", "Budget talks collapse in parliament", "A much longer account with detail and context.")
        )
        val out = BriefingRanker.rank(articles, ::name, ::country)
        assertThat(out).hasSize(1)
        assertThat(out.first().lead.id).isEqualTo("rich")
    }

    @Test fun `empty input is handled and limit is respected`() {
        assertThat(BriefingRanker.rank(emptyList())).isEmpty()

        // Genuinely distinct subjects — nothing should merge, so the limit does the trimming.
        val subjects = listOf(
            "Harvest yields improve across southern farmlands",
            "Museum unveils restored bronze age collection",
            "Ferry service resumes between island communities",
            "Regulators approve merger of two energy suppliers",
            "Marathon record broken at the autumn championship",
            "Wildfire containment reaches ninety percent",
            "University launches quantum computing faculty",
            "Currency steadies following central bank guidance"
        )
        val many = subjects.mapIndexed { i, t -> a("$i", "bbc", t) }
        assertThat(BriefingRanker.rank(many, ::name, ::country, limit = 5)).hasSize(5)
        assertThat(BriefingRanker.rank(many, ::name, ::country, limit = 99)).hasSize(subjects.size)
    }
}
