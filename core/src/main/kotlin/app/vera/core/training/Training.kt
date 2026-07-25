package app.vera.core.training

import app.vera.core.llm.LlmEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One step of the SIFT method as coached to the user. */
data class SiftStep(val label: String, val question: String)

/** Vera's non-verdict coaching for a claim: a lead-in, the four SIFT moves, and a closing nudge. */
data class SiftGuidance(
    val lead: String,
    val steps: List<SiftStep>,
    val closing: String
)

/**
 * Coaches a user through verifying a claim with the SIFT method — never a verdict. Uses the
 * on-device model when available and falls back to deterministic scripted guidance (ported from the
 * web prototype) so it always works. Scripted paths are what the unit tests pin down.
 */
class SiftCoach(
    private val llm: LlmEngine,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun coach(claim: String): SiftGuidance {
        val raw = llm.generate(prompt(claim), system = SYSTEM, maxTokens = 380)
        return parse(raw) ?: scripted(claim)
    }

    private fun prompt(claim: String) = """
        The user is unsure about this: "$claim".
        Coach them to check it with SIFT. Return ONLY JSON:
        {"lead": "...", "steps":[{"label":"Stop","question":"..."},
        {"label":"Investigate the source","question":"..."},
        {"label":"Find better coverage","question":"..."},
        {"label":"Trace","question":"..."}], "closing":"..."}
        Never say whether it is true or false.
    """.trimIndent()

    internal fun parse(raw: String): SiftGuidance? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start !in 0 until end) return null
        return try {
            val dto = json.decodeFromString<GuidanceDto>(raw.substring(start, end + 1))
            val steps = dto.steps.orEmpty()
                .filter { it.label.isNotBlank() && it.question.isNotBlank() }
                .map { SiftStep(it.label.trim(), it.question.trim()) }
            if (dto.lead.isNullOrBlank() || steps.size < 2) null
            else SiftGuidance(dto.lead.trim(), steps, dto.closing?.trim().orEmpty())
        } catch (_: Exception) {
            null
        }
    }

    /** Scenario-aware scripted coaching — the reliable fallback. */
    internal fun scripted(claim: String): SiftGuidance {
        val c = claim.lowercase()
        return when {
            Regex("shark|photo|image|picture|flood|storm").containsMatchIn(c) -> SiftGuidance(
                "Powerful images spread fastest — and old photos get reused for new events. Walk it back:",
                sift(
                    "That reaction is exactly what a recycled image relies on. Pause before sharing.",
                    "Who first posted it — a news outlet, or an anonymous account with no history?",
                    "Are established outlets reporting this same event and image, or only social posts?",
                    "Run a reverse image search (Google Lens, TinEye). When does the photo first appear?"
                ),
                "What date does the image actually trace back to?"
            )
            Regex("cure|miracle|doctor|vaccine|cancer|supplement|health|weight").containsMatchIn(c) -> SiftGuidance(
                "\"They don't want you to know\" is a sales pitch, not evidence. Check health claims slowly:",
                sift(
                    "Notice the fear-or-hope hook. Strong emotion is a signal to slow down.",
                    "Is there a named author with real credentials, or an anonymous site selling something?",
                    "What do health bodies (WHO, national services) and peer-reviewed studies say?",
                    "Is a real study linked? Who ran it, on how many people, and did others confirm it?"
                ),
                "Who benefits if you believe this — and does a reputable source back it up?"
            )
            Regex("clip|video|politician|speech|said|quote|footage").containsMatchIn(c) -> SiftGuidance(
                "Clips are the easiest thing to take out of context — or edit outright. A few seconds rarely tells all:",
                sift(
                    "A shocking quote is designed to be shared before it's checked. Hold off.",
                    "Who cut and posted this clip, and what's their angle?",
                    "Do reliable outlets report the same quote — and how do they describe the context?",
                    "Find the full, unedited video. What was said right before and after?"
                ),
                "Does the full context change what the clip seemed to say?"
            )
            else -> SiftGuidance(
                "Good instinct to pause. Let's not ask \"is it true?\" yet — let's ask better questions:",
                sift(
                    "What are you feeling right now? Content built to go viral targets emotion first.",
                    "Who is telling you this, and what might they gain? Open a tab and read about them.",
                    "Look away from the post. What do several independent, reputable sources say?",
                    "Follow any quote, statistic or image back to where it first appeared, in full context."
                ),
                "Try one step and tell me what you found — we'll take the next together."
            )
        }
    }

    private fun sift(stop: String, investigate: String, find: String, trace: String) = listOf(
        SiftStep("Stop", stop),
        SiftStep("Investigate the source", investigate),
        SiftStep("Find better coverage", find),
        SiftStep("Trace", trace)
    )

    @Serializable private data class GuidanceDto(
        val lead: String? = null,
        val steps: List<StepDto>? = null,
        val closing: String? = null
    )
    @Serializable private data class StepDto(val label: String = "", val question: String = "")

    companion object {
        const val SYSTEM = "You are Vera, a media-literacy coach. Never give a verdict. Coach with SIFT. Output only JSON."
    }
}

/** A prebunking challenge: spot the manipulation *technique*, not the specific hoax. */
data class TechniqueChallenge(
    val id: String,
    val claim: String,
    val source: String,
    val options: List<String>,
    val correctIndex: Int,
    val technique: String,
    val explanation: String
)

/** Inoculation content (ported from the web prototype). English; more languages later. */
object InoculationBank {
    val challenges: List<TechniqueChallenge> = listOf(
        TechniqueChallenge(
            "urgency",
            "\"SHOCKING truth THEY are hiding from you — share this NOW before it gets DELETED!!\"",
            "forwarded message",
            listOf("Balanced reporting", "Emotional bait + false urgency", "A verifiable statistic", "A named expert source"),
            1, "Manufactured urgency",
            "Capitals, secrecy and \"share before it's deleted\" push you to act on emotion before thinking. Urgency is a red flag, not evidence."
        ),
        TechniqueChallenge(
            "bandwagon",
            "\"97% of REAL patriots agree with this. Are you a real patriot, or not?\"",
            "social post",
            listOf("Bandwagon + in-group loyalty", "Peer-reviewed data", "Neutral survey result", "Primary document"),
            0, "Bandwagon / identity trap",
            "It pressures you to agree to belong, and dares you to prove your identity. The \"97%\" has no source."
        ),
        TechniqueChallenge(
            "decontext",
            "\"Photo proves the crisis in the capital today.\" (The image is from another country, in 2011.)",
            "viral image",
            listOf("Verified eyewitness photo", "Official government data", "Decontextualised image", "Satire clearly labelled"),
            2, "Decontextualised image",
            "The picture may be real — but it's from another place and time. A reverse image search reveals the origin in seconds."
        ),
        TechniqueChallenge(
            "fakeexpert",
            "\"Dr. J., a self-described health expert, warns this everyday food is toxic.\" (No study, no institution.)",
            "blog headline",
            listOf("Clinical trial", "Fake / unverifiable expert", "Government advisory", "Meta-analysis"),
            1, "Fake expert",
            "A confident title with no verifiable credentials or study behind it is a costume, not authority."
        )
    )
}

object InoculationScoring {
    fun isCorrect(challenge: TechniqueChallenge, pickedIndex: Int): Boolean =
        pickedIndex == challenge.correctIndex
}
