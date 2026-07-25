package app.vera.core

import app.vera.core.insights.DietMeter
import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DietMeterTest {

    private fun src(id: String, country: String, own: Ownership) =
        NewsSource(id, id, country, country.take(2).uppercase(), "https://x/$id.rss", "en", own)

    @Test fun `single source is flagged as an echo chamber`() {
        val stats = DietMeter.compute(List(5) { src("bbc", "UK", Ownership.PUBLIC) })
        assertThat(stats.isEchoChamber).isTrue()
        assertThat(stats.dominantSource).isEqualTo("bbc")
        assertThat(stats.diversityScore).isEqualTo(0.0)
    }

    @Test fun `balanced varied diet scores high and is not an echo chamber`() {
        val stats = DietMeter.compute(
            listOf(
                src("bbc", "UK", Ownership.PUBLIC),
                src("npr", "USA", Ownership.PUBLIC),
                src("hindu", "India", Ownership.PRIVATE),
                src("nhk", "Japan", Ownership.PUBLIC)
            )
        )
        assertThat(stats.isEchoChamber).isFalse()
        assertThat(stats.dominantSource).isNull()
        assertThat(stats.diversityScore).isWithin(0.001).of(1.0)
        assertThat(stats.byCountry).hasSize(4)
    }

    @Test fun `empty diet is handled`() {
        val stats = DietMeter.compute(emptyList())
        assertThat(stats.total).isEqualTo(0)
        assertThat(stats.isEchoChamber).isFalse()
    }
}
