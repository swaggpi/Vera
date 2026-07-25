package app.vera.core

import app.vera.core.gamification.Gamification
import app.vera.core.gamification.SpacedRepetition
import app.vera.core.gamification.SrsCard
import app.vera.core.model.UserProgress
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GamificationTest {

    @Test fun `first completion starts a streak of one and grants xp`() {
        val p = Gamification.completeBriefing(UserProgress(), todayEpochDay = 100, correctAnswers = 2)
        assertThat(p.streak).isEqualTo(1)
        assertThat(p.longestStreak).isEqualTo(1)
        assertThat(p.xp).isEqualTo(Gamification.XP_PER_BRIEFING + 2 * Gamification.XP_PER_CORRECT_ANSWER)
        assertThat(p.lastCompletedEpochDay).isEqualTo(100)
    }

    @Test fun `consecutive day increments streak`() {
        var p = Gamification.completeBriefing(UserProgress(), 100)
        p = Gamification.completeBriefing(p, 101)
        assertThat(p.streak).isEqualTo(2)
        assertThat(p.longestStreak).isEqualTo(2)
    }

    @Test fun `same day again keeps streak but still adds xp`() {
        var p = Gamification.completeBriefing(UserProgress(), 100)
        val xp1 = p.xp
        p = Gamification.completeBriefing(p, 100)
        assertThat(p.streak).isEqualTo(1)
        assertThat(p.xp).isGreaterThan(xp1)
    }

    @Test fun `a gap resets the streak but preserves the record`() {
        var p = Gamification.completeBriefing(UserProgress(), 100)
        p = Gamification.completeBriefing(p, 101)   // streak 2
        p = Gamification.completeBriefing(p, 105)   // gap -> reset
        assertThat(p.streak).isEqualTo(1)
        assertThat(p.longestStreak).isEqualTo(2)
    }

    @Test fun `streak activity and levels`() {
        val p = Gamification.completeBriefing(UserProgress(), 100)
        assertThat(Gamification.isStreakActive(p, 101)).isTrue()
        assertThat(Gamification.isStreakActive(p, 103)).isFalse()
        assertThat(Gamification.level(0)).isEqualTo(1)
        assertThat(Gamification.level(250)).isEqualTo(3)
    }

    @Test fun `srs grows interval on success and resets on failure`() {
        val fresh = SrsCard(techniqueId = "urgency")
        val pass1 = SpacedRepetition.review(fresh, quality = 5, todayEpochDay = 10)
        assertThat(pass1.intervalDays).isEqualTo(1)
        assertThat(pass1.dueEpochDay).isEqualTo(11)

        val pass2 = SpacedRepetition.review(pass1, quality = 5, todayEpochDay = 11)
        assertThat(pass2.intervalDays).isEqualTo(6)

        val failed = SpacedRepetition.review(pass2, quality = 1, todayEpochDay = 17)
        assertThat(failed.intervalDays).isEqualTo(1)
        assertThat(failed.reps).isEqualTo(0)
        assertThat(SpacedRepetition.isDue(failed, 18)).isTrue()
    }
}
