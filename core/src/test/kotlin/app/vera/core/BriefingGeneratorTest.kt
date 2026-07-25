package app.vera.core

import app.vera.core.briefing.BriefingGenerator
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.model.Article
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BriefingGeneratorTest {

    private val article = Article(
        id = "a1", sourceId = "ex",
        title = "City council approves new transit budget",
        body = "The council voted to fund three new lines. Supporters say it cuts commute times. " +
            "Critics question the cost. The plan starts next year.",
        url = "https://ex.org/a1"
    )

    @Test fun `maps well-formed model JSON into a briefing item`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine())   // default responder returns valid JSON
        val item = gen.generate(article)

        assertThat(item.plainSummary).isNotEmpty()
        assertThat(item.quiz).isNotEmpty()
        val q = item.quiz.first()
        assertThat(q.correctIndex).isIn(q.options.indices.toList())
    }

    @Test fun `falls back deterministically when the model returns prose`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "Sorry, here is a plain answer with no JSON." }))
        val item = gen.generate(article)

        // Fallback summary is drawn from the article body, and a quiz is always present.
        assertThat(item.plainSummary).contains("council")
        assertThat(item.quiz).isNotEmpty()
        assertThat(item.manipulationWatch).isNotEmpty()
    }

    @Test fun `drops quiz options with an out-of-range correct index`() = runTest {
        val badJson = """
            {"summary":"s","whyItMatters":"w","manipulationWatch":"m",
             "quiz":[{"question":"q","options":["a","b"],"correctIndex":9,"explanation":"e"}]}
        """.trimIndent()
        val gen = BriefingGenerator(FakeLlmEngine(responder = { badJson }))
        val item = gen.generate(article)

        // The invalid question is filtered, but a safe default question backfills.
        assertThat(item.quiz).isNotEmpty()
        assertThat(item.quiz.first().correctIndex).isIn(item.quiz.first().options.indices.toList())
    }
}
