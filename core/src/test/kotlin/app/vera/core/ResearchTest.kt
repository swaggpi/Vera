package app.vera.core

import app.vera.core.llm.FakeLlmEngine
import app.vera.core.model.Ownership
import app.vera.core.research.FakeSearchProvider
import app.vera.core.research.Leaning
import app.vera.core.research.OutletDirectory
import app.vera.core.research.ResearchRepository
import app.vera.core.research.SearchResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResearchTest {

    private val results = listOf(
        SearchResult("Study finds coffee may ease some headaches", "https://www.bbc.com/news/health-1",
            "A study suggests caffeine can ease certain headaches for some people."),
        SearchResult("Coffee and headaches: what to know", "https://www.foxnews.com/health/coffee",
            "Experts weigh in on caffeine, coffee and head pain."),
        SearchResult("Caffeine", "https://en.wikipedia.org/wiki/Caffeine",
            "Caffeine is a central nervous system stimulant found in coffee."),
        SearchResult("Another BBC piece on coffee", "https://www.bbc.com/news/health-2",
            "More coffee and caffeine coverage from the BBC."),
        SearchResult("Unrelated celebrity gossip", "https://gossipsite.example/story",
            "A celebrity was seen at a party last night.")
    )

    private fun repo(ready: Boolean = false) =
        ResearchRepository(FakeLlmEngine(ready = ready), FakeSearchProvider(results))

    @Test fun `selects one per domain and drops irrelevant, keeping variety`() = runTest {
        val r = repo().investigate("does coffee help headaches")
        val outlets = r.sources.map { it.outletName }
        // bbc de-duplicated to one; foxnews + wikipedia kept; gossip dropped as irrelevant
        assertThat(outlets).containsAtLeast("BBC", "Fox News", "Wikipedia")
        assertThat(outlets.count { it == "BBC" }).isEqualTo(1)
        assertThat(outlets).doesNotContain("Gossipsite")
    }

    @Test fun `each source carries an outlet, leaning and a bias note`() = runTest {
        val r = repo().investigate("does coffee help headaches")
        val fox = r.sources.first { it.outletName == "Fox News" }
        assertThat(fox.leaning).isEqualTo(Leaning.RIGHT)
        assertThat(fox.ownership).isEqualTo(Ownership.PRIVATE)
        r.sources.forEach { assertThat(it.biasNote).isNotEmpty() }
    }

    @Test fun `coaching reflects the spread of perspectives`() = runTest {
        val r = repo().investigate("does coffee help headaches")
        assertThat(r.coaching).contains("BBC")
        assertThat(r.diversityNote).contains("outlets")
    }

    @Test fun `relevance ranks on-topic above off-topic`() {
        val rr = repo()
        val hi = rr.relevanceScore("coffee headaches caffeine", results[0])
        val lo = rr.relevanceScore("coffee headaches caffeine", results[4])
        assertThat(hi).isGreaterThan(lo)
    }

    @Test fun `no results yields coaching, not a crash`() = runTest {
        val r = ResearchRepository(FakeLlmEngine(), FakeSearchProvider(emptyList())).investigate("obscure claim")
        assertThat(r.sources).isEmpty()
        assertThat(r.coaching).isNotEmpty()
    }
}

class OutletDirectoryTest {

    @Test fun `known domains resolve to profiles`() {
        val bbc = OutletDirectory.forUrl("https://www.bbc.com/news/x")
        assertThat(bbc.name).isEqualTo("BBC")
        assertThat(bbc.ownership).isEqualTo(Ownership.PUBLIC)

        val rt = OutletDirectory.forUrl("https://rt.com/news")
        assertThat(rt.ownership).isEqualTo(Ownership.STATE)
    }

    @Test fun `subdomains match the registrable domain`() {
        val cnn = OutletDirectory.forUrl("https://edition.cnn.com/2026/story")
        assertThat(cnn.name).isEqualTo("CNN")
    }

    @Test fun `unknown domains are flagged, not guessed`() {
        val p = OutletDirectory.forUrl("https://some-random-blog.example/post")
        assertThat(p.ownership).isEqualTo(Ownership.UNKNOWN)
        assertThat(p.leaning).isEqualTo(Leaning.UNKNOWN)
    }

    @Test fun `host extraction strips scheme and www`() {
        assertThat(OutletDirectory.hostOf("https://www.example.com/a/b?c=1")).isEqualTo("example.com")
    }
}
