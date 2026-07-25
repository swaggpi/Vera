package app.vera.core

import app.vera.core.llm.FakeLlmEngine
import app.vera.core.training.InoculationBank
import app.vera.core.training.InoculationScoring
import app.vera.core.training.SiftCoach
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TrainingTest {

    @Test fun `scripted coach picks the image scenario and never gives a verdict`() = runTest {
        // Model returns prose -> fallback to scripted guidance.
        val coach = SiftCoach(FakeLlmEngine(responder = { "no json here" }))
        val g = coach.coach("A viral photo of a shark on a flooded motorway after the storm")

        assertThat(g.steps).hasSize(4)
        assertThat(g.steps.map { it.label }).containsExactly(
            "Stop", "Investigate the source", "Find better coverage", "Trace"
        ).inOrder()
        assertThat(g.lead.lowercase()).doesNotContain("true")
        assertThat(g.lead.lowercase()).doesNotContain("false")
    }

    @Test fun `generic scenario handles an unknown claim`() = runTest {
        val coach = SiftCoach(FakeLlmEngine(responder = { "prose" }))
        val g = coach.coach("Something my neighbour mentioned about interest rates")
        assertThat(g.steps).hasSize(4)
        assertThat(g.closing).isNotEmpty()
    }

    @Test fun `parses well-formed model JSON guidance`() = runTest {
        val jsonReply = """
          {"lead":"Let's check it.","steps":[
            {"label":"Stop","question":"How do you feel?"},
            {"label":"Investigate","question":"Who says this?"}],
           "closing":"What did you find?"}
        """.trimIndent()
        val coach = SiftCoach(FakeLlmEngine(responder = { jsonReply }))
        val g = coach.coach("some claim")
        assertThat(g.lead).isEqualTo("Let's check it.")
        assertThat(g.steps).hasSize(2)
    }

    @Test fun `inoculation bank is well-formed`() {
        assertThat(InoculationBank.challenges).isNotEmpty()
        InoculationBank.challenges.forEach { c ->
            assertThat(c.correctIndex).isIn(c.options.indices.toList())
            assertThat(c.options.size).isAtLeast(3)
            assertThat(c.explanation).isNotEmpty()
        }
    }

    @Test fun `inoculation scoring works`() {
        val c = InoculationBank.challenges.first()
        assertThat(InoculationScoring.isCorrect(c, c.correctIndex)).isTrue()
        assertThat(InoculationScoring.isCorrect(c, (c.correctIndex + 1) % c.options.size)).isFalse()
    }
}
