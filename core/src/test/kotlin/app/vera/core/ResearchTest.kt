package app.vera.core

import app.vera.core.llm.FakeLlmEngine
import app.vera.core.research.FakeSearchProvider
import app.vera.core.research.ResearchRepository
import app.vera.core.research.SearchResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResearchTest {

    private val sources = listOf(
        SearchResult("Fact check: the claim", "https://a/1", "Independent checkers rate it misleading.", "FactOrg"),
        SearchResult("Background", "https://b/2", "Context on the topic.", "Encyclopedia")
    )

    @Test fun `investigate returns grounded coaching with sources`() = runTest {
        val repo = ResearchRepository(
            llm = FakeLlmEngine(responder = { "These two outlets add context but don't settle it; check who published each and how recent." }),
            search = FakeSearchProvider(sources)
        )
        val result = repo.investigate("Did the mayor really ban all cars downtown?")

        assertThat(result.sources).hasSize(2)
        assertThat(result.coaching).contains("check who published")
        assertThat(result.claim).contains("mayor")
    }

    @Test fun `falls back to grounded summary when the model echoes JSON`() = runTest {
        val repo = ResearchRepository(
            llm = FakeLlmEngine(responder = { "{\"unexpected\":true}" }),
            search = FakeSearchProvider(sources)
        )
        val result = repo.investigate("some claim")
        // Fallback names the outlets it found rather than trusting the JSON echo.
        assertThat(result.coaching).contains("FactOrg")
    }

    @Test fun `no sources yields coaching, not a crash`() = runTest {
        val repo = ResearchRepository(FakeLlmEngine(), FakeSearchProvider(emptyList()))
        val result = repo.investigate("a very obscure claim")
        assertThat(result.sources).isEmpty()
        assertThat(result.coaching).isNotEmpty()
    }

    @Test fun `query is trimmed to a tight search string`() = runTest {
        val repo = ResearchRepository(FakeLlmEngine(), FakeSearchProvider(emptyList()))
        val q = repo.buildQuery("  one two three four five six seven eight nine ten eleven twelve thirteen  ")
        assertThat(q.split(" ")).hasSize(12)
    }
}
