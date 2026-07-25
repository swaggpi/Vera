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

    @Test fun `uses the model's plain-text summary when it looks valid`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = {
            "The council approved money for three new transit lines starting next year. Supporters cite shorter commutes; critics question the cost."
        }))
        val item = gen.generate(article)
        assertThat(item.plainSummary).contains("transit lines")
        assertThat(item.quiz).isNotEmpty()
        val q = item.quiz.first()
        assertThat(q.correctIndex).isIn(q.options.indices.toList())
    }

    @Test fun `falls back to an article excerpt when the model returns JSON or junk`() = runTest {
        // FakeLlmEngine default responder returns a JSON blob (starts with '{') -> rejected -> excerpt.
        val gen = BriefingGenerator(FakeLlmEngine())
        val item = gen.generate(article)
        assertThat(item.plainSummary).contains("council")   // drawn from the article body
        assertThat(item.plainSummary).doesNotContain("{")
        assertThat(item.quiz).isNotEmpty()
    }

    @Test fun `quiz question is stable per article and valid`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "A plain valid summary of the story for readers." }))
        val a = gen.generate(article).quiz.first()
        val b = gen.generate(article).quiz.first()
        assertThat(a.question).isEqualTo(b.question)          // deterministic per article
        assertThat(a.correctIndex).isIn(a.options.indices.toList())
    }

    @Test fun `key points parse a bulleted list and strip markers`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = {
            "- The council funded three transit lines.\n2) Supporters expect shorter commutes.\n• Critics question the cost."
        }))
        val points = gen.keyPoints(article)
        assertThat(points).hasSize(3)
        assertThat(points[0]).isEqualTo("The council funded three transit lines.")
        assertThat(points.none { it.startsWith("-") || it.startsWith("•") }).isTrue()
    }

    @Test fun `key points fall back to article sentences on junk`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine())   // default returns JSON blob
        val points = gen.keyPoints(article)
        assertThat(points).isNotEmpty()
        assertThat(points.first()).contains("council")
    }

    @Test fun `answer returns model text, or a safe fallback on junk`() = runTest {
        val ok = BriefingGenerator(FakeLlmEngine(responder = { "The plan starts next year, according to the article." }))
        assertThat(ok.answer(article, "When does it start?", "")).contains("next year")

        val junk = BriefingGenerator(FakeLlmEngine(responder = { "{\"x\":1}" }))
        assertThat(junk.answer(article, "Anything?", "")).contains("Get more sources")
    }
}
