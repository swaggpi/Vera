package app.vera.core

import app.vera.core.model.Ownership
import app.vera.core.news.RssParser
import app.vera.core.news.SourceCatalog
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NewsTest {

    @Test fun `parses RSS 2 items`() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <item>
                <title>Flood hits the coast</title>
                <link>https://ex.org/a1</link>
                <description>&lt;p&gt;Heavy rain caused &lt;b&gt;flooding&lt;/b&gt;.&lt;/p&gt;</description>
              </item>
              <item>
                <title>Second story</title>
                <link>https://ex.org/a2</link>
                <description>Body two.</description>
              </item>
            </channel></rss>
        """.trimIndent()

        val out = RssParser.parse(xml, sourceId = "ex")
        assertThat(out).hasSize(2)
        assertThat(out[0].title).isEqualTo("Flood hits the coast")
        assertThat(out[0].url).isEqualTo("https://ex.org/a1")
        assertThat(out[0].body).doesNotContain("<")          // HTML stripped
        assertThat(out[0].body).contains("flooding")
    }

    @Test fun `parses Atom entries`() {
        val xml = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Atom headline</title>
                <link href="https://ex.org/atom1" rel="alternate"/>
                <summary>An atom summary.</summary>
              </entry>
            </feed>
        """.trimIndent()

        val out = RssParser.parse(xml, sourceId = "ex")
        assertThat(out).hasSize(1)
        assertThat(out[0].title).isEqualTo("Atom headline")
        assertThat(out[0].url).isEqualTo("https://ex.org/atom1")
    }

    @Test fun `malformed feed yields empty list, never throws`() {
        assertThat(RssParser.parse("not xml at all <<<", "ex")).isEmpty()
        assertThat(RssParser.parse("", "ex")).isEmpty()
    }

    @Test fun `catalog parses selectable sources`() {
        val json = """
          [
            {"id":"ard","name":"Tagesschau","country":"Germany","country_code":"DE",
             "rss_url":"https://www.tagesschau.de/xml/rss2","language":"de",
             "ownership":"public","press_freedom_tier":1,"default_on":true},
            {"id":"xh","name":"Xinhua","country":"China","country_code":"CN",
             "rss_url":"https://ex/cn.rss","language":"zh","ownership":"state","press_freedom_tier":5}
          ]
        """.trimIndent()

        val sources = SourceCatalog.parse(json)
        assertThat(sources).hasSize(2)
        assertThat(sources[0].ownership).isEqualTo(Ownership.PUBLIC)
        assertThat(sources[0].defaultOn).isTrue()
        assertThat(sources[1].ownership).isEqualTo(Ownership.STATE)
        assertThat(sources[1].pressFreedomTier).isEqualTo(5)
    }
}
