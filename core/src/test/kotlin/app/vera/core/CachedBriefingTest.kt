package app.vera.core

import app.vera.core.briefing.CachedBriefing
import app.vera.core.briefing.CachedCard
import app.vera.core.briefing.slotForHour
import app.vera.core.model.BriefingSlot
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CachedBriefingTest {

    private val card = CachedCard(
        articleId = "a1", title = "Headline", url = "https://x/1", summary = "A summary.",
        manipulationWatch = "Check the source.", outlet = "BBC", country = "UK",
        ownership = "PUBLIC", leaning = "CENTER", alsoReportedBy = listOf("NPR")
    )

    @Test fun `round trips through json`() {
        val b = CachedBriefing("MORNING", "en", 1_000L, listOf(card))
        val decoded = CachedBriefing.decode(CachedBriefing.encode(b))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.cards).hasSize(1)
        assertThat(decoded.cards.first().outlet).isEqualTo("BBC")
        assertThat(decoded.cards.first().alsoReportedBy).containsExactly("NPR")
    }

    @Test fun `freshness expires after the window and empty is never fresh`() {
        val now = 100_000_000L
        val fresh = CachedBriefing("MORNING", "en", now - 60_000, listOf(card))
        val old = CachedBriefing("MORNING", "en", now - CachedBriefing.FRESH_FOR_MS - 1, listOf(card))
        val empty = CachedBriefing("MORNING", "en", now, emptyList())

        assertThat(fresh.isFresh(now)).isTrue()
        assertThat(old.isFresh(now)).isFalse()
        assertThat(empty.isFresh(now)).isFalse()
    }

    @Test fun `corrupt payload decodes to null rather than crashing`() {
        assertThat(CachedBriefing.decode("not json")).isNull()
    }

    @Test fun `language code round trips so a cache in the wrong language can be rejected`() {
        val de = CachedBriefing("MORNING", "de", 1_000L, listOf(card))
        assertThat(CachedBriefing.decode(CachedBriefing.encode(de))!!.languageCode).isEqualTo("de")
    }

    @Test fun `slot is chosen by hour of day`() {
        assertThat(slotForHour(7)).isEqualTo(BriefingSlot.MORNING)
        assertThat(slotForHour(13)).isEqualTo(BriefingSlot.MORNING)
        assertThat(slotForHour(14)).isEqualTo(BriefingSlot.EVENING)
        assertThat(slotForHour(22)).isEqualTo(BriefingSlot.EVENING)
    }
}

class AppLanguageTest {
    @Test fun `device preference resolves against the phone locale, falling back to English`() {
        assertThat(app.vera.core.model.AppLanguage.resolve(app.vera.core.model.AppLanguage.DEVICE, "de"))
            .isEqualTo(app.vera.core.model.AppLanguage.GERMAN)
        assertThat(app.vera.core.model.AppLanguage.resolve(app.vera.core.model.AppLanguage.DEVICE, "xx"))
            .isEqualTo(app.vera.core.model.AppLanguage.ENGLISH)
    }

    @Test fun `an explicit choice always wins over the device locale`() {
        assertThat(app.vera.core.model.AppLanguage.resolve(app.vera.core.model.AppLanguage.SWAHILI, "de"))
            .isEqualTo(app.vera.core.model.AppLanguage.SWAHILI)
    }

    @Test fun `codes are unique and round trip`() {
        val codes = app.vera.core.model.AppLanguage.entries.map { it.code }
        assertThat(codes).containsNoDuplicates()
        assertThat(app.vera.core.model.AppLanguage.fromCode("fr")).isEqualTo(app.vera.core.model.AppLanguage.FRENCH)
        assertThat(app.vera.core.model.AppLanguage.fromCode(null)).isEqualTo(app.vera.core.model.AppLanguage.DEVICE)
    }
}
