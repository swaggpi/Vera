package app.vera.data

import app.vera.core.model.Article

/** Offline fallback so the briefing always has content (first run, no network, or blocked feed). */
object SampleData {
    val articles = listOf(
        Article(
            id = "sample-1", sourceId = "sample",
            title = "Coastal city unveils plan to cut commute times",
            body = "The council approved funding for three new transit lines today. Supporters say the " +
                "plan will shorten average commutes and lower emissions. Critics question the budget and " +
                "timeline. Construction is expected to begin next year.",
            url = "https://example.org/transit"
        ),
        Article(
            id = "sample-2", sourceId = "sample",
            title = "Viral photo of flooded highway resurfaces after storm",
            body = "A dramatic image spreading online is being shared as proof of this week's flooding. " +
                "Fact-checkers note the same photo circulated after a different storm years ago. Officials " +
                "urge people to check the date and source before resharing images.",
            url = "https://example.org/flood-photo"
        ),
        Article(
            id = "sample-3", sourceId = "sample",
            title = "Health agency responds to claims about a 'miracle' supplement",
            body = "Posts promising rapid results from a supplement have gained traction. The national " +
                "health agency says there is no peer-reviewed evidence behind the claims and advises " +
                "consulting a professional. Experts note the emotional, urgent framing typical of scams.",
            url = "https://example.org/supplement"
        )
    )
}
