package app.vera.core.gamification

import app.vera.core.model.UserProgress

/** Streak & XP rules. Pure functions over [UserProgress] — fully unit-tested, no Android. */
object Gamification {

    const val XP_PER_BRIEFING = 20
    const val XP_PER_CORRECT_ANSWER = 5

    /**
     * Apply completion of a briefing on [todayEpochDay].
     * - same day again → XP still accrues, streak unchanged (no double-counting the day)
     * - consecutive day → streak + 1
     * - a gap → streak resets to 1
     */
    fun completeBriefing(
        progress: UserProgress,
        todayEpochDay: Long,
        correctAnswers: Int = 0
    ): UserProgress {
        val gainedXp = XP_PER_BRIEFING + correctAnswers * XP_PER_CORRECT_ANSWER
        val newStreak = when (progress.lastCompletedEpochDay) {
            todayEpochDay -> progress.streak.coerceAtLeast(1)
            todayEpochDay - 1 -> progress.streak + 1
            else -> 1
        }
        return progress.copy(
            streak = newStreak,
            longestStreak = maxOf(progress.longestStreak, newStreak),
            xp = progress.xp + gainedXp,
            lastCompletedEpochDay = todayEpochDay
        )
    }

    /** A streak is "alive" only if the last completion was today or yesterday. */
    fun isStreakActive(progress: UserProgress, todayEpochDay: Long): Boolean =
        progress.lastCompletedEpochDay >= todayEpochDay - 1 && progress.streak > 0

    /** Simple level curve: every 100 XP is a level. */
    fun level(xp: Int): Int = xp / 100 + 1
}
