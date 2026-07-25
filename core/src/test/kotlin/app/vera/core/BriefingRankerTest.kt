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
