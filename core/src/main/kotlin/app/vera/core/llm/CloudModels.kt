package app.vera.core.llm

/**
 * Optional cloud back-ends. Vera is local-first: these exist only so a user who *chooses* to can
 * trade privacy for speed and answer quality, using **their own API key**.
 *
 * Note on subscriptions: a ChatGPT Plus / Claude Pro subscription cannot be used here. Anthropic
 * explicitly prohibits consumer-plan OAuth tokens in third-party tools (Free/Pro/Max OAuth is for
 * Claude Code and claude.ai only), and OpenAI likewise separates subscriptions from API billing.
 * Cloud use therefore requires a pay-per-token API key from console.anthropic.com / platform.openai.com.
 */
enum class CloudProvider(val label: String, val keyUrl: String, val keyPrefix: String) {
    ANTHROPIC("Anthropic (Claude)", "console.anthropic.com", "sk-ant-"),
    OPENAI("OpenAI (GPT)", "platform.openai.com", "sk-")
}

/** A selectable cloud model. [id] is the exact API model string. */
data class CloudModel(
    val provider: CloudProvider,
    val id: String,
    val label: String,
    val note: String
)

object CloudCatalog {
    val MODELS: List<CloudModel> = listOf(
        // Anthropic — Sonnet is the balanced default for summarising/coaching.
        CloudModel(CloudProvider.ANTHROPIC, "claude-sonnet-4-6", "Claude Sonnet 4.6", "balanced · recommended"),
        CloudModel(CloudProvider.ANTHROPIC, "claude-opus-4-8", "Claude Opus 4.8", "highest quality"),
        CloudModel(CloudProvider.ANTHROPIC, "claude-haiku-4-5", "Claude Haiku 4.5", "fastest · cheapest"),
        // OpenAI — GPT-5.6 family.
        CloudModel(CloudProvider.OPENAI, "gpt-5.6-terra", "GPT-5.6 Terra", "balanced · recommended"),
        CloudModel(CloudProvider.OPENAI, "gpt-5.6-sol", "GPT-5.6 Sol", "highest quality"),
        CloudModel(CloudProvider.OPENAI, "gpt-5.6-luna", "GPT-5.6 Luna", "fastest · cheapest"),
    )

    fun forProvider(p: CloudProvider) = MODELS.filter { it.provider == p }
    fun byId(id: String?) = MODELS.firstOrNull { it.id == id }
    fun default(p: CloudProvider) = forProvider(p).first()
}

/** What the user configured. [enabled] is false unless they explicitly opt in. */
data class CloudConfig(
    val enabled: Boolean = false,
    val provider: CloudProvider = CloudProvider.ANTHROPIC,
    val modelId: String = CloudCatalog.default(CloudProvider.ANTHROPIC).id,
    val hasKey: Boolean = false
) {
    /** Cloud is only usable when the user opted in *and* supplied a key. */
    val usable: Boolean get() = enabled && hasKey
}
