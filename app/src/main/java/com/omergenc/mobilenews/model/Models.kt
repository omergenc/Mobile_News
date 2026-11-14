package com.omergenc.mobilenews.model

import kotlinx.serialization.Serializable

enum class Category(val label: String) {
    EKONOMI("Ekonomi"),
    SIYASET("Siyaset"),
    SPOR("Spor"),
    GENEL("Diğer"),
}

/** One entry from the sources config file (assets/sources.json). */
@Serializable
data class SourceConfig(
    val id: String,
    /** Plugin type: rss, bluesky, youtube, telegram, x, instagram. */
    val type: String,
    val name: String,
    val enabled: Boolean = true,
    val url: String? = null,
    val channel: String? = null,
    val channelId: String? = null,
    val actor: String? = null,
    val query: String? = null,
    val categoryHint: Category? = null,
    /** Every item from this source is about Galatasaray. */
    val galatasaray: Boolean = false,
    val note: String? = null,
)

@Serializable
data class SourcesFile(
    val version: Int = 1,
    val sources: List<SourceConfig>,
)

/** A single fetched post / article. */
@Serializable
data class NewsItem(
    val id: String,
    val sourceId: String,
    /** Who published it; distinct outlets decide whether a story counts as confirmed. */
    val outlet: String,
    val title: String,
    val snippet: String,
    val url: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    /** Social media post rather than an article. */
    val social: Boolean = false,
    val category: Category = Category.GENEL,
    val galatasaray: Boolean = false,
)

@Serializable
data class StoredSummary(
    val memberIds: Set<String>,
    val text: String,
    val category: Category? = null,
    val galatasaray: Boolean? = null,
    val itemCount: Int,
    val createdAt: Long,
)

@Serializable
data class NewsStore(
    val items: List<NewsItem> = emptyList(),
    val summaries: List<StoredSummary> = emptyList(),
    val notified: Set<String> = emptySet(),
    val lastRefresh: Long = 0L,
    val lastErrors: List<String> = emptyList(),
)

/** A cluster of items describing the same event. */
data class Story(
    val id: String,
    val title: String,
    val summary: String?,
    val category: Category,
    val galatasaray: Boolean,
    val items: List<NewsItem>,
    val outletCount: Int,
    val firstAt: Long,
    val latestAt: Long,
) {
    /** Only one outlet reports it — shown as "teyit edilmedi". */
    val unconfirmed: Boolean get() = outletCount < 2
}
