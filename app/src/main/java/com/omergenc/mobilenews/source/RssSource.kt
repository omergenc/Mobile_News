package com.omergenc.mobilenews.source

import com.omergenc.mobilenews.model.NewsItem
import com.omergenc.mobilenews.model.SourceConfig
import java.io.IOException

private const val MAX_ITEMS_PER_FEED = 40

internal fun feedEntriesToItems(
    entries: List<FeedParser.Entry>,
    config: SourceConfig,
    outlet: String,
    now: Long,
): List<NewsItem> = entries.take(MAX_ITEMS_PER_FEED).mapNotNull { e ->
    val title = stripHtml(e.title)
    if (title.isBlank()) return@mapNotNull null
    val link = e.link.trim()
    NewsItem(
        id = stableId(config.id, link.ifBlank { title }),
        sourceId = config.id,
        outlet = outlet,
        title = title,
        snippet = stripHtml(e.description).take(400),
        url = link,
        publishedAt = (e.date ?: now).coerceAtMost(now),
        fetchedAt = now,
    )
}

/** Plain RSS / Atom / RDF feed. */
class RssSource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> {
        val url = config.url ?: throw IOException("'url' tanımlı değil")
        val entries = FeedParser.parse(Http.getBytes(ctx.http, url))
        return feedEntriesToItems(entries, config, config.name, ctx.now)
    }
}

/**
 * Public Telegram channel, read through an RSS bridge the user runs (e.g. RSSHub:
 * `<bridge>/telegram/channel/<name>`). No HTML scraping is done by the app.
 */
class TelegramSource(override val config: SourceConfig) : NewsSource {
    override suspend fun fetch(ctx: SourceContext): List<NewsItem> {
        val bridge = ctx.settings.telegramBridgeUrl.trim().trimEnd('/')
        if (bridge.isEmpty()) throw IOException("Ayarlardan Telegram RSS köprüsü adresi girilmedi")
        val channel = config.channel ?: throw IOException("'channel' tanımlı değil")
        val entries = FeedParser.parse(Http.getBytes(ctx.http, "$bridge/telegram/channel/$channel"))
        return feedEntriesToItems(entries, config, config.name, ctx.now)
    }
}
