package app.vera.core

import app.vera.core.model.Ownership
import app.vera.core.research.Leaning
import app.vera.core.research.OutletDirectory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Display names shown in the "also checked:" line and the research source cards. An on-device run
 * surfaced machine-derived names ("Abcnews", "Lemonde", "Msn", "Usnews") — these tests pin the
 * directory entries that fixed them, plus the fallback used for domains we don't know.
 */
class OutletNameTest {

    private fun name(url: String) = OutletDirectory.forUrl(url).name

    @Test fun `outlets seen on device now have proper display names`() {
        assertThat(name("https://abcnews.go.com/US/story-id")).isEqualTo("ABC News")
        assertThat(name("https://www.lemonde.fr/international/article/x.html")).isEqualTo("Le Monde")
        assertThat(name("https://www.msn.com/en-gb/news/world/x")).isEqualTo("MSN")
        assertThat(name("https://www.usnews.com/news/world/articles/x"))
            .isEqualTo("U.S. News & World Report")
    }

    @Test fun `directory covers the common wires, broadcasters and papers`() {
        assertThat(name("https://www.cbsnews.com/news/x")).isEqualTo("CBS News")
        assertThat(name("https://www.nbcnews.com/politics/x")).isEqualTo("NBC News")
        assertThat(name("https://www.telegraph.co.uk/news/x")).isEqualTo("The Telegraph")
        assertThat(name("https://www.spiegel.de/politik/x")).isEqualTo("Der Spiegel")
        assertThat(name("https://elpais.com/internacional/x")).isEqualTo("El País")
        assertThat(name("https://www.scmp.com/news/china/x")).isEqualTo("South China Morning Post")
    }

    @Test fun `directory entries keep their ownership and press-freedom band`() {
        val abc = OutletDirectory.forUrl("https://abcnews.go.com/US/story-id")
        assertThat(abc.ownership).isEqualTo(Ownership.PRIVATE)
        assertThat(abc.leaning).isNotEqualTo(Leaning.UNKNOWN)

        val cgtn = OutletDirectory.forUrl("https://www.cgtn.com/news/x")
        assertThat(cgtn.ownership).isEqualTo(Ownership.STATE)
        assertThat(cgtn.pressFreedomTier).isEqualTo(5)
    }

    @Test fun `subdomains and deep hosts still resolve to the directory entry`() {
        assertThat(name("https://news.sky.com/story/x")).isEqualTo("Sky News")
        assertThat(name("https://timesofindia.indiatimes.com/world/x")).isEqualTo("The Times of India")
        assertThat(name("https://www.abc.net.au/news/x")).isEqualTo("ABC News (Australia)")
        assertThat(name("https://amp.theguardian.com/world/x")).isEqualTo("The Guardian")
    }

    @Test fun `fallback splits hyphenated domains into words`() {
        assertThat(name("https://some-local-news.example/post")).isEqualTo("Some Local News")
    }

    @Test fun `fallback strips two-part public suffixes instead of naming them`() {
        // the old fallback took the last two labels, so this read "Co"
        assertThat(name("https://www.newcastle-chronicle.co.uk/story/1"))
            .isEqualTo("Newcastle Chronicle")
    }

    @Test fun `fallback splits glued-on news words`() {
        assertThat(name("https://www.denverpost.example/news/1")).isEqualTo("Denver Post")
        assertThat(name("https://theolivepress.example/story")).isEqualTo("The Olive Press")
    }

    @Test fun `fallback separates trailing digits and uppercases initialisms`() {
        assertThat(name("https://kanal5.example/nyheter")).isEqualTo("Kanal 5")
        assertThat(name("https://www.kcrw.example/shows")).isEqualTo("KCRW")
    }

    @Test fun `fallback leaves short ordinary words alone`() {
        assertThat(name("https://gossipsite.example/story")).isEqualTo("Gossipsite")
        assertThat(name("https://vox.example/explainer")).isEqualTo("Vox")
    }

    @Test fun `fallback never claims to know the outlet`() {
        val p = OutletDirectory.forUrl("https://some-local-news.example/post")
        assertThat(p.ownership).isEqualTo(Ownership.UNKNOWN)
        assertThat(p.leaning).isEqualTo(Leaning.UNKNOWN)
        assertThat(p.pressFreedomTier).isEqualTo(3)
    }

    @Test fun `unusable urls degrade to a readable placeholder`() {
        assertThat(name("")).isEqualTo("Unknown source")
        assertThat(name("https:///story")).isEqualTo("Unknown source")
    }
}
