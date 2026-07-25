package app.vera.core

import app.vera.core.briefing.StoryChat
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.model.Article
import app.vera.core.research.FakeSearchProvider
import app.vera.core.research.SearchResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StoryChatTest {

    private val article = Article(
        id = "a1", sourceId = "bbc",
        title = "Central bank raises interest rates by half a point",
        body = "The central bank raised rates to 4.5% today, citing persistent inflation.",
        url = "https://www.bbc.com/news/econ-1"
    )

    private val web = listOf(
        SearchResult("Markets react to the rate decision", "https://www.reuters.com/m/1",
            "Equities fell after the central bank's move, analysts said."),
        SearchResult("What the rate rise means for mortgages", "https://www.theguardian.com/m/2",
            "Homeowners on variable rates will see monthly payments rise.")
    )

    @Test fun `answers from the article when it covers the question`() = runTest {
        val chat = StoryChat(
            FakeLlmEngine(responder = { "The bank raised rates to 4.5%, citing inflation." }),
            FakeSearchProvider(web)
        )
        val ans = chat.ask(article, "How much did rates go up?", "")
        assertThat(ans.text).contains("4.5%")
        assertThat(ans.extraSources).isEmpty()          // no web trip needed
    }

    @Test fun `searches the web when the article cannot answer`() = runTest {
        var call = 0
        val llm = FakeLlmEngine(responder = {
            call++
            if (call == 1) "NOT_IN_ARTICLE" else "Reuters reports equities fell after the decision."
        })
        val chat = StoryChat(llm, FakeSearchProvider(web))
        val ans = chat.ask(article, "How did markets react?", "")

        assertThat(ans.text).contains("equities fell")
        assertThat(ans.extraSources).containsAtLeast("Reuters", "The Guardian")
    }

    @Test fun `a phrased miss also triggers a web search`() = runTest {
        var call = 0
        val llm = FakeLlmEngine(responder = {
            call++
            if (call == 1) "The article does not say anything about that." else "Found via the web."
        })
        val chat = StoryChat(llm, FakeSearchProvider(web))
        val ans = chat.ask(article, "What happened next?", "")
        assertThat(ans.extraSources).isNotEmpty()
    }

    @Test fun `no web results yields a helpful message, not a dead end`() = runTest {
        val chat = StoryChat(FakeLlmEngine(responder = { "NOT_IN_ARTICLE" }), FakeSearchProvider(emptyList()))
        val ans = chat.ask(article, "Anything else?", "")
        assertThat(ans.text).contains("Get more sources")
    }

    @Test fun `search query is anchored to the story`() {
        val chat = StoryChat(FakeLlmEngine(), FakeSearchProvider(emptyList()))
        val q = chat.searchQuery(article, "how did markets react?")
        assertThat(q).contains("Central bank")
        assertThat(q).contains("markets")
    }
}
