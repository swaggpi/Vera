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

    // Seen on device: asked which countries landed on the D-Day beaches, the model replayed the
    // article's own sentences instead of admitting the article doesn't say.
    @Test fun `a reply copied verbatim from the article triggers a web search`() = runTest {
        val long = article.copy(
            body = "The central bank raised rates to 4.5% today, citing persistent inflation that " +
                "has stayed above target for eleven consecutive months across the whole economy."
        )
        var call = 0
        val llm = FakeLlmEngine(responder = {
            call++
            if (call == 1) long.body else "Reuters reports equities fell after the decision."
        })
        val chat = StoryChat(llm, FakeSearchProvider(web))
        val ans = chat.ask(long, "Which countries were affected?", "")

        assertThat(ans.text).contains("equities fell")
        assertThat(ans.extraSources).isNotEmpty()
    }

    @Test fun `an answer that quotes the article but adds to it is kept`() = runTest {
        val chat = StoryChat(
            FakeLlmEngine(responder = {
                "The central bank raised rates to 4.5% today, citing persistent inflation. That is the " +
                    "third rise this year and the steepest single step since the pandemic, so borrowers " +
                    "on variable deals feel it first and savers see rates follow more slowly."
            }),
            FakeSearchProvider(web)
        )
        val ans = chat.ask(article, "How much did rates go up?", "")
        assertThat(ans.text).contains("third rise")
        assertThat(ans.extraSources).isEmpty()
    }

    // Seen on device: this exact wording was shown to the reader as the final answer instead of
    // sending the question to search.
    @Test fun `an unlisted miss wording still triggers a web search`() = runTest {
        val missWordings = listOf(
            "The article does not provide specific information about which countries landed.",
            "This story doesn't cover what happened afterwards.",
            "The text fails to mention the countries involved."
        )
        for (wording in missWordings) {
            var call = 0
            val llm = FakeLlmEngine(responder = {
                call++
                if (call == 1) wording else "Reuters reports equities fell after the decision."
            })
            val ans = StoryChat(llm, FakeSearchProvider(web)).ask(article, "Who was involved?", "")
            assertThat(ans.extraSources).isNotEmpty()
        }
    }

    @Test fun `a real answer mentioning the article is not mistaken for a miss`() = runTest {
        val chat = StoryChat(
            FakeLlmEngine(responder = { "The article says the bank raised rates to 4.5% today." }),
            FakeSearchProvider(web)
        )
        val ans = chat.ask(article, "How much did rates go up?", "")
        assertThat(ans.text).contains("4.5%")
        assertThat(ans.extraSources).isEmpty()
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
