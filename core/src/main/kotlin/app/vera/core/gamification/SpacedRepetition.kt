package app.vera.core.gamification

/**
 * A manipulation-technique flashcard scheduled with an SM-2-lite algorithm, so inoculation
 * ("prebunking") actually sticks: techniques you struggle with resurface sooner.
 */
data class SrsCard(
    val techniqueId: String,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val dueEpochDay: Long = 0L
)

object SpacedRepetition {

    /**
     * Review a card with [quality] 0..5 (>=3 is a pass), on [todayEpochDay].
     * Passing grows the interval by ease; failing resets it to be seen again tomorrow.
     */
    fun review(card: SrsCard, quality: Int, todayEpochDay: Long): SrsCard {
        val q = quality.coerceIn(0, 5)
        if (q < 3) {
            return card.copy(reps = 0, intervalDays = 1, dueEpochDay = todayEpochDay + 1)
        }
        val newEase = (card.ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
            .coerceAtLeast(1.3)
        val reps = card.reps + 1
        val interval = when (reps) {
            1 -> 1
            2 -> 6
            else -> Math.round(card.intervalDays * newEase).toInt().coerceAtLeast(1)
        }
        return card.copy(
            reps = reps,
            ease = newEase,
            intervalDays = interval,
            dueEpochDay = todayEpochDay + interval
        )
    }

    fun isDue(card: SrsCard, todayEpochDay: Long): Boolean = card.dueEpochDay <= todayEpochDay
}
