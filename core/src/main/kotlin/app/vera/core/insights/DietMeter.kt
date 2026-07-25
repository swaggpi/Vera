package app.vera.core.insights

import app.vera.core.model.NewsSource
import app.vera.core.model.Ownership
import kotlin.math.ln

data class DietStats(
    val total: Int,
    val byCountry: Map<String, Int>,
    val byOwnership: Map<Ownership, Int>,
    val diversityScore: Double,        // 0 (one-sided) .. 1 (perfectly spread)
    val dominantSource: String?,       // source name reading > 50% of the diet, else null
    val isEchoChamber: Boolean
)

/**
 * Turns the list of sources a user actually read into a news-diet picture, so the app can nudge
 * toward pluralism — a core Media & Information Literacy value. Diversity is normalized Shannon
 * entropy across sources.
 */
object DietMeter {

    fun compute(readSources: List<NewsSource>): DietStats {
        if (readSources.isEmpty()) {
            return DietStats(0, emptyMap(), emptyMap(), 0.0, null, false)
        }
        val total = readSources.size
        val bySource = readSources.groupingBy { it.name }.eachCount()
        val byCountry = readSources.groupingBy { it.country }.eachCount()
        val byOwnership = readSources.groupingBy { it.ownership }.eachCount()

        val diversity = normalizedEntropy(bySource.values, total)
        val (topName, topCount) = bySource.maxByOrNull { it.value }!!.toPair()
        val topShare = topCount.toDouble() / total
        val dominant = if (topShare > 0.5) topName else null

        return DietStats(
            total = total,
            byCountry = byCountry,
            byOwnership = byOwnership,
            diversityScore = diversity,
            dominantSource = dominant,
            isEchoChamber = bySource.size <= 1 || topShare > 0.6
        )
    }

    private fun normalizedEntropy(counts: Collection<Int>, total: Int): Double {
        if (counts.size <= 1) return 0.0
        val entropy = counts.sumOf {
            val p = it.toDouble() / total
            if (p > 0) -p * ln(p) else 0.0
        }
        return entropy / ln(counts.size.toDouble())
    }
}
