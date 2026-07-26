package app.vera.core

import app.vera.core.briefing.BriefingGenerator
import app.vera.core.llm.FakeLlmEngine
import app.vera.core.model.AppLanguage
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

    // Seen on device: a Tagesschau card rendered as
    // "Headline in target language: Against Water Shortage Ban".
    @Test fun `localized headline drops an instruction label the model echoed back`() = runTest {
        val german = article.copy(title = "Gegen Wassermangel: Verbot der Wasserentnahme")
        val gen = BriefingGenerator(FakeLlmEngine(responder = {
            "Headline in target language: Ban on water extraction against shortage"
        }))
        val title = gen.localizedTitle(german, AppLanguage.ENGLISH)
        assertThat(title).isEqualTo("Ban on water extraction against shortage")
    }

    @Test fun `localized headline keeps a real colon in the headline`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = {
            "Berlin: vehicle drives into crowd at Christopher Street Day"
        }))
        val title = gen.localizedTitle(article.copy(title = "Berlin: Fahrzeug fährt in Menschenmenge"),
            AppLanguage.ENGLISH)
        assertThat(title).isEqualTo("Berlin: vehicle drives into crowd at Christopher Street Day")
    }

    // "in" is as German as it is English — treating it as an English marker left German
    // headlines untranslated on device.
    @Test fun `a german headline containing 'in' is still translated`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "Vehicle drives into crowd in Berlin" }))
        val title = gen.localizedTitle(
            article.copy(title = "Fahrzeug fährt in Menschenmenge in Berlin"), AppLanguage.ENGLISH)
        assertThat(title).isEqualTo("Vehicle drives into crowd in Berlin")
    }

    // The Japan Times headline came back re-capitalised because an English outlet was still
    // being "translated" into English.
    @Test fun `an outlet already publishing in the reader's language is never translated`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "A Reworded, Title-Cased Headline" }))
        val title = gen.localizedTitle(article, AppLanguage.ENGLISH, sourceLanguage = "en")
        assertThat(title).isEqualTo(article.title)
    }

    @Test fun `an outlet publishing in another language is translated`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "Water extraction banned in Brandenburg" }))
        val title = gen.localizedTitle(
            article.copy(title = "Wasserentnahme in Brandenburg verboten"),
            AppLanguage.ENGLISH, sourceLanguage = "de")
        assertThat(title).isEqualTo("Water extraction banned in Brandenburg")
    }

    @Test fun `an english headline is passed through without asking the model`() = runTest {
        val english = article.copy(title = "City council approves the new transit budget")
        val gen = BriefingGenerator(FakeLlmEngine(responder = { "SHOULD NOT BE CALLED" }))
        assertThat(gen.localizedTitle(english, AppLanguage.ENGLISH)).isEqualTo(english.title)
    }

    @Test fun `localized headline takes the headline and drops trailing commentary`() = runTest {
        val gen = BriefingGenerator(FakeLlmEngine(responder = {
            "Ban on water extraction in Brandenburg\n\nNote: this is a faithful translation."
        }))
        val title = gen.localizedTitle(article.copy(title = "Verbot der Wasserentnahme"),
            AppLanguage.ENGLISH)
        assertThat(title).isEqualTo("Ban on water extraction in Brandenburg")
    }

    @Test fun `answer returns model text, or a safe fallback on junk`() = runTest {
        val ok = BriefingGenerator(FakeLlmEngine(responder = { "The plan starts next year, according to the article." }))
        assertThat(ok.answer(article, "When does it start?", "")).contains("next year")

        val junk = BriefingGenerator(FakeLlmEngine(responder = { "{\"x\":1}" }))
        assertThat(junk.answer(article, "Anything?", "")).contains("Get more sources")
    }
}
