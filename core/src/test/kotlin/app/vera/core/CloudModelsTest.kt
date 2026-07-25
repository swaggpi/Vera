package app.vera.core

import app.vera.core.llm.CloudCatalog
import app.vera.core.llm.CloudConfig
import app.vera.core.llm.CloudProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudModelsTest {

    @Test fun `cloud is unusable unless enabled AND a key is present`() {
        assertThat(CloudConfig().usable).isFalse()                                   // default: off
        assertThat(CloudConfig(enabled = true, hasKey = false).usable).isFalse()     // opted in, no key
        assertThat(CloudConfig(enabled = false, hasKey = true).usable).isFalse()     // key, not opted in
        assertThat(CloudConfig(enabled = true, hasKey = true).usable).isTrue()
    }

    @Test fun `defaults are local-first`() {
        val c = CloudConfig()
        assertThat(c.enabled).isFalse()
        assertThat(c.hasKey).isFalse()
    }

    @Test fun `catalog offers models for both providers and ids are unique`() {
        val anthropic = CloudCatalog.forProvider(CloudProvider.ANTHROPIC)
        val openai = CloudCatalog.forProvider(CloudProvider.OPENAI)
        assertThat(anthropic).isNotEmpty()
        assertThat(openai).isNotEmpty()
        assertThat(anthropic.all { it.provider == CloudProvider.ANTHROPIC }).isTrue()
        assertThat(openai.all { it.provider == CloudProvider.OPENAI }).isTrue()
        val ids = CloudCatalog.MODELS.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test fun `sonnet is the anthropic default and lookup works`() {
        assertThat(CloudCatalog.default(CloudProvider.ANTHROPIC).id).isEqualTo("claude-sonnet-4-6")
        assertThat(CloudCatalog.byId("claude-sonnet-4-6")?.provider).isEqualTo(CloudProvider.ANTHROPIC)
        assertThat(CloudCatalog.byId("gpt-5.6-terra")?.provider).isEqualTo(CloudProvider.OPENAI)
        assertThat(CloudCatalog.byId("nope")).isNull()
    }
}
